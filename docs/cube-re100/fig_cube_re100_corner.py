"""Figures for the cube Re 100 corner article.

Reads cube_re100_corner_diagonal.csv (Cp of the front-face cells along the
diagonal towards one vertex, four meshes) and draws two figures:
  cube-re100-corner-refinement.png  vertex-cell Cp against cell size h/D
  cube-re100-corner-diagonal.png    Cp against distance r/D from the vertex
"""
import csv
import matplotlib.pyplot as plt

DATA = "cube_re100_corner_diagonal.csv"
TRIM = [("trim_128", 1 / 128), ("trim_256", 1 / 256), ("trim_512", 1 / 512)]
LABEL = {"prism_base": "prism layers (sweep mesh)",
         "trim_128": "no layers, h = D/128",
         "trim_256": "no layers, h = D/256",
         "trim_512": "no layers, h = D/512"}
STAGNATION = 1.0676

rows = {}
with open(DATA, newline="") as f:
    for r in csv.DictReader(f):
        rows.setdefault(r["mesh"], []).append((float(r["r_over_D"]), float(r["cp"])))

# 1. vertex cell against cell size
h = [s for _, s in TRIM]
cpv = [rows[m][0][1] for m, _ in TRIM]
fig, ax = plt.subplots(figsize=(6, 4))
ax.loglog(h, cpv, "o-", color="#1f4e79", label="vertex cell, no layers")
ax.axhline(STAGNATION, color="grey", ls="--", lw=1, label="stagnation point")
for x, y in zip(h, cpv):
    ax.annotate(f"{y:.2f}", (x, y), textcoords="offset points", xytext=(6, 4), fontsize=9)
ax.set_xlabel("cell size h/D")
ax.set_ylabel("Cp")
ax.legend(frameon=False, fontsize=9)
fig.tight_layout()
fig.savefig("cube-re100-corner-refinement.png", dpi=150)

# 2. profile towards the vertex
fig, ax = plt.subplots(figsize=(6, 4))
for m, c in [("prism_base", "#b8860b"), ("trim_128", "#9ecae1"),
             ("trim_256", "#4292c6"), ("trim_512", "#08306b")]:
    pts = sorted(p for p in rows[m] if p[0] <= 0.1)
    ax.plot([p[0] for p in pts], [p[1] for p in pts], "o-", ms=3, lw=1, color=c, label=LABEL[m])
ax.axhline(STAGNATION, color="grey", ls="--", lw=1)
ax.set_xscale("log")
ax.set_xlabel("distance from the vertex along the face diagonal, r/D")
ax.set_ylabel("Cp")
ax.legend(frameon=False, fontsize=9)
fig.tight_layout()
fig.savefig("cube-re100-corner-diagonal.png", dpi=150)
