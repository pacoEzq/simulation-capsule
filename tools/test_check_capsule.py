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

4. The diff extension of SPEC 3.4 and 4.7. A capsule that declares 'diff'
   and carries both artifacts passes; either one without the other fails,
   in whichever direction the mismatch runs. An instrument directory fails
   whether or not anything is declared. Inside diff.json, a comparison sign
   the SPEC does not allow is an error and a measured colormap that is not
   grayscale is a warning.

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


def build_suffix_grammar(root, wrong):
    """The suffixes migrate_reference 1.0 wrote, against the ones SPEC 3.1
    asks for. velocity_m_s matches the unit pattern and is a product."""
    write(os.path.join(root, "setup.txt"), "Case: suffixes\n")
    if wrong:
        reference = {"chord_m": 1.0, "velocity_m_s": 15.0,
                     "density_kg_m3": 1.18, "viscosity_Pa_s": 1.77e-05,
                     "q_inf_Pa": 0.5}
    else:
        reference = {"chord_m": 1.0, "velocity_m_per_s": 15.0,
                     "density_kg_per_m3": 1.18, "viscosity_pa_s": 1.77e-05,
                     "q_inf_pa": 0.5, "kinematic_viscosity_m2_per_s": 1.5e-05,
                     "re_c": 1.0e6}
    write(os.path.join(root, "summary.json"), json.dumps({
        "case": "suffixes", "reference": reference}, indent=2))


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


def diff_manifest(**overrides):
    """A diff.json that satisfies SPEC 4.7, before the overrides a fixture
    applies to break exactly one thing."""
    manifest = {
        "schema_version": "0.3",
        "base": {
            "alias": "naca0012_aoa5",
            "repo": "https://github.com/example/simulation-capsule",
            "commit": "9a805f3",
            "path": "examples/capsule_naca0012_aoa5",
            "frozen_copy_path": "examples/as-published/capsule_naca0012_aoa5",
        },
        "variant": {"capsule": "capsule_naca0012_aoa8", "case": "aoa 8 deg"},
        "difference": {
            "parameter": "angle_of_attack",
            "base_value_deg": 5.0,
            "variant_value_deg": 8.0,
            "delta_deg": 3.0,
            "implementation": "inlet direction rotated",
            "mesh_identical": True,
        },
        "view_contract": {
            "camera": "nearfield",
            "width_px": 1024,
            "export_height_px": 576,
            "artifact_height_px": 576,
            "crop_rows_top": 48,
            "colorbar_levels": 256,
            "contour_style": "banded",
            "body_fill": "magenta",
            "colormap_published": "viridis",
            "colormap_measured": "grayscale",
            "colorbar": {"cp": [-2.0, 1.0], "u_over_u": [0.0, 1.6]},
        },
        "pipeline": {
            "order": "crop_then_mask_then_diff",
            "body_dilation_px": 3,
            "colorbar_box_excluded_px": 8200,
            "pixels_evaluated": 512000,
            "tool": "diff_capsule.py",
            "tool_version": "1.0",
        },
        "pairs": [{
            "name": "cp_nearfield",
            "question": "where does the suction peak move?",
            "base_view": "view_cp_nearfield.png",
            "variant_view": "view_cp_nearfield.png",
            "output": "diff_cp_nearfield.png",
            "threshold_physical": 0.05,
            "threshold_comparison": ">=",
            "threshold_levels": 4,
            "level_size": 0.0117,
            "changed_pixel_fraction": 0.081,
            "max_delta_levels": 71,
            "max_delta_physical": 0.83,
        }],
        "scalar_deltas": {
            "delta_cl": {"base": 0.55, "variant": 0.88, "relative": 0.6},
            "delta_cm_quarter_chord": {"base": -0.003, "variant": -0.004},
        },
        "known_differences": ["render date", "solver build"],
    }
    manifest.update(overrides)
    return manifest


def build_variant(root, declare=True, manifest=True, images=True,
                  instrument=False, overrides=None):
    """A section 4 capsule that also carries the diff extension. Each flag
    removes one half of the contract so a fixture can break it."""
    write(os.path.join(root, "setup.txt"), "Case: naca0012_aoa8\n")
    summary = {
        "case": "naca0012_aoa8",
        "reference": {"chord_m": 1.0, "re_c": 1.0e6},
        "forces": {"cl": 0.88, "cd": 0.0141},
    }
    if declare:
        summary["extensions"] = ["diff"]
    write(os.path.join(root, "summary.json"), json.dumps(summary, indent=2))
    os.makedirs(os.path.join(root, "views"), exist_ok=True)
    write_png(os.path.join(root, "views", "view_cp_nearfield.png"))
    if images:
        os.makedirs(os.path.join(root, "diff"), exist_ok=True)
        write_png(os.path.join(root, "diff", "diff_cp_nearfield.png"))
    if manifest:
        write(os.path.join(root, "diff.json"),
              json.dumps(diff_manifest(**(overrides or {})), indent=2))
    if instrument:
        os.makedirs(os.path.join(root, "diffsrc"), exist_ok=True)
        write_png(os.path.join(root, "diffsrc", "gray_cp_nearfield.png"))


def status_of(root, check_id, strict=False):
    for check in check_capsule.validate(root):
        if check.id == check_id:
            return check.status(strict)
    return None


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
        variant_ok = os.path.join(workspace, "capsule_naca0012_aoa8")
        undeclared = os.path.join(workspace, "capsule_undeclared")
        promised = os.path.join(workspace, "capsule_promised")
        instrument = os.path.join(workspace, "capsule_instrument")
        bad_sign = os.path.join(workspace, "capsule_bad_sign")
        bad_colormap = os.path.join(workspace, "capsule_bad_colormap")
        zero_base = os.path.join(workspace, "capsule_zero_base")
        suffix_bad = os.path.join(workspace, "capsule_suffix_bad")
        suffix_ok = os.path.join(workspace, "capsule_suffix_ok")

        build_jet(jet_bad, True)
        build_jet(jet_ok, False)
        build_torture(torture)
        build_no_summary(anchorless)
        build_no_setup(blind)
        build_bad_name(badname)
        build_legacy(legacy)
        build_angles(angles_ok)
        build_suffix_grammar(suffix_bad, True)
        build_suffix_grammar(suffix_ok, False)
        build_variant(variant_ok)
        build_variant(undeclared, declare=False)
        build_variant(promised, manifest=False)
        build_variant(instrument, declare=False, manifest=False,
                      images=False, instrument=True)
        contract = diff_manifest()["view_contract"]
        pair = dict(diff_manifest()["pairs"][0])
        pair["threshold_comparison"] = "approx"
        build_variant(bad_sign, overrides={"pairs": [pair]})
        build_variant(bad_colormap, overrides={
            "view_contract": dict(contract, colormap_measured="viridis")})
        build_variant(zero_base, overrides={"scalar_deltas": {
            "delta_cl": {"base": 0.55, "variant": 0.88, "relative": 0.6},
            "delta_cm_quarter_chord": {"base": 0.0, "variant": 0.004,
                                       "relative": 4.0e6}}})

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
        for root in (jet_bad, torture, anchorless, blind, badname, legacy,
                     undeclared, promised, instrument, bad_sign,
                     bad_colormap):
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

        msgs = [f["message"] for f in findings(suffix_bad, "dimensional_flags")]
        wrong = ("velocity_m_s", "density_kg_m3", "viscosity_Pa_s", "q_inf_Pa")
        missed = [k for k in wrong if not any(k in m for m in msgs)]
        if missed:
            failures.append("suffix grammar not reported for %s" % missed)
        elif "dimensional_flags" in failed_checks(suffix_bad):
            failures.append("suffix grammar should warn, not fail, in normal mode")
        elif "dimensional_flags" not in failed_checks(suffix_bad, strict=True):
            failures.append("suffix grammar should fail under --strict")
        else:
            print("PASS  products for quotients and capital units warn, fail strict")
        msgs = [f["message"] for f in findings(suffix_ok, "dimensional_flags")]
        if msgs:
            failures.append("grammatical suffixes fired: %s" % msgs)
        else:
            print("PASS  _m_per_s, _kg_per_m3, _pa_s and kinematic viscosity stay silent")

        # 4. The diff extension.
        got = failed_checks(variant_ok)
        if got:
            failures.append("a declared and complete diff extension should "
                            "pass, failed %s" % sorted(got))
        else:
            print("PASS  declared and complete diff extension passes")

        # The fixture is not a git checkout, so the base commit cannot be
        # resolved here. SPEC 4.7 asks for a warning and not an error: an
        # unreachable base repository is the normal case for a capsule that
        # travels, and a validator that fails on it fails on every copy.
        left = [f for f in findings(variant_ok, "diff_manifest")]
        unresolved = [f for f in left if "could not be resolved" in
                      f["message"]]
        if len(left) != len(unresolved) or not unresolved:
            failures.append("a complete manifest should leave only the "
                            "unresolved base commit: %s"
                            % [f["message"] for f in left])
        elif unresolved[0]["severity"] != check_capsule.WARN:
            failures.append("an unreachable base repository should warn, "
                            "not error")
        else:
            print("PASS  an unreachable base repository warns, never errors")

        if "extensions" not in failed_checks(undeclared):
            failures.append("diff/ and diff.json without the declaration "
                            "should fail the extensions check")
        else:
            print("PASS  diff artifacts without the declaration fail")

        if "extensions" not in failed_checks(promised):
            failures.append("'diff' declared without diff.json should fail "
                            "the extensions check")
        elif status_of(promised, "diff_manifest") != "SKIP":
            failures.append("diff_manifest should skip when the manifest is "
                            "missing: the extensions check owns that finding")
        else:
            print("PASS  'diff' declared without diff.json fails in one "
                  "place")

        got = failed_checks(instrument)
        if "no_instrument_dir" not in got:
            failures.append("diffsrc/ inside a capsule should fail")
        elif status_of(instrument, "no_instrument_dir") == "SKIP":
            failures.append("no_instrument_dir should never skip")
        elif "declared_files" in got:
            failures.append("diffsrc/ should fail in one place, not two: %s"
                            % sorted(got))
        else:
            print("PASS  diffsrc/ fails with nothing declared, one place")

        if status_of(jet_ok, "no_instrument_dir") == "SKIP":
            failures.append("no_instrument_dir skipped on a clean capsule")
        else:
            print("PASS  no_instrument_dir never skips")

        if "diff_manifest" not in failed_checks(bad_sign):
            failures.append("threshold_comparison 'approx' should fail")
        else:
            print("PASS  a comparison sign outside >= and > fails")

        colormap = [f for f in findings(bad_colormap, "diff_manifest")
                    if "colormap_measured" in f["message"]]
        if not colormap or colormap[0]["severity"] != check_capsule.WARN:
            failures.append("colormap_measured other than grayscale should "
                            "warn, got %s" % colormap)
        elif "diff_manifest" in failed_checks(bad_colormap):
            failures.append("colormap_measured should warn, not fail")
        else:
            print("PASS  colormap_measured not grayscale warns, fails strict")

        zero = [f for f in findings(zero_base, "diff_manifest")
                if "practically zero" in f["message"]]
        if not zero or zero[0]["severity"] != check_capsule.WARN:
            failures.append("a relative delta against a zero base should "
                            "warn, got %s" % zero)
        elif any("delta_cl" in f["message"] for f in zero):
            failures.append("a relative delta against a real base fired")
        else:
            print("PASS  relative delta against a zero base warns, only it")

        if status_of(jet_ok, "extensions") != "SKIP":
            failures.append("extensions should skip on a capsule that "
                            "declares none and carries none")
        elif status_of(jet_ok, "diff_manifest") != "SKIP":
            failures.append("diff_manifest should skip when 'diff' is not "
                            "declared")
        else:
            print("PASS  a 0.2 capsule skips both diff checks, unchanged")
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
