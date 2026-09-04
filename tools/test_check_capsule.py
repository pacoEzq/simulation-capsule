#!/usr/bin/env python3
"""Self test for check_capsule.py.

Three things are proved here.

1. The jet regression. The jet capsule as published on 2026-09-01, before
   correction, has to fail in exactly two places: json_parses and
   plane_pairs. The same capsule after correction has to pass.

2. Coverage. Every check has to fail on at least one fixture. A check that
   never fires on any input has not been verified, it has only been quiet,
   and quiet is what let the two jet defects reach a published post.

3. The dimensional rule of SPEC 3.1. A unit suffix outside the reference
   block, the object form and the bare number are all legacy: a warning in
   normal mode, a failure under --strict. A dimensionless group inside
   reference, an angle in degrees anywhere, and a key named after a symbol
   such as width_W fire nothing.

Run: python test_check_capsule.py
Exit 0 when all hold. Standard library only, temporary files, nothing
written outside the system temp directory.
"""

import json
import os
import shutil
import struct
import sys
import tempfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import check_capsule  # noqa: E402


def write_png(path, width=1024, height=576, exif=False, text=None):
    raw = b"".join(b"\x00" + bytes((x + y) % 256 for x in range(width))
                   for y in range(height))

    def chunk(ctype, body):
        return (struct.pack(">I", len(body)) + ctype + body
                + struct.pack(">I", zlib.crc32(ctype + body)))

    parts = [b"\x89PNG\r\n\x1a\n",
             chunk(b"IHDR", struct.pack(">IIBBBBB", width, height,
                                        8, 0, 0, 0, 0))]
    if text:
        parts.append(chunk(b"tEXt", text.encode("latin-1")))
    if exif:
        parts.append(chunk(b"eXIf", b"MM\x00\x2a\x00\x00\x00\x08"))
    parts.append(chunk(b"IDAT", zlib.compress(raw, 6)))
    parts.append(chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(b"".join(parts))


def write(path, text, encoding="utf-8"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding=encoding) as handle:
        handle.write(text)


GOOD_HEADER = ("# plane y_D = 0 | grid 33 x 13, spacing 0.25 D | "
               "419 of 429 nodes | fields %.3f | nondimensional\n"
               "x_D,z_D,u_U,cp\n-2.000,-1.500,0.993,0.031\n")


def build_jet(root, defective):
    write(os.path.join(root, "setup.txt"), "Case: jet_r2_re100\n")
    trailing = "," if defective else ""
    write(os.path.join(root, "summary.json"),
          '{\n  "case": "jet_r2_re100",\n'
          '  "reference": {"length_scale_D_m": 2.0, "re_D": 100}%s\n}\n'
          % trailing)
    for tag in ("y0", "x5", "x10"):
        write(os.path.join(root, "planes", "plane_%s.csv" % tag), GOOD_HEADER)
        name = ("jet_r2_re100_scene_%s.png" % tag) if defective \
            else ("plane_%s.png" % tag)
        os.makedirs(os.path.join(root, "planes"), exist_ok=True)
        write_png(os.path.join(root, "planes", name))


def build_torture(root):
    """One capsule that trips everything the jet does not."""
    write(os.path.join(root, "setup.txt"), "Case: torture\n")
    write(os.path.join(root, "README.md"), "not allowed inside a capsule\n")
    write(os.path.join(root, "features.json"), "")
    write(os.path.join(root, "summary.json"), json.dumps({
        "case": "torture",
        "status": "draft",
        "note": "a line with an em dash \u2014 inside it",
        "forces": {"cl": 0.5, "cd": 0.012, "drag_n": 1.2},
    }, indent=2))
    write(os.path.join(root, "planes", "plane_y0.csv"),
          "# plane y_D = 0 | fields %.3f\nx_D,cp\n0.0,0.1\n")
    write_png(os.path.join(root, "planes", "plane_y0.png"),
              width=512, height=288, exif=True, text="Software:starccm")
    with open(os.path.join(root, "samples.csv"), "wb") as handle:
        handle.write(b"x_D,cp\n0.0,\xff\xfe not utf8\n")


def build_no_summary(root):
    write(os.path.join(root, "setup.txt"), "Case: anchorless\n")


def build_no_setup(root):
    write(os.path.join(root, "summary.json"),
          '{"case": "blind", "reference": {"chord_m": 1.0}}\n')


def build_bad_name(root):
    """Wrong root name, and reference scales shipped as bare numbers in a
    container the SPEC does not name."""
    write(os.path.join(root, "setup.txt"), "Case: backup\n")
    write(os.path.join(root, "summary.json"), json.dumps({
        "case": "backup",
        "scales": {"length_L": 2.0, "velocity_U": 15.0},
    }, indent=2))


def build_angles(root):
    """What the Ahmed and NACA capsules taught: an angle in degrees is a
    group, a width named W is not a power, and a units line is not an
    object form."""
    write(os.path.join(root, "setup.txt"), "Case: angles\n")
    write(os.path.join(root, "summary.json"), json.dumps({
        "case": "angles",
        "geometry": {"width_W": 0.389, "slant_angle_deg": 25.0},
        "regime": {"angle_of_attack_deg": 5.0},
        "reference": {"length_scale_L_m": 1.044, "units": "SI"},
    }, indent=2))


def build_legacy(root):
    """The Part 2 and 3 form: reference quantities as {value, unit_flag}
    objects, plus one bare number. Legal until Part 7, never an error."""
    write(os.path.join(root, "setup.txt"), "Case: legacy\n")
    write(os.path.join(root, "summary.json"), json.dumps({
        "case": "legacy",
        "reference": {
            "chord": {"value": 1.0, "unit_flag": "m"},
            "velocity_inf": 25.0,
            "re_c": 1.0e6,
            "velocity_ratio": 2.0,
        },
    }, indent=2))


def failed_checks(root, strict=False):
    checks = check_capsule.validate(root)
    return {c.id for c in checks if c.status(strict) == "FAIL"}


def findings(root, check_id):
    for check in check_capsule.validate(root):
        if check.id == check_id:
            return check.findings
    return []


def main():
    workspace = tempfile.mkdtemp(prefix="capsule_selftest_")
    failures = []
    try:
        jet_bad = os.path.join(workspace, "capsule_jet_r2_re100_defective")
        jet_ok = os.path.join(workspace, "capsule_jet_r2_re100")
        torture = os.path.join(workspace, "capsule_torture")
        anchorless = os.path.join(workspace, "capsule_anchorless")
        blind = os.path.join(workspace, "capsule_blind")
        badname = os.path.join(workspace, "jet_capsule_backup")
        legacy = os.path.join(workspace, "capsule_legacy")
        angles_ok = os.path.join(workspace, "capsule_angles")

        build_jet(jet_bad, True)
        build_jet(jet_ok, False)
        build_torture(torture)
        build_no_summary(anchorless)
        build_no_setup(blind)
        build_bad_name(badname)
        build_legacy(legacy)
        build_angles(angles_ok)

        # 1. The jet regression.
        got = failed_checks(jet_bad)
        expected = {"json_parses", "plane_pairs"}
        if got != expected:
            failures.append("jet regression: expected %s, got %s"
                            % (sorted(expected), sorted(got)))
        else:
            print("PASS  jet before correction fails in exactly two places: "
                  "%s" % ", ".join(sorted(got)))

        got = failed_checks(jet_ok, strict=True)
        if got:
            failures.append("corrected jet should pass under --strict, "
                            "failed %s" % sorted(got))
        else:
            print("PASS  jet after correction passes every check, strict")

        # 2. Coverage.
        fired = set()
        for root in (jet_bad, torture, anchorless, blind, badname, legacy):
            fired |= failed_checks(root, strict=True)
        all_ids = {c(workspace, []).id for c in check_capsule.CHECKS}
        silent = sorted(all_ids - fired)
        if silent:
            failures.append("checks that never fired on any fixture: %s"
                            % ", ".join(silent))
        else:
            print("PASS  all %d checks fire on at least one fixture"
                  % len(all_ids))

        # 3. The dimensional rule.
        outside = [f for f in findings(torture, "dimensional_flags")
                   if "outside the reference block" in f["message"]]
        if not outside or outside[0]["severity"] != check_capsule.WARN:
            failures.append("unit suffix outside reference should warn")
        else:
            print("PASS  unit suffix outside reference warns, fails strict")

        angles = [f["message"] for f in findings(angles_ok, "dimensional_flags")]
        if angles:
            failures.append("angles and named symbols fired: %s" % angles)
        else:
            print("PASS  _deg angles and width_W stay silent")

        if "dimensional_flags" in failed_checks(legacy):
            failures.append("legacy forms should not fail in normal mode")
        elif "dimensional_flags" not in failed_checks(legacy, strict=True):
            failures.append("legacy forms should fail under --strict")
        else:
            print("PASS  legacy object and bare number warn, fail strict")

        msgs = [f["message"] for f in findings(legacy, "dimensional_flags")]
        if not any("legacy object form" in m for m in msgs):
            failures.append("object form not reported as legacy")
        if not any("no unit suffix" in m and "velocity_inf" in m
                   for m in msgs):
            failures.append("bare number not reported as legacy")
        if any("re_c" in m or "velocity_ratio" in m for m in msgs):
            failures.append("dimensionless group inside reference fired")
        else:
            print("PASS  dimensionless groups inside reference stay silent")
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
