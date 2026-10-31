#!/usr/bin/env python3
# build_signals.py
# Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1). Reads the CSVs left by
# export_signals in work/signals_raw/ (cd, cl, p1_u, p1_v, p2_u, p2_v: tU_D plus one value column)
# and writes the signals/ layer in --out: phase_average.csv, spectrum.csv and signals.json.
# The window is the one of build_summary (period_window, imported): n whole periods of cl between
# the first upward crossing of cl - mean(cl) at or after t0 and the last at or before t1. Time is
# factored, not sampled: the phase average folds the n cycles onto 32 phases, the spectrum holds
# exactly n periods so the shedding line falls on bin n. numpy and the standard library only.
# Output files are written with LF line ends on every system. CSV headers follow SPEC 0.11 4.9.1:
# first line "# signals over tU/D a to b | ...", then one "# column name: ..." per column; the
# header names quantities, never tools, and signals.json is flat (reference, window, st, files).

import argparse
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_summary  # noqa: E402

SIGNALS = ("cd", "cl", "p1_u", "p1_v", "p2_u", "p2_v")
SPECTRUM_SIGNALS = ("cd", "cl", "p1_v")
# Multiple of the window St at which each signal is expected to peak: drag oscillates at twice
# the shedding frequency.
HARMONIC = {"cd": 2}
PHASES = 32
ST_MAX = 2.0
T0 = build_summary.T0
T1 = build_summary.T1
FMT = "%.6g"


def read_signals(signals_dir):
    out = {}
    for name in SIGNALS:
        t, y = build_summary.read_signal(os.path.join(signals_dir, name + ".csv"))
        out[name] = (np.asarray(t, dtype=float), np.asarray(y, dtype=float))
    return out


# Phase average: for cycle k (crossings[k] to crossings[k + 1]) and phase j, the signal linearly
# interpolated at t = crossings[k] + j / PHASES * (crossings[k + 1] - crossings[k]).
# Returns phase (PHASES,), mean over cycles (PHASES,), sample sd (ddof = 1) over cycles (PHASES,).
def phase_average(t, y, crossings, phases=PHASES):
    c = np.asarray(crossings, dtype=float)
    frac = np.arange(phases, dtype=float) / phases
    tt = c[:-1, None] + frac[None, :] * (c[1:, None] - c[:-1, None])  # (n_cycles, phases)
    vals = np.interp(tt.ravel(), t, y).reshape(tt.shape)
    ddof = 1 if len(c) > 2 else 0
    return frac, vals.mean(axis=0), vals.std(axis=0, ddof=ddof)


# PSD on exactly [t_start, t_end): the signal resampled by linear interpolation on N uniform points
# (N = round((t_end - t_start) / dt), the native step kept), mean removed, no taper, numpy.fft.rfft.
# One-sided, normalised so that sum(psd) == variance (ddof = 0) of the resampled signal.
# St_k = k / (t_end - t_start) with U = D = 1, so the shedding line sits on k = n_periods.
def spectrum(t, y, t_start, t_end, st_max=ST_MAX):
    dt = t[1] - t[0]
    n_pts = int(round((t_end - t_start) / dt))
    tt = t_start + (t_end - t_start) * np.arange(n_pts) / n_pts
    yy = np.interp(tt, t, y)
    yy = yy - yy.mean()
    x = np.fft.rfft(yy)
    psd = np.abs(x) ** 2 / n_pts ** 2
    psd[1:] *= 2.0
    if n_pts % 2 == 0:
        psd[-1] /= 2.0
    st = np.arange(len(psd)) / (t_end - t_start)
    keep = st <= st_max + 1e-12
    return st[keep], psd[keep], psd.sum()


def fmt_row(vals):
    return ",".join(FMT % v for v in vals)


# First header line in the written form of SPEC 3.6 / 4.9.1; `extra` is "<P> phases" or
# "st_max <v>". The second line says how the window was cut, naming the quantities only.
def window_header(w, extra):
    return ["# signals over tU/D %.3f to %.3f | %d periods, St %.4f | %s | nondimensional"
            % (w["t_start"], w["t_end"], w["n_periods"], w["st"], extra),
            "# window: %d whole periods of cl, from the first upward crossing of the fluctuation"
            " of cl about its mean over the search range tU/D %g to %g to the last upward crossing"
            " at or before %g" % (w["n_periods"], w["t0"], w["t1"], w["t1"])]


def write_csv(path, header, names, rows):
    lines = list(header) + [",".join(names)] + [fmt_row(r) for r in rows]
    text = "\n".join(lines) + "\n"
    with open(path, "w", newline="\n") as f:
        f.write(text)
    return os.path.getsize(path)


def build(signals_dir, out_dir, t0=T0, t1=T1, log=print):
    sig = read_signals(signals_dir)
    t_cl, cl = sig["cl"]
    crossings, n, a, b = build_summary.period_window(t_cl.tolist(), cl.tolist(), t0, t1)
    st_window = n / (b - a)
    w = {"t0": t0, "t1": t1, "t_start": a, "t_end": b, "n_periods": n, "st": st_window}
    sig_dir = os.path.join(out_dir, "signals")
    os.makedirs(sig_dir, exist_ok=True)

    # phase_average.csv
    pa_cols = {"phase": "j / %d, j = 0 to %d; phase 0 at each upward crossing of cl, phase j by"
                        " linear interpolation in time within each cycle" % (PHASES, PHASES - 1)}
    phase = None
    pa_rows = {}
    for name in SIGNALS:
        t, y = sig[name]
        phase, m, sd = phase_average(t, y, crossings)
        pa_rows[name + "_mean"] = m
        pa_rows[name + "_sd"] = sd
        pa_cols[name + "_mean"] = "mean over the %d cycles of %s at that phase" % (n, name)
        pa_cols[name + "_sd"] = ("sample standard deviation over the %d cycles of %s at that"
                                 " phase: the spread from one cycle to the next" % (n, name))
    pa_names = ["phase"] + [k for name in SIGNALS for k in (name + "_mean", name + "_sd")]
    header = window_header(w, "%d phases" % PHASES) + [
        "# column %s: %s" % (k, pa_cols[k]) for k in pa_names]
    pa_rows_list = [[phase[j]] + [pa_rows[k][j] for k in pa_names[1:]] for j in range(PHASES)]
    pa_path = os.path.join(sig_dir, "phase_average.csv")
    write_csv(pa_path, header, pa_names, pa_rows_list)

    # spectrum.csv
    sp_cols = {"St": "f L / U = k / T, k = 0, 1, ... while St is at most %g, T the length of the"
                     " window in units of L / U; the line of cl falls on bin %d,"
                     " that of cd on bin %d"
                     % (ST_MAX, n, 2 * n)}
    sp_data = {}
    for name in SPECTRUM_SIGNALS:
        t, y = sig[name]
        st, psd, total = spectrum(t, y, a, b)
        sp_data[name] = psd
        k = int(np.argmax(psd))
        sp_cols["psd_" + name] = (
            "one-sided power spectrum of %s over exactly the window, resampled by linear"
            " interpolation to even steps at the native step, mean removed, no taper; normalised"
            " so that the sum over all bins up to Nyquist equals the variance of %s over the"
            " window, and the rows published sum to the variance below st_max %g"
            % (name, name, ST_MAX))
        h = HARMONIC.get(name, 1)
        if h == 1:
            log("build_signals: %s peak at St = %.6g (bin %d), window St = n/(t_end - t_start)"
                " = %.6g, diff = %.3g" % (name, st[k], k, st_window, st[k] - st_window))
        else:
            log("build_signals: %s peak at St = %.6g (bin %d), %d x window St = %.6g (bin %d),"
                " diff = %d bins" % (name, st[k], k, h, h * st_window, h * n, k - h * n))
    sp_names = ["St"] + ["psd_" + name for name in SPECTRUM_SIGNALS]
    header = window_header(w, "st_max %g" % ST_MAX) + [
        "# column %s: %s" % (k, sp_cols[k]) for k in sp_names]
    sp_rows = [[st[i]] + [sp_data[name][i] for name in SPECTRUM_SIGNALS] for i in range(len(st))]
    sp_path = os.path.join(sig_dir, "spectrum.csv")
    write_csv(sp_path, header, sp_names, sp_rows)

    # signals.json, flat, as SPEC 4.9.1 lists it; columns repeat the header text column by column
    info = {
        "reference": "cl",
        "search_tU_over_L": [t0, t1],
        "window_tU_over_L": [a, b],
        "n_periods": n,
        "st": st_window,
        "phases": PHASES,
        "st_max": ST_MAX,
        "signals": list(SPECTRUM_SIGNALS),  # the signals both files carry
        "files": {
            "phase_average.csv": {"rows": PHASES, "columns": pa_cols},
            "spectrum.csv": {"rows": len(st), "columns": sp_cols},
        },
    }
    json_path = os.path.join(sig_dir, "signals.json")
    with open(json_path, "w", newline="\n") as f:
        json.dump(info, f, indent=2)
        f.write("\n")
    log("build_signals: wrote %s, %s, %s" % (pa_path, sp_path, json_path))
    return info


def main(argv=None):
    ap = argparse.ArgumentParser(description="Write signals/ from work/signals_raw/.")
    ap.add_argument("--signals", default=os.path.join("work", "signals_raw"))
    ap.add_argument("--out", required=True, help="folder of the .sim; signals/ goes here")
    ap.add_argument("--t0", type=float, default=T0)
    ap.add_argument("--t1", type=float, default=T1)
    args = ap.parse_args(argv)
    build(args.signals, args.out, args.t0, args.t1)


if __name__ == "__main__":
    main(sys.argv[1:])
