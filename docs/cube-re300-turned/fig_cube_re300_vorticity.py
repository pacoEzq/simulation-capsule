#!/usr/bin/env python3
# fig_cube_re300_vorticity.py: vorticity figure of the cube-re300-turned side run. 2x2 panels (--tags, four
# tags, default aligned, rot30, rot45, tilt3), each the time-mean streamwise vorticity at x = 2D read from
# cube_re300_<tag>_slice_x2.csv in <table>, or <table>/<tag>/<tag>_slice_x2.csv (sym_plane_angle.py), viewed from downstream
# (y right, z up, in D). Per panel: tricontourf of the mean omega_x with 256 levels on a diverging map
# symmetric about 0 (shared |w| limit, one colour bar); the cube section turned ROT (FACES of
# sym_plane_angle.py: rot30 30, rot45 45, every other tag 0); the four face normals ROT + k*90 in blue;
# the perpendicular bisector of the weighted lobe centroids (sym_plane_angle.lobes) as a solid black line;
# the mean side force atan2(<cl>, <cy>) over t > tmin as an orange arrow. Panel title: the configuration
# (aligned, turned 30°, ...); legend below each panel with the plane and force angles of sym_plane_angle.py,
# reduced to (-90, 90] as shown (a value that rounds to -90.0 is shown as 90.0); the arrow keeps the
# unreduced direction. <cd> is read and printed with the other means.
# Usage: python3 fig_cube_re300_vorticity.py --table <root> --out <png> [--tags a,b,c,d] [--tmin 725]
#        [--frac 0.1] [--extent 1.5] [--vmax 0 (auto)] [--dpi 200]
import argparse
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sym_plane_angle import FACES, force, lobes, monitor, plane_angle, reduce90, slice_file  # noqa: E402

import matplotlib  # noqa: E402
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import Polygon  # noqa: E402

FIG_IN = 16.0
CMAP = "RdBu_r"
NLEV = 256
C_NORMAL = "tab:blue"
C_FORCE = "tab:orange"
C_CUBE = "0.25"
NAMES = {"aligned": "aligned", "rot30": "turned 30°", "rot45": "turned 45°", "tilt1": "tilted 1°",
         "tilt3": "tilted 3°", "tilt3w": "tilted 3°, wide domain"}


def rot_of(tag):
    return FACES.get(tag, (0.0, 90.0))[0]


def shown(a):
    """Line angle in (-90, 90] at the one decimal printed: -89.96 would print as -90.0, so it is 90.0."""
    a = reduce90(a)
    return 90.0 if round(a, 1) <= -90.0 else a


def case(table, tag, frac, tmin):
    path = slice_file(table, tag)
    tp, wmax = plane_angle(path, frac)
    cs, _, ywz = lobes(path, frac)
    tf, (tl, cl, ty, cy, t0) = force(table, tag, tmin)
    mcl, mcy = cl[tl > t0].mean(), cy[ty > t0].mean()
    td, cd = monitor(table, tag, "cd")
    mcd = cd[td > t0].mean()
    print(f"{tag}: plane {shown(tp):.1f} deg, force {shown(tf):.1f} deg, <cd> {mcd:.4g}, <cl> {mcl:.4g}, <cy> {mcy:.4g}, "
          f"t0 {t0:.4g}, max|w| {wmax:.3g}")
    return dict(tag=tag, tp=tp, tf=tf, cs=cs, ywz=ywz, fdir=math.degrees(math.atan2(mcl, mcy)), wmax=wmax)


def panel(ax, c, vmax, ext):
    y, z, w = c["ywz"]
    keep = (np.abs(y) <= 1.3 * ext) & (np.abs(z) <= 1.3 * ext)
    levels = np.linspace(-vmax, vmax, NLEV)
    cf = ax.tricontourf(y[keep], z[keep], np.clip(w[keep], -vmax, vmax), levels=levels, cmap=CMAP,
                        extend="both")
    r = math.radians(rot_of(c["tag"]))
    rm = np.array([[math.cos(r), -math.sin(r)], [math.sin(r), math.cos(r)]])
    sq = np.array([[0.5, 0.5], [-0.5, 0.5], [-0.5, -0.5], [0.5, -0.5]]) @ rm.T
    ax.add_patch(Polygon(sq, closed=True, fill=False, ec=C_CUBE, lw=2.5, zorder=3))
    big = 2.0 * ext
    for k in range(4):
        a = r + k * math.pi / 2
        ax.plot([0.5 * math.cos(a), big * math.cos(a)], [0.5 * math.sin(a), big * math.sin(a)],
                color=C_NORMAL, lw=2.0, zorder=4, label="face normals" if k == 0 else None)
    (yp, zp), (yn, zn) = c["cs"]
    my, mz = 0.5 * (yp + yn), 0.5 * (zp + zn)
    a = math.radians(c["tp"])
    ax.plot([my - big * math.cos(a), my + big * math.cos(a)], [mz - big * math.sin(a), mz + big * math.sin(a)],
            color="k", lw=2.5, zorder=5, label=f"wake plane (bisector of ω̄x lobes) θ={shown(c['tp']):.1f}°")
    ax.plot([yp], [zp], "k+", ms=18, mew=3, zorder=6)
    ax.plot([yn], [zn], "k_", ms=18, mew=3, zorder=6)
    a = math.radians(c["fdir"])
    L = 0.8 * ext
    ax.annotate("", xy=(L * math.cos(a), L * math.sin(a)), xytext=(0.0, 0.0), zorder=7,
                arrowprops=dict(arrowstyle="-|>,head_width=0.6,head_length=1.2", color=C_FORCE, lw=4))
    ax.plot([], [], color=C_FORCE, lw=4, label=f"mean side force θ={shown(c['tf']):.1f}°")
    ax.set_xlim(-ext, ext)
    ax.set_ylim(-ext, ext)
    ax.set_aspect("equal")
    ax.set_xlabel("y / D")
    ax.set_ylabel("z / D")
    ax.set_title(NAMES.get(c["tag"], c["tag"]))
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.13), frameon=False)
    return cf


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--table", required=True, help="folder with the published CSVs, or root with one folder per tag (sym_table.java)")
    p.add_argument("--out", required=True, help="output PNG")
    p.add_argument("--tags", default="aligned,rot30,rot45,tilt3", help="four tags, panel order")
    p.add_argument("--tmin", type=float, default=725.0, help="force means only over t > tmin")
    p.add_argument("--frac", type=float, default=0.1, help="lobe threshold, as sym_plane_angle.py")
    p.add_argument("--extent", type=float, default=1.5, help="half-width of each panel in D")
    p.add_argument("--vmax", type=float, default=0.0, help="colour limit |mean omega_x|; 0 = max over panels")
    p.add_argument("--dpi", type=int, default=200)
    a = p.parse_args()
    tags = [s.strip() for s in a.tags.split(",") if s.strip()]
    if len(tags) != 4:
        sys.exit(f"fig_cube_re300_vorticity: --tags needs four tags, got {tags}")
    cases = [case(a.table, t, a.frac, a.tmin) for t in tags]
    vmax = a.vmax if a.vmax > 0 else max(c["wmax"] for c in cases)
    plt.rcParams.update({"font.size": 22, "axes.titlesize": 26, "axes.labelsize": 24,
                         "xtick.labelsize": 20, "ytick.labelsize": 20, "legend.fontsize": 18})
    fig, axs = plt.subplots(2, 2, figsize=(FIG_IN, 1.2 * FIG_IN), constrained_layout=True)
    for ax, c in zip(axs.flat, cases):
        cf = panel(ax, c, vmax, a.extent)
    cb = fig.colorbar(cf, ax=axs, location="right", shrink=0.6, ticks=np.linspace(-vmax, vmax, 5))
    cb.set_label("ω̄x D/U")
    fig.savefig(a.out, dpi=a.dpi)
    print(f"wrote {a.out} ({int(FIG_IN * a.dpi)} x {int(1.2 * FIG_IN * a.dpi)} px), vmax {vmax:.3g}")


if __name__ == "__main__":
    main()
