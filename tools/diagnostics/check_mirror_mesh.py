#!/usr/bin/env python3
"""
check_mirror_mesh.py — mirror-symmetry test for the delta wing mesh.

Runs on a plain XYZ table exported from STAR-CCM+ right after meshing. No
solution needed: this measures the geometry and the mesh, not the flow.

Three independent checks, from coarse to fine:

  1. BULK      cell (or face) count and total weight per side, and the
               relative difference between halves. Catches a chamfer applied
               with different distances left and right.
  2. STATION   the same, binned in x. Localizes where the asymmetry lives:
               an apex defect shows up in the first bins and fades.
  3. PAIRING   for every point at y > 0, the distance to the nearest point of
               the mirrored port set. On a mesh whose template is aligned to
               y = 0 this should be at machine level. Anything above a small
               fraction of the local cell size means the mesh itself is not
               a mirror, independently of the geometry.

Check 3 is the strict one and the only one that cannot be passed by accident:
two halves can carry the same area and still be shaped differently.

Usage:
  python check_mirror_mesh.py wing_faces.csv
  python check_mirror_mesh.py cells_slab.csv --weight Volume --stations 0.05 1.00 0.05
  python check_mirror_mesh.py wing_faces.csv --tol-weight 0.001 --tol-pair 0.02

Dependencies: numpy only.
"""

import argparse
import csv
import numpy as np

SCRIPT_VERSION = "1.0 (2026-08-01, chat t5r-3)"


def find_col(header, *prefixes):
    low = [h.strip().lower() for h in header]
    for p in prefixes:
        for j, h in enumerate(low):
            if h.startswith(p.lower()):
                return j
    return None


def load(path, weight_name):
    with open(path, newline="") as f:
        r = csv.reader(f)
        header = next(r)
        rows = [row for row in r if row]
    arr = np.array(rows, dtype=float)
    if arr.ndim != 2 or arr.shape[0] == 0:
        raise SystemExit(f"[fatal] {path}: no data rows. Did you set the table "
                         f"representation to the volume mesh and run "
                         f"'Extract Data' before exporting?")
    jx, jy, jz = find_col(header, "x "), find_col(header, "y "), find_col(header, "z ")
    if None in (jx, jy, jz):
        raise SystemExit(f"[fatal] {path}: X/Y/Z columns not found in {header}")
    if weight_name:
        jw = find_col(header, weight_name)
    else:
        jw = find_col(header, "area", "volume")
    w = arr[:, jw] if jw is not None else np.ones(arr.shape[0])
    wname = header[jw] if jw is not None else "unit weight (no area/volume column)"
    return arr[:, jx], arr[:, jy], arr[:, jz], w, wname


def nearest_distances(a, b, cell):
    """For each row of a (n,3), distance to nearest row of b, via grid hashing.

    cell sets the bucket size; neighbours are searched in the 27 surrounding
    buckets, so any true nearest neighbour closer than `cell` is found.
    """
    from collections import defaultdict
    if len(b) == 0:
        return np.full(len(a), np.inf)
    idx = defaultdict(list)
    kb = np.floor(b / cell).astype(int)
    for i in range(len(b)):
        idx[(kb[i, 0], kb[i, 1], kb[i, 2])].append(i)
    ka = np.floor(a / cell).astype(int)
    out = np.empty(len(a))
    for i in range(len(a)):
        cand = []
        cx, cy, cz = ka[i]
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    cand.extend(idx.get((cx + dx, cy + dy, cz + dz), ()))
        if not cand:
            out[i] = np.inf
            continue
        d = b[cand] - a[i]
        out[i] = float(np.sqrt((d * d).sum(axis=1)).min())
    return out


def rel(a, b):
    m = max(abs(a), abs(b), 1e-30)
    return abs(a - b) / m


def main():
    p = argparse.ArgumentParser()
    p.add_argument("table", help="XYZ table exported after meshing")
    p.add_argument("--weight", default=None,
                   help="column to sum per side (default: Area or Volume)")
    p.add_argument("--stations", type=float, nargs=3, default=[0.05, 1.00, 0.05],
                   metavar=("X0", "X1", "DX"))
    p.add_argument("--tol-weight", type=float, default=0.002,
                   help="max relative difference between halves (default 0.2%%)")
    p.add_argument("--tol-pair", type=float, default=0.05,
                   help="max pairing distance as a fraction of local size")
    p.add_argument("--pair-sample", type=int, default=20000,
                   help="cap on points used for the pairing check")
    p.add_argument("--y-dead", type=float, default=1e-9,
                   help="ignore points with |y| below this")
    a = p.parse_args()

    x, y, z, w, wname = load(a.table, a.weight)
    print(f"check_mirror_mesh {SCRIPT_VERSION}")
    print(f"[table] {a.table}: {len(x)} rows; weight column: {wname}")
    print(f"  x {x.min():+.4f} .. {x.max():+.4f}   y {y.min():+.4f} .. {y.max():+.4f}"
          f"   z {z.min():+.4f} .. {z.max():+.4f}")

    sb = y > a.y_dead
    pt = y < -a.y_dead
    n_mid = len(x) - sb.sum() - pt.sum()

    # ---- 1. bulk
    print("\n[1] bulk")
    wsb, wpt = float(w[sb].sum()), float(w[pt].sum())
    d_w = rel(wsb, wpt)
    d_n = rel(sb.sum(), pt.sum())
    print(f"  count      starboard {sb.sum():>8d}   port {pt.sum():>8d}   "
          f"rel diff {d_n:.5f}")
    print(f"  weight     starboard {wsb:>12.6g}   port {wpt:>12.6g}   "
          f"rel diff {d_w:.5f}")
    if n_mid:
        print(f"  on y = 0   {n_mid} rows ignored")
    print(f"  z extent   starboard [{z[sb].min():+.5f}, {z[sb].max():+.5f}]   "
          f"port [{z[pt].min():+.5f}, {z[pt].max():+.5f}]")
    ok_bulk = d_w <= a.tol_weight and d_n <= a.tol_weight
    print(f"  -> {'PASS' if ok_bulk else 'FAIL'} (tolerance {a.tol_weight})")

    # ---- 2. per station
    x0, x1, dx = a.stations
    print(f"\n[2] per station (dx = {dx})")
    print(f"{'x':>6}{'n_sb':>8}{'n_pt':>8}{'d_n':>9}"
          f"{'w_sb':>13}{'w_pt':>13}{'d_w':>9}")
    worst_x, worst_d = None, 0.0
    for xc in np.arange(x0, x1 + 1e-9, dx):
        m = np.abs(x - xc) <= dx / 2
        ms, mp = m & sb, m & pt
        if not (ms.any() or mp.any()):
            continue
        ws, wp = float(w[ms].sum()), float(w[mp].sum())
        dw = rel(ws, wp)
        dn = rel(ms.sum(), mp.sum())
        if dw > worst_d:
            worst_d, worst_x = dw, float(xc)
        print(f"{xc:>6.2f}{ms.sum():>8d}{mp.sum():>8d}{dn:>9.5f}"
              f"{ws:>13.6g}{wp:>13.6g}{dw:>9.5f}")
    print(f"  worst station: x = {worst_x} with rel diff {worst_d:.5f}")

    # ---- 3. mirrored pairing
    print("\n[3] mirrored pairing")
    A = np.column_stack([x[sb], y[sb], z[sb]])
    B = np.column_stack([x[pt], -y[pt], z[pt]])
    if len(A) > a.pair_sample:
        step = len(A) // a.pair_sample + 1
        A = A[::step]
        print(f"  sampled {len(A)} of {int(sb.sum())} starboard points")
    scale = float(np.median(np.cbrt(w[sb]))) if wname.lower().startswith("volume") \
        else float(np.median(np.sqrt(w[sb]))) if "area" in wname.lower() \
        else 0.005
    d = nearest_distances(A, B, max(scale * 4, 1e-6))
    finite = d[np.isfinite(d)]
    if len(finite) == 0:
        print("  -> FAIL: no pairs found. Increase the bucket size or check the table.")
        return
    rel_d = finite / scale
    print(f"  local size estimate: {scale:.6g}")
    print(f"  distance / local size:  mean {rel_d.mean():.5f}   "
          f"p99 {np.percentile(rel_d, 99):.5f}   max {rel_d.max():.5f}")
    n_bad = int((rel_d > a.tol_pair).sum())
    print(f"  points above tolerance {a.tol_pair}: {n_bad} of {len(rel_d)} "
          f"({100.0 * n_bad / len(rel_d):.3f}%)")
    ok_pair = n_bad == 0
    print(f"  -> {'PASS' if ok_pair else 'FAIL'}")

    print("\n[verdict] " + ("mesh is a mirror within tolerance"
                           if (ok_bulk and ok_pair) else
                           "asymmetry detected, see the station table for where"))


if __name__ == "__main__":
    main()
