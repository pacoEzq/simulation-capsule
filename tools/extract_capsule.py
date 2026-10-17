#!/usr/bin/env python3
"""extract_capsule.py: build a Simulation Capsule from a neutral package.

    python tools/extract_capsule.py pkg_<alias>/ --out capsules/ [--strict] [--repo-commit SHA]
    python tools/extract_capsule.py --propose-card setup.txt [summary.json]

The neutral package is what a solver adapter leaves behind (the STAR-CCM+ adapter is the Part 7
macro chain): card.json, adapter.json, setup.txt, summary.json, planes/*_raw.csv, planes/*.png,
views/*.png, render.json, staleness.json, run_log.txt. One command does the three steps in a fixed
order: build capsule_<alias>/, check it with check_capsule.py, measure it with capsule_ledger.py.

Exit codes: 0 built and green, 1 built but a check failed or the build stopped, 2 usage.
Python 3.8+, standard library only.
"""
import argparse
import csv
import hashlib
import json
import math
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

SPEC_VERSION = "0.7"
COORDS = ("x_D", "y_D", "z_D")
COORD_TOL = 1e-6
SPACING_REL_TOL = 1e-4
LEDGER_ROUNDS = 5
COLORMAPS = ("viridis", "grayscale", "cividis", "inferno", "magma")  # lightness-monotonic
HERE = Path(__file__).resolve().parent


class ExtractError(Exception):
    pass


def utc_stamp():
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(65536), b""):
            h.update(block)
    return h.hexdigest()


def read_json(path, what):
    if not path.is_file():
        raise ExtractError("missing %s" % what)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except ValueError as e:
        raise ExtractError("%s does not parse: %s" % (what, e))


def fmt_number(v):
    s = ("%.6f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def fmt_value(v):
    s = "%.3f" % v
    return "0.000" if s == "-0.000" else s


# ---------------------------------------------------------------- card
def validate_card(card):
    """The card is checked here, in plain code, not against a schema."""
    def need(obj, key, where):
        if key not in obj:
            raise ExtractError("card.json: %s has no %s" % (where, key))
        return obj[key]

    def sourced(entry, where):
        if not isinstance(entry, dict):
            raise ExtractError("card.json: %s is not an object" % where)
        src = entry.get("source")
        if src not in ("read", "inferred"):
            raise ExtractError("card.json: %s.source must be read or inferred" % where)
        if src == "inferred" and not entry.get("why"):
            raise ExtractError("card.json: %s is inferred and gives no why" % where)

    if card.get("card_source") not in ("user", "llm_proposed"):
        raise ExtractError("card.json: card_source must be user or llm_proposed")
    body = need(card, "body", "card")
    alias = need(body, "alias", "body")
    if not isinstance(alias, str) or alias != alias.lower() or not alias.replace("_", "").isalnum():
        raise ExtractError("card.json: body.alias %r is not lowercase snake_case" % alias)
    for key in ("length", "velocity", "group", "regime", "formation", "fluid_region", "body_boundaries"):
        sourced(need(body, key, "body"), "body." + key)
    formation = body["formation"]
    for key in ("scalars", "fields"):
        if not isinstance(formation.get(key), str) or not formation[key]:
            raise ExtractError("card.json: body.formation.%s is empty" % key)
    if not isinstance(body.get("colormap"), str):
        raise ExtractError("card.json: body.colormap is missing")
    for i, st in enumerate(card.get("stations", [])):
        sourced(st, "stations[%d]" % i)
        for key in ("stem", "axis", "value", "grid", "spacing", "fields"):
            need(st, key, "stations[%d]" % i)
        if st["axis"] not in ("x", "y", "z") or not isinstance(st["grid"], list) or len(st["grid"]) != 2:
            raise ExtractError("card.json: stations[%d] axis or grid" % i)
    for i, vw in enumerate(card.get("views", [])):
        sourced(vw, "views[%d]" % i)
        for key in ("file", "question", "field", "range"):
            need(vw, key, "views[%d]" % i)
        if not isinstance(vw["range"], list) or len(vw["range"]) != 2:
            raise ExtractError("card.json: views[%d].range must be two numbers" % i)
        if not vw["question"].strip():
            raise ExtractError("card.json: views[%d] has an empty question" % i)
    return alias


# ---------------------------------------------------------------- package
def check_staleness(stale):
    enforced = stale.get("enforced", {})
    for key, on in enforced.items():
        if on and stale.get("%s_equal" % key) is False:
            raise ExtractError("staleness.json: %s differs between setup.txt, summary.json and the report" % key)


def check_audit(adapter):
    audit = adapter.get("audit", {})
    result = audit.get("result")
    if result is not None and str(result).lower() not in ("ok", "clean", "pass"):
        raise ExtractError("adapter.json: audit result is %r" % result)


# ---------------------------------------------------------------- planes
def lattice(values):
    keys = {}
    for v in values:
        keys.setdefault(round(v / COORD_TOL), v)
    return [keys[k] for k in sorted(keys)]


def uniform_spacing(axis_values, name, stem):
    if len(axis_values) < 2:
        raise ExtractError("plane %s: %s has %d distinct value(s)" % (stem, name, len(axis_values)))
    h = (axis_values[-1] - axis_values[0]) / (len(axis_values) - 1)
    steps = [b - a for a, b in zip(axis_values, axis_values[1:])]
    if any(abs(s - h) > SPACING_REL_TOL * max(1.0, abs(h)) for s in steps):
        raise ExtractError("plane %s: %s is not evenly spaced" % (stem, name))
    return h


def reformat_plane(pkg, capsule, station, formation):
    """planes/plane_<stem>.csv from the raw CSV, per SPEC 4.3. Returns the manifest entry."""
    stem = station["stem"]
    raw = pkg / "planes" / ("plane_%s_raw.csv" % stem)
    if not raw.is_file():
        raise ExtractError("missing %s" % raw.relative_to(pkg).as_posix())
    with open(raw, newline="", encoding="utf-8") as f:
        rows = [r for r in csv.reader(f) if any(c.strip() for c in r)]
    if len(rows) < 2:
        raise ExtractError("%s has no data rows" % raw.name)
    header = [h.strip() for h in rows[0]]
    if len(set(header)) != len(header) or any(c not in header for c in COORDS):
        raise ExtractError("%s header %s" % (raw.name, ",".join(header)))
    data = []
    for i, r in enumerate(rows[1:], start=2):
        if len(r) != len(header):
            raise ExtractError("%s line %d has %d fields, header has %d" % (raw.name, i, len(r), len(header)))
        try:
            vals = [float(c) for c in r]
        except ValueError:
            raise ExtractError("%s line %d is not numeric" % (raw.name, i))
        if not all(math.isfinite(v) for v in vals):
            raise ExtractError("%s line %d holds a non-finite value" % (raw.name, i))
        data.append(dict(zip(header, vals)))
    const = "%s_D" % station["axis"]
    if len(lattice(d[const] for d in data)) != 1:
        raise ExtractError("plane %s: %s is not constant in the data" % (stem, const))
    if abs(data[0][const] - float(station["value"])) > COORD_TOL:
        raise ExtractError("plane %s: data at %s = %s, card says %s" % (stem, const, fmt_number(data[0][const]),
                                                                          station["value"]))
    axes = [c for c in COORDS if c != const]
    fields = [h for h in header if h not in COORDS]
    missing_fields = [f for f in station["fields"] if f not in fields]
    if missing_fields:
        raise ExtractError("plane %s: card fields %s are not in the raw CSV" % (stem, missing_fields))
    columns = axes + list(station["fields"])
    a_vals = lattice(d[axes[0]] for d in data)
    b_vals = lattice(d[axes[1]] for d in data)
    n1, n2 = len(a_vals), len(b_vals)
    h1 = uniform_spacing(a_vals, axes[0], stem)
    h2 = uniform_spacing(b_vals, axes[1], stem)
    if [n1, n2] != [int(g) for g in station["grid"]]:
        raise ExtractError("plane %s: data grid %d x %d, card grid %s" % (stem, n1, n2, station["grid"]))
    nodes = {(round(d[axes[0]] / COORD_TOL), round(d[axes[1]] / COORD_TOL)) for d in data}
    if len(nodes) != len(data):
        raise ExtractError("plane %s: %d rows but %d distinct nodes" % (stem, len(data), len(nodes)))
    spacing = fmt_number(h1) if abs(h1 - h2) <= SPACING_REL_TOL * max(1.0, abs(h1)) \
        else "%s x %s" % (fmt_number(h1), fmt_number(h2))
    requested = n1 * n2
    in_body = requested - len(data)
    comment = "# plane %s = %s | grid %d x %d, spacing %s D | %d of %d nodes, %d in the body | %s | fields %%.3f | nondimensional" % (
        const, fmt_number(data[0][const]), n1, n2, spacing, len(data), requested, in_body, formation)
    data.sort(key=lambda d: (d[axes[1]], d[axes[0]]))
    out = capsule / "planes" / ("plane_%s.csv" % stem)
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(comment + "\n")
        f.write(",".join(columns) + "\n")
        for d in data:
            f.write(",".join(fmt_value(d[c]) for c in columns) + "\n")
    return {"file": "planes/plane_%s.csv" % stem, "axis": const, "value": float(station["value"]),
            "grid": [n1, n2], "spacing": spacing, "rows_expected": requested, "rows_written": len(data),
            "missing_in_body": in_body, "columns": columns, "formation": formation,
            "source": station["source"], "why": station.get("why"),
            "sha256_raw": sha256(raw), "sha256": sha256(out)}


# ---------------------------------------------------------------- images
def copy_png(pkg, capsule, rel):
    src = pkg / rel
    if not src.is_file():
        raise ExtractError("missing %s" % rel)
    dst = capsule / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)
    return sha256(dst)


def render_entry(render, kind, key):
    """The adapter's record of a plane or view render, kept whole under recipe."""
    if kind == "plane":
        return (render.get("planes") or {}).get(key) or {}
    for v in render.get("views") or []:
        if Path(v.get("file", "")).name == key:
            return v
    return {}


# ---------------------------------------------------------------- tools
def run_tool(args, merge=True):
    try:
        p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT if merge else subprocess.PIPE,
                           stdin=subprocess.DEVNULL, universal_newlines=True)
    except OSError as e:
        raise ExtractError("%s could not start: %s" % (Path(args[1]).name, e))
    if not merge and p.returncode != 0:
        return p.returncode, p.stdout + (p.stderr or "")
    return p.returncode, p.stdout


def tool(tools, name):
    path = tools / name
    if not path.is_file():
        raise ExtractError("%s not found in %s" % (name, tools))
    return path


def run_ledger(tools, capsule):
    rc, out = run_tool([sys.executable, str(tool(tools, "capsule_ledger.py")), str(capsule), "--json", "--per-file"],
                       merge=False)
    if rc != 0:
        raise ExtractError("capsule_ledger.py exit %d: %s" % (rc, " ".join(out.split())[-300:]))
    try:
        r = json.loads(out)[0]
    except (ValueError, IndexError, TypeError):
        raise ExtractError("capsule_ledger.py --json output does not parse")
    files = {}
    for a in r.get("artifacts", []):
        if a.get("crlf"):
            raise ExtractError("capsule_ledger.py reports CRLF in %s" % a.get("path"))
        files[a["path"]] = a["tokens"]
    if sum(files.values()) != r.get("tokens"):
        raise ExtractError("capsule_ledger.py total is not the sum of its artifacts")
    return {"tool": "capsule_ledger.py", "eol": "LF", "files": dict(sorted(files.items())), "total": r["tokens"]}


def write_manifest(capsule, manifest):
    (capsule / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                                           encoding="utf-8", newline="\n")


def settle_tokens(tools, capsule, manifest):
    for _ in range(LEDGER_ROUNDS):
        tokens = run_ledger(tools, capsule)
        if manifest.get("tokens") == tokens:
            return
        manifest["tokens"] = tokens
        write_manifest(capsule, manifest)
    raise ExtractError("manifest.tokens did not settle after %d ledger runs" % LEDGER_ROUNDS)


def write_zip(capsule):
    target = capsule.parent / (capsule.name + ".zip")
    files = sorted(p for p in capsule.rglob("*") if p.is_file())
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED) as z:
        for p in files:
            info = zipfile.ZipInfo((Path(capsule.name) / p.relative_to(capsule)).as_posix(), (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, p.read_bytes())
    return target


# ---------------------------------------------------------------- build
def build(pkg, out, tools, strict, commit):
    card = read_json(pkg / "card.json", "card.json")
    alias = validate_card(card)
    adapter = read_json(pkg / "adapter.json", "adapter.json")
    render = read_json(pkg / "render.json", "render.json")
    check_staleness(read_json(pkg / "staleness.json", "staleness.json"))
    check_audit(adapter)
    for name in ("setup.txt", "summary.json"):
        if not (pkg / name).is_file():
            raise ExtractError("missing %s" % name)

    capsule = out / ("capsule_%s" % alias)
    if capsule.exists():
        prev = out / "_prev" / ("%s_%s" % (capsule.name, utc_stamp()))
        prev.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(capsule), str(prev))
        (capsule.parent / (capsule.name + ".zip")).unlink(missing_ok=True)
    capsule.mkdir(parents=True)
    shutil.copyfile(pkg / "setup.txt", capsule / "setup.txt")
    shutil.copyfile(pkg / "summary.json", capsule / "summary.json")

    body = card["body"]
    warnings = []
    colormap = body["colormap"]
    if colormap not in COLORMAPS:
        warnings.append("colormap %r is not lightness-monotonic (SPEC 4.6); allowed: %s" % (colormap, ", ".join(COLORMAPS)))

    planes = []
    for st in card.get("stations", []):
        entry = reformat_plane(pkg, capsule, st, body["formation"]["fields"])
        png_rel = "planes/plane_%s.png" % st["stem"]
        entry["png"] = {"file": png_rel, "sha256": copy_png(pkg, capsule, png_rel),
                        "recipe": render_entry(render, "plane", st["stem"])}
        planes.append(entry)

    views = []
    for vw in card.get("views", []):
        rel = "views/%s" % vw["file"]
        rec = render_entry(render, "view", vw["file"])
        views.append({"file": rel, "question": vw["question"], "field": vw["field"], "parts": vw.get("parts"),
                      "colorbar": {"range": vw["range"], "colormap": colormap}, "camera": vw.get("camera"),
                      "formation": body["formation"]["fields"], "source": vw["source"], "why": vw.get("why"),
                      "sha256": copy_png(pkg, capsule, rel), "recipe": rec})

    manifest = {
        "capsule": capsule.name,
        "spec_version": SPEC_VERSION,
        "produced": utc_stamp(),
        "written_by": "extract_capsule.py",
        "card_source": card["card_source"],
        "card": {"file": "card.json", "sha256": sha256(pkg / "card.json"), "version": card.get("card_version")},
        "body": {k: body[k] for k in ("alias", "length", "velocity", "group", "regime", "formation", "colormap")},
        "adapter": adapter,
        "repo": {"url": "https://github.com/pacoEzq/simulation-capsule", "commit": commit},
        "planes": planes,
        "views": views,
        "checks": {"colormap_lightness_monotonic": colormap in COLORMAPS,
                   "stations": len(planes), "views": len(views)},
        "tokens": None,
    }
    write_manifest(capsule, manifest)

    rc, report = run_tool([sys.executable, str(tool(tools, "check_capsule.py"))] + (["--strict"] if strict else [])
                          + [str(capsule)])
    settle_tokens(tools, capsule, manifest)
    green = rc == 0 and not (strict and warnings)
    zipped = write_zip(capsule) if green else None
    return capsule, green, warnings, report, zipped, manifest["tokens"]["total"]


def propose_card(setup, summary):
    text = (HERE / "propose_card.md").read_text(encoding="utf-8")
    sys.stdout.write(text.rstrip() + "\n\n----- setup.txt -----\n")
    sys.stdout.write(Path(setup).read_text(encoding="utf-8").rstrip() + "\n")
    if summary:
        sys.stdout.write("\n----- summary.json -----\n" + Path(summary).read_text(encoding="utf-8").rstrip() + "\n")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Build a Simulation Capsule from a neutral package.")
    ap.add_argument("package", nargs="?", help="pkg_<alias>/ written by a solver adapter")
    ap.add_argument("--out", default="capsules", help="folder that receives capsule_<alias>/ (default: capsules)")
    ap.add_argument("--tools", default=None, help="tools/ of a simulation-capsule clone (default: this script's folder)")
    ap.add_argument("--strict", action="store_true", help="warnings fail the build, as for publication")
    ap.add_argument("--repo-commit", default=None, help="commit recorded in manifest.repo.commit")
    ap.add_argument("--propose-card", nargs="+", metavar="FILE",
                    help="print the card-proposal instructions followed by setup.txt [summary.json]")
    args = ap.parse_args(argv)
    if args.propose_card:
        propose_card(args.propose_card[0], args.propose_card[1] if len(args.propose_card) > 1 else None)
        return 0
    if not args.package:
        ap.print_usage(sys.stderr)
        return 2
    pkg = Path(args.package)
    if not pkg.is_dir():
        print("not a folder: %s" % pkg, file=sys.stderr)
        return 2
    tools = Path(args.tools) if args.tools else HERE
    try:
        capsule, green, warnings, report, zipped, total = build(pkg, Path(args.out), tools, args.strict,
                                                                args.repo_commit)
    except ExtractError as e:
        print("%s: stopped: %s" % (pkg.name, e))
        return 1
    for w in warnings:
        print("%s: warning: %s" % (capsule.name, w))
    if not green:
        print(report.rstrip())
    print("%s: %s, %d tokens%s" % (capsule.name, "green" if green else "built, not green", total,
                                   ", zip written" if zipped else ""))
    return 0 if green else 1


if __name__ == "__main__":
    sys.exit(main())
