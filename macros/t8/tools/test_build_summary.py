#!/usr/bin/env python3
# test_build_summary.py
# Synthetic signals: cl = 0.05 plus a sine of St = 0.165 and amplitude 0.33, cd = 1.33 plus a sine
# at 2 St; optionally a small linear drift and seeded Gaussian noise on both.
# dt = 0.02 from 0.02 to tU/D = 300, as export_signals writes them. Run: python3 test_build_summary.py
# Upward crossings of the sine in [100, 300] at (k - 0.3 / 2 pi) / St, k = 17 to 49: 32 periods.
# environment: an example Version: line of setup.txt and an example environment JSON, written to the
# temporary directory of each test.

import json
import math
import os
import random
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_summary  # noqa: E402

ST = 0.165
CL_AMP = 0.33
CL_MEAN = 0.05
CD_MEAN = 1.33
CD_AMP = 0.01
CELLS = 61234
N_PERIODS = 32
T_START = (17 - 0.3 / (2 * math.pi)) / ST
T_END = (49 - 0.3 / (2 * math.pi)) / ST
VERSION_LINE_TEXT = ("  Version: BuildArch: linux-x86_64-2.28 | BuildEnv: clang20.1-r8 |"
                     " PresentationVersion: 2606 | ReleaseDate: Tue May 26 16:41:56 UTC 2026 |"
                     " ReleaseNumber: 21.04.007\n")
ENV_JSON_TEXT = """{
 "environment": {
  "solver": "Simcenter STAR-CCM+",
  "version": "2606",
  "build": "21.04.007",
  "platform": "linux-x86_64-2.28",
  "precision": "double"
 }
}
"""
ENV_EXAMPLE = {"solver": "Simcenter STAR-CCM+", "version": "2606", "build": "21.04.007",
               "platform": "linux-x86_64-2.28", "precision": "double"}


def write_signals(d, cl_mean=CL_MEAN, st=ST, drift=0.0, noise=0.0):
    n = 15000
    t = [0.02 * (i + 1) for i in range(n)]
    rng = random.Random(7)
    with open(os.path.join(d, "cl.csv"), "w") as f:
        f.write("tU_D,cl\n")
        for ti in t:
            f.write("%r,%r\n" % (ti, cl_mean + 5 * drift * (ti - 200)
                                  + CL_AMP * math.sin(2 * math.pi * st * ti + 0.3)
                                  + rng.gauss(0.0, noise)))
    with open(os.path.join(d, "cd.csv"), "w") as f:
        f.write("tU_D,cd\n")
        for ti in t:
            f.write("%r,%r\n" % (ti, CD_MEAN + drift * (ti - 200)
                                  + CD_AMP * math.sin(4 * math.pi * st * ti)
                                  + rng.gauss(0.0, noise)))
    with open(os.path.join(d, "cells.csv"), "w") as f:
        f.write("cells\n%d\n" % CELLS)


class BuildSummaryTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.signals = os.path.join(self.tmp.name, "work", "signals_raw")
        os.makedirs(self.signals)
        write_signals(self.signals)
        self.version_line = os.path.join(self.tmp.name, "setup_version_line_example.txt")
        with open(self.version_line, "w") as f:
            f.write(VERSION_LINE_TEXT)
        self.env_json = os.path.join(self.tmp.name, "environment_example.json")
        with open(self.env_json, "w") as f:
            f.write(ENV_JSON_TEXT)
        self.s, self.log = self.run_summary()

    # Runs build_summary.py with extra arguments; returns summary.json and the log.
    def run_summary(self, *extra):
        out = self.tmp.name
        p = subprocess.run([sys.executable, os.path.join(HERE, "build_summary.py"),
                            "--signals", self.signals, "--out", out] + list(extra), check=True,
                           stdout=subprocess.PIPE, universal_newlines=True)
        with open(os.path.join(out, "summary.json")) as f:
            return json.load(f), p.stdout

    def tearDown(self):
        self.tmp.cleanup()

    def test_reference(self):
        r = self.s["reference"]
        self.assertEqual(r["length_scale_D_m"], 1.0)
        self.assertEqual(r["velocity_m_per_s"], 1.0)
        self.assertEqual(r["density_kg_per_m3"], 1.0)
        self.assertEqual(r["dynamic_viscosity_pa_s"], 0.01)
        self.assertEqual(r["re"], 100)

    def test_adapter_keys(self):
        self.assertEqual(self.s["mesh"]["cells"], CELLS)
        self.assertIn("cd", self.s["forces"])
        self.assertIn("cl", self.s["forces"])

    # Version: line of the example: copied as printed, BuildEnv clang20.1-r8 is double.
    def test_environment_from_setup(self):
        s, log = self.run_summary("--setup", self.version_line)
        self.assertEqual(s["environment"], ENV_EXAMPLE)
        self.assertNotIn("warning", log)
        keys = list(s)
        self.assertEqual(keys.index("environment") + 1, keys.index("forces"))
        mixed = os.path.join(self.tmp.name, "setup.txt")
        line = VERSION_LINE_TEXT.replace("clang20.1-r8", "clang20.1")
        with open(mixed, "w") as f:
            f.write("Simcenter STAR-CCM+ session\n" + line)
        self.assertEqual(build_summary.environment_from_setup(mixed)["precision"], "mixed")

    # The JSON block goes in as is and wins over --setup.
    def test_environment_from_json(self):
        env = dict(ENV_EXAMPLE, precision="single")
        path = os.path.join(self.tmp.name, "env.json")
        with open(path, "w") as f:
            json.dump({"environment": env}, f)
        s, log = self.run_summary("--setup", self.version_line, "--environment", path)
        self.assertEqual(s["environment"], env)
        s, log = self.run_summary("--environment", self.env_json)
        self.assertEqual(s["environment"], ENV_EXAMPLE)

    # Neither: five nulls, one warning line, the rest of the file unchanged.
    def test_environment_none(self):
        self.assertEqual(self.s["environment"], dict.fromkeys(
            ("solver", "version", "build", "platform", "precision")))
        warnings = [ln for ln in self.log.splitlines() if "warning" in ln]
        self.assertEqual(len(warnings), 1)
        self.assertEqual(list(self.s), ["reference", "mesh", "environment", "forces", "temporal",
                                        "window"])
        s = self.run_summary("--setup", self.version_line)[0]
        del s["environment"], self.s["environment"]
        self.assertEqual(s, self.s)

    def test_window_statistics(self):
        f = self.s["forces"]
        self.assertAlmostEqual(f["cd"], CD_MEAN, places=4)
        self.assertAlmostEqual(f["cd_rms"], CD_AMP / math.sqrt(2), places=4)
        self.assertAlmostEqual(f["cl"], CL_MEAN, places=3)
        self.assertAlmostEqual(f["cl_rms"], CL_AMP / math.sqrt(2), places=3)
        self.assertEqual(f["cd_statistic"], "time_mean")
        self.assertEqual(f["cl_rms_statistic"], "time_rms")
        self.assertNotIn("cl_rms_sd", f)
        self.assertAlmostEqual(f["cd_window_tU_over_L"][0], T_START, places=3)
        self.assertAlmostEqual(f["cd_window_tU_over_L"][1], T_END, places=3)
        self.assertEqual(f["cd_n_windows"], 1)
        self.assertTrue(f["formed"].startswith("time_mean over 32 whole periods of cl"))
        for k in ("cd", "cd_rms", "cl", "cl_rms"):
            self.assertEqual(f[k + "_n_periods"], N_PERIODS)
            self.assertAlmostEqual(f[k + "_t_start"], T_START, places=3)
            self.assertAlmostEqual(f[k + "_t_end"], T_END, places=3)

    def test_strouhal_and_amplitude(self):
        tp = self.s["temporal"]
        self.assertAlmostEqual(tp["st"], ST, places=4)
        self.assertEqual(tp["st_n_cycles"], 32)
        self.assertAlmostEqual(tp["cl_amplitude"], CL_AMP, places=3)
        self.assertAlmostEqual(tp["cl_peak_to_peak"], 2 * CL_AMP, places=3)
        self.assertEqual(tp["st_statistic"], "whole_periods_per_time")
        self.assertEqual(tp["st_n_periods"], N_PERIODS)

    def test_window_block(self):
        w = self.s["window"]
        self.assertEqual(w["search_tU_over_L"], [100.0, 300.0])
        self.assertEqual(w["n_periods"], N_PERIODS)
        self.assertGreaterEqual(w["t_start"], 100.0)
        self.assertLessEqual(w["t_end"], 300.0)
        h = w["halves"]
        self.assertEqual(h["n_periods_each"], 16)
        self.assertEqual(h["threshold"], 0.001)
        self.assertTrue(h["pass"])

    # Small drift and noise: n and St still exact to the crossings, halves under 0.1 %.
    def test_drift_and_noise(self):
        d = os.path.join(self.tmp.name, "drift")
        os.makedirs(d)
        write_signals(d, drift=2e-6, noise=5e-4)
        s = build_summary.build(d)
        w = s["window"]
        self.assertEqual(w["n_periods"], N_PERIODS)
        self.assertAlmostEqual(s["temporal"]["st"], ST, places=4)
        self.assertAlmostEqual(s["temporal"]["st"], N_PERIODS / (w["t_end"] - w["t_start"]),
                               places=12)
        self.assertAlmostEqual(s["forces"]["cd"], CD_MEAN, places=4)
        h = w["halves"]
        self.assertTrue(h["pass"], h["reason"])
        self.assertLess(h["rel_diff"]["cd_mean"], 0.001)
        self.assertLess(h["rel_diff"]["cl_rms"], 0.001)
        self.assertIsNone(h["reason"])

    # St = 0.02 leaves three whole periods in [100, 300]: halves do not pass.
    def test_too_few_periods(self):
        d = os.path.join(self.tmp.name, "slow")
        os.makedirs(d)
        write_signals(d, st=0.02)
        w = build_summary.build(d)["window"]
        self.assertEqual(w["n_periods"], 3)
        self.assertFalse(w["halves"]["pass"])
        self.assertEqual(w["halves"]["reason"], "n_periods = 3 < 4")

    # A mean above the amplitude leaves raw cl without crossings; St still comes out.
    def test_crossings_about_mean(self):
        d = os.path.join(self.tmp.name, "offset")
        os.makedirs(d)
        write_signals(d, cl_mean=0.5)
        self.assertAlmostEqual(build_summary.build(d)["temporal"]["st"], ST, places=4)

    def test_rms_is_of_fluctuation(self):
        y = [5.0 + math.sin(0.1 * i) for i in range(10000)]
        m = build_summary.mean(y)
        self.assertLess(abs(build_summary.rms_fluct(y, m) - 1 / math.sqrt(2)), 1e-2)

    def test_short_signal_rejected(self):
        with open(os.path.join(self.signals, "cl.csv")) as f:
            lines = f.readlines()[:10001]
        with open(os.path.join(self.signals, "cl.csv"), "w") as f:
            f.writelines(lines)
        with self.assertRaises(SystemExit):
            build_summary.build(self.signals)


if __name__ == "__main__":
    unittest.main()
