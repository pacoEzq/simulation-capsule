"""Figure for the cube literature-anchors article.

Reads lateral_re0100.csv, lateral_re0300.csv and lateral_re3000.csv (columns
z_D, u_U, written by the probe macro along the line x/D = -0.5, y = 0, from
the cube wall at z/D = 0.5 to the lateral boundary at z/D = 5) and draws
cube-anchors-lateral.png: u/U against z/D for the three Reynolds numbers,
with the ideal 1.00, the 1% blockage level 1.01 and the lateral boundaries
of this domain (z/D = 5) and of Khan et al. (7.5).
"""
import csv
import matplotlib.pyplot as plt

RUNS = [("re0100", "Re 100", "#08306b"), ("re0300", "Re 300", "#4292c6"), ("re3000", "Re 3000", "#b8860b")]
BLOCKAGE = 1.0 / (1.0 - 0.01)   # 1 D^2 in a 10 D by 10 D section


def load(name):
    z, u = [], []
    with open(name, newline="") as f:
        for r in csv.DictReader(f):
            z.append(float(r["z_D"]))
            u.append(float(r["u_over_U"]))
    return z, u


fig, ax = plt.subplots(figsize=(6.4, 4.2))
for run, label, color in RUNS:
    z, u = load(f"lateral_{run}.csv")
    ax.plot(z, u, color=color, lw=1.4, label=label)
    print(f"{run}: u/U at z/D = {z[-1]:.2f} is {u[-1]:.4f}; "
          f"max u/U {max(u):.4f} at z/D = {z[u.index(max(u))]:.2f}")
ax.axhline(1.0, color="grey", ls="--", lw=1)
ax.axhline(BLOCKAGE, color="grey", ls=":", lw=1)
ax.text(7.9, BLOCKAGE, "1% blockage, section average", ha="right", va="bottom", fontsize=8, color="grey")
ax.text(7.9, 1.0, "u/U = 1", ha="right", va="bottom", fontsize=8, color="grey")
ax.axvline(5.0, color="#c0392b", lw=1)
ax.axvline(7.5, color="#c0392b", lw=1, ls="--")
ax.text(4.95, 0.975, "this domain, z/D = 5", rotation=90, va="center", ha="right", fontsize=8, color="#c0392b")
ax.text(7.45, 0.975, "Khan et al., z/D = 7.5", rotation=90, va="center", ha="right", fontsize=8, color="#c0392b")
ax.set_xlim(0.5, 8)
ax.set_ylim(0.95, 1.04)
ax.set_xlabel("z/D along x/D = -0.5, y = 0")
ax.set_ylabel("u/U")
ax.legend(frameon=False, fontsize=9, loc="upper right")
fig.tight_layout()
fig.savefig("cube-anchors-lateral.png", dpi=150)
