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

Every check cites the SPEC section it enforces. A check with nothing to cite
is a check to remove.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

SPEC_VERSION = "0.4"
TOOL_VERSION = "1.5"

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
    "diff.json": "T6",
}

# Subdirectories allowed, and the filename patterns allowed inside each.
# Only the file type is policed here, because this check answers one question:
# does this file belong in a capsule at all. Naming and pairing inside
# planes/ belong to the plane_pairs check, so a badly named plane fails in
# one place and not in two.
SUBDIRS = {
    "planes": [r"^.+\.(csv|png)$"],
    "views": [r"^[a-z0-9_]+\.png$"],
    # diff/ holds the published difference images and nothing else. The
    # manifest that describes them is diff.json at the root, per SPEC 4.7,
    # so the pattern here is as strict as the one for views/.
    "diff": [r"^[a-z0-9_]+\.png$"],
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

# Keys that name a reference quantity. SPEC 3.1 asks that every one of them
# carries its unit as a suffix and lives in the reference block.
REFERENCE_ROOTS = ("length", "velocity", "density", "viscosity", "pressure",
                   "temperature", "time", "chord", "side", "diameter", "span",
                   "area", "frequency")

# The one container SPEC 3.1 names. The others were seen in capsules
# published before v0.2 and are reported as legacy locations.
REFERENCE_BLOCK = "reference"
LEGACY_CONTAINERS = ("references", "reference_quantities",
                     "nondimensionalization", "nondimensionalisation",
                     "scales")

# A key that says it is a ratio, a group or a coefficient is nondimensional by
# name and is never a reference quantity, however it starts. Without this,
# velocity_ratio reads as a velocity scale and the validator invents a defect.
NONDIM_MARKERS = ("ratio", "number", "coefficient", "coeff", "fraction",
                  "exponent", "index", "nondim", "_over_", "count",
                  "reynolds", "mach", "strouhal", "normalized", "normalised",
                  "rel_error", "relative", "imbalance", "residual", "percent")

# Unit suffix grammar, SPEC 3.1: SI symbol in lowercase, joined with
# underscores, _per_ for division, trailing digit for a power.
# length_scale_D_m, u_inf_m_per_s, rho_kg_per_m3, mu_pa_s.
# Matched on the key as written: width_W is a width named W, not watts.
_UNIT = r"(?:m|mm|s|kg|pa|k|n|j|w|hz)\d?"
UNIT_SUFFIX = re.compile(r"(?:_%s)+(?:_per(?:_%s)+)?$" % (_UNIT, _UNIT))

# Inside reference a unit suffix is not checked against the dimension of its
# root: reference is where dimensional keys are allowed, and velocity_m_s and
# velocity_m_per_s both pass. Removed in 1.5; 1.4.1 warned on the first.

# A unit written in its SI capitals. Only the symbols that cannot be a name:
# a single capital such as W or N is how a width or a count is named.
UPPERCASE_UNIT = re.compile(r"_(Pa|kPa|MPa|Hz|kHz)(?=_|\d|$)")

# An angle is already dimensionless. _deg and _rad declare the convention,
# not a dimension, and may appear anywhere in the capsule.
ANGLE_SUFFIX = re.compile(r"_(deg|rad)$")

# Keys that mark the pre v0.2 object form {value, unit_flag}.
LEGACY_UNIT_KEYS = ("unit", "units", "unit_flag", "dimensional", "flag")

# SPEC 4.2.1. The root block that says which build produced the numbers.
ENVIRONMENT_BLOCK = "environment"
ENVIRONMENT_KEYS = ("solver", "version", "build", "platform", "precision")
PRECISIONS = ("single", "double", "mixed")

# SPEC 4.2.2 and 4.2.3. A run judged by window statistics publishes each
# scalar with its window. The value, then four companions.
CONVERGENCE_STATUSES = ("converged", "stationary", "not_stationary",
                        "no_steady_state")
STATISTICS = ("iteration_mean", "window_min", "window_max",
              "window_min_max", "instantaneous")
EXTREME_STATISTICS = ("window_min", "window_max", "window_min_max")
QUINTET_SUFFIXES = ("_sd", "_window_iterations", "_n_windows", "_statistic")
WINDOWED_BLOCKS = ("forces", "pressure", "wake", "mass")
UNCERTAINTY_SOURCES = ("measured", "declared")

# Extensions SPEC 3.4 defines. An extension is additive: what it adds is
# checked only when summary.json declares it.
KNOWN_EXTENSIONS = {"diff": "SPEC 4.7"}

# SPEC 4.7. The grayscale renders the measurement runs on are the
# instrument, not the result, and stay outside the capsule.
INSTRUMENT_DIR = "diffsrc"

# Top level keys diff.json carries. The schema below them is case driven in
# the same way summary.json is, so only the frame is fixed here.
DIFF_REQUIRED_KEYS = ("schema_version", "base", "variant", "difference",
                      "view_contract", "pipeline", "pairs", "scalar_deltas",
                      "known_differences")

# The base is declared by alias, repository, commit and path, and carries a
# pointer to the as-published copy it was measured against.
DIFF_BASE_KEYS = ("repo", "commit", "path", "frozen_copy_path")

# A commit is named by its SHA. SPEC 0.4 asks for all 40 characters: a short
# form unique today stops being unique as the history grows. Seven to 39
# still reads as a SHA and warns; anything else is not a commit.
COMMIT_SHA = re.compile(r"^[0-9a-f]{7,40}$")
FULL_SHA_LENGTH = 40

# SPEC 4.7: the chain is view, grayscale frame, diff, and every link carries
# its sha256 in the pair that uses it.
SOURCE_HASH_KEYS = ("base_view", "variant_view")
INSTRUMENT_HASH_KEYS = ("base_frame", "variant_frame")
SHA256 = re.compile(r"^[0-9a-f]{64}$")

# SPEC 4.7: a pixel distance is a field distance only when the render is
# quantized to the levels the PNG carries.
COLORBAR_LEVELS = 256
MEASURED_COLORMAP = "grayscale"

# The comparison sign is not cosmetic: '>' cuts at the next level up and
# roughly halves the reported fraction.
THRESHOLD_COMPARISONS = (">=", ">")

# Below this a base value is practically zero and a relative delta computed
# against it reports the divisor, not the difference.
PRACTICALLY_ZERO = 1e-9

# How a key announces that it holds a relative quantity, and how the entry it
# is measured against announces itself.
RELATIVE_MARKERS = ("relative", "_rel", "percent", "pct", "_frac")
BASE_VALUE_KEYS = ("base", "base_value", "value_base", "reference")


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


def iter_nodes(node, trail=()):
    """Yield (trail, key, value) for every key of a parsed JSON tree,
    including the ones whose value is a dict or a list."""
    if isinstance(node, dict):
        for key, value in node.items():
            yield trail, key, value
            yield from iter_nodes(value, trail + (key,))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from iter_nodes(value, trail + (str(index),))


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_root(root, entries):
    check = Check("root", "Capsule root is a directory named capsule_<alias>",
                  "SPEC 2")
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
        if INSTRUMENT_DIR in parts:
            continue  # owned by no_instrument_dir, reported in one place
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
    check = Check("required_files",
                  "The minimum capsule, setup.txt and summary.json, is present",
                  "SPEC 2")
    names = {e.replace("\\", "/").split("/")[0] for e in entries}
    if "summary.json" not in names:
        check.error("summary.json is missing: the capsule has no anchor")
    if "setup.txt" not in names:
        check.error("setup.txt is missing: the capsule cannot be audited "
                    "against what the solver was told")
    return check


def check_not_empty(root, entries):
    check = Check("not_empty", "No zero byte artifacts", "SPEC 2")
    for rel in entries:
        if os.path.getsize(os.path.join(root, rel)) == 0:
            check.error("file is empty", rel)
    return check


def check_json_parses(root, entries):
    check = Check("json_parses", "Every JSON artifact parses",
                  "SPEC 4.2, 4.5")
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
                  "SPEC 4.3")
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
                  "SPEC 3.3")
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
                  "SPEC 3.3")
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
            check.error("text chunk %s present: it can carry a machine path "
                        "or a user name, and costs nothing to strip"
                        % ", ".join(sorted(set(carriers))), rel)
    return check


def check_csv_header(root, entries):
    check = Check("csv_header", "Every plane CSV opens with a header that "
                  "closes its world", "SPEC 4.3")
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


def is_reference_root(key):
    lowered = key.lower()
    return (lowered.startswith(REFERENCE_ROOTS)
            and not is_nondimensional_name(lowered))


def is_legacy_object(value):
    """True for the pre v0.2 form {value, unit_flag}: a value key next to a
    unit key. A block that merely lists its units is not one."""
    if not isinstance(value, dict):
        return False
    keys = {k.lower() for k in value}
    return "value" in keys and bool(keys & set(LEGACY_UNIT_KEYS))


def find_reference_block(node, trail=()):
    """Return a list of (path, convention) for reference quantities found."""
    hits = []
    if isinstance(node, dict):
        for key, value in node.items():
            lowered = key.lower()
            here = trail + (key,)
            if lowered == REFERENCE_BLOCK and isinstance(value, dict):
                hits.append(("/".join(here), "reference block"))
            elif lowered in LEGACY_CONTAINERS and isinstance(value, dict):
                hits.append(("/".join(here), "legacy container"))
            elif is_reference_root(lowered):
                if is_legacy_object(value):
                    hits.append(("/".join(here), "value plus unit flag"))
                elif UNIT_SUFFIX.search(key):
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
                  "quantities", "SPEC 3.1")
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
    containers = ("reference block", "legacy container")
    conventions = sorted({convention for _, convention in hits})
    leaves = sorted(p for p, c in hits if c not in containers)
    where = ", ".join(leaves[:4]) if leaves else \
        ", ".join(sorted(p for p, _ in hits))
    check.info("reference quantities declared by '%s' at %s"
               % ("', '".join(conventions), where), "summary.json")
    leaf_conventions = sorted({c for _, c in hits if c not in containers})
    if len(leaf_conventions) > 1:
        check.info("this capsule mixes %d conventions in one file; the "
                   "dimensional_flags check reports which are legacy"
                   % len(leaf_conventions))
    return check


def check_dimensional_flags(root, entries):
    check = Check("dimensional_flags", "Dimensional values carry the unit as "
                  "a key suffix and live in the reference block", "SPEC 3.1")
    targets = [e for e in entries if e.lower().endswith(".json")]
    if not targets:
        check.skip("no JSON artifacts")
        return check
    legacy = 0
    for rel in targets:
        try:
            text, _ = read_text(os.path.join(root, rel))
            data = json.loads(text)
        except (ValueError, OSError):
            continue
        for trail, key, value in iter_nodes(data):
            lowered = key.lower()
            where = "/".join(trail + (key,))
            in_reference = bool(trail) and trail[0].lower() == REFERENCE_BLOCK
            in_legacy = any(t.lower() in LEGACY_CONTAINERS for t in trail)
            has_suffix = bool(UNIT_SUFFIX.search(key))
            if ANGLE_SUFFIX.search(lowered):
                continue  # an angle is a group; _deg names the convention

            # Rule 1: a unit suffix outside reference. New in SPEC 0.2, so
            # legal in a capsule published under 0.1: a warning here, an
            # error under --strict. Only numeric leaves count: _n and _k
            # are also how a counter or an index ends.
            numeric = isinstance(value, (int, float)) \
                and not isinstance(value, bool)
            if has_suffix and numeric and not in_reference \
                    and not is_nondimensional_name(lowered):
                legacy += 1
                check.warn("dimensional key outside the reference block, "
                           "the SPEC names only 'reference': %s" % where, rel)
                continue

            # Rule 2: the object form {value, unit_flag} is legacy.
            if is_legacy_object(value):
                legacy += 1
                check.warn("legacy object form, migrate to a unit suffix on "
                           "the key: %s" % where, rel)
                continue

            # Rule 3: a known reference quantity as a bare number is legacy.
            if not isinstance(value, (int, float)) or isinstance(value, bool):
                continue

            # Rule 4: units in lowercase, SPEC 3.1. Added in 1.4.1 together
            # with a check of the suffix against the dimension of its root,
            # which 1.5 drops inside reference.
            if in_reference and UPPERCASE_UNIT.search(key):
                legacy += 1
                check.warn("unit symbol in capitals, SPEC 3.1 writes units "
                           "in lowercase: %s" % where, rel)
                continue
            if any(t.lower() in LEGACY_UNIT_KEYS for t in trail):
                continue  # the leaf inside a legacy object, reported above
            if (in_reference or in_legacy) and is_reference_root(lowered) \
            and not has_suffix:
                legacy += 1
                check.warn("reference quantity carries no unit suffix: %s"
                           % where, rel)
    if legacy:
        check.info("%d legacy form(s) accepted until Part 7 migrates the "
                   "published capsules; run with --strict to see the "
                   "post migration verdict" % legacy)
    return check


def load_json(root, rel):
    """Return the parsed artifact, or None when it is absent or broken.
    A parse failure is the json_parses check's finding, not this one's."""
    path = os.path.join(root, rel)
    try:
        text, _ = read_text(path)
        return json.loads(text)
    except (ValueError, OSError):
        return None


def declared_extensions(root, entries):
    """Return the extensions summary.json declares, SPEC 3.4. The
    declaration lives in that file and nowhere else: a validator reads it
    first, and discovery cannot depend on the file being discovered."""
    if "summary.json" not in entries:
        return []
    data = load_json(root, "summary.json")
    if not isinstance(data, dict):
        return []
    declared = data.get("extensions")
    if not isinstance(declared, list):
        return []
    return declared


def diff_artifacts(entries):
    """Return (has_manifest, diff_members) for the diff extension."""
    normalised = [e.replace("\\", "/") for e in entries]
    return ("diff.json" in normalised,
            [e for e in normalised if e.startswith("diff/")])


def check_extensions(root, entries):
    check = Check("extensions", "Extensions are declared in summary.json and "
                  "match what the capsule carries", "SPEC 3.4")
    declared = declared_extensions(root, entries)
    has_manifest, members = diff_artifacts(entries)

    data = load_json(root, "summary.json") if "summary.json" in entries \
        else None
    raw = data.get("extensions") if isinstance(data, dict) else None
    if raw is not None and not isinstance(raw, list):
        check.error("'extensions' is not an array of names", "summary.json")
        raw = None
    for name in declared:
        if not isinstance(name, str):
            check.error("extension name is not a string: %r" % (name,),
                        "summary.json")
        elif name not in KNOWN_EXTENSIONS:
            check.error("unknown extension '%s': the SPEC defines %s"
                        % (name, ", ".join(sorted(KNOWN_EXTENSIONS))),
                        "summary.json")

    names = [n for n in declared if isinstance(n, str)]
    if len(set(names)) != len(names):
        check.error("an extension is declared more than once", "summary.json")

    if "diff" in names:
        if not has_manifest:
            check.error("extension 'diff' is declared and diff.json is "
                        "missing: the declaration promises the manifest",
                        "summary.json")
        if not members:
            check.error("extension 'diff' is declared and diff/ is missing: "
                        "the manifest describes images nobody receives",
                        "summary.json")
    else:
        if has_manifest:
            check.error("diff.json is present and 'diff' is not declared in "
                        "summary.json: an undeclared artifact is invisible "
                        "to a reader who starts at the anchor", "diff.json")
        if members:
            check.error("diff/ is present and 'diff' is not declared in "
                        "summary.json", "diff")

    if raw is None and not has_manifest and not members:
        check.skip("no extensions declared and none carried")
    return check


def check_no_instrument_dir(root, entries):
    check = Check("no_instrument_dir", "The instrument does not travel: no "
                  "diffsrc/ inside a capsule", "SPEC 4.7")
    # Never skips. The grayscale renders are the measurement rig, and SPEC
    # 4.7 refuses them whether or not the extension is declared: a capsule
    # that declares nothing is exactly where they would go unnoticed.
    seen = set()
    for rel in entries:
        parts = rel.replace("\\", "/").split("/")
        if INSTRUMENT_DIR not in parts:
            continue
        cut = parts.index(INSTRUMENT_DIR)
        seen.add("/".join(parts[:cut + 1]))
        check.error("the measurement renders live outside the capsule; "
                    "diff.json declares the recipe to regenerate them", rel)
    # An emptied instrument directory is still one. walk_capsule lists files,
    # so the directory itself is looked for here.
    for dirpath, dirnames, _ in os.walk(root):
        for name in sorted(dirnames):
            if name != INSTRUMENT_DIR:
                continue
            where = os.path.relpath(os.path.join(dirpath, name),
                                    root).replace("\\", "/")
            if where not in seen:
                check.error("instrument directory present, empty or not: it "
                            "is working state and never ships", where)
    return check


def practically_zero(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) \
        and abs(value) < PRACTICALLY_ZERO


def is_relative_key(key):
    lowered = key.lower()
    return any(marker in lowered for marker in RELATIVE_MARKERS)


def relative_against_zero(node, trail=()):
    """Return the paths of relative deltas whose base value is practically
    zero, SPEC 4.7: absolute always, relative only where the base is not
    practically zero. Two shapes are read, the entry object that carries its
    own base and the flat key that names a sibling."""
    hits = []
    if isinstance(node, dict):
        bases = {k.lower(): v for k, v in node.items()
                 if k.lower() in BASE_VALUE_KEYS or k.lower().endswith("_base")
                 or k.lower().startswith("base_")}
        zero_base = any(practically_zero(v) for v in bases.values())
        for key, value in node.items():
            here = trail + (key,)
            if isinstance(value, (dict, list)):
                hits.extend(relative_against_zero(value, here))
                continue
            if zero_base and is_relative_key(key) and value is not None:
                hits.append("/".join(here))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            hits.extend(relative_against_zero(value, trail + (str(index),)))
    return hits


def resolve_commit(root, repo, sha):
    """Return 'known', 'unknown', 'shallow' or 'unavailable' for a base
    commit.

    Unavailable is the common case and never an error: the base lives in
    another repository, or this copy was unpacked from an archive with no
    git objects at all. Shallow is a commit missing from a clone that was
    cut at a fixed depth: absence proves nothing there, so it is a warning
    with its reason and not the error of 'unknown'.
    """
    try:
        remotes = subprocess.run(
            ["git", "-C", root, "remote", "-v"],
            capture_output=True, text=True, timeout=10)
        if remotes.returncode != 0:
            return "unavailable"
        here = remotes.stdout.lower()
        stem = str(repo).lower().rstrip("/").removesuffix(".git")
        stem = stem.split("//")[-1]
        if not stem or stem not in here:
            return "unavailable"  # this is not the repository base names
        found = subprocess.run(
            ["git", "-C", root, "cat-file", "-e", "%s^{commit}" % sha],
            capture_output=True, text=True, timeout=10)
        if found.returncode == 0:
            return "known"
        shallow = subprocess.run(
            ["git", "-C", root, "rev-parse", "--is-shallow-repository"],
            capture_output=True, text=True, timeout=10)
        if shallow.returncode == 0 and shallow.stdout.strip() == "true":
            return "shallow"
        return "unknown"
    except (OSError, subprocess.SubprocessError):
        return "unavailable"


def check_diff_manifest(root, entries):
    check = Check("diff_manifest", "diff.json describes a measurable "
                  "relation between two capsules", "SPEC 4.7")
    if "diff" not in declared_extensions(root, entries):
        check.skip("extension 'diff' not declared")
        return check
    if "diff.json" not in [e.replace("\\", "/") for e in entries]:
        check.skip("no diff.json: see the extensions check")
        return check
    data = load_json(root, "diff.json")
    if not isinstance(data, dict):
        check.skip("diff.json does not parse: see the json_parses check")
        return check
    rel = "diff.json"

    for key in DIFF_REQUIRED_KEYS:
        if key not in data:
            check.error("required key missing: %s" % key, rel)

    # The base is never edited, so it has to be nameable: alias, repository,
    # commit and path, plus the as-published copy it was measured against.
    base = data.get("base")
    if not isinstance(base, dict):
        if "base" in data:
            check.error("'base' is not an object", rel)
    else:
        if not (base.get("alias") or base.get("capsule")):
            check.error("base names neither 'alias' nor 'capsule'", rel)
        for key in DIFF_BASE_KEYS:
            if not base.get(key):
                check.error("base/%s is missing: the base has to be "
                            "retrievable, not remembered" % key, rel)
        sha = base.get("commit")
        if isinstance(sha, str) and sha:
            if not COMMIT_SHA.match(sha.strip().lower()):
                check.error("base/commit does not look like a commit SHA, "
                            "7 to 40 hex characters: %s" % sha, rel)
            else:
                if len(sha.strip()) < FULL_SHA_LENGTH:
                    check.warn("base/commit %s is abbreviated: SPEC 4.7 asks "
                               "for the full %d-character SHA"
                               % (sha, FULL_SHA_LENGTH), rel)
                state = resolve_commit(root, base.get("repo", ""), sha.strip())
                if state == "unknown":
                    check.error("base/commit %s is not in this repository, "
                                "which base/repo names" % sha, rel)
                elif state == "shallow":
                    check.warn("base/commit %s is not in this clone, which is "
                               "shallow: the commit may lie beyond the "
                               "fetched depth; fetch the full history to "
                               "verify it" % sha, rel)
                elif state == "unavailable":
                    check.warn("base/commit %s could not be resolved here: "
                               "the base repository is not available" % sha,
                               rel)

    # The shared view contract. Without it the difference measures the
    # renderer and not the flow.
    contract = data.get("view_contract")
    if not isinstance(contract, dict):
        if "view_contract" in data:
            check.error("'view_contract' is not an object", rel)
        contract = {}
    levels = contract.get("colorbar_levels")
    if levels is None:
        check.error("view_contract/colorbar_levels is missing: a pixel "
                    "distance is a field distance only when the quantization "
                    "is declared", rel)
    elif levels != COLORBAR_LEVELS:
        check.error("view_contract/colorbar_levels is %r, the PNG carries %d"
                    % (levels, COLORBAR_LEVELS), rel)
    measured = contract.get("colormap_measured")
    if measured != MEASURED_COLORMAP:
        check.warn("view_contract/colormap_measured is %r: the comparison "
                   "runs on a %s render, so a pixel distance is a field "
                   "distance" % (measured, MEASURED_COLORMAP), rel)

    pipeline = data.get("pipeline")
    if not isinstance(pipeline, dict):
        if "pipeline" in data:
            check.error("'pipeline' is not an object", rel)
        pipeline = {}
    evaluated = pipeline.get("pixels_evaluated")
    if evaluated is None:
        check.error("pipeline/pixels_evaluated is missing: a changed "
                    "fraction without its denominator cannot be checked", rel)
    elif not isinstance(evaluated, (int, float)) or isinstance(evaluated,
                                                               bool):
        check.error("pipeline/pixels_evaluated is not a number: %r"
                    % (evaluated,), rel)

    published = {e.replace("\\", "/").split("/", 1)[1]
                 for e in entries if e.replace("\\", "/").startswith("diff/")}
    pairs = data.get("pairs")
    if pairs is not None and not isinstance(pairs, list):
        check.error("'pairs' is not an array", rel)
        pairs = []
    for index, pair in enumerate(pairs or []):
        where = "pairs/%d" % index
        if not isinstance(pair, dict):
            check.error("%s is not an object" % where, rel)
            continue
        threshold = pair.get("threshold_physical")
        if not isinstance(threshold, (int, float)) \
                or isinstance(threshold, bool):
            check.error("%s/threshold_physical is not a number: a threshold "
                        "in RGB describes the instrument, not the flow: %r"
                        % (where, threshold), rel)
        comparison = pair.get("threshold_comparison")
        if comparison not in THRESHOLD_COMPARISONS:
            check.error("%s/threshold_comparison is %r, the SPEC allows %s: "
                        "the sign is not cosmetic, '>' cuts at the next "
                        "level up" % (where, comparison,
                                      " or ".join(THRESHOLD_COMPARISONS)),
                        rel)
        output = pair.get("output")
        if not isinstance(output, str) or not output:
            check.error("%s/output does not name a file" % where, rel)
        else:
            name = output.replace("\\", "/")
            name = name.split("/", 1)[1] if name.startswith("diff/") else name
            if "/" in name:
                check.error("%s/output points outside diff/: %s"
                            % (where, output), rel)
            elif name not in published:
                check.error("%s/output is not in diff/: %s" % (where, output),
                            rel)
        for side in ("base_view", "variant_view"):
            value = pair.get(side)
            if not isinstance(value, str) or not value:
                check.error("%s/%s does not name a view" % (where, side), rel)
            elif "/" in value.replace("\\", "/"):
                check.error("%s/%s is a path, the contract says both capsules "
                            "use the same filename in their views/: %s"
                            % (where, side, value), rel)
            elif not re.match(r"^[a-z0-9_]+\.png$", value):
                check.error("%s/%s is not a snake_case PNG filename: %s"
                            % (where, side, value), rel)

    deltas = data.get("scalar_deltas")
    if deltas is not None and not isinstance(deltas, (dict, list)):
        check.error("'scalar_deltas' is not an object", rel)
    else:
        for path in relative_against_zero(deltas or {}):
            check.warn("relative delta against a base that is practically "
                       "zero: scalar_deltas/%s reports the divisor, not the "
                       "difference" % path, rel)

    known = data.get("known_differences")
    if known is not None and not isinstance(known, list):
        check.error("'known_differences' is not an array", rel)
    return check


def check_environment(root, entries):
    check = Check("environment", "summary.json declares the build the numbers "
                  "came from", "SPEC 4.2.1")
    if "summary.json" not in entries:
        check.skip("no summary.json")
        return check
    data = load_json(root, "summary.json")
    if not isinstance(data, dict):
        check.skip("summary.json does not parse: see the json_parses check")
        return check
    rel = "summary.json"
    # New in SPEC 0.4, so every finding is a warning: legal in a capsule
    # written under 0.3, a failure under --strict.
    if "numerics" in data:
        check.warn("root block 'numerics' is absorbed by 'environment' in "
                   "SPEC 0.4: precision is a property of the build", rel)
    env = data.get(ENVIRONMENT_BLOCK)
    if env is None:
        check.warn("no 'environment' block: two builds of the same case are "
                   "two measurements, and nothing says which one this is", rel)
        return check
    if not isinstance(env, dict):
        check.warn("'environment' is not an object", rel)
        return check
    for key in ENVIRONMENT_KEYS:
        if key not in env:
            check.warn("environment/%s is missing" % key, rel)
    precision = env.get("precision")
    if "precision" in env and precision not in PRECISIONS:
        check.warn("environment/precision is %r, the SPEC allows %s: read it "
                   "from BuildEnv in setup.txt, -r8 is double"
                   % (precision, ", ".join(PRECISIONS)), rel)
    if "renders" not in env:
        return check
    renders = env["renders"]
    if not isinstance(renders, dict):
        check.warn("environment/renders is not an object", rel)
        return check
    if "precision" in renders and renders["precision"] not in PRECISIONS:
        check.warn("environment/renders/precision is %r, the SPEC allows %s"
                   % (renders["precision"], ", ".join(PRECISIONS)), rel)
    scope = renders.get("scope")
    if not isinstance(scope, list) or not scope:
        check.warn("environment/renders/scope does not list the directories "
                   "the render environment produced", rel)
        return check
    for name in scope:
        folder = name.strip("/") if isinstance(name, str) else ""
        if not folder or "/" in folder or ".." in folder:
            check.warn("environment/renders/scope names %r, not a capsule "
                       "directory" % (name,), rel)
        elif not os.path.isdir(os.path.join(root, folder)):
            check.warn("environment/renders/scope names %s, which is not in "
                       "the capsule" % name, rel)
    return check


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def find_base_capsule(root, path):
    """Return the directory base/path names, looked up from the capsule and
    each of its parents, or None. base/path is relative to the repository
    root, and a capsule checked out with its repository sits somewhere
    below that root."""
    if not isinstance(path, str) or not path or os.path.isabs(path):
        return None
    here = os.path.abspath(root)
    while True:
        candidate = os.path.join(here, path)
        if os.path.isdir(candidate):
            return candidate
        parent = os.path.dirname(here)
        if parent == here:
            return None
        here = parent


def check_diff_view_hashes(root, entries):
    check = Check("diff_view_hashes", "The views diff.json measured are the "
                  "views both capsules carry", "SPEC 4.7")
    if "diff" not in declared_extensions(root, entries):
        check.skip("extension 'diff' not declared")
        return check
    if "diff.json" not in [e.replace("\\", "/") for e in entries]:
        check.skip("no diff.json: see the extensions check")
        return check
    data = load_json(root, "diff.json")
    if not isinstance(data, dict):
        check.skip("diff.json does not parse: see the json_parses check")
        return check
    rel = "diff.json"
    pairs = data.get("pairs")
    if not isinstance(pairs, list) or not pairs:
        check.skip("no pairs: see the diff_manifest check")
        return check

    base = data.get("base") if isinstance(data.get("base"), dict) else {}
    base_dir = find_base_capsule(root, base.get("path"))
    if base_dir is None:
        # The capsule travels without its base: the same case as a base
        # commit that cannot be resolved, a warning and never an error.
        check.warn("base/path %r is not reachable from here: the base views "
                   "were not hashed" % (base.get("path"),), rel)
    sides = {"base_view": base_dir, "variant_view": os.path.abspath(root)}

    for index, pair in enumerate(pairs):
        where = "pairs/%d" % index
        if not isinstance(pair, dict):
            continue  # reported by diff_manifest
        declared = {}
        for block, keys in (("source_sha256", SOURCE_HASH_KEYS),
                            ("instrument_sha256", INSTRUMENT_HASH_KEYS)):
            hashes = pair.get(block)
            if not isinstance(hashes, dict):
                check.warn("%s/%s is missing: SPEC 4.7 hashes both views and "
                           "both grayscale frames" % (where, block), rel)
                continue
            for key in keys:
                value = hashes.get(key)
                if not isinstance(value, str) \
                        or not SHA256.match(value.lower()):
                    check.warn("%s/%s/%s is not a sha256: %r"
                               % (where, block, key, value), rel)
                elif block == "source_sha256":
                    declared[key] = value.lower()
        for side, capsule_dir in sides.items():
            name = pair.get(side)
            if side not in declared or capsule_dir is None \
                    or not isinstance(name, str) or "/" in name:
                continue
            view = os.path.join(capsule_dir, "views", name)
            owner = "base" if side == "base_view" else "variant"
            if not os.path.isfile(view):
                check.error("%s/%s: %s is not in the views/ of the %s"
                            % (where, side, name, owner), rel)
            elif sha256_of(view) != declared[side]:
                check.error("%s/%s: views/%s of the %s does not match its "
                            "recorded sha256: the diff measured another image"
                            % (where, side, name, owner), rel)
    return check


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def is_integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def check_window_entry(check, rel, where, entry, prefix, windowed):
    """One published scalar and its companions. With prefix the keys are
    <name>_sd and so on inside a block; without, sd and so on inside the
    scalar's own object, which is how probes carry them."""
    def key(suffix):
        return prefix + suffix if prefix else suffix.lstrip("_")
    statistic = entry.get(key("_statistic"))
    if key("_statistic") in entry and statistic not in STATISTICS:
        check.warn("%s: statistic %r is not one of %s"
                   % (where, statistic, ", ".join(STATISTICS)), rel)
    if statistic in EXTREME_STATISTICS and key("_sd") in entry:
        check.warn("%s: an extreme carries no sd, a minimum over a span has "
                   "no spread of its own" % where, rel)
    if not windowed:
        return
    missing = [key(s) for s in QUINTET_SUFFIXES[1:] if key(s) not in entry]
    if statistic == "iteration_mean" and key("_sd") not in entry:
        missing.insert(0, key("_sd"))
    if missing:
        check.warn("%s: a windowed value without %s cannot be checked"
                   % (where, ", ".join(missing)), rel)


def check_summary_blocks(root, entries):
    check = Check("summary_blocks", "Windowed scalars carry their window, "
                  "mass and mesh their 0.4 form", "SPEC 4.2.2, 4.2.3")
    if "summary.json" not in entries:
        check.skip("no summary.json")
        return check
    data = load_json(root, "summary.json")
    if not isinstance(data, dict):
        check.skip("summary.json does not parse: see the json_parses check")
        return check
    rel = "summary.json"
    # New in SPEC 0.4: warnings, failures under --strict.

    convergence = data.get("convergence")
    convergence = convergence if isinstance(convergence, dict) else {}
    status = convergence.get("status")
    if "status" in convergence and status not in CONVERGENCE_STATUSES:
        check.warn("convergence/status is %r, the SPEC allows %s"
                   % (status, ", ".join(CONVERGENCE_STATUSES)), rel)
    # A block without status predates 4.2.3 and is read as it was written.
    windowed = "status" in convergence and status != "converged"
    if "uncertainty" in data:
        check.warn("root block 'uncertainty' belongs inside convergence, "
                   "SPEC 4.2.3", rel)

    for block in WINDOWED_BLOCKS:
        values = data.get(block)
        if not isinstance(values, dict):
            continue
        for name, value in values.items():
            if not is_number(value) or any(
                    name.endswith(s) and name[:-len(s)] in values
                    for s in QUINTET_SUFFIXES):
                continue
            check_window_entry(check, rel, "%s/%s" % (block, name), values,
                               name, windowed)

    probes = data.get("probes")
    if isinstance(probes, dict):
        for name, probe in probes.items():
            if not isinstance(probe, dict):
                continue
            where = "probes/%s" % name
            if windowed and "value" not in probe:
                check.warn("%s: no value" % where, rel)
            check_window_entry(check, rel, where, probe, "", windowed)

    extremes = data.get("plane_extremes")
    if isinstance(extremes, dict):
        statistic = extremes.get("statistic")
        if "statistic" in extremes and statistic not in STATISTICS:
            check.warn("plane_extremes: statistic %r is not one of %s"
                       % (statistic, ", ".join(STATISTICS)), rel)
        missing = [k for k in ("statistic", "window_iterations", "n_windows")
                   if k not in extremes]
        if windowed and missing:
            check.warn("plane_extremes: the block carries %s once for all "
                       "planes" % ", ".join(missing), rel)

    mass = data.get("mass")
    if isinstance(mass, dict):
        for name in mass:
            lowered = name.lower()
            if lowered.startswith("mdot") and "_over_" not in lowered:
                check.warn("mass/%s: a dimensional mass flow, SPEC 4.2.2 "
                           "writes mdot over rho U D^2" % name, rel)

    mesh = data.get("mesh")
    if isinstance(mesh, dict):
        if "cells" in mesh and not is_integer(mesh["cells"]):
            check.warn("mesh/cells is %r, not an integer" % (mesh["cells"],),
                       rel)
        if "designed_for_Re" in mesh:
            check.warn("mesh/designed_for_Re is replaced by "
                       "designed_for_Re_range, [low, high]", rel)
        if "designed_for_Re_range" in mesh:
            span = mesh["designed_for_Re_range"]
            if not (isinstance(span, list) and len(span) == 2
                    and all(is_integer(v) for v in span)
                    and span[0] <= span[1]):
                check.warn("mesh/designed_for_Re_range is %r, not two "
                           "integers low then high" % (span,), rel)

    uncertainty = convergence.get("uncertainty")
    if "uncertainty" in convergence:
        if not isinstance(uncertainty, dict):
            check.warn("convergence/uncertainty is not an object", rel)
        else:
            if not is_number(uncertainty.get("cd_relative")):
                check.warn("convergence/uncertainty/cd_relative is not a "
                           "number", rel)
            if not isinstance(uncertainty.get("basis"), str):
                check.warn("convergence/uncertainty/basis does not say how it "
                           "was obtained", rel)
            if uncertainty.get("source") not in UNCERTAINTY_SOURCES:
                check.warn("convergence/uncertainty/source is %r, the SPEC "
                           "allows %s"
                           % (uncertainty.get("source"),
                              " or ".join(UNCERTAINTY_SOURCES)), rel)
    return check


def check_publication_residue(root, entries):
    check = Check("publication_residue", "No working state left in a "
                  "published capsule", "SPEC 2")
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
            # A status nested inside a declared block is content, not residue:
            # a candidates block records what was tried and rejected, and the
            # capsule is better for carrying it. Only a top level marker is a
            # leftover.
            top_level = not trail
            if (top_level and lowered in ("status", "_pending")) \
                    or lowered.startswith("_"):
                check.warn("working key survives publication: %s"
                           % "/".join(trail + (key,)), rel)
    return check


def check_punctuation(root, entries):
    check = Check("punctuation", "No em dash, en dash or minus sign",
                  "SPEC 3.2")
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
    check = Check("encoding", "Every text artifact is valid UTF-8",
                  "SPEC 3.2")
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
    check_environment,
    check_summary_blocks,
    check_plane_pairs,
    check_csv_header,
    check_png_geometry,
    check_png_metadata,
    check_reference_block,
    check_dimensional_flags,
    check_extensions,
    check_no_instrument_dir,
    check_diff_manifest,
    check_diff_view_hashes,
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
    try:
        sys.exit(main())
    except BrokenPipeError:  # piping into head is normal usage
        os._exit(0)
