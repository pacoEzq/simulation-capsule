"""Tokens of each example capsule against the cells of its mesh.
Reads data/cells_tokens.csv, writes figures/cells_tokens_{light,dark}.{svg,png}."""
import csv
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
THEMES = {
    "light": dict(surface="#fcfcfb", text="#0b0b0b", muted="#52514e", grid="#e4e3df", mark="#2a78d6"),
    "dark": dict(surface="#1a1a19", text="#f0efec", muted="#c3c2b7", grid="#3a3a37", mark="#3987e5"),
}
LABELS = {
    "capsule_naca0012_aoa5": ("naca0012_aoa5", (8, -4)),
    "capsule_cylinder_re0100": ("cylinder_re0100", (-8, 8)),
    "capsule_jet_r2_re100": ("jet_r2_re100", (8, -4)),
    "capsule_cube_re0300": ("cube, five runs", (10, -4)),
    "capsule_ahmed25_re1e6": ("ahmed25_re1e6", (-8, -16)),
    "capsule_delta65_a13p3_re1e6": ("delta65", (-8, 8)),
}

with open(os.path.join(ROOT, "data", "cells_tokens.csv")) as f:
    rows = [(r["capsule"], int(r["cells"]), int(r["tokens"])) for r in csv.DictReader(f)]

for name, c in THEMES.items():
    fig, ax = plt.subplots(figsize=(7.6, 4.3), dpi=150)
    fig.patch.set_facecolor(c["surface"])
    ax.set_facecolor(c["surface"])
    ax.axhline(60000, color=c["muted"], lw=1, ls=(0, (4, 3)))
    ax.text(5.2e4, 61500, "design budget, 60,000 tokens", color=c["muted"], fontsize=9, va="bottom")
    ax.scatter([r[1] for r in rows], [r[2] for r in rows], s=46, color=c["mark"],
               edgecolor=c["surface"], linewidth=1.5, zorder=3)
    for cap, cells, tok in rows:
        if cap in LABELS:
            text, off = LABELS[cap]
            ax.annotate(text, (cells, tok), xytext=off, textcoords="offset points",
                        fontsize=9, color=c["text"], ha="left" if off[0] > 0 else "right")
    ax.set_xscale("log")
    ax.set_xlim(4e4, 1.2e7)
    ax.set_ylim(0, 72000)
    ax.set_yticks([0, 20000, 40000, 60000])
    ax.set_yticklabels(["0", "20,000", "40,000", "60,000"])
    ax.set_xlabel("cells in the mesh", color=c["muted"], fontsize=10)
    ax.set_ylabel("tokens in the capsule", color=c["muted"], fontsize=10)
    ax.set_title("Twelve example capsules: size against mesh", color=c["text"], fontsize=11, loc="left")
    ax.grid(axis="y", color=c["grid"], lw=0.8, zorder=0)
    ax.tick_params(colors=c["muted"], labelsize=9, length=0, which="both")
    for s in ax.spines.values():
        s.set_visible(False)
    fig.tight_layout()
    for ext in ("svg", "png"):
        fig.savefig(os.path.join(ROOT, "figures", "cells_tokens_%s.%s" % (name, ext)), facecolor=c["surface"])
    plt.close(fig)
