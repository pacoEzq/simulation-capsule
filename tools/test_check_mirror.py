#!/usr/bin/env python3
"""Self test for check_mirror.py.

Every table here is synthetic and built in a temporary directory. A lattice of
spacing h has its centroids at (k + 0.5) * h, and a fine patch of 16:1 sits on
the fine lattice of the same form, so an exact mirror is exact in floating
point and not only close.

1. Exact mirror, two levels. A coarse lattice h = 0.5 with a fine patch
   h = 0.03125 straddling the plane. Max delta below 1e-9, nothing unpaired,
   no level mismatch.
2. Quarter-cell shift, level by level. Single-level lattices with h in
   {0.5, 0.125, 0.03125}, each shifted by h/4 along the axis. Every paired
   delta within 1e-6 of 0.5, at every level.
3. Half-cell shift. The same lattices shifted by h/2 are mirrors again, which
   is why test 2 shifts by h/4. Max delta below 1e-9.
4. Patch on one side only. Level mismatch appears, the paired figure does not
   grow beyond test 1, and the classes add up to the queried cells.
5. Mechanical failures. No volume column, and every cell on the plane. Both
   exit 2.
6. --legacy-median adds its block, marked deprecated; without the flag the
   block is absent.
7. JSON keys in the documented order, and only the base name of the table.
8. Size. A two-level table of 501 860 cells, timed. Over ten minutes is a
   failure to report, not a reason to add a dependency.

Run: python3 tools/test_check_mirror.py
Exit 0 when all hold. Standard library only, temporary files, nothing
written outside the system temp directory.
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import check_mirror  # noqa: E402

TOOL = os.path.join(HERE, "check_mirror.py")
HEADER = ['"X (m)"', '"Y (m)"', '"Z (m)"', '"Volume (m^3)"']
H_COARSE = 0.5
H_FINE = 0.03125
REFINE = 16
TIME_LIMIT_S = 600.0

KEYS = ["tool", "version", "table", "axis", "cells", "on_plane", "queried",
        "paired", "level_mismatch", "unpaired", "delta", "histogram",
        "mirror_pair_max_delta", "mirror_unpaired", "mirror_level_mismatch"]


def write_table(path, cells, header=None):
    """cells: iterable of (x, y, z, volume). Header cells are written raw."""
    with open(path, "w", newline="\n") as handle:
        handle.write(",".join(header or HEADER) + "\n")
        for x, y, z, v in cells:
            handle.write("%r,%r,%r,%r\n" % (x, y, z, v))


def lattice(h, nx, ny, nz, shift=0.0, jlo=None):
    """Single-level lattice of ny layers in y from index jlo (default: centred
    on y = 0), then shifted by shift along y."""
    v = h ** 3
    if jlo is None:
        jlo = -(ny // 2)
    return [((i + 0.5) * h, (j + 0.5) * h + shift, (k + 0.5) * h, v)
            for i in range(nx) for j in range(jlo, jlo + ny)
            for k in range(nz)]


def two_level(nx, ny, nz, patch):
    """Coarse lattice h = 0.5; the coarse cells (i, j, k) in patch are each
    replaced by REFINE**3 fine cells on the fine lattice of the same form."""
    vc, vf = H_COARSE ** 3, H_FINE ** 3
    cells = []
    for i in range(nx):
        for j in range(-(ny // 2), ny - ny // 2):
            for k in range(nz):
                if (i, j, k) not in patch:
                    cells.append(((i + 0.5) * H_COARSE, (j + 0.5) * H_COARSE,
                                  (k + 0.5) * H_COARSE, vc))
                    continue
                for a in range(REFINE):
                    x = (REFINE * i + a + 0.5) * H_FINE
                    for b in range(REFINE):
                        y = (REFINE * j + b + 0.5) * H_FINE
                        for c in range(REFINE):
                            z = (REFINE * k + c + 0.5) * H_FINE
                            cells.append((x, y, z, vf))
    return cells


def box(irange, jrange, krange):
    return {(i, j, k) for i in irange for j in jrange for k in krange}


def run_cli(path, *flags):
    proc = subprocess.run([sys.executable, TOOL, path, "--axis", "y"]
                          + list(flags), capture_output=True, text=True)
    payload = None
    if proc.returncode == 0 and "--json" in flags:
        payload = json.loads(proc.stdout,
                             object_pairs_hook=lambda pairs: pairs)
    return proc.returncode, payload, proc.stdout, proc.stderr


def as_dict(pairs):
    """Turn the ordered pairs from run_cli back into nested dicts."""
    if isinstance(pairs, list) and pairs and isinstance(pairs[0], tuple):
        return {k: as_dict(v) for k, v in pairs}
    return pairs


def classes_add_up(rep):
    return (rep["paired"] + rep["level_mismatch"] + rep["unpaired"]
            == rep["queried"])


def raw(path):
    xs, ys, zs, vs = check_mirror.read_table(path)
    return check_mirror.measure(xs, ys, zs, vs, "y")


def build_test1(path):
    write_table(path, two_level(6, 6, 4, box((2, 3), (-1, 0), (1,))))


def build_test4(path):
    write_table(path, two_level(6, 6, 4, box((2, 3), (0,), (1,))))


def main():
    failures = []

    def check(ok, passed, failed):
        if ok:
            print("PASS  %s" % passed)
        else:
            failures.append(failed)

    workspace = tempfile.mkdtemp(prefix="check_mirror_test_")
    try:
        # 1. Exact mirror, two levels.
        t1 = os.path.join(workspace, "exact_two_level.csv")
        build_test1(t1)
        code, rep1, _, err = run_cli(t1, "--json")
        rep1 = as_dict(rep1) if code == 0 else None
        check(rep1 is not None and rep1["mirror_pair_max_delta"] < 1e-9
              and rep1["unpaired"] == 0 and rep1["level_mismatch"] == 0
              and rep1["paired"] == rep1["queried"],
              "exact two-level mirror: max delta 0, all paired",
              "test 1: exit %d, %s %s" % (code, rep1, err))

        # 2. and 3. Shifted single-level lattices.
        for h in (0.5, 0.125, 0.03125):
            t2 = os.path.join(workspace, "quarter_%g.csv" % h)
            write_table(t2, lattice(h, 10, 10, 10, shift=h / 4))
            res = raw(t2)
            deltas = res["deltas"]
            check(res["paired"] == res["queried"] and deltas
                  and abs(deltas[0] - 0.5) <= 1e-6
                  and abs(deltas[-1] - 0.5) <= 1e-6,
                  "h = %g shifted h/4: every paired delta is 0.5" % h,
                  "test 2, h = %g: paired %d of %d, delta %s .. %s"
                  % (h, res["paired"], res["queried"],
                     deltas[0] if deltas else None,
                     deltas[-1] if deltas else None))

            t3 = os.path.join(workspace, "half_%g.csv" % h)
            # Eleven layers at y = -5h .. 5h, the middle one on the plane.
            write_table(t3, lattice(h, 10, 11, 10, shift=h / 2, jlo=-6))
            res = raw(t3)
            deltas = res["deltas"]
            check(res["paired"] == res["queried"] and res["on_plane"] == 100
                  and deltas and deltas[-1] < 1e-9,
                  "h = %g shifted h/2: a mirror again, max delta 0" % h,
                  "test 3, h = %g: paired %d of %d, on plane %d, max %s"
                  % (h, res["paired"], res["queried"], res["on_plane"],
                     deltas[-1] if deltas else None))

        # 4. Patch on one side only.
        t4 = os.path.join(workspace, "patch_one_side.csv")
        build_test4(t4)
        code, rep4, _, err = run_cli(t4, "--json")
        rep4 = as_dict(rep4) if code == 0 else None
        check(rep4 is not None and rep1 is not None
              and rep4["level_mismatch"] > 0
              and rep4["mirror_pair_max_delta"]
              <= rep1["mirror_pair_max_delta"] + 1e-9
              and classes_add_up(rep4),
              "patch on one side: level mismatch %s, max delta held, "
              "classes add up" % (rep4 or {}).get("level_mismatch"),
              "test 4: exit %d, %s %s" % (code, rep4, err))

        # 5. Mechanical failures.
        t5a = os.path.join(workspace, "no_volume.csv")
        with open(t5a, "w", newline="\n") as handle:
            handle.write('"X (m)","Y (m)","Z (m)","Pressure (Pa)"\n')
            handle.write("0.25,0.25,0.25,1.0\n0.25,-0.25,0.25,1.0\n")
        code, _, _, err = run_cli(t5a, "--json")
        check(code == 2 and "Pressure (Pa)" in err,
              "no volume column: exit 2, header in the message",
              "test 5a: exit %d, stderr %r" % (code, err))

        t5b = os.path.join(workspace, "all_on_plane.csv")
        write_table(t5b, [(x, 0.0, z, v) for x, _, z, v in
                          lattice(0.5, 3, 1, 3)])
        code, _, _, err = run_cli(t5b, "--json")
        check(code == 2 and "off-plane" in err,
              "every cell on the plane: exit 2",
              "test 5b: exit %d, stderr %r" % (code, err))

        # 6. Legacy block only with the flag.
        code, pairs, _, _ = run_cli(t1, "--json", "--legacy-median")
        keys = [k for k, _ in pairs] if code == 0 else []
        leg = as_dict(pairs).get("legacy_median") if code == 0 else None
        check(keys[-1:] == ["legacy_median"] and leg is not None
              and leg.get("deprecated") is True
              and list(leg) == ["deprecated", "scale", "max"],
              "--legacy-median: block last, deprecated true",
              "test 6: exit %d, keys %s, block %s" % (code, keys, leg))
        check(rep1 is not None and "legacy_median" not in rep1,
              "no --legacy-median: no legacy block",
              "test 6: legacy block present without the flag")

        # 7. Key order and base name. Columns shuffled, one extra ignored.
        t7 = os.path.join(workspace, "shuffled_columns.csv")
        with open(t7, "w", newline="\n") as handle:
            handle.write('"Volume (m^3)","Pressure (Pa)",'
                         '"Z (m)","x(m)","Y (m)"\n')
            for x, y, z, v in lattice(0.5, 4, 4, 4):
                handle.write("%r,0.0,%r,%r,%r\n" % (v, z, x, y))
        code, pairs, _, err = run_cli(t7, "--json")
        keys = [k for k, _ in pairs] if code == 0 else []
        rep7 = as_dict(pairs) if code == 0 else {}
        sub_ok = (code == 0
                  and list(rep7["delta"]) == ["p50", "p95", "p99", "max"]
                  and list(rep7["histogram"]) == ["edges", "counts",
                                                  "overflow"]
                  and rep7["histogram"]["edges"] == check_mirror.EDGES
                  and len(rep7["histogram"]["counts"]) == 10)
        check(keys == KEYS and sub_ok
              and rep7["table"] == "shuffled_columns.csv"
              and rep7["paired"] == rep7["queried"] == 64,
              "JSON key order as documented, base name only",
              "test 7: exit %d, keys %s, table %r %s"
              % (code, keys, rep7.get("table"), err))
        code, _, text, _ = run_cli(t7)
        check(code == 0 and text.startswith("check_mirror.py %s"
                                            % check_mirror.TOOL_VERSION)
              and workspace not in text,
              "text report: version banner, no path",
              "test 7: text report exit %d, %r" % (code, text[:200]))

        # 8. Size: 61 x 20 x 22 coarse cells, 116 of them refined 16:1.
        t8 = os.path.join(workspace, "size_two_level.csv")
        cells = two_level(61, 20, 22, box(range(16, 45), (-1, 0), (10, 11)))
        write_table(t8, cells)
        n8 = len(cells)
        del cells
        t0 = time.monotonic()
        code, rep8, _, err = run_cli(t8, "--json")
        wall = time.monotonic() - t0
        rep8 = as_dict(rep8) if code == 0 else None
        print("INFO  test 8: %d cells, wall time %.1f s" % (n8, wall))
        check(n8 == 501860 and rep8 is not None and rep8["cells"] == n8
              and rep8["paired"] == rep8["queried"]
              and rep8["mirror_pair_max_delta"] < 1e-9,
              "501 860 cells measured, exact mirror",
              "test 8: %d cells, exit %d, %s %s" % (n8, code, rep8, err))
        check(wall <= TIME_LIMIT_S,
              "501 860 cells within %.0f s" % TIME_LIMIT_S,
              "test 8: wall time %.1f s exceeds %.0f s; report it, do not "
              "add a dependency" % (wall, TIME_LIMIT_S))
    finally:
        shutil.rmtree(workspace, ignore_errors=True)

    if failures:
        for line in failures:
            print("FAIL  %s" % line)
        return 1
    print("\nself test OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
