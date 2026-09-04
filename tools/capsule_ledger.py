#!/usr/bin/env python3
"""capsule_ledger.py: measure the token cost of a Simulation Capsule.

The series reports a token ledger in every part: what the new artifact weighs,
what the capsule weighs, and what share of the budget that is. Until now those
figures were counted by hand, once per part, in whatever shell was open. This
measures them, so the number in a post and the number in the repository come
from the same place.

Measured, not estimated: character counts are exact. Tokens are a conversion
and the conversion is declared per artifact type, because numeric CSV packs
denser than prose and an image does not have characters at all.

    text report (setup.txt)   3.5 characters per token
    JSON and prose            4.0
    numeric CSV               2.5
    source code               3.5
    PNG at 1024 px            1200 tokens each, flat

The image figure is model dependent and always flagged as such. Across
tokenizers the honesty band on the whole ledger is about 20%.

Usage:
    python capsule_ledger.py examples/*/
    python capsule_ledger.py examples/*/ --markdown
    python capsule_ledger.py examples/*/ --json
    python capsule_ledger.py examples/*/ --budget 60000 --window 200000

Standard library only. Shares its directory walk with check_capsule.py so that
the ledger and the validator can never disagree about what is in a capsule.
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_capsule import walk_capsule, read_text  # noqa: E402

TOOL_VERSION = "1.0"
DEFAULT_BUDGET = 60000
DEFAULT_WINDOW = 200000
IMAGE_TOKENS = 1200

# (label, characters per token). None means the artifact is not text.
KINDS = {
    "setup_report": ("setup report", 3.5),
    "json": ("JSON", 4.0),
    "csv": ("numeric CSV", 2.5),
    "code": ("source", 3.5),
    "image": ("image", None),
    "other": ("text", 3.5),
}

# Which part of the series introduces each artifact.
PART = {
    "setup.txt": "T1",
    "summary.json": "T2",
    "planes": "T3",
    "samples.csv": "T4",
    "features.json": "T5",
    "views": "T6",
    "diff": "T6b",
    "run_macro.java": "T7",
    "manifest.json": "T7",
    "signals": "T8",
    "modes": "T9",
    "snapshots": "T8/T9",
    "disclosure.json": "T10",
}


def classify(rel):
    """Return (kind, layer) for a path relative to the capsule root."""
    parts = rel.replace("\\", "/").split("/")
    layer = parts[0] if len(parts) > 1 else parts[0]
    name = parts[-1].lower()
    if name == "setup.txt":
        kind = "setup_report"
    elif name.endswith(".png"):
        kind = "image"
    elif name.endswith(".json"):
        kind = "json"
    elif name.endswith(".csv"):
        kind = "csv"
    elif name.endswith((".java", ".py")):
        kind = "code"
    else:
        kind = "other"
    return kind, layer


def two_significant(value):
    """Round to two significant figures, the series reporting rule."""
    if value <= 0:
        return 0
    magnitude = 10 ** (len(str(int(value))) - 2)
    if magnitude < 1:
        magnitude = 1
    return int(round(value / magnitude) * magnitude)


def measure_file(root, rel):
    """Return a record for one artifact."""
    path = os.path.join(root, rel)
    kind, layer = classify(rel)
    size = os.path.getsize(path)
    record = {
        "path": rel.replace("\\", "/"),
        "layer": layer,
        "kind": kind,
        "part": PART.get(layer, PART.get(os.path.basename(rel), "")),
        "bytes": size,
        "characters": None,
        "crlf": False,
        "tokens": None,
        "estimated": False,
    }
    if kind == "image":
        record["tokens"] = IMAGE_TOKENS
        record["estimated"] = True
        return record
    try:
        text, _ = read_text(path)
    except (ValueError, OSError):
        record["tokens"] = 0
        record["note"] = "unreadable as text, counted as zero"
        return record
    record["characters"] = len(text)
    record["crlf"] = "\r\n" in text
    rate = KINDS[kind][1]
    record["tokens"] = int(round(len(text) / rate))
    return record


def measure_capsule(root, budget, window):
    entries = walk_capsule(root)
    records = [measure_file(root, rel) for rel in entries]
    by_layer = {}
    for record in records:
        bucket = by_layer.setdefault(record["layer"], {
            "layer": record["layer"], "part": record["part"],
            "files": 0, "characters": 0, "tokens": 0, "estimated": False})
        bucket["files"] += 1
        bucket["characters"] += record["characters"] or 0
        bucket["tokens"] += record["tokens"]
        bucket["estimated"] |= record["estimated"]
    total_tokens = sum(r["tokens"] for r in records)
    total_chars = sum(r["characters"] or 0 for r in records)
    return {
        "capsule": os.path.basename(os.path.normpath(root)),
        "files": len(records),
        "characters": total_chars,
        "tokens": total_tokens,
        "tokens_2sf": two_significant(total_tokens),
        "budget_share": total_tokens / budget if budget else None,
        "window_share": total_tokens / window if window else None,
        "crlf": any(r["crlf"] for r in records),
        "artifacts": records,
        "layers": sorted(by_layer.values(), key=lambda b: b["layer"]),
    }


def format_text(results, budget, window):
    lines = []
    for result in results:
        lines.append("capsule: %s" % result["capsule"])
        lines.append("  %-28s %10s %10s %8s"
                     % ("layer", "chars", "tokens", "part"))
        for layer in result["layers"]:
            chars = "%d" % layer["characters"] if layer["characters"] else "n/a"
            label = layer["layer"]
            if "." not in label:
                label += "/"
            lines.append("  %-28s %10s %10d %8s"
                         % (label, chars, layer["tokens"], layer["part"]))
        lines.append("  %-28s %10d %10d"
                     % ("TOTAL", result["characters"], result["tokens"]))
        lines.append("  %s tokens at two significant figures, %.0f%% of the "
                     "%d token budget, %.0f%% of the %d token window"
                     % (f"{result['tokens_2sf']:,}",
                        100 * result["budget_share"], budget,
                        100 * result["window_share"], window))
        if result["crlf"]:
            lines.append("  note: CRLF line endings inflate the character "
                         "count by one per line")
        lines.append("")
    return "\n".join(lines)


def format_markdown(results, budget, window):
    lines = ["## Token cost by artifact", "",
             "Character counts measured. Tokens converted at the declared "
             "rate per artifact type: 3.5 characters per token for the setup "
             "report and source, 4.0 for JSON, 2.5 for numeric CSV, and a "
             "flat 1,200 tokens per 1024 px image, which is model dependent. "
             "The honesty band across tokenizers is about 20%.", ""]
    layers = []
    for result in results:
        for layer in result["layers"]:
            if layer["layer"] not in layers:
                layers.append(layer["layer"])
    layers.sort(key=lambda name: (PART.get(name, "ZZ"), name))

    header = "| Artifact | Part | " + " | ".join(
        r["capsule"].replace("capsule_", "") for r in results) + " |"
    lines.append(header)
    lines.append("|" + "---|" * (len(results) + 2))
    for name in layers:
        cells = []
        for result in results:
            found = [l for l in result["layers"] if l["layer"] == name]
            cells.append(f"{found[0]['tokens']:,}" if found else "")
        lines.append("| `%s` | %s | %s |"
                     % (name, PART.get(name, ""), " | ".join(cells)))
    measured = [sum(a["tokens"] for a in r["artifacts"]
                    if not a["estimated"]) for r in results]
    estimated = [sum(a["tokens"] for a in r["artifacts"]
                     if a["estimated"]) for r in results]
    lines.append("| Text, measured | | %s |"
                 % " | ".join(f"{v:,}" for v in measured))
    lines.append("| Images, estimated | | %s |"
                 % " | ".join(f"{v:,}" for v in estimated))
    lines.append("| **Total** | | %s |" % " | ".join(
        f"**{r['tokens_2sf']:,}**" for r in results))
    lines.append("| Share of %d token budget | | %s |" % (budget, " | ".join(
        "%.0f%%" % (100 * r["budget_share"]) for r in results)))
    lines.append("")
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Measure the token cost of one or more Simulation "
                    "Capsules.")
    parser.add_argument("capsules", nargs="+")
    parser.add_argument("--budget", type=int, default=DEFAULT_BUDGET)
    parser.add_argument("--window", type=int, default=DEFAULT_WINDOW)
    parser.add_argument("--markdown", action="store_true",
                        help="emit a table ready to paste into a post")
    parser.add_argument("--json", action="store_true", dest="as_json")
    parser.add_argument("--per-file", action="store_true",
                        help="list every artifact instead of every layer")
    parser.add_argument("--fail-over", type=int, metavar="N",
                        help="exit 1 if any capsule exceeds N tokens, so the "
                             "budget can be enforced rather than reported")
    args = parser.parse_args(argv)

    results = []
    for root in args.capsules:
        if not os.path.isdir(root):
            sys.stderr.write("not a directory: %s\n" % root)
            return 2
        results.append(measure_capsule(root, args.budget, args.window))

    over = [r for r in results
            if args.fail_over and r["tokens"] > args.fail_over]

    if args.as_json:
        print(json.dumps(results, indent=2))
    elif args.markdown:
        print(format_markdown(results, args.budget, args.window))
    elif args.per_file:
        for result in results:
            print("capsule: %s" % result["capsule"])
            for record in result["artifacts"]:
                chars = record["characters"]
                print("  %-34s %9s %8d  %s"
                      % (record["path"],
                         f"{chars:,}" if chars is not None else "n/a",
                         record["tokens"],
                         "estimated" if record["estimated"] else "measured"))
            print()
    else:
        print(format_text(results, args.budget, args.window))

    for result in over:
        sys.stderr.write("over budget: %s costs %d tokens, the limit is %d\n"
                         % (result["capsule"], result["tokens"],
                            args.fail_over))
    return 1 if over else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:  # piping into head is normal usage
        os._exit(0)
