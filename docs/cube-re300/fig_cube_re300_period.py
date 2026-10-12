"""Lift history and spectrum of a cube at Re = 300 in the transient run: the settled wake repeats every 71 t·U/D.

Reads cube_re300_transient_monitors.csv from this folder and writes cube-re300-transient-period.png next to it.
numpy and matplotlib only.
Run from a clean clone:  python docs/cube-re300/fig_cube_re300_period.py
"""
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = Path(__file__).resolve().parent
u = np.genfromtxt(HERE / "cube_re300_transient_monitors.csv", delimiter=",", names=True)

S = "#fcfcfb"; T1 = "#0b0b0b"; T2 = "#52514e"; G = "#e4e3df"; C1 = "#2a78d6"; C2 = "#eb6834"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.edgecolor": G, "axes.labelcolor": T2,
                     "xtick.color": T2, "ytick.color": T2, "axes.facecolor": S, "figure.facecolor": S, "axes.titlecolor": T1})
t = u["tU_D"]; dt = t[1] - t[0]; T = 71.0; settled = t > 300
fig, ax = plt.subplots(2, 1, figsize=(10.24, 6.4), dpi=200)

a = ax[0]
a.axvspan(0, 300, color=G, alpha=0.6, lw=0, zorder=0)
a.text(150, 0.155, "settling", ha="center", color=T2, fontsize=10)
for k in range(7):
    a.axvline(t[-1] - k * T, color=C2, lw=0.9, ls=(0, (4, 3)), zorder=1)
a.plot(t, u["cl"], color=C1, lw=0.8, zorder=2)
a.set_xlim(0, t[-1]); a.set_ylim(0, 0.17); a.set_xlabel("t·U/D"); a.set_ylabel("$C_l$")
a.set_title("Transient run: after settling, $C_l$ repeats every 71 t·U/D (dashed)", fontsize=11, loc="left")

x = u["cl"][settled] - u["cl"][settled].mean()
n = 8 * len(x)
P = np.abs(np.fft.rfft(x * np.hanning(len(x)), n)) ** 2
f = np.fft.rfftfreq(n, dt)
a = ax[1]
for k in range(1, 12):
    a.axvline(k / T, color=C2, lw=0.9, ls=(0, (4, 3)), zorder=1)
a.semilogy(f, P / P.max(), color=C1, lw=1.0, zorder=2)
a.annotate("St 0.028 = 2/71", (2 / T, 1), xytext=(0.036, 0.35), color=T1, fontsize=10)
a.annotate("St 0.099 = 7/71", (7 / T, 0.08), xytext=(0.105, 0.35), color=T1, fontsize=10)
a.set_xlim(0, 0.16); a.set_ylim(1e-5, 2); a.set_xlabel("St = fD/U"); a.set_ylabel("power of $C_l$ (normalized)")
a.set_title("Spectrum of $C_l$, t·U/D 300 to 725: every peak sits on a multiple of 1/71 (dashed)", fontsize=11, loc="left")
for a in ax:
    for sp in a.spines.values():
        sp.set_color(G)
fig.tight_layout()
fig.savefig(HERE / "cube-re300-transient-period.png", facecolor=S, metadata={"Software": None})
