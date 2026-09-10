"""Subtract the grayscale renders behind a capsule diff, SPEC 4.7.

Everything about the case comes from the variant capsule's diff.json:
which pairs exist, what each frame is called, how many rows the title
band takes, how much field one level is worth and where the threshold
sits. Nothing here names a case.

The grayscale frames are the instrument, not the result. They stay out of
capsule_*/ and only diff/ travels. Never run this over views/: colour
renders spend a different amount of RGB per unit of field and report a
different number for the same physical change. Measured on the NACA pair,
colour said 19 percent where grayscale said 6.

Layout the script expects, mirroring what diff.json already declares:

    <src>/<capsule>/<view file name>

so a pair whose base_view is "views/view_cp_nearfield.png" in capsule
"capsule_naca0012_aoa5" is read from

    <src>/capsule_naca0012_aoa5/view_cp_nearfield.png

Usage:
    python diff_capsule_views.py CAPSULE_DIR [--src DIR] [--write]

CAPSULE_DIR is the variant capsule, the one carrying diff.json. With
--write the measured numbers go back into diff.json; without it they are
only printed, so a run can never quietly rewrite a manifest.
"""

import argparse
import hashlib
import json
import os
import sys

import numpy as np
from PIL import Image

VERSION = "2.2"

DEFAULT_SRC = "diffsrc"

# Gain for the output picture when diff.json does not declare one. Fixed
# and printed, never automatic: an auto level would make two diffs of
# different magnitude look equally loud.
FALLBACK_GAIN = 6


def die(message):
    sys.exit("diff_capsule_views: " + message)


def need(mapping, key, where):
    if key not in mapping:
        die("%s has no '%s'" % (where, key))
    return mapping[key]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def verify(path, expected, label, name):
    """Stop on a mismatch. The chain is view, then grayscale frame, then
    diff: a view re-exported without regenerating its frame would leave
    the frame hash intact and the run would return the previous number
    with nothing to show for it."""
    if not os.path.isfile(path):
        die("%s: missing %s: %s" % (name, label, path))
    if expected is None:
        die("%s: no recorded sha256 for %s; SPEC 4.7 asks for all four"
            % (name, label))
    got = sha256(path)
    if got != expected.lower():
        die("%s: %s does not match its recorded sha256\n"
            "  file     %s\n  recorded %s\n  actual   %s\n"
            "Re-export or regenerate what is stale, then update diff.json."
            % (name, label, path, expected.lower(), got))


def load_frame(path, crop_rows):
    if not os.path.isfile(path):
        die("missing frame: %s" % path)
    rgb = np.array(Image.open(path).convert("RGB")).astype(int)
    if crop_rows:
        rgb = rgb[crop_rows:]
    return rgb


def compare(base_rgb, var_rgb):
    """Return (delta, mask). The mask drops every non grey pixel in
    either frame. That removes the solid body and, in the same step, the
    antialiased edge around it: those pixels blend a constant body colour
    with a field that does change, so they would light up without any
    flow changing."""
    non_grey = ((base_rgb.max(2) - base_rgb.min(2)) > 0) | \
               ((var_rgb.max(2) - var_rgb.min(2)) > 0)
    delta = np.abs(base_rgb.mean(2) - var_rgb.mean(2))
    return delta, ~non_grey


def frame_path(src, capsule, view_ref):
    return os.path.join(src, capsule, os.path.basename(view_ref))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("capsule_dir",
                    help="the variant capsule, which holds diff.json")
    ap.add_argument("--src",
                    default=os.environ.get("CAPSULE_DIFFSRC", DEFAULT_SRC),
                    help="root of the grayscale frames, one directory per capsule")
    ap.add_argument("--write", action="store_true",
                    help="write the measured numbers back into diff.json")
    args = ap.parse_args()

    manifest_path = os.path.join(args.capsule_dir, "diff.json")
    if not os.path.isfile(manifest_path):
        die("no diff.json in %s" % args.capsule_dir)
    with open(manifest_path, encoding="utf-8") as handle:
        manifest = json.load(handle)

    base_block = need(manifest, "base", "diff.json")
    base_capsule = need(base_block, "capsule", "base")
    base_root = base_block.get("path", os.path.join("examples", base_capsule))
    var_capsule = need(need(manifest, "variant", "diff.json"), "capsule",
                       "variant")
    contract = need(manifest, "view_contract", "diff.json")
    pipeline = need(manifest, "pipeline", "diff.json")
    pairs = need(manifest, "pairs", "diff.json")

    crop_rows = contract.get("crop_rows_top", 0)
    gain = pipeline.get("output_gain", FALLBACK_GAIN)

    out_dir = os.path.join(args.capsule_dir, "diff")
    os.makedirs(out_dir, exist_ok=True)

    print("diff_capsule_views %s" % VERSION)
    print("manifest %s" % os.path.abspath(manifest_path))
    print("source   %s" % os.path.abspath(args.src))
    print("base     %s" % base_capsule)
    print("variant  %s" % var_capsule)
    print("crop %d rows, gain x%d" % (crop_rows, gain))

    for pair in pairs:
        name = need(pair, "name", "a pair")
        level = float(need(pair, "level_size", name))
        levels = int(need(pair, "threshold_levels", name))
        # SPEC keeps the sign per pair: two fields may be counted
        # differently, and a reader must not look elsewhere to learn how
        # the number in front of them was counted.
        comparison = need(pair, "threshold_comparison", name)
        if comparison not in (">=", ">"):
            die("%s: threshold_comparison is %r, expected '>=' or '>'"
                % (name, comparison))
        base_file = need(pair, "base_view", name)
        var_file = need(pair, "variant_view", name)
        base_path = frame_path(args.src, base_capsule, base_file)
        var_path = frame_path(args.src, var_capsule, var_file)

        source = need(pair, "source_sha256", name)
        instrument = need(pair, "instrument_sha256", name)
        verify(os.path.join(base_root, "views", base_file),
               source.get("base_view"), "base view", name)
        verify(os.path.join(args.capsule_dir, "views", var_file),
               source.get("variant_view"), "variant view", name)
        verify(base_path, instrument.get("base_frame"), "base frame", name)
        verify(var_path, instrument.get("variant_frame"), "variant frame", name)

        base = load_frame(base_path, crop_rows)
        var = load_frame(var_path, crop_rows)
        if base.shape != var.shape:
            die("%s: frames differ in size, %s against %s"
                % (name, base.shape, var.shape))

        delta, mask = compare(base, var)
        kept = delta[mask]
        changed = kept >= levels if comparison == ">=" else kept > levels
        fraction = round(float(changed.mean()), 4)
        max_levels = int(kept.max())

        picture = delta.copy()
        picture[~mask] = 0
        out_path = os.path.join(out_dir, os.path.basename(
            pair.get("output", name + ".png")))
        Image.fromarray(
            np.clip(picture * gain, 0, 255).astype("uint8")).save(out_path)

        pair["changed_pixel_fraction"] = fraction
        pair["max_delta_levels"] = max_levels
        pair["max_delta_physical"] = round(max_levels * level, 4)
        pipeline["pixels_evaluated"] = int(kept.size)
        pipeline["pixels_excluded"] = int((~mask).sum())

        print("")
        print(name)
        print("  frame                  %d x %d after crop"
              % (base.shape[1], base.shape[0]))
        print("  pixels evaluated       %d" % kept.size)
        print("  pixels excluded        %d" % int((~mask).sum()))
        print("  level size             %.7f" % level)
        print("  threshold              %s %.4f  (%d levels)"
              % (comparison, levels * level, levels))
        print("  changed_pixel_fraction %.4f  (%.2f percent)"
              % (fraction, 100 * fraction))
        print("  max delta              %d levels, %.3f in field units"
              % (max_levels, max_levels * level))
        print("  written                %s" % out_path)

    print("")
    if args.write:
        with open(manifest_path, "w", encoding="utf-8") as handle:
            json.dump(manifest, handle, indent=2)
            handle.write("\n")
        print("diff.json updated in place.")
    else:
        print("Nothing written to diff.json. Re-run with --write to record "
              "these numbers.")


if __name__ == "__main__":
    main()
