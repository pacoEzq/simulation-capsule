"""Side-force vector of a cube at Re = 300 in the cross-flow plane: steady solver against a transient run on the same mesh.

Reads cube_re300_steady_monitors.csv and cube_re300_transient_monitors.csv from this folder and writes
cube-re300-steady-vs-transient-side-force.png next to them. numpy and matplotlib only.
Run from a clean clone:  python docs/cube-re300/fig_cube_re300_side_force.py
"""
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = Path(__file__).resolve().parent
s = np.genfromtxt(HERE / "cube_re300_steady_monitors.csv", delimiter=",", names=True)
u = np.genfromtxt(HERE / "cube_re300_transient_monitors.csv", delimiter=",", names=True)

def fmt(v):
    """Signed with three decimals, without a negative zero."""
    return f"{0.0 if abs(v) < 5e-4 else v:+.3f}"

def lowpass(x, pcut, dt, pad=300):
    """Zero-phase FFT low-pass with mirrored padding; pcut and dt in the same unit (here iterations)."""
    xp = np.concatenate([x[pad:0:-1], x, x[-2:-pad-2:-1]])
    X = np.fft.rfft(xp)
    f = np.fft.rfftfreq(len(xp), dt)
    X[f > 1 / pcut] = 0
    return np.fft.irfft(X, len(xp))[pad:pad + len(x)]

S = "#fcfcfb"; T1 = "#0b0b0b"; T2 = "#52514e"; G = "#e4e3df"; C1 = "#2a78d6"; C2 = "#eb6834"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.edgecolor": G, "axes.labelcolor": T2,
                     "xtick.color": T2, "ytick.color": T2, "axes.facecolor": S, "figure.facecolor": S, "axes.titlecolor": T1})
m = s["iteration"] > 1000
cys, cls = s["cy"][m], s["cl"][m]
t = u["tU_D"]; a = t <= 300; b = t > 300
fig, ax = plt.subplots(figsize=(7.2, 6.6), dpi=200)
ax.axhline(0, color=G, lw=1, zorder=0); ax.axvline(0, color=G, lw=1, zorder=0)
ax.plot(cys, cls, color=C1, lw=0.5, alpha=0.35)
ax.plot(lowpass(cys, 500, 10), lowpass(cls, 500, 10), color=C1, lw=2, label="steady solver, iterations 1000 to 15000")
ax.plot(u["cy"][a], u["cl"][a], color=C2, lw=1.2, alpha=0.45, ls=(0, (3, 2)), label="transient run, t·U/D 0 to 300 (settling)")
ax.plot(u["cy"][b], u["cl"][b], color=C2, lw=2, label="transient run, t·U/D 300 to 725")
for x, y, c in ((cys.mean(), cls.mean(), C1), (u["cy"][b].mean(), u["cl"][b].mean(), C2)):
    ax.plot(x, y, "o", ms=9, color=c, mec=S, mew=2, zorder=5)
ax.annotate(f"iteration mean\n({fmt(cys.mean())}, {fmt(cls.mean())})", (cys.mean(), cls.mean()), xytext=(-0.036, 0.006), color=T1, fontsize=10)
ax.annotate(f"time mean\n({fmt(u['cy'][b].mean())}, {fmt(u['cl'][b].mean())})", (u["cy"][b].mean(), u["cl"][b].mean()), xytext=(-0.080, 0.115), color=T1, fontsize=10)
ax.set_aspect("equal"); ax.set_xlim(-0.10, 0.10); ax.set_ylim(-0.09, 0.15)
ax.set_xlabel("$C_y$"); ax.set_ylabel("$C_l$")
ax.set_title("Cube at Re = 300, same mesh: side-force vector in the cross-flow plane", fontsize=11.5, loc="left")
for sp in ax.spines.values(): sp.set_color(G)
ax.legend(loc="lower left", frameon=False, fontsize=9.5, labelcolor=T1)
fig.tight_layout()
fig.savefig(HERE / "cube-re300-steady-vs-transient-side-force.png", facecolor=S, metadata={"Software": None})
