#!/usr/bin/env python3
"""check_mirror.py: measure whether a volume mesh is a mirror image of itself.

Reads a table of cell centroids and cell volumes exported from STAR-CCM+ right
after meshing (an XYZ internal table on the fluid region with the field
function Volume). No flow solution is involved: the question is about the
mesh alone, so it is answered before the solver runs and whatever the flow
does later cannot hide or fake the answer.

The mirror plane is axis = 0 for the axis given with --axis. Each cell has a
local size h = V ** (1/3). A cell with |axis coordinate| <= 1e-9 * max(h) lies
on the plane and is not queried. Every other cell, on both sides, is reflected
across the plane and its exact nearest cell c on the other side is found, at
distance d, through a k-d tree. Each queried cell falls in exactly one class:

    paired          d <= max(h_i, h_c) and h_c / h_i in [2**-0.5, 2**0.5]
    level_mismatch  d <= max(h_i, h_c) and the size ratio outside that range
    unpaired        d >  max(h_i, h_c), or no cell at all on the other side

For the paired cells delta = d / h_i is reported: nearest-rank p50, p95, p99,
the maximum (mirror_pair_max_delta) and a histogram with fixed edges. Every
reported figure is a count or a ratio of lengths, so the table's units do not
matter. --legacy-median adds, for comparison only, the paired distances
divided by the global median of h, as diagnostics/check_mirror_mesh.py does.

This is not a gate. It measures and reports.

Exit codes:
    0  measured, whatever the values
    2  mechanical failure: file missing or unreadable, column missing,
       no off-plane cell

Usage:
    python3 tools/check_mirror.py <table.csv> --axis y --json
    python3 tools/check_mirror.py <table.csv> --axis y
    python3 tools/check_mirror.py <table.csv> --axis y --json --legacy-median

Standard library only.
"""

import argparse
import bisect
import csv
import json
import math
import os
import statistics
import sys

TOOL_VERSION = "1.0"

ON_PLANE_REL = 1e-9
RATIO_LO = 2 ** -0.5
RATIO_HI = 2 ** 0.5
EDGES = [0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.75, 1.0]
AXES = ("x", "y", "z")

# Upper bound on the points held by one k-d tree leaf. The leaves follow the
# data, not a fixed grid, and the search is exact whatever this value is.
LEAF_SIZE = 12


class TableError(Exception):
    """A mechanical failure: exit 2 with this message."""


def resolve_columns(header):
    """Return the indices of the x, y, z and volume columns, or raise."""
    names = [h.strip().strip('"').strip().lower() for h in header]
    found = {}
    for axis in AXES:
        for j, name in enumerate(names):
            if (name == axis or name.startswith(axis + " ")
                    or name.startswith(axis + "(")):
                found[axis] = j
                break
    for j, name in enumerate(names):
        if name.startswith("volume"):
            found["volume"] = j
            break
    missing = [k for k in AXES + ("volume",) if k not in found]
    if missing:
        raise TableError("column(s) %s not found in header %s"
                         % (", ".join(missing), header))
    return found["x"], found["y"], found["z"], found["volume"]


def read_table(path):
    """Read the table. Return three coordinate lists and a volume list."""
    try:
        handle = open(path, newline="", encoding="utf-8-sig")
    except OSError as exc:
        raise TableError("cannot open table: %s" % exc.strerror)
    xs, ys, zs, vs = [], [], [], []
    with handle:
        try:
            reader = csv.reader(handle)
            header = next(reader, None)
            if header is None:
                raise TableError("table is empty, no header")
            jx, jy, jz, jv = resolve_columns(header)
            need = max(jx, jy, jz, jv)
            for row in reader:
                if not row or all(not cell.strip() for cell in row):
                    continue
                if len(row) <= need:
                    raise TableError("line %d has %d fields, expected at "
                                     "least %d" % (reader.line_num, len(row),
                                                   need + 1))
                try:
                    x, y, z, v = (float(row[jx]), float(row[jy]),
                                  float(row[jz]), float(row[jv]))
                except ValueError:
                    raise TableError("line %d is not numeric"
                                     % reader.line_num)
                if not all(math.isfinite(c) for c in (x, y, z, v)):
                    raise TableError("line %d is not finite" % reader.line_num)
                # A cell with no positive volume has no size h, and the cube
                # root of a negative number is not a length.
                if v <= 0.0:
                    raise TableError("line %d has volume %r, not positive"
                                     % (reader.line_num, v))
                xs.append(x)
                ys.append(y)
                zs.append(z)
                vs.append(v)
        except (OSError, UnicodeDecodeError, csv.Error) as exc:
            raise TableError("cannot read table: %s" % exc)
    return xs, ys, zs, vs


class KDTree:
    """Exact nearest neighbour over a fixed point set, standard library only.

    Median split on the widest dimension of each node's region. Left holds
    coordinates <= the split value and right >= it, so a far branch whose
    plane lies farther than the best distance so far cannot hold a nearer
    point and is skipped. Ties are broken by the lowest point id, so the
    answer does not depend on the order of the search.
    """

    def __init__(self, xs, ys, zs, ids):
        coords = (xs, ys, zs)
        order = list(range(len(ids)))
        self.dim, self.val, self.left, self.right = [], [], [], []
        self.start, self.end = [], []
        if not order:
            self.X = self.Y = self.Z = self.I = []
            return
        box = [[min(c), max(c)] for c in coords]
        root = self._new_node()
        stack = [(root, 0, len(order), box)]
        while stack:
            node, s, e, box = stack.pop()
            if e - s <= LEAF_SIZE:
                self.start[node], self.end[node] = s, e
                continue
            d = max(range(3), key=lambda k: box[k][1] - box[k][0])
            c = coords[d]
            order[s:e] = sorted(order[s:e], key=c.__getitem__)
            mid = (s + e) // 2
            val = c[order[mid]]
            self.dim[node], self.val[node] = d, val
            left, right = self._new_node(), self._new_node()
            self.left[node], self.right[node] = left, right
            lbox = [list(b) for b in box]
            rbox = [list(b) for b in box]
            lbox[d][1] = val
            rbox[d][0] = val
            stack.append((left, s, mid, lbox))
            stack.append((right, mid, e, rbox))
        self.X = [xs[i] for i in order]
        self.Y = [ys[i] for i in order]
        self.Z = [zs[i] for i in order]
        self.I = [ids[i] for i in order]

    def _new_node(self):
        self.dim.append(-1)
        self.val.append(0.0)
        self.left.append(-1)
        self.right.append(-1)
        self.start.append(0)
        self.end.append(0)
        return len(self.dim) - 1

    def nearest(self, qx, qy, qz):
        """Return (squared distance, id) of the nearest point, or (inf, -1)."""
        if not self.I:
            return math.inf, -1
        dim, val, left, right = self.dim, self.val, self.left, self.right
        start, end = self.start, self.end
        X, Y, Z, I = self.X, self.Y, self.Z, self.I
        q = (qx, qy, qz)
        best, best_id = math.inf, -1
        stack = [(0, 0.0)]
        while stack:
            node, bound = stack.pop()
            if bound > best:
                continue
            while True:
                d = dim[node]
                if d < 0:
                    for k in range(start[node], end[node]):
                        dx = X[k] - qx
                        dy = Y[k] - qy
                        dz = Z[k] - qz
                        d2 = dx * dx + dy * dy + dz * dz
                        if d2 < best or (d2 == best and I[k] < best_id):
                            best, best_id = d2, I[k]
                    break
                diff = q[d] - val[node]
                if diff < 0.0:
                    near, far = left[node], right[node]
                else:
                    near, far = right[node], left[node]
                far_bound = diff * diff
                if far_bound <= best:
                    stack.append((far, far_bound))
                node = near
        return best, best_id


def nearest_rank(sorted_values, p):
    """Nearest-rank percentile of an ascending list, None when empty."""
    n = len(sorted_values)
    if n == 0:
        return None
    rank = max(1, math.ceil(p / 100.0 * n))
    return sorted_values[rank - 1]


def measure(xs, ys, zs, vs, axis):
    """Classify every off-plane cell. Return a dict of raw results.

    Raises TableError when no cell lies off the plane.
    """
    n = len(vs)
    hs = [v ** (1.0 / 3.0) for v in vs]
    a = AXES.index(axis)
    coords = [xs, ys, zs]
    ac = coords[a]
    tol = ON_PLANE_REL * max(hs) if hs else 0.0

    pos = [i for i in range(n) if ac[i] > tol]
    neg = [i for i in range(n) if ac[i] < -tol]
    on_plane = n - len(pos) - len(neg)
    queried = len(pos) + len(neg)
    if queried == 0:
        raise TableError("no off-plane cell: %d cells, all within %.3g of "
                         "%s = 0" % (n, tol, axis))

    def tree_of(ids):
        return KDTree([xs[i] for i in ids], [ys[i] for i in ids],
                      [zs[i] for i in ids], ids)

    trees = {1: tree_of(neg), -1: tree_of(pos)}
    paired_d, paired_delta = [], []
    level_mismatch = unpaired = 0
    for side, ids in ((1, pos), (-1, neg)):
        tree = trees[side]
        for i in ids:
            q = [xs[i], ys[i], zs[i]]
            q[a] = -q[a]
            d2, c = tree.nearest(q[0], q[1], q[2])
            if c < 0:
                unpaired += 1
                continue
            d = math.sqrt(d2)
            hi, hc = hs[i], hs[c]
            if d > max(hi, hc):
                unpaired += 1
            elif RATIO_LO <= hc / hi <= RATIO_HI:
                paired_d.append(d)
                paired_delta.append(d / hi)
            else:
                level_mismatch += 1
    paired = len(paired_delta)
    assert paired + level_mismatch + unpaired == queried, \
        "class counts do not add up to the queried cells"
    return {
        "cells": n,
        "on_plane": on_plane,
        "queried": queried,
        "paired": paired,
        "level_mismatch": level_mismatch,
        "unpaired": unpaired,
        "paired_d": paired_d,
        "deltas": sorted(paired_delta),
        "h_median": statistics.median(hs),
    }


def histogram(sorted_deltas):
    counts = [0] * (len(EDGES) - 1)
    overflow = 0
    for delta in sorted_deltas:
        if delta >= EDGES[-1]:
            overflow += 1
        else:
            counts[bisect.bisect_right(EDGES, delta) - 1] += 1
    return counts, overflow


def r6(value):
    return None if value is None else round(value, 6)


def build_report(table, axis, res, legacy):
    deltas = res["deltas"]
    top = deltas[-1] if deltas else None
    counts, overflow = histogram(deltas)
    out = {
        "tool": "check_mirror.py",
        "version": TOOL_VERSION,
        "table": os.path.basename(table),
        "axis": axis,
        "cells": res["cells"],
        "on_plane": res["on_plane"],
        "queried": res["queried"],
        "paired": res["paired"],
        "level_mismatch": res["level_mismatch"],
        "unpaired": res["unpaired"],
        "delta": {
            "p50": r6(nearest_rank(deltas, 50)),
            "p95": r6(nearest_rank(deltas, 95)),
            "p99": r6(nearest_rank(deltas, 99)),
            "max": r6(top),
        },
        "histogram": {"edges": list(EDGES), "counts": counts,
                      "overflow": overflow},
        "mirror_pair_max_delta": r6(top),
        "mirror_unpaired": res["unpaired"],
        "mirror_level_mismatch": res["level_mismatch"],
    }
    if legacy:
        scale = res["h_median"]
        out["legacy_median"] = {
            "deprecated": True,
            "scale": r6(scale),
            "max": r6(max(res["paired_d"]) / scale if res["paired_d"]
                      else None),
        }
    return out


def fmt(value):
    return "n/a" if value is None else "%.6f" % value


def report_text(rep):
    lines = ["check_mirror.py %s" % rep["version"],
             "  table  %s" % rep["table"],
             "  plane  %s = 0" % rep["axis"],
             "",
             "  cells           %d" % rep["cells"],
             "  on plane        %d" % rep["on_plane"],
             "  queried         %d" % rep["queried"],
             "    paired          %d" % rep["paired"],
             "    level mismatch  %d" % rep["level_mismatch"],
             "    unpaired        %d" % rep["unpaired"],
             "",
             "  delta = d / h_i over paired cells",
             "    p50 %s   p95 %s   p99 %s   max %s" % tuple(
                 fmt(rep["delta"][k]) for k in ("p50", "p95", "p99", "max")),
             "",
             "  histogram of delta"]
    counts = rep["histogram"]["counts"]
    width = max([len(str(c)) for c in counts]
                + [len(str(rep["histogram"]["overflow"]))])
    for k, count in enumerate(counts):
        lines.append("    [%-4g, %-4g)  %*d" % (EDGES[k], EDGES[k + 1], width,
                                                count))
    lines.append("    >= %-9g  %*d" % (EDGES[-1], width,
                                        rep["histogram"]["overflow"]))
    lines.append("")
    lines.append("  mirror_pair_max_delta  %s"
                 % fmt(rep["mirror_pair_max_delta"]))
    lines.append("  mirror_unpaired        %d" % rep["mirror_unpaired"])
    lines.append("  mirror_level_mismatch  %d" % rep["mirror_level_mismatch"])
    if "legacy_median" in rep:
        leg = rep["legacy_median"]
        lines.append("")
        lines.append("  legacy median (deprecated, comparison only)")
        lines.append("    scale %s   max d / scale %s"
                     % (fmt(leg["scale"]), fmt(leg["max"])))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Measure whether a volume mesh is a mirror image of "
                    "itself across the plane axis = 0.")
    parser.add_argument("table", help="CSV of cell centroids and volumes")
    parser.add_argument("--axis", required=True, choices=AXES,
                        help="the mirror plane is axis = 0")
    parser.add_argument("--json", action="store_true", dest="as_json",
                        help="print one JSON object on stdout")
    parser.add_argument("--legacy-median", action="store_true",
                        help="add the deprecated median-scaled figure")
    args = parser.parse_args(argv)

    try:
        xs, ys, zs, vs = read_table(args.table)
        res = measure(xs, ys, zs, vs, args.axis)
    except TableError as exc:
        sys.stderr.write("check_mirror.py: %s: %s\n"
                         % (os.path.basename(args.table), exc))
        return 2
    rep = build_report(args.table, args.axis, res, args.legacy_median)
    if args.as_json:
        print(json.dumps(rep))
    else:
        print(report_text(rep))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:  # piping into head is normal usage
        os._exit(0)
