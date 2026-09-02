#!/usr/bin/env python3
"""check_capsule.py: validate a Simulation Capsule against the SPEC.

A Simulation Capsule is a self-contained, token-budgeted distillation of a
simulation case, designed to be consumed by a language model. This script
turns the parts of the SPEC that can be checked mechanically into a program.

Standard library only. No Pillow, no third party packages: a validator that
needs an install is a validator nobody runs.

Usage:
    python check_capsule.py capsule_naca0012_aoa5
    python check_capsule.py examples/*/            # several at once
    python check_capsule.py capsule_x --strict     # warnings count as failures
    python check_capsule.py capsule_x --json       # machine readable report

Exit codes:
    0  every check passed (warnings allowed unless --strict)
    1  at least one check failed
    2  usage error, or a path that is not a directory

Reporting model: findings are grouped into checks. A capsule fails in as many
places as it has failing checks, not as many as it has findings. Three orphan
plane files are one broken pairing, not three.
"""

import argparse
import json
import os
import re
import sys

SPEC_VERSION = "0.1"
TOOL_VERSION = "1.0"

ERROR = "ERROR"
WARN = "WARN"
INFO = "INFO"

# Files allowed at the capsule root, mapped to the part that introduces them.
ROOT_FILES = {
    "setup.txt": "T1",
    "summary.json": "T2",
    "samples.csv": "T4",
    "features.json": "T5",
    "run_macro.java": "T7",
    "manifest.json": "T7",
    "disclosure.json": "T10",
}

# Subdirectories allowed, and the filename patterns allowed inside each.
# Only the file type is policed here, because this check answers one question:
# does this file belong in a capsule at all. Naming and pairing inside
# planes/ belong to the plane_pairs check, so a badly named plane fails in
# one place and not in two.
SUBDIRS = {
    "planes": [r"^.+\.(csv|png)$"],
    "views": [r"^[a-z0-9_]+\.png$"],
    "diff": [r"^[a-z0-9_]+\.png$", r"^diff\.json$"],
    "signals": [r"^(signal|spectra)_[A-Za-z0-9_]+\.csv$"],
    "modes": [r"^mode_[A-Za-z0-9_]+\.(csv|png)$", r"^modal_summary\.json$"],
    "snapshots": [r"^[a-z0-9_]+\.png$"],
}

TEXT_SUFFIXES = {".json", ".txt", ".csv", ".java", ".md"}

# Forbidden punctuation: em dash, en dash, minus sign. These break expression
# parsers downstream and are a series wide rule for published material.
FORBIDDEN_CHARS = {
    "\u2014": "em dash",
    "\u2013": "en dash",
    "\u2212": "minus sign",
}

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PNG_LONG_SIDE = 1024

# Keys that name a reference quantity, under any of the conventions seen in
# the published examples. The SPEC asks for the block to be present; T7 closes
# which spelling wins.
REFERENCE_ROOTS = ("length", "velocity", "density", "viscosity", "pressure",
                   "temperature", "time", "chord", "side", "diameter", "span",
                   "area", "frequency")

REFERENCE_CONTAINERS = ("reference", "references", "reference_quantities",
                        "nondimensionalization", "nondimensionalisation")

# A key that says it is a ratio, a group or a coefficient is nondimensional by
# name and is never a reference quantity, however it starts. Without this,
# velocity_ratio reads as a velocity scale and the validator invents a defect.
NONDIM_MARKERS = ("ratio", "number", "coefficient", "coeff", "fraction",
                  "exponent", "index", "nondim", "_over_", "count",
                  "reynolds", "mach", "strouhal", "normalized", "normalised")

UNIT_SUFFIX = re.compile(r"_(m|mm|cm|s|ms|kg|pa|k|c|deg|rad|hz|n|nm|j|w|"
                         r"m_s|m2|m3|kg_m3|pa_s)$", re.IGNORECASE)

UNIT_KEYS = ("unit", "units", "unit_flag", "dimensional", "flag")


class Check:
    """One named check. Holds its findings and resolves to a single status."""

    def __init__(self, check_id, title, spec_ref):
        self.id = check_id
        self.title = title
        self.spec_ref = spec_ref
        self.findings = []
        self.skipped = False
        self.skip_reason = ""

    def add(self, severity, message, path=""):
        self.findings.append({"severity": severity, "message": message,
                              "path": path})

    def error(self, message, path=""):
        self.add(ERROR, message, path)

    def warn(self, message, path=""):
        self.add(WARN, message, path)

    def info(self, message, path=""):
        self.add(INFO, message, path)

    def skip(self, reason):
        self.skipped = True
        self.skip_reason = reason

    def status(self, strict=False):
        if self.skipped:
            return "SKIP"
        severities = [f["severity"] for f in self.findings]
        if ERROR in severities:
            return "FAIL"
        if WARN in severities:
            return "FAIL" if strict else "WARN"
        return "PASS"


# ---------------------------------------------------------------------------
# Low level readers
# ---------------------------------------------------------------------------

def read_png_chunks(path):
    """Return (width, height, chunk_types) or raise ValueError."""
    with open(path, "rb") as handle:
        data = handle.read()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG file (signature missing)")
    offset = len(PNG_SIGNATURE)
    width = height = None
    chunk_types = []
    while offset + 8 <= len(data):
        length = int.from_bytes(data[offset:offset + 4], "big")
        ctype = data[offset + 4:offset + 8].decode("ascii", "replace")
        chunk_types.append(ctype)
        body = data[offset + 8:offset + 8 + length]
        if ctype == "IHDR" and len(body) >= 8:
            width = int.from_bytes(body[0:4], "big")
            height = int.from_bytes(body[4:8], "big")
        if ctype == "IEND":
            break
        offset += 12 + length
    if width is None:
        raise ValueError("IHDR chunk not found")
    return width, height, chunk_types


def read_text(path):
    """Return (text, had_bom) or raise ValueError on a decode failure."""
    with open(path, "rb") as handle:
        raw = handle.read()
    had_bom = raw.startswith(b"\xef\xbb\xbf")
    if had_bom:
        raw = raw[3:]
    try:
        return raw.decode("utf-8"), had_bom
    except UnicodeDecodeError as exc:
        raise ValueError("not valid UTF-8: %s" % exc)


def walk_capsule(root):
    """Return a list of paths relative to the capsule root, files only."""
    entries = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for name in sorted(filenames):
            full = os.path.join(dirpath, name)
            entries.append(os.path.relpath(full, root))
    return entries


def iter_leaves(node, trail=()):
    """Yield (trail, key, value) for every scalar leaf of a parsed JSON tree."""
    if isinstance(node, dict):
        for key, value in node.items():
            if isinstance(value, (dict, list)):
                yield from iter_leaves(value, trail + (key,))
            else:
                yield trail, key, value
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from iter_leaves(value, trail + (str(index),))


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_root(root, entries):
    check = Check("root", "Capsule root is a directory named capsule_<alias>",
                  "SPEC 1")
    name = os.path.basename(os.path.normpath(root))
    if not name.startswith("capsule_"):
        check.error("directory name does not start with 'capsule_'", name)
    elif name == "capsule_":
        check.error("alias is empty", name)
    if not entries:
        check.error("capsule is empty", name)
    return check


def check_declared_files(root, entries):
    check = Check("declared_files",
                  "Every file is an artifact the SPEC defines", "SPEC 2")
    for rel in entries:
        parts = rel.replace("\\", "/").split("/")
        if len(parts) == 1:
            if parts[0] not in ROOT_FILES:
                check.error("file not declared by the SPEC at capsule root",
                            rel)
            continue
        if len(parts) > 2:
            check.error("nesting deeper than one subdirectory", rel)
            continue
        folder, filename = parts
        patterns = SUBDIRS.get(folder)
        if patterns is None:
            check.error("subdirectory not declared by the SPEC", rel)
            continue
        if not any(re.match(p, filename) for p in patterns):
            check.error("filename does not match the pattern for %s/" % folder,
                        rel)
    return check


def check_required_files(root, entries):
    check = Check("required_files", "Mandatory artifacts are present",
                  "SPEC 2")
    names = {e.replace("\\", "/").split("/")[0] for e in entries}
    if "summary.json" not in names:
        check.error("summary.json is missing: the capsule has no anchor")
    if "setup.txt" not in names:
        check.warn("setup.txt is missing: the capsule cannot be audited "
                   "against what the solver was told")
    return check


def check_not_empty(root, entries):
    check = Check("not_empty", "No zero byte artifacts", "SPEC 2")
    for rel in entries:
        if os.path.getsize(os.path.join(root, rel)) == 0:
            check.error("file is empty", rel)
    return check


def check_json_parses(root, entries):
    check = Check("json_parses", "Every JSON artifact parses", "SPEC 3")
    targets = [e for e in entries if e.lower().endswith(".json")]
    if not targets:
        check.skip("no JSON artifacts")
        return check
    for rel in targets:
        path = os.path.join(root, rel)
        try:
            text, had_bom = read_text(path)
        except ValueError as exc:
            check.error(str(exc), rel)
            continue
        if had_bom:
            check.warn("byte order mark present: strict parsers reject it",
                       rel)
        try:
            json.loads(text)
        except json.JSONDecodeError as exc:
            check.error("invalid JSON at line %d column %d: %s"
                        % (exc.lineno, exc.colno, exc.msg), rel)
    return check


def check_plane_pairs(root, entries):
    check = Check("plane_pairs",
                  "Each plane ships as a CSV and PNG sharing one root",
                  "SPEC 4")
    members = [e.replace("\\", "/") for e in entries
               if e.replace("\\", "/").startswith("planes/")]
    if not members:
        check.skip("no planes/ directory")
        return check
    csv_roots, png_roots = {}, {}
    for rel in members:
        filename = rel.split("/", 1)[1]
        stem, _, ext = filename.rpartition(".")
        if ext.lower() == "csv":
            csv_roots[stem] = rel
        elif ext.lower() == "png":
            png_roots[stem] = rel
        else:
            check.error("planes/ holds a file that is neither CSV nor PNG",
                        rel)
            continue
        if not stem.startswith("plane_"):
            check.error("plane artifact is not named plane_<id>", rel)
    for stem, rel in sorted(csv_roots.items()):
        if stem not in png_roots:
            check.error("CSV has no PNG with the same root: the pair is "
                        "what ties the machine layer to the vision layer",
                        rel)
    for stem, rel in sorted(png_roots.items()):
        if stem not in csv_roots:
            check.error("PNG has no CSV with the same root", rel)
    return check


def check_png_geometry(root, entries):
    check = Check("png_geometry", "Every PNG is 1024 px on the long side",
                  "SPEC 5")
    targets = [e for e in entries if e.lower().endswith(".png")]
    if not targets:
        check.skip("no PNG artifacts")
        return check
    for rel in targets:
        try:
            width, height, _ = read_png_chunks(os.path.join(root, rel))
        except (ValueError, OSError) as exc:
            check.error(str(exc), rel)
            continue
        if max(width, height) != PNG_LONG_SIDE:
            check.error("long side is %d px, the contract says %d: images "
                        "of different size cannot be subtracted"
                        % (max(width, height), PNG_LONG_SIDE), rel)
    return check


def check_png_metadata(root, entries):
    check = Check("png_metadata", "No metadata rides along inside a PNG",
                  "SPEC 5, T10")
    targets = [e for e in entries if e.lower().endswith(".png")]
    if not targets:
        check.skip("no PNG artifacts")
        return check
    for rel in targets:
        try:
            _, _, chunk_types = read_png_chunks(os.path.join(root, rel))
        except (ValueError, OSError):
            continue
        if "eXIf" in chunk_types:
            check.error("EXIF chunk present: strip it before the capsule "
                        "leaves the building", rel)
        carriers = [c for c in chunk_types if c in ("tEXt", "iTXt", "zTXt")]
        if carriers:
            check.warn("text chunk %s present: it can carry a machine path "
                       "or a user name" % ", ".join(sorted(set(carriers))),
                       rel)
    return check


def check_csv_header(root, entries):
    check = Check("csv_header", "Every plane CSV opens with a header that "
                  "closes its world", "SPEC 4")
    targets = [e for e in entries
               if e.replace("\\", "/").startswith("planes/")
               and e.lower().endswith(".csv")]
    if not targets:
        check.skip("no plane CSV artifacts")
        return check
    for rel in targets:
        try:
            text, _ = read_text(os.path.join(root, rel))
        except (ValueError, OSError) as exc:
            check.error(str(exc), rel)
            continue
        first = text.splitlines()[0] if text.splitlines() else ""
        if not first.startswith("#"):
            check.error("first line is not a comment header", rel)
            continue
        if "nondimensional" not in first.lower():
            check.error("header does not declare the values nondimensional",
                        rel)
        segments = [s.strip() for s in first.lstrip("#").split("|")]
        missing = []
        if not any(re.search(r"grid\s", s) for s in segments):
            missing.append("grid")
        if not any("spacing" in s for s in segments):
            missing.append("spacing")
        if not any(re.search(r"\d+\s+of\s+\d+\s+nodes", s) for s in segments):
            missing.append("nodes returned of nodes requested")
        if not any("%" in s for s in segments):
            missing.append("field precision")
        if missing:
            check.warn("header omits %s" % ", ".join(missing), rel)
    return check


def is_nondimensional_name(key):
    """True when the key declares itself a group, a ratio or a coefficient."""
    lowered = key.lower()
    return any(marker in lowered for marker in NONDIM_MARKERS)


def find_reference_block(node, trail=()):
    """Return a list of (path, convention) for reference quantities found."""
    hits = []
    if isinstance(node, dict):
        for key, value in node.items():
            lowered = key.lower()
            here = trail + (key,)
            if lowered in REFERENCE_CONTAINERS and isinstance(value, dict):
                hits.append(("/".join(here), "named container"))
            elif is_nondimensional_name(lowered):
                pass
            elif lowered.startswith(REFERENCE_ROOTS):
                if isinstance(value, dict) and any(
                        k.lower() in UNIT_KEYS for k in value):
                    hits.append(("/".join(here), "value plus unit flag"))
                elif UNIT_SUFFIX.search(lowered):
                    hits.append(("/".join(here), "unit in the key name"))
                elif isinstance(value, (int, float)):
                    hits.append(("/".join(here), "bare number"))
            if isinstance(value, (dict, list)):
                hits.extend(find_reference_block(value, here))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            hits.extend(find_reference_block(value, trail + (str(index),)))
    return hits


def check_reference_block(root, entries):
    check = Check("reference_block", "summary.json declares its reference "
                  "quantities", "SPEC 3")
    if "summary.json" not in entries:
        check.skip("no summary.json")
        return check
    path = os.path.join(root, "summary.json")
    try:
        text, _ = read_text(path)
        data = json.loads(text)
    except (ValueError, OSError):
        check.skip("summary.json does not parse: see the json_parses check")
        return check
    hits = find_reference_block(data)
    if not hits:
        check.error("no reference quantities found: coefficients without an "
                    "anchor are numbers the model cannot check",
                    "summary.json")
        return check
    conventions = sorted({convention for _, convention in hits})
    leaves = sorted(p for p, c in hits if c != "named container")
    where = ", ".join(leaves[:4]) if leaves else \
        ", ".join(sorted(p for p, _ in hits))
    check.info("reference quantities declared by '%s' at %s"
               % ("', '".join(conventions), where), "summary.json")
    if len(conventions) > 1:
        check.info("this capsule mixes conventions; the SPEC leaves the "
                   "divergence visible until T7 closes the schema")
    return check


def check_dimensional_flags(root, entries):
    check = Check("dimensional_flags", "Dimensional values carry an explicit "
                  "flag", "SPEC 3")
    targets = [e for e in entries if e.lower().endswith(".json")]
    if not targets:
        check.skip("no JSON artifacts")
        return check
    unflagged = []
    for rel in targets:
        try:
            text, _ = read_text(os.path.join(root, rel))
            data = json.loads(text)
        except (ValueError, OSError):
            continue
        for trail, key, value in iter_leaves(data):
            if not isinstance(value, (int, float)):
                continue
            lowered = key.lower()
            if is_nondimensional_name(lowered):
                continue
            if UNIT_SUFFIX.search(lowered):
                continue  # the unit is spelled in the key: that is the flag
            if any(k.lower() in UNIT_KEYS for k in trail):
                continue
            if lowered.startswith(REFERENCE_ROOTS):
                unflagged.append((rel, "/".join(trail + (key,))))
    for rel, where in unflagged:
        check.warn("reference quantity carries no unit flag: %s" % where, rel)
    if unflagged:
        check.info("the SPEC allows this until T7 closes the schema; a bare "
                   "number is not wrong, it is unreadable")
    return check


def check_publication_residue(root, entries):
    check = Check("publication_residue", "No working state left in a "
                  "published capsule", "SPEC 3")
    targets = [e for e in entries if e.lower().endswith(".json")]
    if not targets:
        check.skip("no JSON artifacts")
        return check
    for rel in targets:
        try:
            text, _ = read_text(os.path.join(root, rel))
            data = json.loads(text)
        except (ValueError, OSError):
            continue
        for trail, key, _ in iter_leaves(data):
            lowered = key.lower()
            if lowered in ("status", "_pending") or lowered.startswith("_"):
                check.warn("working key survives publication: %s"
                           % "/".join(trail + (key,)), rel)
    return check


def check_punctuation(root, entries):
    check = Check("punctuation", "No em dash, en dash or minus sign",
                  "SPEC 6")
    targets = [e for e in entries
               if os.path.splitext(e)[1].lower() in TEXT_SUFFIXES]
    if not targets:
        check.skip("no text artifacts")
        return check
    for rel in targets:
        try:
            text, _ = read_text(os.path.join(root, rel))
        except (ValueError, OSError) as exc:
            check.error(str(exc), rel)
            continue
        verbatim = os.path.basename(rel) == "setup.txt"
        for char, label in FORBIDDEN_CHARS.items():
            count = text.count(char)
            if not count:
                continue
            message = "%d occurrence(s) of %s: expression parsers read it as "\
                      "punctuation, not as a sign" % (count, label)
            if verbatim:
                check.warn(message + " (verbatim solver export, so this is a "
                           "report, not an edit)", rel)
            else:
                check.error(message, rel)
    return check


def check_encoding(root, entries):
    check = Check("encoding", "Every text artifact is valid UTF-8", "SPEC 6")
    targets = [e for e in entries
               if os.path.splitext(e)[1].lower() in TEXT_SUFFIXES]
    if not targets:
        check.skip("no text artifacts")
        return check
    for rel in targets:
        try:
            read_text(os.path.join(root, rel))
        except ValueError as exc:
            check.error(str(exc), rel)
        except OSError as exc:
            check.error("cannot read: %s" % exc, rel)
    return check


CHECKS = [
    check_root,
    check_required_files,
    check_declared_files,
    check_not_empty,
    check_encoding,
    check_json_parses,
    check_plane_pairs,
    check_csv_header,
    check_png_geometry,
    check_png_metadata,
    check_reference_block,
    check_dimensional_flags,
    check_publication_residue,
    check_punctuation,
]


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def validate(root):
    entries = walk_capsule(root)
    return [check(root, entries) for check in CHECKS]


def report_text(root, checks, strict, verbose):
    lines = []
    name = os.path.basename(os.path.normpath(root))
    lines.append("capsule: %s" % name)
    failed = []
    for check in checks:
        status = check.status(strict)
        if status == "FAIL":
            failed.append(check.id)
        if status == "PASS" and not verbose:
            continue
        if status == "SKIP" and not verbose:
            continue
        lines.append("  [%-4s] %-21s %s" % (status, check.id, check.title))
        if status == "SKIP":
            lines.append("           %s" % check.skip_reason)
        for finding in check.findings:
            where = finding["path"] or name
            lines.append("           %-5s %s" % (finding["severity"], where))
            lines.append("                 %s" % finding["message"])
    total = len([c for c in checks if not c.skipped])
    if failed:
        lines.append("  RESULT: failed %d of %d checks in %d place(s): %s"
                     % (len(failed), total, len(failed), ", ".join(failed)))
    else:
        warned = [c.id for c in checks if c.status(strict) == "WARN"]
        suffix = " with warnings in %s" % ", ".join(warned) if warned else ""
        lines.append("  RESULT: passed %d of %d checks%s"
                     % (total - len(warned), total, suffix))
    return "\n".join(lines), failed


def report_json(root, checks, strict):
    return {
        "capsule": os.path.basename(os.path.normpath(root)),
        "spec_version": SPEC_VERSION,
        "tool_version": TOOL_VERSION,
        "strict": strict,
        "checks": [
            {
                "id": check.id,
                "title": check.title,
                "spec": check.spec_ref,
                "status": check.status(strict),
                "skip_reason": check.skip_reason or None,
                "findings": check.findings,
            }
            for check in checks
        ],
        "failed": [c.id for c in checks if c.status(strict) == "FAIL"],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Validate one or more Simulation Capsules against the "
                    "SPEC.")
    parser.add_argument("capsules", nargs="+",
                        help="capsule directories to validate")
    parser.add_argument("--strict", action="store_true",
                        help="treat warnings as failures")
    parser.add_argument("--json", action="store_true", dest="as_json",
                        help="emit a machine readable report")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="list passing and skipped checks too")
    args = parser.parse_args(argv)

    reports, any_failure = [], False
    for root in args.capsules:
        if not os.path.isdir(root):
            sys.stderr.write("not a directory: %s\n" % root)
            return 2
        checks = validate(root)
        if args.as_json:
            payload = report_json(root, checks, args.strict)
            reports.append(payload)
            if payload["failed"]:
                any_failure = True
        else:
            text, failed = report_text(root, checks, args.strict, args.verbose)
            reports.append(text)
            if failed:
                any_failure = True

    if args.as_json:
        print(json.dumps(reports if len(reports) > 1 else reports[0],
                         indent=2))
    else:
        print("\n\n".join(reports))
    return 1 if any_failure else 0


if __name__ == "__main__":
    sys.exit(main())
