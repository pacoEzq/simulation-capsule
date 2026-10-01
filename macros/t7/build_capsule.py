#!/usr/bin/env python3
"""Builds the capsules of a finished sweep: the one command after sweep_driver.sh (T7 chain,
spec v16, 3.6). Standard library only, Python 3.12 or later (it stops otherwise; the system python3
may be older). Run it from the run directory, which holds sweep.json:

    <python3.12> build_capsule.py --tools <dir> [--repo-commit <sha>] [--base-commit <sha>] [--points 100,300]

--tools is the tools/ folder of a simulation-capsule clone (check_mirror.py, check_capsule.py,
capsule_ledger.py, diff_capsule_views.py). Every tool runs under the interpreter that runs this
script (sys.executable); diff_capsule_views.py also needs numpy and Pillow in it. sweep.json gives
the baseline ("baseline", one of "points"); without it the script stops before building.

  0. once, before any point: check_mirror.py work/_prepare/mesh_cells.csv --axis y --json, its
     stdout written to work/_prepare/mesh_mirror.json. mesh_cells.csv is the cell table object_audit
     exports on the template. Exit 0 is a measurement, whatever the values (mirror_pair_max_delta
     is null when no cell is paired). No mesh_cells.csv, any other exit or an output without the
     three mirror_* keys fails every point with the reason, and no stale mesh_mirror.json is left

Then every work/cube_re<NNNN>/ is built on its own (work/_failed/ never is); a failure is reported
and the next point goes on. Steps 1 to 4 below are the build (spec step 1), for every point first:

  1. an existing capsules/capsule_cube_re<NNNN>/ (and its zip) moves to
     work/_prev/capsule_cube_re<NNNN>_<UTC>/; the capsule is built into a new folder
  2. the whitelist is copied, nothing else: setup.txt, summary.json, manifest.json,
     planes/plane_<plane>.png and the files manifest.json names in views[] (since v16 only
     views/view_cp_body.png; any other PNG left in work/.../views/ by an older session B is ignored)
  3. planes/plane_<plane>.csv is written from planes/plane_<plane>_raw.csv: the coordinate held
     constant is dropped, every value is rounded to %.3f and one comment line closes the file's
     world; grid, spacing and node counts come from the data and the manifest
  4. manifest.json gets the final planes[].sha256, chain.repo.commit (--repo-commit, else null) and
     checks = {mirror_pair_max_delta, mirror_unpaired, mirror_level_mismatch}, copied from the keys
     of the same name in mesh_mirror.json: one measurement of the meshed template, the same in every
     capsule. Last, the gray frame work/cube_re<NNNN>/diffsrc/view_cp_body.png is copied to
     diffsrc/capsule_cube_re<NNNN>/view_cp_body.png (outside the capsule, spec 8.3). If any of 1 to
     4 fails, the new folder is removed: the point leaves nothing in capsules/

Then, with every point built (spec steps 2 to 4):

  5. diff against the baseline, every built point but the baseline: diff.json is written at the
     capsule's root in the T6b shape (schema diff/2) that diff_capsule_views.py reads, with the
     scalar comparison of forces and pressure and known_differences Re, status and iterations; then
     diff_capsule_views.py <capsule> --src diffsrc --write measures the gray frames, writes
     diff/diff_cp_body.png and puts its numbers back into diff.json; then summary.json gets
     "extensions": ["diff"]. While the tool runs, diff.json base.path points at the baseline in
     capsules/; once it has run it points where the baseline is published, examples/<capsule>.
     base.frozen_copy_path is always base.path; base.commit is --base-commit (40 lowercase hex, the
     exact commit the baseline is published at), else null and the variant is not publishable.
     The gray frame carries no title, so crop_rows_top is 0. No baseline, or any failure, leaves the
     variant without diff.json, diff/ and extensions: built, not publishable
  6. tokens, after the diff so that diff.json and diff/ are counted: capsule_ledger.py <capsule>
     --json --per-file measures the capsule and this script writes manifest.tokens = {tool, eol:
     "LF", files, total} from that output, measuring again until the manifest's own entry agrees
     with the file it sits in. A failure removes the capsule (and its gray frame): the point failed
  7. publication, not existence: a point is publishable only if check_capsule.py --strict passes,
     chain.repo.commit is not null, step 0 exited 0 (a point is never built otherwise) and, for a
     variant, its diff was written. A null checks.mirror_pair_max_delta is a measurement and does
     not block. Otherwise the capsule stays for the operator with no zip; if all hold, the zip is
     written next to it
  8. one line per point: built or failed, publishable or not, each with its reason(s)

Exit: 0 every point built, 1 at least one point failed, 2 usage or setup error.
"""
import argparse
import csv
import hashlib
import json
import math
import re
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

WORK = Path("work")
CAPSULES = Path("capsules")
PREV = WORK / "_prev"
PREPARE = WORK / "_prepare"
MESH_CELLS = PREPARE / "mesh_cells.csv"
MESH_MIRROR = PREPARE / "mesh_mirror.json"
MIRROR_KEYS = ("mirror_pair_max_delta", "mirror_unpaired", "mirror_level_mismatch")
MIN_PYTHON = (3, 12)
POINT_DIR = re.compile(r"^cube_re(\d{4})$")
ROOT_WHITELIST = ("setup.txt", "summary.json", "manifest.json")
SWEEP_JSON = Path("sweep.json")
# Body view and its diff (spec v16, 8.1 and 8.3; names of decision 79).
VIEW_FILE = "views/view_cp_body.png"
DIFFSRC = Path("diffsrc")                # gray frames, outside the capsules, as in the public repo root
DIFF_PAIR = "diff_cp_body"
DIFF_OUTPUT = "diff/diff_cp_body.png"
PUBLISHED_ROOT = "examples"              # where a publishable capsule goes in simulation-capsule (spec 9)
CP_RANGE = [-2.8, 1.1]                   # colour bar of the body view, decision 82
COLORBAR_LEVELS = 256
THRESHOLD_LEVELS = 3                     # 3 levels of 256 over the range of 3.9, rule ">=" as in T6b
OUTPUT_GAIN = 6                          # same gain as T6b
FORCES = ("cd", "cd_pressure", "cd_friction", "cl", "cy")
PRESSURE = ("cp_base", "cp_min", "cp_stagnation")
COORDS = ("x_D", "y_D", "z_D")
COORD_TOL = 1.0e-6         # same lattice tolerance as output_exporter.java, in D
SPACING_REL_TOL = 1.0e-6
TOOL_TAIL = 300            # characters of a tool's output kept in a reason
LEDGER_ROUNDS = 5          # ledger runs allowed for manifest.tokens to settle


class BuildError(Exception):
    pass


def utc_stamp():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(65536), b""):
            h.update(block)
    return h.hexdigest()


def fmt_number(v):
    """Short form for the comment line: 0.25, 2, 0; never -0."""
    s = ("%.6f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def fmt_value(v):
    s = "%.3f" % v
    return "0.000" if s == "-0.000" else s


def one_line(text):
    return " ".join(str(text).split())[-TOOL_TAIL:]


# ---------------------------------------------------------------- tools
def run_tool(args, merge=True):
    """(exit code, stdout); stderr is merged into stdout unless merge is False, when it is appended
    to the output only on a non-zero exit (a JSON reader must see stdout alone)."""
    try:
        p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT if merge else subprocess.PIPE,
                           stdin=subprocess.DEVNULL, universal_newlines=True)
    except OSError as e:
        raise BuildError("%s could not start: %s" % (Path(args[1]).name, e))
    if not merge and p.returncode != 0:
        return p.returncode, p.stdout + (p.stderr or "")
    return p.returncode, p.stdout


def tool(tools, name):
    path = tools / name
    if not path.is_file():
        raise BuildError("%s not found in --tools" % name)
    return path


def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def run_ledger(tools, capsule):
    """capsule_ledger.py <capsule> --json --per-file, parsed into the manifest.tokens block.

    The --json output of capsule_ledger.py (simulation-capsule tools/) is a list with one result per
    capsule argument: {"capsule", "tokens", "crlf", "artifacts": [{"path", "tokens", "crlf", ...}]}.
    files maps every artifact path to its tokens; total is the result's tokens and must equal their
    sum. A CRLF anywhere contradicts eol "LF" and fails the point."""
    script = tool(tools, "capsule_ledger.py")
    rc, out = run_tool([sys.executable, str(script), str(capsule), "--json", "--per-file"], merge=False)
    if rc != 0:
        raise BuildError("capsule_ledger.py exit %d: %s" % (rc, one_line(out)))
    try:
        results = json.loads(out)
    except ValueError:
        raise BuildError("capsule_ledger.py --json output does not parse: %s" % one_line(out))
    if not isinstance(results, list) or len(results) != 1 or not isinstance(results[0], dict):
        raise BuildError("capsule_ledger.py --json gave %s, expected one result" % one_line(out))
    r = results[0]
    arts, total = r.get("artifacts"), r.get("tokens")
    if not isinstance(arts, list) or not arts or not is_int(total):
        raise BuildError("capsule_ledger.py result has no artifacts[] or no integer tokens")
    files = {}
    for a in arts:
        if not isinstance(a, dict) or not isinstance(a.get("path"), str) or not is_int(a.get("tokens")) \
                or a["path"] in files:
            raise BuildError("capsule_ledger.py artifact %s" % one_line(json.dumps(a)))
        if a.get("crlf"):
            raise BuildError("capsule_ledger.py reports CRLF in %s, eol must be LF" % a["path"])
        files[a["path"]] = a["tokens"]
    if r.get("crlf"):
        raise BuildError("capsule_ledger.py reports CRLF in the capsule, eol must be LF")
    if sum(files.values()) != total:
        raise BuildError("capsule_ledger.py total %d is not the sum of its artifacts (%d)" % (total, sum(files.values())))
    return {"tool": "capsule_ledger.py", "eol": "LF", "files": dict(sorted(files.items())), "total": total}


def write_tokens(tools, capsule, manifest):
    """Step 4, second half: manifest.tokens from the ledger. manifest.json is itself measured, so the
    ledger runs again after each write until what it measures is what the manifest says."""
    for _ in range(LEDGER_ROUNDS):
        tokens = run_ledger(tools, capsule)
        if "manifest.json" not in tokens["files"]:
            raise BuildError("capsule_ledger.py lists no manifest.json in its artifacts")
        if manifest.get("tokens") == tokens:
            return
        manifest["tokens"] = tokens
        write_manifest(capsule, manifest)
    raise BuildError("manifest.tokens did not settle after %d ledger runs" % LEDGER_ROUNDS)


def is_number(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


def measure_mirror(tools):
    """Step 0: check_mirror.py on the template's cell table, once. Returns the checks block, the three
    mirror_* keys of its --json output; raises BuildError with the reason on anything but exit 0 with
    those keys. A mesh_mirror.json from an earlier build is removed first, so it only ever holds the
    measurement of this build."""
    if MESH_MIRROR.exists():
        MESH_MIRROR.unlink()
    if not MESH_CELLS.is_file():
        raise BuildError("no %s (object_audit writes it on the template)" % MESH_CELLS.as_posix())
    script = tool(tools, "check_mirror.py")
    rc, out = run_tool([sys.executable, str(script), MESH_CELLS.as_posix(), "--axis", "y", "--json"], merge=False)
    if rc != 0:
        raise BuildError("check_mirror.py exit %d: %s" % (rc, one_line(out)))
    try:
        report = json.loads(out)
    except ValueError:
        raise BuildError("check_mirror.py --json output does not parse: %s" % one_line(out))
    if not isinstance(report, dict) or any(k not in report for k in MIRROR_KEYS):
        raise BuildError("check_mirror.py --json output has no %s" % ", ".join(MIRROR_KEYS))
    delta = report["mirror_pair_max_delta"]
    if delta is not None and not is_number(delta):
        raise BuildError("check_mirror.py mirror_pair_max_delta %s is neither a number nor null" % one_line(delta))
    for k in MIRROR_KEYS[1:]:
        if not is_int(report[k]) or report[k] < 0:
            raise BuildError("check_mirror.py %s %s is not a count" % (k, one_line(report[k])))
    MESH_MIRROR.write_text(out, encoding="utf-8", newline="\n")
    return {k: report[k] for k in MIRROR_KEYS}


def run_strict_check(tools, capsule):
    script = tool(tools, "check_capsule.py")
    rc, out = run_tool([sys.executable, str(script), "--strict", str(capsule)])
    return rc, out


# ---------------------------------------------------------------- steps
def free_prev(name, stamp, suffix):
    """A work/_prev/ name not taken yet: two builds in the same UTC second get _2, _3, ..."""
    dest, n = PREV / ("%s_%s%s" % (name, stamp, suffix)), 1
    while dest.exists():
        n += 1
        dest = PREV / ("%s_%s_%d%s" % (name, stamp, n, suffix))
    return dest


def move_previous(name, stamp):
    """Step 1: the previous capsule and its zip leave capsules/ for work/_prev/."""
    moved = []
    old_dir, old_zip = CAPSULES / name, CAPSULES / (name + ".zip")
    if old_dir.exists():
        PREV.mkdir(parents=True, exist_ok=True)
        dest = free_prev(name, stamp, "")
        shutil.move(str(old_dir), str(dest))
        moved.append(dest)
    if old_zip.exists():
        PREV.mkdir(parents=True, exist_ok=True)
        dest = free_prev(name, stamp, ".zip")
        shutil.move(str(old_zip), str(dest))
        moved.append(dest)
    return moved


def read_manifest(point):
    path = point / "manifest.json"
    if not path.is_file():
        raise BuildError("no manifest.json in %s" % point.as_posix())
    try:
        m = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as e:
        raise BuildError("manifest.json does not parse: %s" % e)
    planes = m.get("planes")
    if not isinstance(planes, list) or not planes:
        raise BuildError("manifest.json has no planes[]")
    for e in planes:
        f = e.get("file", "") if isinstance(e, dict) else ""
        if not re.fullmatch(r"planes/plane_[a-z0-9]+\.csv", f):
            raise BuildError("manifest.json planes[] entry with file '%s'" % f)
    return m


def plane_of(entry):
    return entry["file"][len("planes/plane_"):-len(".csv")]


def copy_whitelist(point, capsule, manifest):
    """Step 2: setup.txt, summary.json, manifest.json, planes/plane_<plane>.png and the views
    manifest.json names. A PNG in views/ that views[] does not name stays behind: the .sim files of
    30.09 left the four retired cut views there."""
    wanted = [Path(n) for n in ROOT_WHITELIST]
    wanted += [Path("planes") / ("plane_%s.png" % plane_of(e)) for e in manifest["planes"]]
    views = manifest.get("views")
    if not isinstance(views, list) or not views:
        raise BuildError("manifest.json has no views[]")
    for e in views:
        f = e.get("file", "") if isinstance(e, dict) else ""
        if not re.fullmatch(r"views/[a-z0-9_]+\.png", f):
            raise BuildError("manifest.json views[] entry with file '%s'" % f)
        wanted.append(Path(f))
    for rel in wanted:
        src = point / rel
        if not src.is_file():
            raise BuildError("missing %s" % (point / rel).as_posix())
        (capsule / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(str(src), str(capsule / rel))


def lattice(values):
    """Sorted distinct values of one coordinate, merged within COORD_TOL."""
    keys = {}
    for v in values:
        keys.setdefault(round(v / COORD_TOL), v)
    return [keys[k] for k in sorted(keys)]


def uniform_spacing(axis_values, name, plane):
    if len(axis_values) < 2:
        raise BuildError("plane %s: %s has %d distinct value(s), no spacing" % (plane, name, len(axis_values)))
    steps = [b - a for a, b in zip(axis_values, axis_values[1:])]
    h = (axis_values[-1] - axis_values[0]) / (len(axis_values) - 1)
    if any(abs(s - h) > SPACING_REL_TOL * max(1.0, abs(h)) for s in steps):
        raise BuildError("plane %s: %s is not evenly spaced" % (plane, name))
    return h


def reformat_plane(point, capsule, entry):
    """Step 3: planes/plane_<plane>.csv from the raw CSV of output_exporter. Returns its sha256."""
    plane = plane_of(entry)
    raw_rel = entry.get("raw_file") or "planes/plane_%s_raw.csv" % plane
    raw = point / raw_rel
    if not raw.is_file():
        raise BuildError("missing %s" % raw.as_posix())
    with open(raw, newline="", encoding="utf-8") as f:
        rows = [r for r in csv.reader(f) if any(c.strip() for c in r)]
    if not rows:
        raise BuildError("%s is empty" % raw.as_posix())
    header = [h.strip() for h in rows[0]]
    if len(set(header)) != len(header) or any(c not in header for c in COORDS):
        raise BuildError("%s header %s" % (raw.as_posix(), ",".join(header)))
    data = []
    for i, r in enumerate(rows[1:], start=2):
        if len(r) != len(header):
            raise BuildError("%s line %d has %d fields, header has %d" % (raw.as_posix(), i, len(r), len(header)))
        try:
            vals = [float(c) for c in r]
        except ValueError:
            raise BuildError("%s line %d is not numeric" % (raw.as_posix(), i))
        if not all(math.isfinite(v) for v in vals):
            raise BuildError("%s line %d holds a non-finite value" % (raw.as_posix(), i))
        data.append(dict(zip(header, vals)))
    if not data:
        raise BuildError("%s has no data rows" % raw.as_posix())

    # the coordinate held constant, from the data
    constant = [c for c in COORDS if len(lattice(d[c] for d in data)) == 1]
    if len(constant) != 1:
        raise BuildError("plane %s: %d coordinates are constant (%s), expected one"
                         % (plane, len(constant), ",".join(constant)))
    const = constant[0]
    columns = entry.get("columns")
    expected = [h for h in header if h != const]
    if not isinstance(columns, list) or len(columns) != 6 or sorted(columns) != sorted(expected):
        raise BuildError("plane %s: manifest columns %s differ from the raw columns without %s (%s)"
                         % (plane, columns, const, ",".join(expected)))
    axes = [c for c in columns if c in COORDS]
    a_vals = lattice(d[axes[0]] for d in data)
    b_vals = lattice(d[axes[1]] for d in data)
    n1, n2 = len(a_vals), len(b_vals)
    h1 = uniform_spacing(a_vals, axes[0], plane)
    h2 = uniform_spacing(b_vals, axes[1], plane)

    # cross-check with the manifest, never hard-coded
    gi = entry.get("grid_intervals")
    if gi != [n1 - 1, n2 - 1]:
        raise BuildError("plane %s: data grid %d x %d, manifest grid_intervals %s" % (plane, n1, n2, gi))
    if entry.get("rows_expected") != n1 * n2:
        raise BuildError("plane %s: %d x %d nodes, manifest rows_expected %s"
                         % (plane, n1, n2, entry.get("rows_expected")))
    if entry.get("rows_written") != len(data):
        raise BuildError("plane %s: %d rows in %s, manifest rows_written %s"
                         % (plane, len(data), raw_rel, entry.get("rows_written")))
    nodes = {(round(d[axes[0]] / COORD_TOL), round(d[axes[1]] / COORD_TOL)) for d in data}
    if len(nodes) != len(data):
        raise BuildError("plane %s: %d rows but %d distinct nodes" % (plane, len(data), len(nodes)))

    spacing = fmt_number(h1) if abs(h1 - h2) <= SPACING_REL_TOL * max(1.0, abs(h1)) \
        else "%s x %s" % (fmt_number(h1), fmt_number(h2))
    comment = "# plane %s = %s | grid %d x %d, spacing %s D | %d of %d nodes | fields %%.3f | nondimensional" % (
        const, fmt_number(data[0][const]), n1, n2, spacing, len(data), n1 * n2)
    data.sort(key=lambda d: (d[axes[1]], d[axes[0]]))
    out = capsule / entry["file"]
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(comment + "\n")
        f.write(",".join(columns) + "\n")
        for d in data:
            f.write(",".join(fmt_value(d[c]) for c in columns) + "\n")
    return sha256(out)


def finish_manifest(capsule, manifest, plane_shas, commit, checks):
    """Step 4, first half: final planes[].sha256, chain.repo.commit and checks in the capsule's copy."""
    for e in manifest["planes"]:
        e["sha256"] = plane_shas[plane_of(e)]
    repo = manifest.get("chain", {}).get("repo") if isinstance(manifest.get("chain"), dict) else None
    if not isinstance(repo, dict):
        raise BuildError("manifest.json has no chain.repo")
    repo["commit"] = commit
    manifest["checks"] = dict(checks)
    write_manifest(capsule, manifest)


def write_manifest(capsule, manifest):
    (capsule / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                                           encoding="utf-8", newline="\n")


def publication_blockers(manifest):
    """Step 5 besides --strict: chain.repo.commit. Step 0 needs no line here: without it no point is
    built, and a null checks.mirror_pair_max_delta is a measurement, not a blocker."""
    reasons = []
    repo = manifest.get("chain", {}).get("repo", {})
    if repo.get("commit") is None:
        reasons.append("no --repo-commit, chain.repo.commit is null")
    return reasons


def write_zip(capsule):
    """The zip next to the capsule, entries under the capsule's name, sorted, fixed timestamps."""
    target = capsule.parent / (capsule.name + ".zip")
    files = sorted(p for p in capsule.rglob("*") if p.is_file())
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED) as z:
        for p in files:
            info = zipfile.ZipInfo((Path(capsule.name) / p.relative_to(capsule)).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, p.read_bytes())
    return target


# ---------------------------------------------------------------- body view and diff
def frame_of(name):
    """The gray frame of a capsule, where diff_capsule_views.py looks for it (--src diffsrc)."""
    return DIFFSRC / name / Path(VIEW_FILE).name


def copy_frame(point, name):
    """Step 4, last: work/cube_re<NNNN>/diffsrc/view_cp_body.png to diffsrc/<capsule>/."""
    src = point / "diffsrc" / Path(VIEW_FILE).name
    if not src.is_file():
        raise BuildError("missing %s" % src.as_posix())
    dest = frame_of(name)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(str(src), str(dest))


def png_size(path):
    """(width, height) from the IHDR chunk; anything else is not a PNG."""
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        raise BuildError("%s is not a PNG" % Path(path).as_posix())
    return int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")


def read_json(path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise BuildError("%s: %s" % (Path(path).as_posix(), e))


def body_view(manifest, name):
    for e in manifest.get("views", []):
        if isinstance(e, dict) and e.get("file") == VIEW_FILE:
            return e
    raise BuildError("%s manifest.json views[] has no %s" % (name, VIEW_FILE))


def summary_value(summary, name, block, key):
    b = summary.get(block)
    if not isinstance(b, dict) or key not in b:
        raise BuildError("%s summary.json has no %s.%s" % (name, block, key))
    return b[key]


def scalar_block(summary, name):
    """The forces and pressure of one capsule as summary.json publishes them, each with its statistic."""
    out = {}
    for block, keys in (("forces", FORCES), ("pressure", PRESSURE)):
        out[block] = {}
        for k in keys:
            v = summary_value(summary, name, block, k)
            if not is_number(v):
                raise BuildError("%s summary.json %s.%s is not a finite number" % (name, block, k))
            out[block][k] = v
            out[block][k + "_statistic"] = summary_value(summary, name, block, k + "_statistic")
    return out


def diff_manifest(base, variant, base_commit):
    """diff.json of one variant, T6b shape (schema diff/2) as diff_capsule_views.py reads it. base and
    variant are dicts {name, capsule, manifest, summary}; base_commit is --base-commit or None. The
    numbers of the pair and of pixels_* are left to the tool."""
    bs, vs = base["summary"], variant["summary"]
    bview, vview = body_view(base["manifest"], base["name"]), body_view(variant["manifest"], variant["name"])
    # Same camera and same colour bar, as read by session B in each point; otherwise the difference
    # measures the renderer.
    for key in ("camera", "colorbar"):
        if bview.get(key) != vview.get(key):
            raise BuildError("views[] %s of %s differs from the baseline's" % (key, variant["name"]))
    colorbar = vview.get("colorbar") or {}
    if colorbar.get("range") != CP_RANGE or colorbar.get("levels") != COLORBAR_LEVELS:
        raise BuildError("views[] colorbar of %s reads range %s levels %s, declared %s and %d"
                         % (variant["name"], colorbar.get("range"), colorbar.get("levels"), CP_RANGE,
                            COLORBAR_LEVELS))
    bframe, vframe = frame_of(base["name"]), frame_of(variant["name"])
    for f in (bframe, vframe):
        if not f.is_file():
            raise BuildError("missing gray frame %s" % f.as_posix())
    width, height = png_size(vframe)
    if png_size(bframe) != (width, height):
        raise BuildError("gray frames of %s and the baseline differ in size" % variant["name"])

    bre, vre = summary_value(bs, base["name"], "sweep", "value"), summary_value(vs, variant["name"], "sweep", "value")
    bconv, vconv = bs.get("convergence") or {}, vs.get("convergence") or {}
    level = (CP_RANGE[1] - CP_RANGE[0]) / COLORBAR_LEVELS
    bscal, vscal = scalar_block(bs, base["name"]), scalar_block(vs, variant["name"])
    deltas = {}
    for block, keys in (("forces", FORCES), ("pressure", PRESSURE)):
        for k in keys:
            deltas["delta_" + k] = round(vscal[block][k] - bscal[block][k], 7)
    deltas["base"] = bscal
    deltas["variant"] = vscal
    deltas["note"] = ("delta is variant minus base. Each side keeps its own *_statistic: a converged point "
                      "publishes the last iteration, a stationary or no_steady_state point a pooled mean over "
                      "its closed windows.")
    # build time: the baseline in capsules/; rewritten to PUBLISHED_ROOT after the tool ran
    base_path = (CAPSULES / base["name"]).as_posix()
    return {
        "schema_version": "diff/2",
        "base": {"capsule": base["name"], "case": "cube_re%04d" % bre, "repo": "simulation-capsule",
                 "path": base_path, "frozen_copy_path": base_path, "commit": base_commit},
        "variant": {"capsule": variant["name"], "case": "cube_re%04d" % vre},
        "difference": {"parameter": "Re", "base_value": bre, "variant_value": vre,
                       "implementation": "global parameter Re, mu = rho*U*D/Re",
                       "mesh_identical": True},
        "view_contract": {
            "camera": vview.get("camera"),
            "projection": "parallel",
            "width_px": width, "view_height_px": height, "measured_height_px": height,
            "crop_rows_top": 0,
            "colorbar_levels": COLORBAR_LEVELS,
            "contour_style": "Smooth Filled",
            "colormap_published": colorbar.get("colormap"),
            "colormap_measured": "grayscale",
            "range_cp": CP_RANGE,
            "range_note": "envelope of Re 300 to 3000; Re 100 saturates at the edges, clamped rather than clipped",
        },
        "pipeline": {
            "order": "crop_then_mask_then_diff",
            "crop": "none: the gray frame is exported without the title, so no row differs by its text",
            "mask": ("every non grey pixel in either frame, which removes the magenta background (1, 0, 1) and "
                     "its antialiased edge around the body; the colour bar is grey in both frames and is "
                     "evaluated"),
            "tool": "tools/diff_capsule_views.py",
            "instrument_recipe": ("the grayscale renders are the instrument and stay outside the capsule, SPEC 4.7. "
                                  "Session B exports the body view a second time from the same scene, camera and "
                                  "range, with colormap grayscale, a solid magenta background and no title."),
            "output_gain": OUTPUT_GAIN,
            "instrument_root": DIFFSRC.as_posix(),
            "integrity": ("the chain is view then grayscale frame then diff. All four hashes are checked before "
                          "measuring and a mismatch stops the run."),
        },
        "pairs": [{
            "name": DIFF_PAIR,
            "base_view": Path(VIEW_FILE).name,
            "variant_view": Path(VIEW_FILE).name,
            "source_sha256": {"base_view": sha256(base["capsule"] / VIEW_FILE),
                              "variant_view": sha256(variant["capsule"] / VIEW_FILE)},
            "instrument_sha256": {"base_frame": sha256(bframe), "variant_frame": sha256(vframe)},
            "output": DIFF_OUTPUT,
            "level_size": level,
            "threshold_levels": THRESHOLD_LEVELS,
            "threshold_comparison": ">=",
            "threshold_physical": round(THRESHOLD_LEVELS * level, 4),
        }],
        "scalar_deltas": deltas,
        "known_differences": [
            "Re: %s in the base, %s in the variant" % (bre, vre),
            "status: %s in the base, %s in the variant" % (bconv.get("status"), vconv.get("status")),
            "iterations: %s in the base, %s in the variant" % (bconv.get("iterations"), vconv.get("iterations")),
        ],
    }


def add_extension(capsule):
    """summary.json gets "extensions": ["diff"] before "sweep" (spec 5.2 order), inserted as text so
    every other byte run_macro and output_exporter wrote stays as written."""
    path = capsule / "summary.json"
    text = path.read_text(encoding="utf-8")
    before = json.loads(text)
    if "extensions" in before:
        raise BuildError("summary.json already has extensions")
    hits = list(re.finditer(r'\n([ \t]*)"sweep"\s*:', text))
    if len(hits) != 1:
        raise BuildError("summary.json has %d \"sweep\" keys, expected one" % len(hits))
    m = hits[0]
    text = text[:m.start()] + '\n%s"extensions": ["diff"],' % m.group(1) + text[m.start():]
    after = json.loads(text)
    if after.pop("extensions") != ["diff"] or after != before:
        raise BuildError("summary.json changed beyond extensions")
    path.write_text(text, encoding="utf-8", newline="\n")


def drop_diff(capsule):
    if (capsule / "diff.json").exists():
        (capsule / "diff.json").unlink()
    if (capsule / "diff").exists():
        shutil.rmtree(str(capsule / "diff"))


def write_diff(tools, base, variant, base_commit):
    """Step 5 for one variant. Returns None or the reason it has no diff."""
    capsule = variant["capsule"]
    try:
        dm = diff_manifest(base, variant, base_commit)
        (capsule / "diff.json").write_text(json.dumps(dm, indent=2, ensure_ascii=False) + "\n",
                                           encoding="utf-8", newline="\n")
        script = tool(tools, "diff_capsule_views.py")
        rc, out = run_tool([sys.executable, str(script), str(capsule), "--src", DIFFSRC.as_posix(), "--write"])
        if rc != 0:
            raise BuildError("diff_capsule_views.py exit %d: %s" % (rc, one_line(out)))
        if not (capsule / DIFF_OUTPUT).is_file():
            raise BuildError("diff_capsule_views.py wrote no %s" % DIFF_OUTPUT)
        extra = sorted(p.name for p in (capsule / "diff").iterdir() if p.as_posix() != (capsule / DIFF_OUTPUT).as_posix())
        if extra:
            raise BuildError("diff/ holds more than %s: %s" % (DIFF_OUTPUT, ", ".join(extra)))
        dm = read_json(capsule / "diff.json")
        pair = dm["pairs"][0]
        for k in ("changed_pixel_fraction", "max_delta_levels", "max_delta_physical"):
            if not is_number(pair.get(k)):
                raise BuildError("diff_capsule_views.py left no %s in diff.json" % k)
        version = re.match(r"diff_capsule_views (\S+)", out.strip())
        dm["pipeline"]["tool"] = "tools/diff_capsule_views.py" + (" " + version.group(1) if version else "")
        dm["base"]["path"] = "%s/%s" % (PUBLISHED_ROOT, base["name"])
        dm["base"]["frozen_copy_path"] = dm["base"]["path"]
        (capsule / "diff.json").write_text(json.dumps(dm, indent=2, ensure_ascii=False) + "\n",
                                           encoding="utf-8", newline="\n")
        add_extension(capsule)
    except (BuildError, OSError, ValueError, KeyError, TypeError, IndexError) as e:
        drop_diff(capsule)
        return str(e) if isinstance(e, BuildError) else "%s: %s" % (type(e).__name__, e)
    return None


# ---------------------------------------------------------------- one point
def build_point(nnnn, commit, checks):
    """Steps 1 to 4. Returns the point record; record["failed"] holds the reason, or None."""
    name = "capsule_cube_re%s" % nnnn
    point = WORK / ("cube_re%s" % nnnn)
    capsule = CAPSULES / name
    rec = {"nnnn": nnnn, "name": name, "capsule": capsule, "failed": None, "blockers": []}
    created = False
    try:
        if not point.is_dir():
            raise BuildError("no %s" % point.as_posix())
        manifest = read_manifest(point)
        move_previous(name, utc_stamp())
        capsule.mkdir(parents=True)
        created = True
        copy_whitelist(point, capsule, manifest)
        shas = {plane_of(e): reformat_plane(point, capsule, e) for e in manifest["planes"]}
        finish_manifest(capsule, manifest, shas, commit, checks)
        copy_frame(point, name)
        rec["manifest"] = manifest
        rec["summary"] = read_json(capsule / "summary.json")
    except (BuildError, OSError, ValueError, KeyError, TypeError) as e:
        if created and capsule.exists():
            shutil.rmtree(str(capsule))
        rec["failed"] = str(e) if isinstance(e, BuildError) else "%s: %s" % (type(e).__name__, e)
    return rec


def fail_after_build(rec, reason):
    if rec["capsule"].exists():
        shutil.rmtree(str(rec["capsule"]))
    if frame_of(rec["name"]).exists():
        frame_of(rec["name"]).unlink()
    rec["failed"] = reason


def read_baseline():
    """sweep.json "baseline": an integer among "points"."""
    if not SWEEP_JSON.is_file():
        raise BuildError("no sweep.json here; run from the run directory")
    sweep = read_json(SWEEP_JSON)
    points, base = sweep.get("points"), sweep.get("baseline")
    if not is_int(base) or not isinstance(points, list) or base not in points:
        raise BuildError("sweep.json baseline %r is not one of its points %r" % (base, points))
    return "%04d" % base


def main(argv=None):
    if sys.version_info < MIN_PYTHON:
        print("build_capsule: needs Python %d.%d or later, this interpreter is %d.%d; run it with a Python "
              "3.12 interpreter (the system python3 may be older)"
              % (MIN_PYTHON + tuple(sys.version_info[:2])), file=sys.stderr)
        return 2
    ap = argparse.ArgumentParser(description="Build the capsules of a finished T7 sweep (run from the run directory).")
    ap.add_argument("--tools", required=True, help="tools/ folder of a simulation-capsule clone")
    ap.add_argument("--repo-commit", default=None, help="simulation-capsule commit for chain.repo.commit")
    ap.add_argument("--base-commit", default=None,
                    help="simulation-capsule commit the baseline is published at, 40 lowercase hex, for diff.json base.commit")
    ap.add_argument("--points", default=None, help="comma list of Re values, e.g. 100,300 (default: every point)")
    args = ap.parse_args(argv)

    tools = Path(args.tools)
    if not tools.is_dir():
        print("build_capsule: --tools '%s' is not a directory" % args.tools, file=sys.stderr)
        return 2
    commit = args.repo_commit
    if commit is not None and not re.fullmatch(r"[0-9a-f]{7,40}", commit):
        print("build_capsule: --repo-commit '%s' is not a hex commit id" % commit, file=sys.stderr)
        return 2
    base_commit = args.base_commit
    if base_commit is not None and not re.fullmatch(r"[0-9a-f]{40}", base_commit):
        print("build_capsule: --base-commit '%s' is not a full commit id (40 lowercase hex characters)"
              % base_commit, file=sys.stderr)
        return 2
    if not WORK.is_dir():
        print("build_capsule: no work/ here; run it from the run directory", file=sys.stderr)
        return 2
    try:
        base_nnnn = read_baseline()
    except BuildError as e:
        print("build_capsule: %s" % e, file=sys.stderr)
        return 2

    if args.points:
        nnnns = []
        for p in args.points.split(","):
            p = p.strip()
            if not re.fullmatch(r"\d{1,4}", p) or int(p) < 1 or "%04d" % int(p) in nnnns:
                print("build_capsule: bad or repeated point '%s' in --points" % p, file=sys.stderr)
                return 2
            nnnns.append("%04d" % int(p))
    else:
        nnnns = sorted(m.group(1) for m in (POINT_DIR.match(d.name) for d in WORK.iterdir() if d.is_dir()) if m)
    if not nnnns:
        print("build_capsule: no work/cube_re<NNNN>/ to build", file=sys.stderr)
        return 2

    try:
        checks, step0 = measure_mirror(tools), None
    except (BuildError, OSError) as e:
        checks = None
        step0 = "step 0: %s" % (e if isinstance(e, BuildError) else "%s: %s" % (type(e).__name__, e))

    CAPSULES.mkdir(exist_ok=True)
    recs = []
    for nnnn in nnnns:
        if step0:
            recs.append({"nnnn": nnnn, "name": "capsule_cube_re%s" % nnnn, "failed": step0})
        else:
            recs.append(build_point(nnnn, commit, checks))

    # Step 5: the diff of every built variant against the baseline, with every point built first.
    base_name = "capsule_cube_re%s" % base_nnnn
    base = next((r for r in recs if r["nnnn"] == base_nnnn and not r["failed"]), None)
    no_base = "baseline %s not built" % base_name
    if base is None and base_nnnn not in nnnns and (CAPSULES / base_name / "manifest.json").is_file():
        # a baseline built by an earlier run and left in place by --points
        base = {"nnnn": base_nnnn, "name": base_name, "capsule": CAPSULES / base_name, "failed": None}
        try:
            base["manifest"] = read_json(base["capsule"] / "manifest.json")
            base["summary"] = read_json(base["capsule"] / "summary.json")
        except BuildError as e:
            base, no_base = None, "baseline %s unreadable: %s" % (base_name, e)
    for r in recs:
        if r["failed"] or r["nnnn"] == base_nnnn:
            continue
        if base_commit is None:
            r["blockers"].append("no --base-commit")
        reason = no_base if base is None else write_diff(tools, base, r, base_commit)
        if reason:
            r["blockers"].append("no diff: %s" % reason)
        else:
            r["diffed"] = True

    # Step 6: tokens, after the diff.
    for r in recs:
        if r["failed"]:
            continue
        try:
            write_tokens(tools, r["capsule"], r["manifest"])
        except (BuildError, OSError, ValueError) as e:
            fail_after_build(r, str(e) if isinstance(e, BuildError) else "%s: %s" % (type(e).__name__, e))
    if base is not None and base.get("failed"):
        for r in recs:
            if r.get("diffed") and not r["failed"]:
                r["blockers"].append("baseline %s failed after the diff" % base_name)

    # Steps 7 and 8.
    failed = 0
    for r in recs:
        if r["failed"]:
            built, published = "%s failed: %s" % (r["name"], r["failed"]), "not publishable: not built"
            failed += 1
        else:
            rc, out = run_strict_check(tools, r["capsule"])
            reasons = ["check_capsule.py --strict exit %d" % rc] if rc != 0 else []
            reasons += r["blockers"] + publication_blockers(r["manifest"])
            built = "%s built" % r["name"]
            if reasons:
                published = "not publishable: %s" % "; ".join(reasons)
            else:
                published = "publishable: %s written" % write_zip(r["capsule"]).as_posix()
        print("%s; %s" % (built, published))
    return 1 if failed else 0

if __name__ == "__main__":
    sys.exit(main())
