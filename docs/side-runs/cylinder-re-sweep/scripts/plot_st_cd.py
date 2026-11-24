#!/usr/bin/env python3
"""Plot St(Re) and Cd(Re) of the 2D cylinder sweeps against Williamson's fit.

Reads ../data/sweep_60D.csv and ../data/sweep_15D.csv, writes
../figures/st_vs_re.{png,svg} and ../figures/cd_vs_re.{png,svg}.
Run from any directory: paths are resolved relative to this file.
"""
import csv
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "data"
FIG = HERE.parent / "figures"
FIG.mkdir(exist_ok=True)

BLUE = "#1f5fbf"    # domain at +-60D
ORANGE = "#d9780a"  # domain at +-15D
GREY = "#6b6b6b"    # reference fit


def williamson(re):
    """Williamson (1988), parallel shedding, 49 < Re < 178."""
    return -3.3265 / re + 0.1816 + 1.6e-4 * re


def read(name):
    with open(DATA / name, newline="") as f:
        rows = list(csv.DictReader(f))
    return [float(r["Re"]) for r in rows], rows


def style(ax, xlabel, ylabel):
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, color="#dddddd", linewidth=0.6)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    ax.set_xlim(40, 190)
    ax.set_xticks([50, 70, 100, 130, 160, 180])


def main():
    plt.rcParams.update({"font.size": 11, "axes.labelsize": 12, "legend.frameon": False})
    re60, s60 = read("sweep_60D.csv")
    re15, s15 = read("sweep_15D.csv")

    # --- Strouhal number -------------------------------------------------
    fig, ax = plt.subplots(figsize=(6.4, 4.2), dpi=300)
    re_fit = [r / 10 for r in range(480, 1860)]
    ax.plot(re_fit, [williamson(r) for r in re_fit], "--", color=GREY, linewidth=1.6,
            label="Williamson (1988) fit")
    ax.plot(re15, [float(r["St"]) for r in s15], "s", color=ORANGE, markersize=7,
            label="domain $\\pm 15D$ (first sweep)")
    ax.plot(re60, [float(r["St"]) for r in s60], "o", color=BLUE, markersize=7,
            label="domain $\\pm 60D$ (second sweep)")
    style(ax, "Re", "St")
    ax.set_ylim(0.11, 0.20)
    ax.legend(loc="lower right")
    fig.tight_layout()
    fig.savefig(FIG / "st_vs_re.png")
    fig.savefig(FIG / "st_vs_re.svg")
    plt.close(fig)

    # --- Drag coefficient ------------------------------------------------
    fig, ax = plt.subplots(figsize=(6.4, 4.2), dpi=300)
    ax.plot(re15, [float(r["cd"]) for r in s15], "s-", color=ORANGE, markersize=7,
            linewidth=1.2, label="domain $\\pm 15D$ (first sweep)")
    ax.plot(re60, [float(r["cd"]) for r in s60], "o-", color=BLUE, markersize=7,
            linewidth=1.2, label="domain $\\pm 60D$ (second sweep)")
    style(ax, "Re", "$C_d$ (mean)")
    ax.set_ylim(1.28, 1.48)
    ax.legend(loc="upper right")
    fig.tight_layout()
    fig.savefig(FIG / "cd_vs_re.png")
    fig.savefig(FIG / "cd_vs_re.svg")
    plt.close(fig)

    # --- Console check ---------------------------------------------------
    for r in s60:
        re, st = float(r["Re"]), float(r["St"])
        w = williamson(re)
        print(f"Re {re:5.0f}  St {st:.4f}  Williamson {w:.4f}  {100 * (st - w) / w:+.2f} %")


if __name__ == "__main__":
    main()
