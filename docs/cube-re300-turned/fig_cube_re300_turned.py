"""A cube at Re = 300 turned inside a fixed mesh: does the side-force plane follow the body or the grid?

Reads the five monitor files in this folder and the run-1 monitors in ../cube-re300/, and writes
the figures cube-re300-turned-*.png next to this script.
numpy and matplotlib only. Run from a clean clone:  python docs/cube-re300-turned/fig_cube_re300_turned.py
"""
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon, FancyBboxPatch

HERE = Path(__file__).resolve().parent
REF = HERE.parent / "cube-re300" / "cube_re300_transient_monitors.csv"

def load(p):
    return np.genfromtxt(p, delimiter=",", names=True)

runs = {
    "aligned": load(REF) if REF.exists() else None,
    "rot30": load(HERE / "cube_re300_rot30_transient_monitors.csv"),
    "rot45": load(HERE / "cube_re300_rot45_transient_monitors.csv"),
    "tilt1": load(HERE / "cube_re300_tilt1_transient_monitors.csv"),
    "tilt3": load(HERE / "cube_re300_tilt3_transient_monitors.csv"),
    "tilt3w": load(HERE / "cube_re300_tilt3w_transient_monitors.csv"),
}

S = "#fcfcfb"; T1 = "#0b0b0b"; T2 = "#52514e"; G = "#e4e3df"; K = "#9a9893"
C0 = "#52514e"; C30 = "#2a78d6"; C45 = "#eb6834"; CT1 = "#1f9e6e"; CT3 = "#8a4fd0"; FACE = "#2a78d6"; DIAG = "#eb6834"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11, "axes.edgecolor": G, "axes.labelcolor": T2,
                     "xtick.color": T2, "ytick.color": T2, "axes.facecolor": S, "figure.facecolor": S, "axes.titlecolor": T1})

def save(fig, name):
    fig.savefig(HERE / name, facecolor=S, metadata={"Software": None})
    plt.close(fig)

def square(ax, ang, half=0.5, **kw):
    a = np.radians(ang)
    pts = np.array([[-half, -half], [half, -half], [half, half], [-half, half]])
    R = np.array([[np.cos(a), -np.sin(a)], [np.sin(a), np.cos(a)]])
    ax.add_patch(Polygon(pts @ R.T, closed=True, **kw))

def mirror_lines(ax, ang, L=1.25):
    for k, (col, ls) in enumerate(((FACE, "-"), (DIAG, "--"), (FACE, "-"), (DIAG, "--"))):
        a = np.radians(ang + 45 * k)
        ax.plot([-L * np.cos(a), L * np.cos(a)], [-L * np.sin(a), L * np.sin(a)], color=col, ls=ls, lw=1.1, zorder=3)

def grid(ax, L=1.3, h=0.25):
    for v in np.arange(-L, L + 1e-9, h):
        ax.plot([v, v], [-L, L], color=G, lw=0.6, zorder=0)
        ax.plot([-L, L], [v, v], color=G, lw=0.6, zorder=0)

# Figure 1: the configurations seen from downstream (y to the right, z up), with the mesh grid behind.
fig, axs = plt.subplots(1, 4, figsize=(13.0, 3.9), dpi=200)
cfg = [("aligned cube", 0, None), ("turned 30° about x", 30, None), ("turned 45° about x", 45, None), ("tilted β towards the diagonal", 0, 45)]
for ax, (title, ang, tilt) in zip(axs, cfg):
    grid(ax)
    square(ax, ang, facecolor="#d9d7d2", edgecolor=T2, lw=1.2, zorder=2)
    mirror_lines(ax, ang)
    if tilt is not None:
        a = np.radians(tilt)
        ax.annotate("", xy=(0.95 * np.cos(a), 0.95 * np.sin(a)), xytext=(0, 0),
                    arrowprops=dict(arrowstyle="-|>", color=CT3, lw=2), zorder=5)
        ax.text(0.98 * np.cos(a), 0.98 * np.sin(a) + 0.08, "flow seen\nfrom the cube", color=CT3, fontsize=8.5, ha="center")
    ax.set_xlim(-1.3, 1.3); ax.set_ylim(-1.3, 1.3); ax.set_aspect("equal")
    ax.set_xticks([]); ax.set_yticks([])
    ax.set_title(title, fontsize=10.5, loc="left")
    for sp in ax.spines.values(): sp.set_color(G)
axs[0].text(-1.25, -1.22, "y →", color=T2, fontsize=9); axs[0].text(-1.25, 1.08, "z ↑", color=T2, fontsize=9)
fig.text(0.01, 0.015, "solid blue: mirror planes through the faces · dashed orange: mirror planes through the diagonals · grey: the cartesian mesh, fixed in all four",
         color=T2, fontsize=9)
fig.tight_layout(rect=(0, 0.05, 1, 1))
save(fig, "cube-re300-turned-configurations.png")

# Figure 2: side-force vector in the Cy-Cl plane for the turned cubes, with the face planes of each.
fig, axs = plt.subplots(1, 2, figsize=(11.2, 5.6), dpi=200)
for ax, key, ang, col, title in ((axs[0], "rot30", 30, C30, "turned 30°"), (axs[1], "rot45", 45, C45, "turned 45°")):
    u = runs[key]
    ax.axhline(0, color=G, lw=1, zorder=0); ax.axvline(0, color=G, lw=1, zorder=0)
    for k in range(4):
        a = np.radians(ang + 90 * k)
        ax.plot([0, 0.2 * np.cos(a)], [0, 0.2 * np.sin(a)], color=FACE, lw=1, alpha=0.5)
        a2 = np.radians(ang + 45 + 90 * k)
        ax.plot([0, 0.2 * np.cos(a2)], [0, 0.2 * np.sin(a2)], color=DIAG, lw=1, ls="--", alpha=0.5)
    m = u["tU_D"] >= 300
    ax.plot(u["cy"][~m], u["cl"][~m], color=K, lw=0.5, alpha=0.7, label="t·U/D 0 to 300")
    ax.plot(u["cy"][m], u["cl"][m], color=col, lw=0.6, alpha=0.9, label="t·U/D 300 to 725")
    mc, ml = u["cy"][m].mean(), u["cl"][m].mean()
    ax.plot(mc, ml, "o", ms=8, color=S, mec=T1, mew=2, zorder=6)
    ax.annotate(f"mean {np.degrees(np.arctan2(ml, mc)):.1f}°", (mc, ml), xytext=(mc + 0.02, ml - 0.03), color=T1, fontsize=10)
    ax.set_aspect("equal"); ax.set_xlim(-0.2, 0.2); ax.set_ylim(-0.2, 0.2)
    ax.set_xlabel("$C_y$"); ax.set_ylabel("$C_l$")
    ax.set_title(f"Cube {title}: side force in the cross-flow plane", fontsize=11, loc="left")
    for sp in ax.spines.values(): sp.set_color(G)
    ax.legend(loc="lower left", frameon=False, fontsize=9, labelcolor=T1)
fig.text(0.01, 0.01, "blue rays: normals of the turned faces · dashed orange rays: its diagonals", color=T2, fontsize=9)
fig.tight_layout(rect=(0, 0.03, 1, 1))
save(fig, "cube-re300-turned-side-force.png")

# Figure 3: angle of the side force against time, smoothed over one shedding period.
def smooth(u, w=10.0):
    dt = u["tU_D"][1] - u["tU_D"][0]; n = max(1, int(round(w / dt)))
    k = np.ones(n) / n
    return np.convolve(u["cy"], k, "same"), np.convolve(u["cl"], k, "same")

fig, ax = plt.subplots(figsize=(9.0, 4.6), dpi=200)
for key, col, lab, faces in (("rot30", C30, "turned 30°", (30, -60)), ("rot45", C45, "turned 45°", (45, -45))):
    u = runs[key]; cy, cl = smooth(u)
    ang = np.degrees(np.arctan2(cl, cy)); mag = np.hypot(cy, cl)
    ok = mag > 0.004
    ax.plot(u["tU_D"][ok], ang[ok], color=col, lw=1.0, label=lab)
    ax.axhline(faces[0], color=col, lw=0.8, ls=":", alpha=0.8)
ax.axhline(0, color=G, lw=1); ax.axhline(90, color=G, lw=1)
ax.text(722, 92, "mesh axis z", color=T2, fontsize=8.5, ha="right"); ax.text(722, 2, "mesh axis y", color=T2, fontsize=8.5, ha="right")
ax.set_xlim(0, 725); ax.set_ylim(-10, 100)
ax.set_xlabel("t·U/D"); ax.set_ylabel("angle of the side force, atan2($C_l$, $C_y$) [°]")
ax.set_title("The angle starts near a mesh axis and locks onto a face of the turned cube", fontsize=11, loc="left")
ax.legend(loc="center right", frameon=False, fontsize=9.5, labelcolor=T1)
for sp in ax.spines.values(): sp.set_color(G)
fig.text(0.01, 0.01, "running mean over 10 time units (about one shedding period); dotted: face normal of each run", color=T2, fontsize=9)
fig.tight_layout(rect=(0, 0.03, 1, 1))
save(fig, "cube-re300-turned-angle.png")

# Figure 4: tilted cube, side-force cloud in the settled window with mean vector and oscillation axis.
def cloud(ax, u, col, title, t0=400):
    m = u["tU_D"] >= t0
    y, z = u["cy"][m], u["cl"][m]
    my, mz = y.mean(), z.mean()
    cov = np.cov(np.vstack([y - my, z - mz]))
    w, v = np.linalg.eigh(cov)
    ax.axhline(0, color=G, lw=1, zorder=0); ax.axvline(0, color=G, lw=1, zorder=0)
    for k in range(4):
        a = np.radians(90 * k)
        ax.plot([0, 0.26 * np.cos(a)], [0, 0.26 * np.sin(a)], color=FACE, lw=1, alpha=0.4)
        a2 = np.radians(45 + 90 * k)
        ax.plot([0, 0.26 * np.cos(a2)], [0, 0.26 * np.sin(a2)], color=DIAG, lw=1, ls="--", alpha=0.4)
    ax.plot(y, z, color=col, lw=0.8, alpha=0.8)
    ax.annotate("", xy=(my, mz), xytext=(0, 0), arrowprops=dict(arrowstyle="-|>", color=T1, lw=1.8), zorder=6)
    s = np.sqrt(w[1]) * 2.5; e = v[:, 1]
    ax.plot([my - s * e[0], my + s * e[0]], [mz - s * e[1], mz + s * e[1]], color=T1, lw=1, ls="-.", zorder=6)
    ratio = np.sqrt(w[1] / max(w[0], 1e-12))
    ax.set_title(f"{title}\nmean {np.degrees(np.arctan2(mz, my)):.0f}°, oscillation axis {((np.degrees(np.arctan2(e[1], e[0])) + 90) % 180) - 90:.0f}°, planarity {ratio:.0f}",
                 fontsize=10, loc="left")
    ax.set_aspect("equal"); ax.set_xlim(-0.26, 0.26); ax.set_ylim(-0.26, 0.26)
    ax.set_xlabel("$C_y$"); ax.set_ylabel("$C_l$")
    for sp in ax.spines.values(): sp.set_color(G)

fig, axs = plt.subplots(1, 3, figsize=(14.4, 5.4), dpi=200)
cloud(axs[0], runs["rot30"], C30, "turned 30° (reference)")
cloud(axs[1], runs["tilt1"], CT1, "tilted β = 1°")
cloud(axs[2], runs["tilt3"], CT3, "tilted β = 3°")
fig.text(0.01, 0.01, "t·U/D 400 to 725 · arrow: mean side force · dash-dot: axis of the oscillation · planarity: rms along that axis over rms across it · rays: faces (blue) and diagonals (orange) of the untilted cube",
         color=T2, fontsize=8.5)
fig.tight_layout(rect=(0, 0.04, 1, 1))
save(fig, "cube-re300-turned-tilt.png")

# Figure 5: the operation chain that turns the body inside a fixed domain.
fig, ax = plt.subplots(figsize=(13.0, 2.7), dpi=200)
steps = ["cube_solid, domain_box\none Block Surface each",
         "split by angle (89°)\nname faces by min/max\nof Position on Geometry",
         "Transform: rotate\ncube_solid and refine_wake\n(tilt: cube_solid only)",
         "Subtract:\ndomain_box minus cube_solid\n→ fluid_rot30",
         "Automated Mesh:\ncopy of the base operation,\ncontrols mapped by face name",
         "check: cell count\ndiffers from the base;\nboundaries by position"]
x = 0.01
for i, s in enumerate(steps):
    ax.add_patch(FancyBboxPatch((x, 0.2), 0.13, 0.6, boxstyle="round,pad=0.005", fc="#eef3fb" if i % 2 == 0 else "#fdf0ea", ec=T2, lw=1))
    ax.text(x + 0.065, 0.5, s, ha="center", va="center", fontsize=8.6, color=T1)
    if i < len(steps) - 1:
        ax.annotate("", xy=(x + 0.162, 0.5), xytext=(x + 0.138, 0.5), arrowprops=dict(arrowstyle="-|>", color=T2, lw=1.2))
    x += 0.165
ax.set_xlim(0, 1); ax.set_ylim(0, 1); ax.axis("off")
ax.set_title("Turning a body inside a fixed domain: part operations that the mesher replays every time", fontsize=11, loc="left")
fig.tight_layout()
save(fig, "cube-re300-turned-chain.png")
# Figure 6: the mesh of the turned cube, two cross-flow sections from the macro run (screenshots).
from PIL import Image
shots = [(HERE / "cube-re300-turned-mesh-x0-raw.png", "x = 0, through the cube"), (HERE / "cube-re300-turned-mesh-x2-raw.png", "x = 2D, in the wake")]
if all(p.exists() for p, _ in shots):
    fig, axs = plt.subplots(1, 2, figsize=(12.0, 5.8), dpi=200)
    for ax, (p, t) in zip(axs, shots):
        im = Image.open(p); w, h = im.size
        ax.imshow(im.crop((0, int(0.12 * h), w, h))); ax.axis("off"); ax.set_title(t, fontsize=10.5, loc="left")
    fig.suptitle("Cube turned 30°: wall refinement and wake block turn with it, the background grid does not", fontsize=11, x=0.01, ha="left")
    fig.tight_layout()
    save(fig, "cube-re300-turned-mesh.png")
print("ok")

# Figure 7: confinement check, the 3-degree tilt in the 10D and the 20D domain, with the tilted body's
# remaining mirror (the diagonal plane at 45 / -135 degrees) drawn across both.
fig, axs = plt.subplots(1, 2, figsize=(10.4, 5.4), dpi=200)
for ax, key, col, title in ((axs[0], "tilt3", CT3, "β = 3°, domain 10D × 10D"), (axs[1], "tilt3w", "#c2417a", "β = 3°, domain 20D × 20D")):
    cloud(ax, runs[key], col, title)
    L = 0.26
    ax.plot([-L, L], [-L, L], color=T1, lw=1.4, ls=(0, (6, 3)), alpha=0.55, zorder=1)
    ax.text(0.17, 0.2, "remaining mirror", color=T2, fontsize=8.5, rotation=45, ha="center", va="center")
fig.text(0.01, 0.01, "t·U/D 400 to 725 · black dashed: the one mirror plane of the tilted cube (diagonal) · the two runs end in mirror-image states of equal size",
         color=T2, fontsize=8.5)
fig.tight_layout(rect=(0, 0.04, 1, 1))
save(fig, "cube-re300-turned-confinement.png")
