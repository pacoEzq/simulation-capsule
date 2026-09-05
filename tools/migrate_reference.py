#!/usr/bin/env python3
"""Migrate a capsule summary.json to the SPEC 0.2 reference block.

SPEC 0.2 fixes one form for a dimensional quantity: a unit suffix on the key,
inside a single top level "reference" block. The five published capsules were
written under 0.1 and use three different conventions, so the migration is not
a generic rewrite. A bare 1.0 does not say whether it is a metre or a
millimetre. Each case therefore carries an explicit, hand written rule table
below, and the script only applies it.

Usage:
    python tools/migrate_reference.py examples/capsule_naca0012_aoa5/summary.json
    python tools/migrate_reference.py examples/capsule_naca0012_aoa5/summary.json --write
    python tools/migrate_reference.py examples/*/summary.json --write

Without --write the file is not touched and the migrated text goes to stdout
after the operation log. With --write the file is replaced.

The script is idempotent: an operation whose source key is gone and whose
target key is already there is reported as "already" and changes nothing, so a
second run on a migrated file exits 0 with no diff.

It does not touch examples/as-published/, which is frozen and guarded by
check_frozen.py.

Number literals survive: 6.976341e-04 is written back as 6.976341e-04, not as
0.0006976341, so the diff stays inside the block that was migrated. Layout does
not survive as faithfully. Blocks written on one line in the source come back
one key per line, and the written text is checked against the migrated data
before the file is replaced.

Standard library only. Python 3.8 or later.
"""

import argparse
import copy
import json
import sys

VERSION = "1.0"


# ---------------------------------------------------------------------------
# Rule tables, one per case. Written by hand against the five summary.json.
#
# Operations, applied in the order listed:
#   ensure  path                       create an empty block if absent
#   expand  path key to [note_to]      {"value": v, "unit_flag": u} -> to: v
#   rename  path key to                bare number, key gains the unit suffix
#   move    from_path key to_path to   lift a key into the reference block
#   drop    path key                   delete a key
#   drop_path path                     delete a whole block
#   set     path key value             add a key, only if absent
#   order   path keys                  reorder; unlisted keys keep their order
# ---------------------------------------------------------------------------

RULES = {

    "ahmed25_re1e6": {
        "note": (
            "Bare numbers in reference. Four are dimensional and take a suffix. "
            "reynolds_L and frontal_area are not: frontal_area is A/L^2 with "
            "L = 1 m, which is why the validator counted four and not six."
        ),
        "ops": [
            {"op": "rename", "path": "reference", "key": "length_L", "to": "length_L_m"},
            {"op": "rename", "path": "reference", "key": "velocity_U", "to": "velocity_U_m_s"},
            {"op": "rename", "path": "reference", "key": "density_rho", "to": "density_rho_kg_m3"},
            {"op": "rename", "path": "reference", "key": "viscosity_mu", "to": "viscosity_mu_Pa_s"},
            {"op": "set", "path": "reference", "key": "frontal_area_note",
             "value": "A/L^2, nondimensional; the dimensional area is frontal_area times length_L_m squared"},
            {"op": "order", "path": "reference", "keys": [
                "length_L_m", "velocity_U_m_s", "density_rho_kg_m3", "viscosity_mu_Pa_s",
                "reynolds_L", "frontal_area", "frontal_area_note",
            ]},
        ],
    },

    "cube_re200": {
        "note": "Object form {value, unit_flag} on five keys. Notes are kept as <key>_note.",
        "ops": [
            {"op": "expand", "path": "reference", "key": "side", "to": "side_m"},
            {"op": "expand", "path": "reference", "key": "velocity", "to": "velocity_m_s"},
            {"op": "expand", "path": "reference", "key": "density", "to": "density_kg_m3"},
            {"op": "expand", "path": "reference", "key": "viscosity", "to": "viscosity_Pa_s",
             "note_to": "viscosity_note"},
            {"op": "expand", "path": "reference", "key": "area", "to": "area_m2",
             "note_to": "area_note"},
            {"op": "order", "path": "reference", "keys": [
                "side_m", "velocity_m_s", "density_kg_m3",
                "viscosity_Pa_s", "viscosity_note", "area_m2", "area_note",
            ]},
        ],
    },

    "naca0012_aoa5": {
        "note": "Object form {value, unit_flag} on five keys. Same treatment as the cube.",
        "ops": [
            {"op": "expand", "path": "reference", "key": "chord", "to": "chord_m"},
            {"op": "expand", "path": "reference", "key": "velocity", "to": "velocity_m_s"},
            {"op": "expand", "path": "reference", "key": "density", "to": "density_kg_m3"},
            {"op": "expand", "path": "reference", "key": "viscosity", "to": "viscosity_Pa_s"},
            {"op": "expand", "path": "reference", "key": "area", "to": "area_m2",
             "note_to": "area_note"},
            {"op": "order", "path": "reference", "keys": [
                "chord_m", "velocity_m_s", "density_kg_m3",
                "viscosity_Pa_s", "area_m2", "area_note",
            ]},
        ],
    },

    "delta65_a13p3_re1e6": {
        "note": (
            "Reference split over two blocks, with chord_root declared twice and "
            "the moment frame a third place. The migration builds one top level "
            "reference block and deletes the nondimensionalization block. "
            "span_b, s_ref and aspect_ratio stay in geometry: they are ratios "
            "over chord_root, not dimensional lengths."
        ),
        "ops": [
            {"op": "ensure", "path": "reference"},
            {"op": "move", "from_path": "nondimensionalization/reference_quantities",
             "key": "chord_root", "to_path": "reference", "to": "chord_root_m"},
            {"op": "move", "from_path": "nondimensionalization/reference_quantities",
             "key": "u_inf", "to_path": "reference", "to": "u_inf_m_s"},
            {"op": "move", "from_path": "nondimensionalization/reference_quantities",
             "key": "rho_ref", "to_path": "reference", "to": "rho_ref_kg_m3"},
            {"op": "move", "from_path": "nondimensionalization/reference_quantities",
             "key": "mu_ref", "to_path": "reference", "to": "mu_ref_Pa_s"},
            {"op": "move", "from_path": "nondimensionalization/reference_quantities",
             "key": "q_inf", "to_path": "reference", "to": "q_inf_Pa"},
            {"op": "move", "from_path": "nondimensionalization",
             "key": "convention", "to_path": "reference", "to": "convention"},
            {"op": "move", "from_path": "case/geometry",
             "key": "moment_reference", "to_path": "reference", "to": "moment_reference"},
            {"op": "drop", "path": "case/geometry", "key": "chord_root"},
            {"op": "drop_path", "path": "nondimensionalization/reference_quantities"},
            {"op": "drop_path", "path": "nondimensionalization"},
            {"op": "order", "path": "reference", "keys": [
                "chord_root_m", "u_inf_m_s", "rho_ref_kg_m3", "mu_ref_Pa_s",
                "q_inf_Pa", "moment_reference", "convention",
            ]},
            {"op": "order", "path": "", "keys": ["case", "reference", "scalars"]},
        ],
    },

    "jet_r2_re100": {
        "note": (
            "Already suffixed: this capsule is where the winning form came from. "
            "What goes is the unit_flag marker, in reference and in performance, "
            "which 0.2 replaces with the suffix itself. The two areas of the jet "
            "block are A/D^2 and stay bare; the note says so."
        ),
        "ops": [
            {"op": "drop", "path": "reference", "key": "unit_flag"},
            {"op": "drop", "path": "performance", "key": "unit_flag"},
            {"op": "order", "path": "reference", "keys": [
                "length_scale_D_m", "velocity_scale_U_m_s", "density_rho_kg_m3",
                "dynamic_viscosity_Pa_s", "reynolds_number", "note",
            ]},
        ],
    },
}

# Directory name to rule key, for the cases whose alias is not the folder name.
ALIASES = {
    "capsule_ahmed25_re1e6": "ahmed25_re1e6",
    "capsule_cube_re200": "cube_re200",
    "capsule_naca0012_aoa5": "naca0012_aoa5",
    "capsule_delta65_a13p3_re1e6": "delta65_a13p3_re1e6",
    "capsule_jet_r2_re100": "jet_r2_re100",
}


# ---------------------------------------------------------------------------
# Reading and writing, with the number literals preserved
#
# A plain json round trip rewrites 6.976341e-04 as 0.0006976341 and drops the
# trailing zero of 0.3218340. The values are equal but the capsule is a
# document, and a migration of the reference block has no business restating
# every residual. So numbers are parsed into a wrapper that remembers the text
# it came from, and the writer prints that text back.
# ---------------------------------------------------------------------------

class RawFloat(float):

    def __new__(cls, text):
        obj = float.__new__(cls, text)
        obj.raw = text
        return obj


class RawInt(int):

    def __new__(cls, text):
        obj = int.__new__(cls, text)
        obj.raw = text
        return obj


def load_text(text):
    return json.loads(text, parse_float=RawFloat, parse_int=RawInt)


INLINE_WIDTH = 78


def dumps(value, level=0):
    pad = "  " * level
    inner = "  " * (level + 1)
    if isinstance(value, dict):
        if not value:
            return "{}"
        items = ['%s%s: %s' % (inner, json.dumps(k, ensure_ascii=False), dumps(v, level + 1))
                 for k, v in value.items()]
        return "{\n" + ",\n".join(items) + "\n" + pad + "}"
    if isinstance(value, list):
        if not value:
            return "[]"
        parts = [dumps(v, level + 1) for v in value]
        flat = "[" + ", ".join(parts) + "]"
        scalar_only = all(not isinstance(v, (dict, list)) for v in value)
        if scalar_only and len(pad) + len(flat) <= INLINE_WIDTH:
            return flat
        return "[\n" + ",\n".join(inner + p for p in parts) + "\n" + pad + "]"
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return "null"
    if isinstance(value, (RawFloat, RawInt)):
        return value.raw
    if isinstance(value, (int, float)):
        return repr(value)
    return json.dumps(value, ensure_ascii=False)


# ---------------------------------------------------------------------------
# Path helpers
# ---------------------------------------------------------------------------

def get_block(data, path):
    """Return the dict at a slash separated path, or None."""
    node = data
    if path:
        for part in path.split("/"):
            if not isinstance(node, dict) or part not in node:
                return None
            node = node[part]
    return node if isinstance(node, dict) else None


def parent_and_leaf(data, path):
    parts = path.split("/")
    node = data
    for part in parts[:-1]:
        if not isinstance(node, dict) or part not in node:
            return None, None
        node = node[part]
    if not isinstance(node, dict):
        return None, None
    return node, parts[-1]


# ---------------------------------------------------------------------------
# Operations
# ---------------------------------------------------------------------------

class Log:
    def __init__(self):
        self.lines = []
        self.changes = 0
        self.problems = 0

    def done(self, text):
        self.lines.append("  ok      " + text)
        self.changes += 1

    def already(self, text):
        self.lines.append("  already " + text)

    def problem(self, text):
        self.lines.append("  PROBLEM " + text)
        self.problems += 1


def op_ensure(data, op, log):
    block = get_block(data, op["path"])
    if block is not None:
        log.already("block %s exists" % op["path"])
        return
    parent, leaf = parent_and_leaf(data, op["path"])
    if parent is None:
        log.problem("cannot create block %s, parent missing" % op["path"])
        return
    parent[leaf] = {}
    log.done("created block %s" % op["path"])


def op_expand(data, op, log):
    block = get_block(data, op["path"])
    if block is None:
        log.problem("block %s missing" % op["path"])
        return
    key, to = op["key"], op["to"]
    if key not in block:
        if to in block:
            log.already("%s/%s" % (op["path"], to))
        else:
            log.problem("neither %s nor %s in %s" % (key, to, op["path"]))
        return
    obj = block[key]
    if not isinstance(obj, dict) or "value" not in obj:
        log.problem("%s/%s is not object form" % (op["path"], key))
        return
    note_to = op.get("note_to")
    if "note" in obj and not note_to:
        log.problem("%s/%s carries a note and the rule has no note_to" % (op["path"], key))
        return
    block[key] = obj["value"]
    rename_key(block, key, to)
    if note_to and "note" in obj:
        insert_after(block, to, note_to, obj["note"])
    log.done("%s/%s -> %s (unit_flag %r dropped)" % (op["path"], key, to, obj.get("unit_flag")))


def op_rename(data, op, log):
    block = get_block(data, op["path"])
    if block is None:
        log.problem("block %s missing" % op["path"])
        return
    key, to = op["key"], op["to"]
    if key not in block:
        if to in block:
            log.already("%s/%s" % (op["path"], to))
        else:
            log.problem("neither %s nor %s in %s" % (key, to, op["path"]))
        return
    rename_key(block, key, to)
    log.done("%s/%s -> %s" % (op["path"], key, to))


def op_move(data, op, log):
    src = get_block(data, op["from_path"])
    dst = get_block(data, op["to_path"])
    if dst is None:
        log.problem("target block %s missing" % op["to_path"])
        return
    key, to = op["key"], op["to"]
    if src is None or key not in src:
        if to in dst:
            log.already("%s/%s" % (op["to_path"], to))
        else:
            log.problem("%s/%s not found and %s/%s absent"
                        % (op["from_path"], key, op["to_path"], to))
        return
    dst[to] = src.pop(key)
    log.done("%s/%s -> %s/%s" % (op["from_path"], key, op["to_path"], to))


def op_drop(data, op, log):
    block = get_block(data, op["path"])
    if block is None:
        log.problem("block %s missing" % op["path"])
        return
    if op["key"] not in block:
        log.already("%s/%s gone" % (op["path"], op["key"]))
        return
    block.pop(op["key"])
    log.done("dropped %s/%s" % (op["path"], op["key"]))


def op_drop_path(data, op, log):
    parent, leaf = parent_and_leaf(data, op["path"])
    if parent is None or leaf not in parent:
        log.already("block %s gone" % op["path"])
        return
    block = parent[leaf]
    if isinstance(block, dict) and block:
        log.problem("block %s not empty, refusing to drop: %s"
                    % (op["path"], sorted(block)))
        return
    parent.pop(leaf)
    log.done("dropped block %s" % op["path"])


def op_set(data, op, log):
    block = get_block(data, op["path"])
    if block is None:
        log.problem("block %s missing" % op["path"])
        return
    if op["key"] in block:
        log.already("%s/%s" % (op["path"], op["key"]))
        return
    block[op["key"]] = op["value"]
    log.done("added %s/%s" % (op["path"], op["key"]))


def op_order(data, op, log):
    block = get_block(data, op["path"])
    if block is None:
        log.problem("block %s missing" % op["path"])
        return
    before = list(block)
    ordered = [k for k in op["keys"] if k in block]
    ordered += [k for k in before if k not in ordered]
    if ordered == before:
        log.already("order of %s" % (op["path"] or "root"))
        return
    for k in ordered:
        block[k] = block.pop(k)
    log.done("reordered %s" % (op["path"] or "root"))


OPS = {
    "ensure": op_ensure,
    "expand": op_expand,
    "rename": op_rename,
    "move": op_move,
    "drop": op_drop,
    "drop_path": op_drop_path,
    "set": op_set,
    "order": op_order,
}


def rename_key(block, key, to):
    """Rename in place, keeping the position of the old key."""
    items = [(to, block[key]) if k == key else (k, v) for k, v in block.items()]
    block.clear()
    block.update(items)


def insert_after(block, anchor, key, value):
    items = []
    for k, v in block.items():
        items.append((k, v))
        if k == anchor:
            items.append((key, value))
    block.clear()
    block.update(items)


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def pick_rule(path, data):
    """Rule key from the capsule directory, falling back to the case field."""
    parts = [p for p in path.replace("\\", "/").split("/") if p]
    for part in reversed(parts):
        if part in ALIASES:
            return ALIASES[part]
        if part in RULES:
            return part
    alias = data.get("alias")
    if isinstance(alias, str) and alias in ALIASES:
        return ALIASES[alias]
    case = data.get("case")
    if isinstance(case, str) and case in RULES:
        return case
    if isinstance(case, dict) and case.get("alias") in RULES:
        return case["alias"]
    return None


def migrate_file(path, write, quiet=False):
    with open(path, "r", encoding="utf-8") as handle:
        text = handle.read()
    try:
        data = load_text(text)
    except ValueError as error:
        print("%s\n  PROBLEM not valid JSON: %s" % (path, error))
        return 1

    name = pick_rule(path, data)
    if name is None:
        print("%s\n  PROBLEM no rule for this capsule" % path)
        return 1

    rule = RULES[name]
    original = copy.deepcopy(data)
    log = Log()
    for op in rule["ops"]:
        OPS[op["op"]](data, op, log)

    print("%s  [rule %s]" % (path, name))
    if not quiet:
        for line in log.lines:
            print(line)

    if log.problems:
        print("  %d problem(s), file not written" % log.problems)
        return 1

    if data == original:
        print("  no change, already migrated")
        return 0

    out = dumps(data) + "\n"
    try:
        if json.loads(out) != json.loads(json.dumps(data)):
            print("  PROBLEM the written text does not reparse to the migrated data")
            return 1
    except ValueError as error:
        print("  PROBLEM the written text is not valid JSON: %s" % error)
        return 1
    if write:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(out)
        print("  written, %d change(s)" % log.changes)
    else:
        print("  %d change(s), dry run; rerun with --write" % log.changes)
        print(out)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Migrate capsule summary.json files to the SPEC 0.2 reference block.")
    parser.add_argument("paths", nargs="+", help="one or more summary.json")
    parser.add_argument("--write", action="store_true",
                        help="replace the file instead of printing it")
    parser.add_argument("--quiet", action="store_true",
                        help="verdict only, no operation log")
    parser.add_argument("--version", action="version", version="migrate_reference " + VERSION)
    args = parser.parse_args(argv)

    status = 0
    for path in args.paths:
        status |= migrate_file(path, args.write, args.quiet)
        print("")
    return status


if __name__ == "__main__":
    sys.exit(main())
