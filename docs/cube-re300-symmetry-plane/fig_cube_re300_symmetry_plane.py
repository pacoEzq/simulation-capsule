"""Two transient runs of a cube at Re = 300 started from different steady iterates: which symmetry plane breaks.

Reads the run-2 monitors in this folder and the run-1 monitors in ../cube-re300/, and writes
cube-re300-symmetry-plane-side-force.png and cube-re300-symmetry-plane-histories.png next to this script.
numpy and matplotlib only. Run from a clean clone:  python docs/cube-re300-symmetry-plane/fig_cube_re300_symmetry_plane.py
"""
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = Path(__file__).resolve().parent
RUN1 = HERE.parent / "cube-re300"
s1 = np.genfromtxt(RUN1 / "cube_re300_steady_monitors.csv", delimiter=",", names=True)
u1 = np.genfromtxt(RUN1 / "cube_re300_transient_monitors.csv", delimiter=",", names=True)
s2 = np.genfromtxt(HERE / "cube_re300_run2_steady_monitors.csv", delimiter=",", names=True)
u2 = np.genfromtxt(HERE / "cube_re300_run2_transient_monitors.csv", delimiter=",", names=True)

S = "#fcfcfb"; T1 = "#0b0b0b"; T2 = "#52514e"; G = "#e4e3df"; C1 = "#2a78d6"; C2 = "#eb6834"; K = "#9a9893"; K2 = "#52514e"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.edgecolor": G, "axes.labelcolor": T2,
                     "xtick.color": T2, "ytick.color": T2, "axes.facecolor": S, "figure.facecolor": S, "axes.titlecolor": T1})

# Figure 1: side-force vector, Cy horizontal and Cl vertical as in ../cube-re300.
m = s1["iteration"] > 1000
fig, ax = plt.subplots(figsize=(7.2, 7.0), dpi=200)
ax.axhline(0, color=G, lw=1, zorder=0); ax.axvline(0, color=G, lw=1, zorder=0)
ax.plot(s1["cy"][m], s1["cl"][m], color=K, lw=0.4, alpha=0.5, label="steady solver, iterations 1000 to 15000")
ax.plot(s2["cy"], s2["cl"], color=K2, lw=0.9, label="steady solver continued to 16110")
ax.plot(u1["cy"], u1["cl"], color=C1, lw=0.9, alpha=0.8, label="run 1, transient from iteration 15000")
ax.plot(u2["cy"], u2["cl"], color=C2, lw=0.9, alpha=0.8, label="run 2, transient from iteration 16110")
for u, c, txt, dx, dy in ((u1, C1, "run 1 start, 42°", 0.006, 0.006), (u2, C2, "run 2 start, -86°", 0.008, -0.022)):
    ax.plot(u["cy"][0], u["cl"][0], "o", ms=8, color=S, mec=c, mew=2, zorder=5)
    ax.annotate(txt, (u["cy"][0], u["cl"][0]), xytext=(u["cy"][0] + dx, u["cl"][0] + dy), color=T1, fontsize=10, ha="left" if dx > 0 else "right")
b1 = u1["tU_D"] > 300; b2 = u2["tU_D"] > 300
ax.annotate(f"run 1 settles: Cl {u1['cl'][b1].mean():+.3f}", (0.0, 0.13), xytext=(0.012, 0.135), color=C1, fontsize=10)
ax.annotate(f"run 2 settles: Cy {u2['cy'][b2].mean():+.3f}", (-0.13, 0.0), xytext=(-0.155, 0.028), color=C2, fontsize=10)
ax.set_aspect("equal"); ax.set_xlim(-0.16, 0.16); ax.set_ylim(-0.16, 0.16)
ax.set_xlabel("$C_y$"); ax.set_ylabel("$C_l$")
ax.set_title("Cube at Re = 300, same mesh: two starts, two symmetry planes", fontsize=11.5, loc="left")
for sp in ax.spines.values(): sp.set_color(G)
ax.legend(loc="lower right", frameon=False, fontsize=9, labelcolor=T1)
fig.tight_layout()
fig.savefig(HERE / "cube-re300-symmetry-plane-side-force.png", facecolor=S, metadata={"Software": None})
plt.close(fig)

# Figure 2: the component each run keeps, and the one it loses on a log scale.
fig, (a, b) = plt.subplots(2, 1, figsize=(8.0, 6.4), dpi=200, sharex=True)
for ax in (a, b):
    ax.axvspan(300, 725, color=G, alpha=0.6, lw=0, zorder=0)
    for sp in ax.spines.values(): sp.set_color(G)
a.plot(u1["tU_D"], u1["cl"], color=C1, lw=0.7, label="run 1, $C_l$")
a.plot(u2["tU_D"], -u2["cy"], color=C2, lw=0.7, alpha=0.85, label="run 2, $-C_y$")
a.set_ylabel("kept component"); a.set_ylim(0.0, 0.16)
a.legend(loc="lower right", frameon=False, fontsize=9.5, labelcolor=T1, ncol=2)
a.set_title("Cube at Re = 300: the side-force component each run keeps and loses", fontsize=11.5, loc="left")
b.semilogy(u1["tU_D"], np.abs(u1["cy"]), color=C1, lw=0.6, label="run 1, $|C_y|$")
b.semilogy(u2["tU_D"], np.abs(u2["cl"]), color=C2, lw=0.6, alpha=0.85, label="run 2, $|C_l|$")
b.set_ylim(1e-7, 1e-1); b.set_ylabel("lost component"); b.set_xlabel("t·U/D"); b.set_xlim(0, 725)
b.legend(loc="upper right", frameon=False, fontsize=9.5, labelcolor=T1, ncol=2)
fig.tight_layout()
fig.savefig(HERE / "cube-re300-symmetry-plane-histories.png", facecolor=S, metadata={"Software": None})
