#!/usr/bin/env python3
"""sample_capsule_volume.py - importance-weighted volumetric sampling (Simulation Capsule, T4).

Reads the raw cell-centroid export from STAR-CCM+ (volume_raw.csv), draws N cells
without replacement with probability proportional to

    w_i = Volume_i * g_i ** alpha

where g is a gradient-magnitude field (grad_vel_mag or grad_tracer), and writes
samples.csv with a self-contained header. alpha = 0 reproduces volume-uniform
sampling (the naive baseline); larger alpha concentrates samples where gradients
live. Sampling uses the Efraimidis-Spirakis exponential-key method, exact for
weighted sampling without replacement, with a fixed seed for reproducibility.

The shipped vol_weight column makes the sample usable for arithmetic:
vol_weight_i = Volume_i / (N * p_i), so sum(f * vol_weight) over the sample
estimates the volume integral of f, and sum(vol_weight) itself should land near
the total domain volume (printed as a sanity check).

Usage:
  python sample_capsule_volume.py volume_raw.csv samples.csv --n 800 --alpha 1.0 --field grad_vel_mag --seed 42
  python sample_capsule_volume.py volume_raw.csv --stats-only --alpha 2.0

Standard library only. Windows-safe.
"""

import argparse
import csv
import heapq
import math
import random
import sys

FIELDS_OUT = ["x_D", "y_D", "z_D", "u_U", "v_U", "w_U", "cp", "jet_tracer"]
POS_KEYS = ("x", "y", "z")


def find_column(header, wanted):
    """Match a column by exact name (case-insensitive), ignoring units in parentheses."""
    wl = wanted.lower()
    for i, name in enumerate(header):
        base = name.strip().strip('"').split("(")[0].strip().lower()
        if base == wl:
            return i
    return None


def find_position_columns(header):
    idx = {}
    for i, name in enumerate(header):
        base = name.strip().strip('"').split("(")[0].strip().lower()
        if base in POS_KEYS and base not in idx:
            idx[base] = i
    if len(idx) == 3:
        return idx["x"], idx["y"], idx["z"]
    return None


def main():
    ap = argparse.ArgumentParser(description="Importance-weighted volumetric sampling for the Simulation Capsule.")
    ap.add_argument("infile", help="raw STAR-CCM+ XYZ table export (volume_raw.csv)")
    ap.add_argument("outfile", nargs="?", default=None, help="output samples.csv (omit with --stats-only)")
    ap.add_argument("--n", type=int, default=800, help="number of samples (default 800)")
    ap.add_argument("--alpha", type=float, default=1.0, help="importance exponent alpha (default 1.0; 0 = volume-uniform)")
    ap.add_argument("--field", default="grad_vel_mag", choices=["grad_vel_mag", "grad_tracer"],
                    help="gradient field used as importance (default grad_vel_mag)")
    ap.add_argument("--seed", type=int, default=42, help="random seed (default 42; record it)")
    ap.add_argument("--case", default="jet_r2_re100", help="case alias for the header")
    ap.add_argument("--stats-only", action="store_true", help="print statistics, write nothing")
    args = ap.parse_args()

    if not args.stats_only and args.outfile is None:
        ap.error("outfile is required unless --stats-only is given")

    rng = random.Random(args.seed)

    with open(args.infile, "r", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)

        pos = find_position_columns(header)
        if pos is None:
            sys.exit("ERROR: could not identify X, Y, Z position columns in the header.")
        ix, iy, iz = pos

        col = {}
        needed = ["u_U", "v_U", "w_U", "cp", "jet_tracer", "Volume", args.field]
        for name in needed:
            j = find_column(header, name)
            if j is None:
                sys.exit("ERROR: column '%s' not found in %s" % (name, args.infile))
            col[name] = j

        heap = []  # min-heap of (key, tiebreak, payload); keeps the N largest keys
        n_rows = 0
        n_zero_w = 0
        v_total = 0.0
        w_total = 0.0
        tracer_min = float("inf")
        tracer_max = float("-inf")
        wv_pairs = []  # (w_i, V_i) for concentration stats

        for row in reader:
            if not row:
                continue
            n_rows += 1
            V = float(row[col["Volume"]])
            g = float(row[col[args.field]])
            tr = float(row[col["jet_tracer"]])
            tracer_min = min(tracer_min, tr)
            tracer_max = max(tracer_max, tr)
            v_total += V

            if args.alpha == 0.0:
                w = V
            elif g > 0.0:
                w = V * (g ** args.alpha)
            else:
                w = 0.0

            wv_pairs.append((w, V))
            if w <= 0.0:
                n_zero_w += 1
                continue
            w_total += w

            # Efraimidis-Spirakis key: log(u)/w with u in (0, 1]; keep the N largest.
            u = 1.0 - rng.random()
            key = math.log(u) / w
            payload = (
                float(row[ix]), float(row[iy]), float(row[iz]),
                float(row[col["u_U"]]), float(row[col["v_U"]]), float(row[col["w_U"]]),
                float(row[col["cp"]]), tr, V, w,
            )
            if len(heap) < args.n:
                heapq.heappush(heap, (key, n_rows, payload))
            elif key > heap[0][0]:
                heapq.heapreplace(heap, (key, n_rows, payload))

    if n_rows == 0:
        sys.exit("ERROR: no data rows read.")

    n_sampled = len(heap)

    # Concentration: share of total importance and of volume in the top cells by w.
    wv_pairs.sort(key=lambda t: t[0], reverse=True)
    print("rows read: %d | zero-weight cells: %d (%.1f%%)" % (n_rows, n_zero_w, 100.0 * n_zero_w / n_rows))
    print("field %s, alpha %g, seed %d | total volume %.4f | total weight %.6g"
          % (args.field, args.alpha, args.seed, v_total, w_total))
    print("jet_tracer bounds over all cells: min %.6f, max %.6f" % (tracer_min, tracer_max))
    print("importance concentration (cells sorted by w):")
    cum_w = 0.0
    cum_v = 0.0
    marks = [0.001, 0.01, 0.05, 0.10, 0.25]
    mi = 0
    for k, (w, V) in enumerate(wv_pairs, start=1):
        cum_w += w
        cum_v += V
        while mi < len(marks) and k >= marks[mi] * n_rows:
            print("  top %5.1f%% of cells: %5.1f%% of importance, %5.1f%% of volume"
                  % (100 * marks[mi], 100 * cum_w / w_total if w_total else 0.0, 100 * cum_v / v_total))
            mi += 1
        if mi == len(marks):
            break

    if args.stats_only:
        return

    if n_sampled < args.n:
        print("WARNING: only %d cells with positive weight; sampled all of them." % n_sampled)

    rows = [item[2] for item in heap]
    rows.sort(key=lambda r: (r[0], r[1], r[2]))

    lines = []
    lines.append("# samples.csv | importance-weighted volumetric sample | %s" % args.case)
    lines.append("# field %s, alpha %g, N %d of %d cells, seed %d | p_i ~ Volume*%s^alpha, no replacement"
                 % (args.field, args.alpha, n_sampled, n_rows, args.seed, args.field))
    lines.append("# vol_weight: per-sample volume share, self-normalized so sum(vol_weight) = domain volume;"
                 " sum(f*vol_weight) estimates the volume integral of f | fields %.3f, vol_weight %.2e | nondimensional")
    lines.append(",".join(FIELDS_OUT + ["vol_weight"]))

    raw = [w_total * r[8] / (n_sampled * r[9]) for r in rows]
    coverage = sum(raw) / v_total if v_total else 0.0
    scale = (v_total / sum(raw)) if sum(raw) > 0 else 0.0
    for r, rw in zip(rows, raw):
        vol_weight = rw * scale
        vals = ["%.3f" % v for v in r[0:8]]
        vals.append("%.2e" % vol_weight)
        lines.append(",".join(vals))

    text = "\n".join(lines) + "\n"
    with open(args.outfile, "w", newline="\n") as f:
        f.write(text)

    print("sampled %d cells -> %s | %d characters" % (n_sampled, args.outfile, len(text)))
    print("coverage before normalization: %.3f (1.0 = the sample spans the whole domain;"
          " low values mean the far field is barely sampled at this alpha)" % coverage)
    print("vol_weight self-normalized: sum(vol_weight) = %.4f = domain volume by construction" % v_total)


if __name__ == "__main__":
    main()
