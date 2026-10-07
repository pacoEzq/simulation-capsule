#!/usr/bin/env python3
"""Guardian of the T7 chain: replays the window statistics of run_macro.java from
monitor_history_re<NNNN>.csv and checks them against summary.json.

Spec: T7-cadena-spec-interno v4, section 6.2. Standard library only.

Usage:  python check_window_stats.py work/cube_re<NNNN> [--sweep sweep.json]
        G04 and G09 compare the summary with the point's declarations in sweep.json ("declared",
        "regime_ref"), read from --sweep or else from sweep.json in the current directory.
Exit:   0 if every check passes, 1 otherwise, 2 on bad usage or no readable sweep.json.
"""
import csv
import json
import math
import re
import sys
from pathlib import Path

# ---- constants the spec fixes (v4 section 6.1); the summary must declare the same
SPEC = {
    "criterion": "window_stationarity",
    "criterion_version": 2,
    "discard_iterations": 1000,
    "window_iterations": 2000,
    "sampling_interval": 10,
    "max_iterations": 15000,
    "k_gate": 2.0,
    "k_sd": 3.0,
    "min_cycles": 10,
}
MAX_WINDOWS = 7
HYSTERESIS_SD = 0.1
CONVERGED_SD = 1.0e-4
MASS_IMBALANCE_LIMIT = 1.0e-3
MASS_IMBALANCE_GRACE = 100
REL_TOL = 1.0e-9

GATED = ["cd", "cl", "cy", "cp_base"]
PLANES = ["y0", "x1", "x2", "x4"]
FIELDS = ["cp", "u_over_U"]
MEAN_EXTRA = ["x_reattach", "recirculation_length_D", "LLM_u_over_U_axis_x2D"]
COMPONENTS = ["cd_pressure", "cd_friction", "mdot_in", "mdot_out"]   # 6.3
PLANE_MIN = ["LLM_%s_min_%s" % (f, p) for p in PLANES for f in FIELDS]
PLANE_MAX = ["LLM_%s_max_%s" % (f, p) for p in PLANES for f in FIELDS]
HEADER = (["iteration"] + GATED + ["mass_imbalance"] + COMPONENTS + MEAN_EXTRA
          + ["cp_min", "cp_stagnation"] + PLANE_MIN + PLANE_MAX)          # 31 columns
# 6.3: the five magnitudes that left the single sample -> (block, JSON key, CSV column, divide by rho*U*D^2)
SAMPLED_63 = [("forces", "cd_pressure", "cd_pressure", False),
              ("forces", "cd_friction", "cd_friction", False),
              ("mass", "mdot_in_over_rhoUD2", "mdot_in", True),
              ("mass", "mdot_out_over_rhoUD2", "mdot_out", True),
              ("mass", "mass_imbalance", "mass_imbalance", False)]

STATUSES = {"converged", "stationary", "no_steady_state", "diverged"}
WAKE_NOTE = ("mean of instantaneous lengths; not the recirculation length of the "
             "time-averaged field, which is out of scope for this part")
STOP_TEMPLATES = {
    "stationary": r"Window statistics of cd, cl, cy, cp_base stationary between iterations "
                  r"\d+-\d+ and \d+-\d+ \(max drift/SE = [-+0-9.Ee]+\)\.",
    # contract v10: n is the smaller cycle count of the two windows; the clause names the first test failed
    "no_steady_state": r"Reached \d+ iterations; (cd|cl|cy|cp_base) failed the stationarity gate "
                       r"in windows \d+-\d+ and \d+-\d+ "
                       r"\((n = \d+ cycles < \d+|drift/SE = \S+ > [-+0-9.Ee]+|sd log ratio = \S+ over tolerance)\)\.",
    "converged": r"All four coefficients below the steady floor at iteration \d+\.",
    "diverged": r"NaN / mass imbalance \S+ at iteration \d+\.",
}
# blocks whose flow magnitudes fall under the single-sample rule
SAMPLED_BLOCKS = ["forces", "pressure", "wake", "probes", "plane_extremes", "mass"]
COMPANION = ("_sd", "_window_iterations", "_n_windows", "_statistic")
# unit suffixes; the _per_ grammar (decision 85): <unit>[2|3](_per_<unit>[2|3])+, e.g. kg_per_m3
UNIT_SUFFIX = re.compile(r"_(m|s|kg|pa|m_s|kg_m3|pa_s|(?:m|s|kg|pa)[23]?(?:_per_(?:m|s|kg|pa)[23]?)+)$")


# ---- the macro's arithmetic, same order of operations as run_macro.java
def mean_of(xs):
    return sum(xs) / len(xs) if xs else float("nan")


def sd_of(xs, m):
    if len(xs) < 2:
        return float("nan")
    return math.sqrt(sum((x - m) * (x - m) for x in xs) / (len(xs) - 1))


def crossings(xs, m, band):
    hi, lo, state, count = m + band, m - band, 0, 0
    for v in xs:
        if v > hi:
            if state == -1:
                count += 1
            state = 1
        elif v < lo:
            if state == 1:
                count += 1
            state = -1
    return count


def close(a, b):
    na = a is None or (isinstance(a, float) and not math.isfinite(a))
    nb = b is None or (isinstance(b, float) and not math.isfinite(b))
    if na or nb:
        return na and nb
    return abs(a - b) <= REL_TOL * max(1.0, abs(a), abs(b))


class Report:
    def __init__(self):
        self.rows = []

    def check(self, cid, ok, what, detail=""):
        self.rows.append((cid, bool(ok), what, detail))

    def emit(self):
        bad = 0
        for cid, ok, what, detail in self.rows:
            if not ok:
                bad += 1
            line = "%s %s %s" % ("PASS" if ok else "FAIL", cid, what)
            print(line + (": " + detail if detail and not ok else ""))
        print("RESULT: %s (%d of %d checks failed)" % ("PASS" if bad == 0 else "FAIL", bad, len(self.rows)))
        return 0 if bad == 0 else 1


def first(items, n=4):
    items = list(items)
    s = ", ".join(items[:n])
    return s + (" (+%d more)" % (len(items) - n) if len(items) > n else "")


def replay(rows, conv):
    """Rebuild windows, gate and decision from the CSV exactly as run_macro does."""
    disc, win, samp = SPEC["discard_iterations"], SPEC["window_iterations"], SPEC["sampling_interval"]
    per_window = win // samp
    col = {n: i for i, n in enumerate(HEADER)}
    windows = {q: [] for q in GATED}          # list of (m, s, n_cycles, samples)
    pooled = {n: [] for n in HEADER[1:]}
    buf = {n: [] for n in HEADER[1:]}
    status, reason_fields, stop_it, gate = None, None, None, {}
    next_end = disc + win
    for r in rows:
        it = int(r[0])
        x = {q: r[col[q]] for q in GATED}
        mi = r[col["mass_imbalance"]]
        if any(math.isnan(v) or math.isinf(v) for v in list(x.values()) + [mi]):
            status, stop_it = "diverged", it
            break
        if it >= MASS_IMBALANCE_GRACE and abs(mi) > MASS_IMBALANCE_LIMIT:
            status, stop_it = "diverged", it
            break
        if it <= disc:
            continue
        for n in HEADER[1:]:
            buf[n].append(r[col[n]])
        if it < next_end:
            continue
        if any(len(buf[n]) != per_window for _, _, n, _ in SAMPLED_63):
            return None
        for q in GATED:
            w = buf[q]
            if len(w) != per_window:
                return None
            m = mean_of(w)
            s = sd_of(w, m)
            nc = crossings(w, m, HYSTERESIS_SD * s) // 2
            windows[q].append((m, s, nc, w[:]))
        for n in HEADER[1:]:
            pooled[n].extend(buf[n])
            buf[n] = []
        next_end += win
        k = len(windows["cd"])
        if all(windows[q][-1][1] <= CONVERGED_SD * max(abs(windows[q][-1][0]), 1.0) for q in GATED):
            status, stop_it = "converged", it
            break
        if k >= 2:
            gate = {}
            for q in GATED:
                a, b = windows[q][-2], windows[q][-1]
                n = min(a[2], b[2])
                drift = b[0] - a[0]
                if n < SPEC["min_cycles"]:
                    gate[q] = (drift, float("nan"), float("nan"), False)
                    continue
                se = math.sqrt((a[1] * a[1] + b[1] * b[1]) / n)
                dse = abs(drift) / se if se > 0 else (0.0 if drift == 0 else float("inf"))
                tol = SPEC["k_sd"] / math.sqrt(n)
                lr = abs(math.log(b[1] / a[1])) if a[1] > 0 and b[1] > 0 else float("inf")
                gate[q] = (drift, dse, lr / tol, dse <= SPEC["k_gate"] and lr <= tol)
            if all(g[3] for g in gate.values()):
                status, stop_it = "stationary", it
                break
        if k >= MAX_WINDOWS:
            status, stop_it = "no_steady_state", it
            break
    return {"status": status, "stop_it": stop_it, "windows": windows, "pooled": pooled, "gate": gate}


def main(argv):
    args = argv[1:]
    sweep_p = Path("sweep.json")
    if len(args) == 3 and args[1] == "--sweep":
        sweep_p = Path(args[2])
    elif len(args) != 1:
        print(__doc__.strip())
        return 2
    cap = Path(args[0])
    try:
        sweep = json.loads(sweep_p.read_text(encoding="utf-8"))
        declared_all = sweep.get("declared", {})
        regime_ref = sweep.get("regime_ref")
    except (OSError, ValueError, AttributeError) as e:
        print("check_window_stats: cannot read the declarations in %s: %s" % (sweep_p, e))
        return 2
    rep = Report()

    # G01 files
    summ_p = cap / "summary.json"
    csvs = sorted(cap.glob("monitor_history_re*.csv"))
    rep.check("G01", summ_p.is_file() and len(csvs) == 1, "summary.json and one monitor_history CSV",
              "summary=%s csv=%d" % (summ_p.is_file(), len(csvs)))
    if not (summ_p.is_file() and len(csvs) == 1):
        return rep.emit()
    s = json.loads(summ_p.read_text(encoding="utf-8"))
    conv = s.get("convergence", {})
    re_val = s.get("sweep", {}).get("value")
    rep.check("G01", csvs[0].name == "monitor_history_re%04d.csv" % (re_val or -1), "CSV name matches sweep.value",
              csvs[0].name)

    # G02 header, G03 rows
    with csvs[0].open(newline="", encoding="utf-8") as fh:
        rd = csv.reader(fh)
        header = next(rd, [])
        raw = [row for row in rd if row]
    rep.check("G02", header == HEADER, "CSV header, 31 columns in spec order",
              "got %d columns; first difference at %s" % (len(header), next(
                  (i for i, (a, b) in enumerate(zip(header, HEADER)) if a != b), min(len(header), len(HEADER)))))
    try:
        rows = [[float(v) for v in row] for row in raw]
        numeric = all(len(r) == len(HEADER) for r in rows)
    except ValueError:
        rows, numeric = [], False
    its = [int(r[0]) for r in rows]
    samp = SPEC["sampling_interval"]
    grid = its == list(range(samp, samp * len(its) + 1, samp))
    rep.check("G03", numeric and grid and its and its[-1] == conv.get("iterations"),
              "CSV rows numeric, every %d it., last row = convergence.iterations" % samp,
              "rows=%d last=%s iterations=%s" % (len(its), its[-1] if its else None, conv.get("iterations")))
    if header != HEADER or not numeric:
        return rep.emit()

    # G04 constants, regime
    diff = [k for k, v in SPEC.items() if conv.get(k) != v]
    rep.check("G04", not diff, "convergence constants equal the spec", first(diff))
    declared = declared_all.get(str(re_val), {})
    exp_regime = declared.get("regime_expected")
    rx = conv.get("regime_expected", {})
    status = conv.get("status")
    if exp_regime is None:
        exp_rx, mismatch = {"value": None, "source": "not declared"}, None
    else:
        exp_rx = {"value": exp_regime, "source": "declared", "ref": regime_ref}
        mismatch = status == "converged" and exp_regime != "steady"
    rep.check("G04", rx == exp_rx and "solver_regime_mismatch" in conv
              and conv["solver_regime_mismatch"] is mismatch and "time_basis" in conv,
              "regime_expected, solver_regime_mismatch, time_basis",
              "regime=%s expected=%s mismatch=%s" % (rx.get("value"), exp_regime, conv.get("solver_regime_mismatch")))

    # G05 enum and retired keys (period_iterations_last retired by contract v10)
    retired = []

    def walk(node, path):
        if isinstance(node, dict):
            for k, v in node.items():
                if k == "limit_cycle" or k.startswith("band_") \
                        or k in ("cd_band_last_500", "band_tolerance", "period_iterations_last"):
                    retired.append(path + k)
                walk(v, path + k + ".")
        elif isinstance(node, list):
            for v in node:
                walk(v, path)
        elif node == "limit_cycle" or node == "max_steps":
            retired.append(path.rstrip("."))
    walk(s, "")
    rep.check("G05", status in STATUSES, "status in the enum", str(status))
    rep.check("G05", not retired, "no limit_cycle, max_steps, band_* or period_iterations_last left", first(retired))

    # G06-G08 replay
    rp = replay(rows, conv)
    if rp is None:
        rep.check("G06", False, "every closed window holds 200 samples", "short window in CSV")
        return rep.emit()
    wins = rp["windows"]
    k = len(wins["cd"])
    rep.check("G06", k == conv.get("windows_closed") and k <= MAX_WINDOWS, "windows_closed replayed",
              "replay=%d summary=%s" % (k, conv.get("windows_closed")))
    qs = conv.get("quantities", {})
    bad = []
    for q in GATED:
        e = qs.get(q, {})
        wm, wsd = e.get("window_means", []), e.get("window_sds", [])
        if len(wm) != k or len(wsd) != k or not all(close(w[0], a) for w, a in zip(wins[q], wm)) \
                or not all(close(w[1], a) for w, a in zip(wins[q], wsd)):
            bad.append(q)
    rep.check("G06", not bad, "window_means and window_sds recomputed (1e-9 rel)", first(bad))
    bad = []
    for q in GATED:
        e = qs.get(q, {})
        if k:
            lw = wins[q][-1]
            if e.get("n_cycles_last") != lw[2]:
                bad.append(q + ".cycles")
        g = rp["gate"].get(q)
        if g:
            if not (close(g[0], e.get("drift_last")) and close(g[1], e.get("drift_over_se"))
                    and close(g[2], e.get("sd_log_ratio_over_tol")) and g[3] == e.get("pass")):
                bad.append(q + ".gate")
    rep.check("G07", not bad, "n_cycles, drift/SE, sd log ratio, pass recomputed", first(bad))
    rep.check("G08", rp["status"] == status and rp["stop_it"] == conv.get("iterations"),
              "stop decision replayed", "replay=%s@%s summary=%s@%s" % (
                  rp["status"], rp["stop_it"], status, conv.get("iterations")))
    tpl = STOP_TEMPLATES.get(status)
    rep.check("G08", tpl is not None and re.fullmatch(tpl, conv.get("stop_reason", "")) is not None,
              "stop_reason follows its template", conv.get("stop_reason", ""))

    # G09 uncertainty: present exactly when sweep.json declares it for the point, and equal to it
    exp_u = declared.get("uncertainty")
    if exp_u is not None:
        rep.check("G09", conv.get("uncertainty") == exp_u, "uncertainty equals the declared block",
                  json.dumps(conv.get("uncertainty"))[:80])
    else:
        rep.check("G09", "uncertainty" not in conv, "no uncertainty key where none is declared",
                  json.dumps(conv.get("uncertainty"))[:80])

    # G10-G12 published values
    pooled_path = status in ("stationary", "no_steady_state") and k > 0
    wi = k * SPEC["window_iterations"] if pooled_path else 1
    nw = k if pooled_path else (1 if status == "converged" else 0)
    stat_mean = "iteration_mean" if pooled_path else "instantaneous"
    last = dict(zip(HEADER, rows[-1]))
    pool = rp["pooled"]

    def quintet(block, key, series, kind, with_sd=True, scale=1.0):
        """Checks value and companions of one published magnitude; returns error labels.
        scale divides value and sd after pooling, as the macro does for mdot over rho*U*D^2."""
        errs = []
        b = s.get(block, {})
        if pooled_path:
            val = {"mean": mean_of, "min": min, "max": max}[kind](pool[series])
            sd = sd_of(pool[series], mean_of(pool[series]))
            stat = {"mean": "iteration_mean", "min": "window_min", "max": "window_max"}[kind]
        else:
            val, sd, stat = last[series], 0.0, "instantaneous"
        val, sd = val / scale, sd / scale
        if not close(val, b.get(key)):
            errs.append(key)
        if with_sd and not close(sd, b.get(key + "_sd")):
            errs.append(key + "_sd")
        if b.get(key + "_window_iterations") != wi or b.get(key + "_n_windows") != nw \
                or b.get(key + "_statistic") != stat:
            errs.append(key + "_triple")
        return [block + "." + e for e in errs]

    bad = []
    for q in ["cd", "cl", "cy"]:
        bad += quintet("forces", q, q, "mean")
    bad += quintet("pressure", "cp_base", "cp_base", "mean")
    ref = s.get("reference", {})
    try:
        mass_scale = ref["density_kg_per_m3"] * ref["velocity_m_per_s"] * ref["side_m"] * ref["side_m"]
    except (KeyError, TypeError):
        mass_scale = None
    for block, key, series, scaled in SAMPLED_63:
        if scaled and not mass_scale:
            bad.append("reference (rho*U*D^2 for %s)" % key)
            continue
        bad += quintet(block, key, series, "mean", scale=mass_scale if scaled else 1.0)
    rep.check("G10", not bad, "forces, cp_base and mass published as %s" % stat_mean, first(bad))

    bad = quintet("wake", "x_reattach_D", "x_reattach", "mean")
    bad += quintet("wake", "recirculation_length_D", "recirculation_length_D", "mean")
    pr = s.get("probes", {}).get("LLM_u_over_U_axis_x2D", {})
    pv = mean_of(pool["LLM_u_over_U_axis_x2D"]) if pooled_path else last["LLM_u_over_U_axis_x2D"]
    psd = sd_of(pool["LLM_u_over_U_axis_x2D"], pv) if pooled_path else 0.0
    if not (close(pv, pr.get("value")) and close(psd, pr.get("sd")) and pr.get("window_iterations") == wi
            and pr.get("n_windows") == nw and pr.get("statistic") == stat_mean
            and pr.get("position_D") == [2.0, 0.0, 0.0]):
        bad.append("probes.LLM_u_over_U_axis_x2D")
    if s.get("wake", {}).get("note") != WAKE_NOTE:
        bad.append("wake.note")
    rep.check("G11", not bad, "family A: wake lengths and probe, with wake note", first(bad))

    bad = quintet("pressure", "cp_min", "cp_min", "min", with_sd=False)
    bad += quintet("pressure", "cp_stagnation", "cp_stagnation", "max", with_sd=False)
    pe = s.get("plane_extremes", {})
    for p in PLANES:
        st_keys = set(pe.get(p, {}).keys())
        if st_keys != set(FIELDS):
            bad.append("plane_extremes.%s keys" % p)
        for f in FIELDS:
            lo_n, hi_n = "LLM_%s_min_%s" % (f, p), "LLM_%s_max_%s" % (f, p)
            lo = min(pool[lo_n]) if pooled_path else last[lo_n]
            hi = max(pool[hi_n]) if pooled_path else last[hi_n]
            pair = pe.get(p, {}).get(f, [None, None])
            if not (close(lo, pair[0]) and close(hi, pair[1])):
                bad.append("plane_extremes.%s.%s" % (p, f))
    if pe.get("statistic") != ("window_min_max" if pooled_path else "instantaneous") \
            or pe.get("window_iterations") != wi or pe.get("n_windows") != nw:
        bad.append("plane_extremes triple")
    rep.check("G12", not bad, "family B: cp_min, cp_stagnation, 16 plane extremes", first(bad))

    # G13 single-sample rule: every flow magnitude declares how it was obtained
    single = []
    if status != "converged":
        for block, key, _, _ in SAMPLED_63:
            b = s.get(block, {})
            if key not in b or (key + "_statistic") not in b:
                single.append(block + "." + key + " missing")
        for block in SAMPLED_BLOCKS:
            b = s.get(block, {})
            block_stat = "statistic" in b
            for key, v in b.items():
                if key in ("note", "statistic", "window_iterations", "n_windows") or key.endswith(COMPANION):
                    continue
                if isinstance(v, dict):
                    if not block_stat and "statistic" not in v:
                        single.append(block + "." + key)
                    continue
                if block_stat or (key + "_statistic") in b:
                    continue
                single.append(block + "." + key)
    rep.check("G13", not single, "no magnitude published from a single sample", first(single, 6))

    # G14 dimensionless outside reference
    dim = []

    def walk_units(node, path):
        if isinstance(node, dict):
            for key, v in node.items():
                if path == "" and key == "reference":
                    continue
                if UNIT_SUFFIX.search(key):
                    dim.append(path + key)
                walk_units(v, path + key + ".")
    walk_units(s, "")
    mass = s.get("mass", {})
    for key in mass:
        if key.startswith("mdot_") and not key.startswith(("mdot_in_over_rhoUD2", "mdot_out_over_rhoUD2")):
            dim.append("mass." + key)
    for key in ("mdot_in_over_rhoUD2", "mdot_out_over_rhoUD2"):
        if key not in mass:
            dim.append("mass." + key + " missing")
    rep.check("G14", not dim, "no dimensional key outside reference; mdot over rho*U*D^2", first(dim))

    return rep.emit()


if __name__ == "__main__":
    sys.exit(main(sys.argv))
