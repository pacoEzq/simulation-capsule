#!/usr/bin/env python3
"""
diag_station_clusters.py — every cluster at one station, before tracking.

The symmetry profile in features.json reports a per-station relative difference
between the two primary vortices. An isolated spike at one station has two
possible causes that look identical in the profile:

  A. CLUSTER SPLIT   the vortex was cut into two clusters on one side only, so
                     the named primary carries part of the circulation and the
                     rest went to a sibling cluster at the same station.
  B. TRACKING JUMP   the cluster is intact, but the continuity link attached it
                     to a different track, so the station was paired wrong.

They are told apart by looking at the raw clusters, before any tracking: under
A the side with the low value has two or more clusters whose sum matches the
other side; under B it has one cluster that already carries the full value.

This reuses the extraction functions from extract_features_delta.py, so the
clusters here are exactly the ones the extractor saw. No re-export needed.

Usage:
  python diag_station_clusters.py features_cloud.csv --x 0.30 --beta 0.10
  python diag_station_clusters.py features_cloud.csv --x 0.30 --neighbours

Dependencies: numpy, and extract_features_delta.py in the same folder.
"""

import argparse
import numpy as np

import extract_features_delta as ex

SCRIPT_VERSION = "1.0 (2026-08-01, chat t5r-3)"


def station_clusters(cloud, x_c, dx, beta, link_factor, gamma_min, slope):
    sel = np.abs(cloud["x"] - x_c) <= dx / 2
    return ex.analyze_station(cloud, sel, float(x_c), dx, beta,
                              link_factor, gamma_min, slope)


def report(x_c, clusters, qmax, gamma_min):
    print(f"\n=== station x = {x_c:.2f}   station max(Q) = {qmax:.5g} ===")
    for side, sgn in (("starboard", +1), ("port", -1)):
        cl = [c for c in clusters if (c["y"] > 0) == (sgn > 0)]
        cl.sort(key=lambda c: -abs(c["gamma_star"]))
        tot = sum(c["gamma_star"] for c in cl)
        print(f"\n  {side}: {len(cl)} clusters, sum Gamma* = {tot:+.5f}")
        if not cl:
            continue
        print(f"    {'#':>2}{'Gamma*':>10}{'sense':>7}{'y':>9}{'z':>9}"
              f"{'r_eff':>9}{'q_peak':>11}{'QC':>7}{'cells':>7}")
        for i, c in enumerate(cl):
            print(f"    {i:>2}{c['gamma_star']:>+10.5f}{c['sense']:>7d}"
                  f"{c['y']:>+9.4f}{c['z']:>+9.4f}{c['r_eff']:>9.4f}"
                  f"{c['q_star_max']:>11.4g}{c['qc_offset']:>7.3f}"
                  f"{c['n_cells']:>7d}")
        if len(cl) >= 2:
            top2 = cl[0]["gamma_star"] + cl[1]["gamma_star"]
            same = cl[0]["sense"] == cl[1]["sense"]
            gap = np.hypot(cl[0]["y"] - cl[1]["y"], cl[0]["z"] - cl[1]["z"])
            print(f"    top two: sum {top2:+.5f}, same sense {same}, "
                  f"centroid gap {gap:.4f} "
                  f"({gap / max(cl[0]['r_eff'], 1e-12):.2f} r_eff)")
    print(f"\n  (clusters below the circulation floor {gamma_min} are not listed)")


def verdict(clusters):
    """Compare the two sides and name the likely cause."""
    out = {}
    for side, sgn in (("starboard", +1), ("port", -1)):
        cl = sorted([c for c in clusters if (c["y"] > 0) == (sgn > 0)],
                    key=lambda c: -abs(c["gamma_star"]))
        out[side] = cl
    a, b = out["starboard"], out["port"]
    if not a or not b:
        print("\n[verdict] one side has no cluster above the floor; "
              "check the circulation floor and beta.")
        return
    g_a, g_b = abs(a[0]["gamma_star"]), abs(b[0]["gamma_star"])
    lo, hi = ("starboard", "port") if g_a < g_b else ("port", "starboard")
    low, high = (a, b) if g_a < g_b else (b, a)
    d1 = abs(g_a - g_b) / max(g_a, g_b)
    print(f"\n[verdict] leading clusters differ by {d1:.4f} "
          f"({lo} is the low side)")
    if len(low) >= 2 and low[0]["sense"] == low[1]["sense"]:
        s = abs(low[0]["gamma_star"] + low[1]["gamma_star"])
        d2 = abs(s - abs(high[0]["gamma_star"])) / abs(high[0]["gamma_star"])
        print(f"  adding the second same-sense cluster on the low side: "
              f"{s:.5f} vs {abs(high[0]['gamma_star']):.5f}, diff {d2:.4f}")
        if d2 < d1 / 2:
            print("  -> CLUSTER SPLIT. The vortex was cut in two on the low "
                  "side; the circulation is present but distributed. A method "
                  "artefact, not a flow asymmetry.")
            return
    print("  -> NOT a split at this station: the low side carries a single "
          "cluster and adding its neighbours does not close the gap. Either "
          "the tracking paired the station wrong, or the asymmetry is real. "
          "Compare the centroid positions above between sides.")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("cloud")
    p.add_argument("--x", type=float, required=True, help="station to inspect")
    p.add_argument("--beta", type=float, default=0.10)
    p.add_argument("--dx", type=float, default=0.05)
    p.add_argument("--z-cut", type=float, default=0.005)
    p.add_argument("--link-factor", type=float, default=2.5)
    p.add_argument("--gamma-min", type=float, default=0.005)
    p.add_argument("--semispan-slope", type=float, default=0.4663)
    p.add_argument("--neighbours", action="store_true",
                   help="also report the stations before and after")
    a = p.parse_args()

    print(f"diag_station_clusters {SCRIPT_VERSION}")
    required = ["x", "y", "z", "volume", "wx", "wy", "wz", "u", "v", "w", "q"]
    cloud = ex.load_table(a.cloud, required)
    keep = cloud["z"] >= a.z_cut
    for k in list(cloud.keys()):
        if not k.startswith("_"):
            cloud[k] = cloud[k][keep]
    print(f"[cloud] {a.cloud}: {int(keep.sum())} cells after z >= {a.z_cut}")
    print(f"[params] beta = {a.beta}, dx = {a.dx}, "
          f"gamma_min = {a.gamma_min}, link_factor = {a.link_factor}")

    xs = [a.x - a.dx, a.x, a.x + a.dx] if a.neighbours else [a.x]
    for xv in xs:
        clusters, qmax = station_clusters(
            cloud, xv, a.dx, a.beta, a.link_factor,
            a.gamma_min, a.semispan_slope)
        report(xv, clusters, qmax, a.gamma_min)
        if abs(xv - a.x) < 1e-9:
            verdict(clusters)


if __name__ == "__main__":
    main()
