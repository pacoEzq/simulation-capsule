#!/usr/bin/env python3
# sym_plane_angle.py: angle of the plane of the mean streamwise vortex pair at x = 2D against the angle
# of the mean side force, per case. Reads either the published files in <dir>:
#   cube_re300_<tag>_slice_x2.csv            y, z, omega_x_mean
#   cube_re300_<tag>_transient_monitors.csv  tU_D, cd, cl, cy
# or, under <dir>/<tag>/, the files written by sym_table.java:
#   <tag>_slice_x2.csv                       X, Y, Z and "Mean of omega_x D/U"
#   <tag>_cl_monitor.csv, <tag>_cy_Monitor.csv, <tag>_cd_monitor.csv  monitor histories (time, value)
# Plane: weighted (|w|) centroids of w > +f max|w| and w < -f max|w| (f = --frac, 0.1); angle of c+ - c-
# in (y, z) plus 90 deg. Force: atan2(mean cl, mean cy) over t > t0 = max(first time + 0.01, --tmin). Angles reduced to
# (-90, 90]. Prints a Markdown table and writes <dir>/sym_plane_angle.md; for tilt1 also
# <dir>/tilt1/tilt1_angle_history.csv (t, cy, cl, theta) and, with matplotlib, tilt1_angle_history.png.
# Usage: python3 sym_plane_angle.py --dir <root> [--tags aligned,rot30,...] [--frac 0.1] [--tmin 0]
import argparse
import csv
import math
import os
import sys

import numpy as np

FACES = {"rot30": (30.0, 120.0), "rot45": (45.0, 135.0)}  # every other tag: 0 and 90
MEAN_PREFIX = "Mean of"
FLAT = "cube_re300_{tag}_{what}.csv"
T0_OFFSET = 0.01


def reduce90(a):
    """Reduce an angle in degrees to (-90, 90]."""
    a = (a + 90.0) % 180.0 - 90.0
    return 90.0 if a == -90.0 else a


def shown(a):
    """reduce90 at the one decimal printed: -89.96 would print as -90.0, so it is 90.0."""
    a = reduce90(a)
    return 90.0 if round(a, 1) <= -90.0 else a


def read_csv(path):
    with open(path, newline="") as f:
        rows = list(csv.reader(f))
    head = [h.strip() for h in rows[0]]
    data = np.array([[float(v) for v in r] for r in rows[1:] if r and r[0].strip()], dtype=float)
    return head, data


def col(head, pred, what, path):
    for i, h in enumerate(head):
        if pred(h):
            return i
    sys.exit(f"sym_plane_angle: no {what} column in {path}: {head}")


def plane_angle(path, frac):
    cs, wmax, _ = lobes(path, frac)
    dy, dz = cs[0][0] - cs[1][0], cs[0][1] - cs[1][1]
    return reduce90(math.degrees(math.atan2(dz, dy)) + 90.0), wmax


def lobes(path, frac):
    """Weighted centroids (c+, c-) of the two lobes, max|w| and the (y, z, w) columns of a slice table."""
    head, d = read_csv(path)
    iy = col(head, lambda h: h.split()[0] in ("Y", "y"), "Y", path)
    iz = col(head, lambda h: h.split()[0] in ("Z", "z"), "Z", path)
    iw = col(head, lambda h: h.startswith(MEAN_PREFIX) or h == "omega_x_mean", "'" + MEAN_PREFIX + "'", path)
    y, z, w = d[:, iy], d[:, iz], d[:, iw]
    wmax = float(np.max(np.abs(w)))
    cs = []
    for sel in (w > frac * wmax, w < -frac * wmax):
        if not sel.any():
            sys.exit(f"sym_plane_angle: empty lobe in {path}")
        a = np.abs(w[sel])
        cs.append((np.sum(a * y[sel]) / a.sum(), np.sum(a * z[sel]) / a.sum()))
    return cs, wmax, (y, z, w)


def history(path):
    """Time and value of a monitor export: time column = 'Physical Time' (else any 'Time'), value = last other."""
    head, d = read_csv(path)
    it = next((i for i, h in enumerate(head) if "Physical Time" in h), None)
    if it is None:
        it = col(head, lambda h: "Time" in h, "time", path)
    iv = max(i for i in range(len(head)) if i != it)
    return d[:, it], d[:, iv]


def slice_file(root, tag):
    """Published slice in root if present, else the sym_table.java export under root/tag."""
    flat = os.path.join(root, FLAT.format(tag=tag, what="slice_x2"))
    return flat if os.path.exists(flat) else os.path.join(root, tag, f"{tag}_slice_x2.csv")


def monitor(root, tag, name):
    """Time and one of cd, cl, cy: from the published monitors file if present, else the STAR-CCM+ export."""
    flat = os.path.join(root, FLAT.format(tag=tag, what="transient_monitors"))
    if os.path.exists(flat):
        head, d = read_csv(flat)
        return d[:, col(head, lambda h: h == "tU_D", "tU_D", flat)], d[:, col(head, lambda h: h == name, name, flat)]
    star = {"cd": "cd_monitor", "cl": "cl_monitor", "cy": "cy_Monitor"}[name]
    return history(os.path.join(root, tag, f"{tag}_{star}.csv"))


def force(root, tag, tmin=0.0):
    tl, cl = monitor(root, tag, "cl")
    ty, cy = monitor(root, tag, "cy")
    t0 = max(min(tl[0], ty[0]) + T0_OFFSET, tmin)
    mcl, mcy = cl[tl > t0].mean(), cy[ty > t0].mean()
    return reduce90(math.degrees(math.atan2(mcl, mcy))), (tl, cl, ty, cy, t0)


def nearest_face(tag, theta):
    faces = FACES.get(tag, (0.0, 90.0))
    return min(faces, key=lambda f: abs(reduce90(theta - f)))


def tilt1_history(root, h):
    tl, cl, ty, cy, t0 = h
    cyi = dict(zip(np.round(ty, 9), cy))
    d = os.path.join(root, "tilt1")
    out = os.path.join(d if os.path.isdir(d) else root, "tilt1_angle_history.csv")
    rows = [(t, cyi[k], c) for t, c, k in zip(tl, cl, np.round(tl, 9)) if t > t0 and k in cyi]
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t", "cy", "cl", "theta"])
        for t, y, l in rows:
            w.writerow([f"{t:.6g}", f"{y:.6g}", f"{l:.6g}", f"{reduce90(math.degrees(math.atan2(l, y))):.4f}"])
    print(f"wrote {out} ({len(rows)} rows)")
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib not available, no PNG")
        return
    t = np.array([r[0] for r in rows])
    th = np.array([reduce90(math.degrees(math.atan2(r[2], r[1]))) for r in rows])
    fig, ax = plt.subplots(figsize=(7, 3.5))
    ax.plot(t, th, lw=0.8)
    ax.set_xlabel("t U/D")
    ax.set_ylabel("atan2(cl, cy) [deg]")
    ax.set_title("tilt1: angle of the side force")
    fig.tight_layout()
    png = out[:-4] + ".png"
    fig.savefig(png, dpi=150)
    print(f"wrote {png}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dir", required=True, help="folder with the published CSVs, or root with one folder per tag")
    p.add_argument("--tags", default="aligned,rot30,rot45,tilt1,tilt3,tilt3w")
    p.add_argument("--frac", type=float, default=0.1)
    p.add_argument("--tmin", type=float, default=0.0, help="force averages only over t > tmin")
    a = p.parse_args()
    lines = ["| tag | θ_plane | θ_force | Δ | nearest face normal | \\|ω̄x\\|max |",
             "|---|---|---|---|---|---|"]
    for tag in [s.strip() for s in a.tags.split(",") if s.strip()]:
        tp, wmax = plane_angle(slice_file(a.dir, tag), a.frac)
        tf, h = force(a.dir, tag, a.tmin)
        lines.append(f"| {tag} | {shown(tp):.1f}° | {shown(tf):.1f}° | {reduce90(tp - tf):.1f}° | "
                     f"{nearest_face(tag, tp):.0f}° | {wmax:.3g} |")
        if tag == "tilt1":
            tilt1_history(a.dir, h)
    text = "\n".join(lines) + "\n"
    print(text, end="")
    with open(os.path.join(a.dir, "sym_plane_angle.md"), "w") as f:
        f.write(text)


if __name__ == "__main__":
    main()
