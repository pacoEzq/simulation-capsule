#!/usr/bin/env python3
"""check_frozen.py: prove that published capsules have not changed.

A capsule linked from a published post is frozen: not a byte moves after
publication. This script makes that a rule the CI can enforce rather than an
intention the author has to remember.

    python check_frozen.py --write examples/as-published > examples/frozen.sha256
    python check_frozen.py examples/frozen.sha256

The first form walks a tree and prints one line per file: sha256, two spaces,
path relative to the repository root. The second form reads such a list and
fails if any file is missing, added, or different.

Exit codes: 0 frozen tree intact, 1 drift found, 2 usage error.
Standard library only.
"""

import argparse
import hashlib
import os
import sys


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 16), b""):
            digest.update(block)
    return digest.hexdigest()


def walk(tree):
    for dirpath, dirnames, filenames in os.walk(tree):
        dirnames.sort()
        for name in sorted(filenames):
            yield os.path.join(dirpath, name).replace(os.sep, "/")


def write_manifest(tree):
    for path in walk(tree):
        print("%s  %s" % (sha256_of(path), path))
    return 0


def read_manifest(path):
    expected = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            digest, _, rel = line.partition("  ")
            expected[rel] = digest
    return expected


def verify(manifest):
    expected = read_manifest(manifest)
    if not expected:
        sys.stderr.write("manifest is empty: %s\n" % manifest)
        return 2
    trees = sorted({rel.split("/")[0] + "/" + rel.split("/")[1]
                    for rel in expected if rel.count("/") >= 1})
    present = set()
    for tree in trees:
        present |= set(walk(tree))
    drift = []
    for rel, digest in sorted(expected.items()):
        if rel not in present:
            drift.append("missing   %s" % rel)
        elif sha256_of(rel) != digest:
            drift.append("changed   %s" % rel)
    for rel in sorted(present - set(expected)):
        drift.append("added     %s" % rel)
    if drift:
        print("frozen tree drifted in %d place(s):" % len(drift))
        for line in drift:
            print("  " + line)
        return 1
    print("frozen tree intact: %d files" % len(expected))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Verify or write the manifest of a frozen capsule tree.")
    parser.add_argument("target",
                        help="manifest to verify, or tree to hash with --write")
    parser.add_argument("--write", action="store_true",
                        help="hash the tree and print a manifest to stdout")
    args = parser.parse_args(argv)
    if args.write:
        if not os.path.isdir(args.target):
            sys.stderr.write("not a directory: %s\n" % args.target)
            return 2
        return write_manifest(args.target)
    if not os.path.isfile(args.target):
        sys.stderr.write("not a file: %s\n" % args.target)
        return 2
    return verify(args.target)


if __name__ == "__main__":
    sys.exit(main())
