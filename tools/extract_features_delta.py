#!/usr/bin/env python3
"""
extract_features_delta.py — vortex-core extraction for capsule_delta65 (T5).

From a single thresholded cell cloud exported by STAR-CCM+ (features_cloud.csv,
cells with Q >= q_floor), extracts labeled vortex-core polylines and per-station
scalars, and emits a self-describing features.json.

Method (ratified 2026-07-29, chat T5):
  1. Bin the cloud into chordwise stations x/c_r.
  2. Within each station, partition by sign(omega_x) BEFORE clustering
     (primary and secondary vortices counter-rotate: the sign is the label).
  3. Cluster each sign group by proximity in the (y,z) plane (union-find).
  4. Apply the per-station RELATIVE analysis threshold beta:
     keep cells with Q >= beta * max(Q at that station).
     The export floor (q_floor) only bounds file size; beta defines the set
     that is integrated. Two thresholds, both declared.
  5. Per cluster: circulation Gamma* = sum(omega_x * V)/dx, centroid weighted
     by |omega_x|*V, effective radius, axial velocity, Q peak and the
     centroid-to-peak offset as a per-station quality metric (QC).
  6. Track clusters across stations (nearest-centroid continuity, gap
     tolerant), label by side and |Gamma*| rank.
  7. Verification: L/R symmetry, circulation chain against box_x06.csv and
     against the STAR plane-integral reports, coverage fraction.
  8. Optional beta sweep (--sweep): the threshold appendix, zero re-runs.

Everything is nondimensional by construction (rho = U_inf = c_r = 1).
Frame (declared): right-handed x-y-z, origin at apex, +x downstream along the
root chord, +z toward the suction side (up), +y starboard. Expected senses:
starboard primary omega_x > 0, port primary omega_x < 0.

Deterministic: no randomness anywhere.

Usage:
  python extract_features_delta.py features_cloud.csv \
      --box box_x06.csv --gamma-sb 0.31 --gamma-port -0.31 \
      --q-floor 27888.3 --out features.json
  python extract_features_delta.py features_cloud.csv --sweep 0.05 0.10 0.15 0.20 0.30 0.40

Dependencies: numpy only.
"""

import argparse
import csv
import json
import math
import sys
from collections import defaultdict

import numpy as np

SCRIPT_VERSION = "1.4 (2026-08-01, chat t5r-3: per-station cluster sums)"

# ---------------------------------------------------------------- IO helpers

COLUMN_KEYS = {
    # canonical name -> list of lowercase substrings to match in CSV headers
    "x": ["x (m)"],
    "y": ["y (m)"],
    "z": ["z (m)"],
    "volume": ["volume"],
    "wx": ["vorticity[i]", "vorticity i"],
    "wy": ["vorticity[j]", "vorticity j"],
    "wz": ["vorticity[k]", "vorticity k"],
    "u": ["velocity[i]", "velocity i"],
    "v": ["velocity[j]", "velocity j"],
    "w": ["velocity[k]", "velocity k"],
    "q": ["q criterion", "q-criterion", "qcriterion"],
}


def match_columns(header, required):
    """Map canonical names to CSV column indices; fail loudly if ambiguous."""
    low = [h.strip().lower() for h in header]
    mapping = {}
    for name in required:
        hits = []
        for j, h in enumerate(low):
            if any(k in h for k in COLUMN_KEYS[name]):
                hits.append(j)
        if name in ("x", "y", "z") and not hits:
            # fall back: bare "X", "Y", "Z" columns
            tgt = name
            hits = [j for j, h in enumerate(low)
                    if h == tgt or h.startswith(tgt + " ")]
        if len(hits) == 0:
            raise SystemExit(f"[fatal] column for '{name}' not found in header: {header}")
        if len(hits) > 1:
            raise SystemExit(f"[fatal] column for '{name}' ambiguous: "
                             f"{[header[j] for j in hits]}")
        mapping[name] = hits[0]
    return mapping


def load_table(path, required):
    """Load a STAR-CCM+ XYZ-table CSV into a dict of numpy arrays."""
    with open(path, "r", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        mapping = match_columns(header, required)
        cols = sorted(set(mapping.values()))
        data = []
        for row in reader:
            if not row:
                continue
            data.append([float(row[j]) for j in cols])
    arr = np.asarray(data, dtype=float)
    out = {name: arr[:, cols.index(j)] for name, j in mapping.items()}
    out["_column_mapping"] = {name: header[j] for name, j in mapping.items()}
    out["_n_rows"] = arr.shape[0]
    return out


# ------------------------------------------------------------- clustering

def cluster_yz(y, z, link_radius):
    """Union-find proximity clustering in the (y,z) plane via grid hashing.

    Two points belong to the same cluster if within link_radius. Returns an
    array of cluster ids (0..k-1). Deterministic.
    """
    n = len(y)
    parent = np.arange(n)

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    def union(i, j):
        ri, rj = find(i), find(j)
        if ri != rj:
            parent[max(ri, rj)] = min(ri, rj)

    h = link_radius
    cells = defaultdict(list)
    ix = np.floor(y / h).astype(int)
    iz = np.floor(z / h).astype(int)
    for i in range(n):
        cells[(ix[i], iz[i])].append(i)
    r2 = link_radius * link_radius
    for (cx, cz), members in cells.items():
        neigh = []
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                neigh.extend(cells.get((cx + dx, cz + dz), ()))
        for i in members:
            for j in neigh:
                if j <= i:
                    continue
                dy = y[i] - y[j]
                dzv = z[i] - z[j]
                if dy * dy + dzv * dzv <= r2:
                    union(i, j)
    roots = np.array([find(i) for i in range(n)])
    _, ids = np.unique(roots, return_inverse=True)
    return ids


# ------------------------------------------------------------- extraction

def analyze_station(cloud, sel, x_center, dx, beta, link_factor,
                    gamma_min, semispan_slope):
    """Analyze one station bin. Returns a list of cluster dicts."""
    q_station_max = float(cloud["q"][sel].max()) if sel.any() else 0.0
    out = []
    if not sel.any() or q_station_max <= 0.0:
        return out, q_station_max
    keep = sel & (cloud["q"] >= beta * q_station_max)
    if not keep.any():
        return out, q_station_max

    for sign in (+1, -1):
        grp = keep & ((cloud["wx"] > 0) if sign > 0 else (cloud["wx"] < 0))
        idx = np.where(grp)[0]
        if idx.size == 0:
            continue
        y, z = cloud["y"][idx], cloud["z"][idx]
        vol = cloud["volume"][idx]
        link_radius = link_factor * float(np.median(np.cbrt(vol)))
        ids = cluster_yz(y, z, link_radius)
        for k in range(ids.max() + 1):
            m = idx[ids == k]
            V = cloud["volume"][m]
            wx = cloud["wx"][m]
            gamma = float(np.sum(wx * V) / dx)
            if abs(gamma) < gamma_min:
                continue  # circulation floor: kills shear-layer scraps
            wgt = np.abs(wx) * V
            W = wgt.sum()
            yc = float(np.sum(wgt * cloud["y"][m]) / W)
            zc = float(np.sum(wgt * cloud["z"][m]) / W)
            xc = float(np.sum(wgt * cloud["x"][m]) / W)
            area = float(V.sum() / dx)
            r_eff = math.sqrt(area / math.pi)
            u_ax = float(np.sum(wgt * cloud["u"][m]) / W)
            jpk = m[int(np.argmax(cloud["q"][m]))]
            q_pk = float(cloud["q"][jpk])
            off = math.hypot(cloud["y"][jpk] - yc, cloud["z"][jpk] - zc)
            qc = off / r_eff if r_eff > 0 else float("nan")
            eta = yc / (semispan_slope * x_center) if x_center > 0 else float("nan")
            out.append(dict(
                x=x_center, y=yc, z=zc, eta=eta, sense=sign,
                gamma_star=gamma, r_eff=r_eff, u_ax=u_ax,
                q_star_max=q_pk, qc_offset=qc, n_cells=int(m.size),
                link_radius=link_radius,
            ))
    return out, q_station_max


def track_clusters(stations):
    """Greedy nearest-centroid tracking across stations, gap tolerant.

    stations: list of (x_center, [cluster dicts]). A cluster joins an existing
    track of the same sense whose last centroid is closest and within a
    tolerance that grows with the station gap (conical flow: everything scales
    with x). Returns a list of tracks (lists of cluster dicts).
    """
    tracks = []
    for x_c, clusters in stations:
        for c in sorted(clusters, key=lambda d: -abs(d["gamma_star"])):
            best, best_d = None, None
            for t in tracks:
                last = t[-1]
                if last["sense"] != c["sense"]:
                    continue
                gap = x_c - last["x"]
                if gap <= 0 or gap > 0.201:   # tolerate up to 4 missing bins
                    continue
                tol = 0.03 + 0.35 * gap       # conical growth allowance
                d = math.hypot(c["y"] - last["y"], c["z"] - last["z"])
                if d <= tol and (best is None or d < best_d):
                    best, best_d = t, d
            if best is not None and (not best or best[-1]["x"] < x_c):
                best.append(c)
            else:
                tracks.append([c])
    return tracks


def label_tracks(tracks):
    """Assign feature ids: per side, primary = largest |Gamma|, secondary =
    largest remaining with opposite sense. Extras keep generic ids."""
    def strength(t):
        return sum(abs(c["gamma_star"]) for c in t)

    labeled = []
    for side, name in ((+1, "starboard"), (-1, "port")):
        side_tracks = [t for t in tracks
                       if (np.mean([c["y"] for c in t]) > 0) == (side > 0)]
        side_tracks.sort(key=strength, reverse=True)
        if not side_tracks:
            continue
        primary = side_tracks[0]
        labeled.append((f"vortex_primary_{name}", primary))
        sec = next((t for t in side_tracks[1:]
                    if t[0]["sense"] != primary[0]["sense"]), None)
        if sec is not None:
            labeled.append((f"vortex_secondary_{name}", sec))
        rank = 0
        for t in side_tracks[1:]:
            if t is sec:
                continue
            rank += 1
            labeled.append((f"vortex_extra_{name}_{rank}", t))
    return labeled


def track_summary(track):
    xs = np.array([c["x"] for c in track])
    ys = np.array([c["y"] for c in track])
    zs = np.array([c["z"] for c in track])
    gs = np.array([c["gamma_star"] for c in track])
    etas = np.array([c["eta"] for c in track])
    qcs = np.array([c["qc_offset"] for c in track])
    summary = dict(
        extent_x=[float(xs.min()), float(xs.max())],
        n_stations=len(track),
        gamma_star_mean=float(gs.mean()),
        gamma_star_at_last=float(gs[-1]),
        mean_qc_offset=float(np.nanmean(qcs)),
    )
    if len(track) >= 3:
        # conicality: ray through the apex, y = a*x, z = b*x
        a = float(np.sum(xs * ys) / np.sum(xs * xs))
        b = float(np.sum(xs * zs) / np.sum(xs * xs))
        res = np.sqrt((ys - a * xs) ** 2 + (zs - b * xs) ** 2)
        g_slope = float(np.polyfit(xs, gs, 1)[0])
        summary.update(
            conical_ray=dict(dy_dx=a, dz_dx=b),
            conicality_rms=float(res.mean()),
            eta_mean=float(np.nanmean(etas)),
            eta_std=float(np.nanstd(etas)),
            dgamma_dx=g_slope,
        )
    return summary


# ------------------------------------------------------------- verification

def box_circulations(box, dx_box):
    """Cell-sum circulation per side from box_x06.csv (NOT Q-thresholded)."""
    out = {}
    for side, m in (("starboard", box["y"] > 0), ("port", box["y"] < 0)):
        out[side] = float(np.sum(box["wx"][m] * box["volume"][m]) / dx_box)
    return out


# ------------------------------------------------------------------- main

def run(args):
    required = ["x", "y", "z", "volume", "wx", "wy", "wz", "u", "v", "w", "q"]
    cloud = load_table(args.cloud, required)
    n = cloud["_n_rows"]
    print(f"[cloud] {args.cloud}: {n} cells; columns: {cloud['_column_mapping']}")

    # v1.2: drop the sharp-leading-edge singularity before anything else.
    # Q there reaches 1e6-1e7 against 1e4 in the core, so a per-station
    # relative threshold anchored to max(Q) would measure the edge, not the
    # vortex. The cut is declared in method.z_cut.
    if args.z_cut is not None:
        keep_z = cloud["z"] >= args.z_cut
        n_drop = int((~keep_z).sum())
        for k in list(cloud.keys()):
            if not k.startswith("_"):
                cloud[k] = cloud[k][keep_z]
        n = int(keep_z.sum())
        cloud["_n_rows"] = n
        print(f"[z-cut] z >= {args.z_cut}: dropped {n_drop} cells, {n} remain")

    x_lo, x_hi, step = args.stations
    centers = np.arange(x_lo, x_hi + 1e-9, step)
    dx = step

    betas = args.sweep if args.sweep else [args.beta]
    sweep_rows = []
    result = None

    for beta in betas:
        stations = []
        q_maxes = {}
        for x_c in centers:
            sel = (np.abs(cloud["x"] - x_c) <= dx / 2)
            clusters, qmax = analyze_station(
                cloud, sel, float(x_c), dx, beta, args.link_factor,
                args.gamma_min, args.semispan_slope)
            stations.append((float(x_c), clusters))
            q_maxes[round(float(x_c), 4)] = qmax
        tracks = track_clusters(stations)
        # v1.3: a track spanning one or two stations is a fragment, not a
        # feature. Naming it would let a single-station scrap at the trailing
        # edge appear in the capsule with the same status as a vortex core.
        n_frag = sum(1 for t in tracks if len(t) < args.min_stations)
        tracks = [t for t in tracks if len(t) >= args.min_stations]
        labeled = label_tracks(tracks)
        n_feat = len(labeled)
        prim = {name: t for name, t in labeled if name.startswith("vortex_primary")}
        row = dict(beta=beta, n_features=n_feat)
        for name, t in prim.items():
            s = track_summary(t)
            row[f"{name}_extent"] = s["extent_x"]
            row[f"{name}_gamma_last"] = round(s["gamma_star_at_last"], 5)
            # v1.1: per-station profile for the sweep table (Gamma*/x, n_cells)
            row[f"{name}_profile"] = {
                round(c["x"], 3): (round(c["gamma_star"] / c["x"], 5), c["n_cells"])
                for c in t if c["x"] > 0
            }
        sweep_rows.append(row)
        if beta == args.beta or result is None:
            result = (beta, stations, labeled, q_maxes)

    if args.sweep:
        # v1.1: matrix of Gamma*/x, rows = beta, columns = station.
        print("\n[beta sweep] Gamma*/x per station")
        for side in ("starboard", "port"):
            key = f"vortex_primary_{side}_profile"
            rows = [r for r in sweep_rows if key in r]
            if not rows:
                continue
            xs = sorted({x for r in rows for x in r[key]})
            print(f"\n  {side}")
            print("     beta" + "".join(f"{x:>9.2f}" for x in xs))
            for r in rows:
                print("   %6.3f" % r["beta"] + "".join(
                    f"{r[key][x][0]:>9.4f}" if x in r[key] else f"{'-':>9}"
                    for x in xs))
            print("    cells")
            for r in rows:
                print("   %6.3f" % r["beta"] + "".join(
                    f"{r[key][x][1]:>9d}" if x in r[key] else f"{'-':>9}"
                    for x in xs))
        print("\n[beta sweep] raw rows")
        for r in sweep_rows:
            slim = {k: v for k, v in r.items() if not k.endswith("_profile")}
            print("  ", json.dumps(slim))

    beta, stations, labeled, q_maxes = result

    # ---- verification chains
    verification = {}
    checks = []
    prim = {n_: t for n_, t in labeled if n_.startswith("vortex_primary")}
    if "vortex_primary_starboard" in prim and "vortex_primary_port" in prim:
        # v1.3: pair by station x, not by list index, and report the whole
        # profile. A single scalar hides where the asymmetry lives, and the
        # maximum alone reads as a case-wide defect when it is usually one
        # station where the tracking split a cluster on one side only.
        sb = {round(c["x"], 4): c["gamma_star"]
              for c in prim["vortex_primary_starboard"]}
        pt = {round(c["x"], 4): c["gamma_star"]
              for c in prim["vortex_primary_port"]}
        xs = sorted(set(sb) & set(pt))
        # v1.4: alongside the named cluster, the sum of every cluster on that
        # side. The named value can differ between sides because a relative
        # per-station threshold is anchored to the stronger side and clips the
        # weaker one harder, shedding a fragment. The station sum is immune to
        # that: if it matches between sides, the circulation is there and only
        # its partition differs.
        sums = {}
        for xc, cl in stations:
            k = round(float(xc), 4)
            sums[k] = (float(sum(c["gamma_star"] for c in cl if c["y"] > 0)),
                       float(sum(c["gamma_star"] for c in cl if c["y"] < 0)))
        profile = []
        for xv in xs:
            d = abs(abs(sb[xv]) - abs(pt[xv])) / max(abs(sb[xv]), 1e-12)
            ssb, spt = sums.get(xv, (float("nan"), float("nan")))
            ds = (abs(abs(ssb) - abs(spt)) / max(abs(ssb), 1e-12)
                  if ssb == ssb and spt == spt else None)
            profile.append(dict(x=xv,
                                gamma_starboard=round(sb[xv], 6),
                                gamma_port=round(pt[xv], 6),
                                rel_diff=round(float(d), 5),
                                sum_starboard=round(ssb, 6),
                                sum_port=round(spt, 6),
                                rel_diff_station_sum=(round(float(ds), 5)
                                                      if ds is not None else None)))
        rels = np.array([p["rel_diff"] for p in profile])
        relsum = np.array([p["rel_diff_station_sum"] for p in profile
                           if p["rel_diff_station_sum"] is not None])
        worst = max(profile, key=lambda p: p["rel_diff"])
        down = np.array([p["rel_diff"] for p in profile if p["x"] >= 0.45])
        rel = float(rels.max())
        opposite = bool(all(np.sign(sb[xv]) == -np.sign(pt[xv]) for xv in xs))
        verification["symmetry"] = dict(
            max_rel_gamma_diff=rel,
            max_at_x=worst["x"],
            median_rel_gamma_diff=float(np.median(rels)),
            median_rel_gamma_diff_downstream=(float(np.median(down))
                                              if down.size else None),
            median_rel_diff_station_sum=(float(np.median(relsum))
                                         if relsum.size else None),
            max_rel_diff_station_sum=(float(relsum.max())
                                      if relsum.size else None),
            n_stations_compared=len(xs),
            senses_opposite=opposite,
            note="Two measures per station. rel_diff compares the named "
                 "primary cluster on each side; rel_diff_station_sum compares "
                 "the sum of every cluster on that side. They diverge when the "
                 "per-station relative threshold, anchored to the stronger "
                 "side, clips the weaker one harder and sheds a fragment: the "
                 "circulation is present but partitioned differently. A large "
                 "rel_diff with a small rel_diff_station_sum is a method "
                 "artefact; both large is a genuine asymmetry. Use the median "
                 "for case symmetry and max_at_x to locate the worst station. "
                 "Cross-check against verification.circulation_chain, which is "
                 "threshold-free and tracking-free.",
            per_station=profile)
        checks.append(("L/R symmetry, median", float(np.median(rels)),
                       "< 0.02 expected"))
        checks.append(("L/R symmetry, max", rel,
                       f"at x = {worst['x']}"))
        if relsum.size:
            checks.append(("L/R station sum, median", float(np.median(relsum)),
                           "immune to cluster partition"))
    verification["fragments_dropped"] = dict(
        n=n_frag, min_stations=args.min_stations,
        note="tracks shorter than min_stations were not named as features")
    if "vortex_primary_starboard" in prim:
        sb_sense = prim["vortex_primary_starboard"][0]["sense"]
        verification["sense_check"] = dict(
            starboard_primary_sense=sb_sense, expected=+1,
            ok=(sb_sense == +1))

    if args.box:
        box = load_table(args.box, ["x", "y", "z", "volume", "wx"])
        gb = box_circulations(box, args.box_dx)
        verification["circulation_chain"] = dict(
            box_cell_sum=gb,
            star_reports=dict(starboard=args.gamma_sb, port=args.gamma_port),
        )
        for side in ("starboard", "port"):
            rep = getattr(args, f"gamma_{'sb' if side == 'starboard' else 'port'}")
            if rep is not None:
                diff = abs(gb[side] - rep) / max(abs(rep), 1e-12)
                verification["circulation_chain"][f"rel_diff_{side}"] = diff
                checks.append((f"box vs STAR report ({side})", diff,
                               "few % expected (interpolation vs cell sum)"))
        # coverage: clusters at x=0.6 inside the box limits vs box total
        cov = {}
        st06 = next((cl for xc, cl in stations if abs(xc - 0.60) < 1e-6), [])
        for side, sgn in (("starboard", +1), ("port", -1)):
            in_box = [c for c in st06
                      if (c["y"] > 0) == (sgn > 0)
                      and 0.05 <= abs(c["y"]) <= 0.45 and 0.02 <= c["z"] <= 0.25]
            tot = sum(c["gamma_star"] for c in in_box)
            cov[side] = dict(
                clusters_sum=tot,
                box_total=gb[side],
                coverage=(tot / gb[side]) if abs(gb[side]) > 1e-12 else None)
        verification["coverage_x06"] = cov
        # v1.1: the box sum is beta-independent, so print it up front.
        print("\n[anchor] box_x06 cell-sum circulation (no Q filter): "
              f"starboard {gb['starboard']:+.5f}, port {gb['port']:+.5f}")

    # ---- assemble features.json
    features = []
    for name, t in labeled:
        features.append(dict(
            id=name,
            type="vortex_core",
            side="starboard" if "starboard" in name else "port",
            sense=int(t[0]["sense"]),
            stations=[{k: (round(c[k], 6) if isinstance(c[k], float) else c[k])
                       for k in ("x", "y", "z", "eta", "gamma_star", "r_eff",
                                 "u_ax", "q_star_max", "qc_offset", "n_cells")}
                      for c in t],
            summary=track_summary(t),
        ))

    doc = dict(
        case=dict(
            alias="delta65_a13p3_re1e6",
            description="65 deg sharp-edged delta wing (VFE-2 class), "
                        "alpha = 13.3 deg, Re_cr = 1e6, steady RANS SST, "
                        "full model, nondimensional by construction",
            groups=dict(sweep_le_deg=65.0, alpha_deg=13.3, re_cr=1.0e6,
                        aspect_ratio=1.865, s_ref=0.4663, span_b=0.9326),
        ),
        frame=dict(
            handedness="right-handed x-y-z",
            origin="wing apex",
            x="downstream along root chord",
            z="toward suction side (up)",
            y="starboard",
            expected_sense=dict(starboard_primary=+1, port_primary=-1),
            units="nondimensional (rho = U_inf = c_r = 1); positions in x/c_r, "
                  "circulation as Gamma/(U_inf c_r), Q in (U_inf/c_r)^2",
        ),
        method=dict(
            criterion="Q-criterion",
            export_floor_q_star=args.q_floor,
            export_floor_note="bounds file size only; does NOT define the core",
            analysis_beta=beta,
            analysis_threshold="Q >= beta * max(Q) per station (relative)",
            z_cut=args.z_cut,
            z_cut_note="cells below z_cut are discarded before the station "
                       "maximum is taken. The sharp leading edge is a "
                       "geometric singularity where Q exceeds the core value "
                       "by two to three orders of magnitude; without this cut "
                       "the relative threshold anchors to the edge.",
            weighting="|omega_x| * V (vorticity-weighted centroid)",
            circulation="Gamma* = sum(omega_x * V) / dx per cluster",
            stations=dict(x_from=x_lo, x_to=x_hi, dx=step),
            sign_partition="clusters never cross a change of sign(omega_x)",
            clustering=f"union-find in (y,z), link = {args.link_factor} * "
                       f"median(V^(1/3)) per station/sign group",
            circulation_floor=args.gamma_min,
            validity="stations normal to x; valid for cores at moderate angle "
                     "to the x-axis (here ~16-18 deg). Curved features "
                     "(hairpins, rings) would need core-normal marching.",
            script=f"extract_features_delta.py {SCRIPT_VERSION}",
        ),
        features=features,
        verification=verification,
        provenance=dict(
            source_cloud=args.cloud,
            source_box=args.box,
            n_cloud_cells=n,
            column_mapping=cloud["_column_mapping"],
            q_station_max=q_maxes,
        ),
    )

    with open(args.out, "w") as f:
        json.dump(doc, f, indent=1)
    print(f"\n[out] {args.out}: {len(features)} features")
    for name, t in labeled:
        s = track_summary(t)
        print(f"  {name}: {len(t)} stations, extent {s['extent_x']}, "
              f"Gamma*(last) = {s['gamma_star_at_last']:+.4f}, "
              f"mean QC = {s['mean_qc_offset']:.3f}"
              + (f", eta = {s['eta_mean']:.3f} +/- {s['eta_std']:.3f}"
                 if "eta_mean" in s else ""))
    if checks:
        print("\n[verification]")
        for label, val, note in checks:
            print(f"  {label}: {val:.4f} ({note})")
    print("\nReminder: report extent with its beta convention; the physical "
          "decay measure is Gamma*(x), not tube length.")


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("cloud", help="features_cloud.csv (XYZ table, Q >= q_floor)")
    p.add_argument("--box", default=None, help="box_x06.csv (control box, no Q filter)")
    p.add_argument("--out", default="features.json")
    p.add_argument("--beta", type=float, default=0.15,
                   help="per-station relative analysis threshold (default 0.15)")
    p.add_argument("--sweep", type=float, nargs="+", default=None,
                   help="list of beta values for the threshold appendix")
    p.add_argument("--stations", type=float, nargs=3, default=[0.10, 0.90, 0.05],
                   metavar=("X0", "X1", "DX"))
    p.add_argument("--link-factor", type=float, default=2.5)
    p.add_argument("--z-cut", type=float, default=0.005,
                   help="exclude cells with z < z_cut before analysis; removes "
                        "the sharp-leading-edge geometric singularity that "
                        "would otherwise anchor the per-station beta")
    p.add_argument("--gamma-min", type=float, default=0.005,
                   help="circulation floor per cluster (drops shear-layer scraps)")
    p.add_argument("--min-stations", type=int, default=3,
                   help="a track needs at least this many stations to be named "
                        "a feature; shorter ones are fragments, not features")
    p.add_argument("--semispan-slope", type=float, default=0.4663,
                   help="local semispan = slope * x (cot 65 deg)")
    p.add_argument("--q-floor", type=float, default=None,
                   help="export floor used in STAR (declared in provenance)")
    p.add_argument("--box-dx", type=float, default=0.05,
                   help="control box thickness in x (default 0.05)")
    p.add_argument("--gamma-sb", type=float, default=None,
                   help="STAR report gamma_sb_x06 (for the two-route chain)")
    p.add_argument("--gamma-port", type=float, default=None,
                   help="STAR report gamma_port_x06")
    args = p.parse_args()
    run(args)


if __name__ == "__main__":
    main()
