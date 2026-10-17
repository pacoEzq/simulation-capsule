"""Figure for the cube literature-anchors article.

Reads cube_anchors_cd.csv and draws cube-anchors-cd.png: drag coefficient
against Reynolds number for the six capsules, the transient run at Re 300,
the points of Khan et al. (2020) and Saha (2004), and the two correlations
of the Clift / Haider-Levenspiel form, with the band between them shaded.
"""
import csv
import numpy as np
import matplotlib.pyplot as plt

DATA = "cube_anchors_cd.csv"


def corr(re, a, b, c, d, e):
    return 24 / re * (1 + a * re ** b) + c / (1 + d * re ** e)


KHAN = dict(a=1.059, b=0.09412, c=1.106, d=237.4, e=-1)   # Khan et al. 2020, Eq. 13, Re <= 900
HL = dict(a=0.2734, b=0.5510, c=1.406, d=762.39, e=-1)      # Haider and Levenspiel 1989, cube

rows = {}
with open(DATA, newline="") as f:
    for r in csv.DictReader(f):
        rows.setdefault(r["source"], []).append((float(r["Re"]), float(r["cd"])))

re = np.logspace(np.log10(80), np.log10(3500), 300)
ck, ch = corr(re, **KHAN), corr(re, **HL)

fig, ax = plt.subplots(figsize=(6.4, 4.2))
ax.fill_between(re, ck, ch, color="#dde5ee", label="between the two correlations")
ax.plot(re[re <= 900], ck[re <= 900], color="#1f4e79", lw=1.2, label="Khan et al. 2020, fit (Re ≤ 900)")
ax.plot(re[re > 900], ck[re > 900], color="#1f4e79", lw=1.2, ls=":")
ax.plot(re, ch, color="#b8860b", lw=1.2, label="Haider and Levenspiel 1989, cube")

for src, style in [("khan2020", dict(marker="s", ms=4, color="#1f4e79", ls="", label="Khan et al. 2020, LBM")),
                   ("saha2004", dict(marker="^", ms=5, color="#7f7f7f", ls="", label="Saha 2004, via Khan")),
                   ("capsule_sweep", dict(marker="o", ms=7, color="#c0392b", ls="", label="capsules, sweep mesh")),
                   ("capsule_part3", dict(marker="D", ms=6, color="#c0392b", mfc="white", ls="", label="capsule, Part 3 mesh")),
                   ("transient", dict(marker="o", ms=7, color="#c0392b", mfc="none", ls="", label="transient run, Re 300"))]:
    pts = sorted(rows.get(src, []))
    ax.plot([p[0] for p in pts], [p[1] for p in pts], **style)

ax.set_xscale("log")
ax.set_xlim(80, 3500)
ax.set_ylim(0.7, 1.45)
ax.set_xlabel("Re")
ax.set_ylabel("Cd")
ax.legend(frameon=False, fontsize=8, loc="upper right")
fig.tight_layout()
fig.savefig("cube-anchors-cd.png", dpi=150)
