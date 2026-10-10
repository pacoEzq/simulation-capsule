"""Side-force histories of a cube at Re = 300: steady solver against iteration, transient run against t·U/D, same mesh.

Reads cube_re300_steady_monitors.csv and cube_re300_transient_monitors.csv from this folder and writes
cube-re300-steady-vs-transient-histories.png next to them. numpy and matplotlib only.
Run from a clean clone:  python docs/cube-re300/fig_cube_re300_histories.py
"""
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = Path(__file__).resolve().parent
s = np.genfromtxt(HERE / "cube_re300_steady_monitors.csv", delimiter=",", names=True)
u = np.genfromtxt(HERE / "cube_re300_transient_monitors.csv", delimiter=",", names=True)

S = "#fcfcfb"; T1 = "#0b0b0b"; T2 = "#52514e"; G = "#e4e3df"; C1 = "#2a78d6"; C2 = "#eb6834"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.edgecolor": G, "axes.labelcolor": T2,
                     "xtick.color": T2, "ytick.color": T2, "axes.facecolor": S, "figure.facecolor": S, "axes.titlecolor": T1})
fig, ax = plt.subplots(2, 1, figsize=(10.24, 6.4), dpi=200)
panels = ((ax[0], s, s["iteration"], "iteration (pseudo-time)", "Steady solver", "upper right"),
          (ax[1], u, u["tU_D"], "t·U/D", "Transient run, same mesh, started from the last steady iterate", "lower right"))
for a, d, x, xl, ttl, loc in panels:
    a.axhline(0, color=G, lw=1, zorder=0)
    a.plot(x, d["cl"], color=C1, lw=1.0, label="$C_l$")
    a.plot(x, d["cy"], color=C2, lw=1.0, label="$C_y$")
    a.set_xlabel(xl); a.set_title(ttl, fontsize=11, loc="left"); a.set_ylim(-0.11, 0.15)
    for sp in a.spines.values(): sp.set_color(G)
    a.legend(loc=loc, frameon=False, fontsize=10, ncol=2, labelcolor=T1)
ax[0].set_xlim(0, 15000); ax[1].set_xlim(0, 300)
fig.tight_layout()
fig.savefig(HERE / "cube-re300-steady-vs-transient-histories.png", facecolor=S, metadata={"Software": None})
