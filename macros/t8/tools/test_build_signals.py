#!/usr/bin/env python3
# test_build_signals.py
# Synthetic signals at St = 0.165, dt = 0.02 from 0.02 to tU/D = 300, as export_signals writes them:
# cl = 0.05 + 0.33 sin(th + 0.3), cd = 1.33 + 0.01 sin(2 th), p1_v = sin(th) + 0.3 sin(2 th)
# (harmonic), p1_u, p2_u, p2_v shifted sines; optional seeded Gaussian noise (5e-4: above that the
# noise adds spurious crossings of cl, whose slope at the crossing is 0.0068 per step). Same window as
# test_build_summary: 32 whole periods in [100, 300]. Run: python3 test_build_signals.py

import json
import math
import os
import random
import subprocess
import sys
import tempfile
import unittest

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_signals  # noqa: E402

ST = 0.165
N_PERIODS = 32
T_START = (17 - 0.3 / (2 * math.pi)) / ST
T_END = (49 - 0.3 / (2 * math.pi)) / ST
PH = 0.3  # phase of cl: th = 2 pi St t; phase 0 of the average is at th + PH = 2 pi k

SHAPES = {
    "cd": lambda th: 1.33 + 0.01 * math.sin(2 * (th + PH)),
    "cl": lambda th: 0.05 + 0.33 * math.sin(th + PH),
    "p1_u": lambda th: 0.9 + 0.2 * math.cos(th + PH),
    "p1_v": lambda th: math.sin(th + PH) + 0.3 * math.sin(2 * (th + PH)),
    "p2_u": lambda th: 0.95 + 0.1 * math.sin(th + PH + 1.0),
    "p2_v": lambda th: 0.5 * math.sin(th + PH - 0.7),
}


def write_signals(d, st=ST, noise=0.0):
    n = 15000
    t = [0.02 * (i + 1) for i in range(n)]
    rng = random.Random(7)
    for name, shape in SHAPES.items():
        with open(os.path.join(d, name + ".csv"), "w") as f:
            f.write("tU_D,%s\n" % name)
            for ti in t:
                f.write("%r,%r\n" % (ti, shape(2 * math.pi * st * ti) + rng.gauss(0.0, noise)))


def read_csv(path):
    header, names, rows = [], None, []
    with open(path) as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("#"):
                header.append(line)
            elif names is None:
                names = line.split(",")
            elif line:
                rows.append([float(v) for v in line.split(",")])
    return header, names, np.array(rows)


class BuildSignalsTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.signals = os.path.join(self.tmp.name, "work", "signals_raw")
        os.makedirs(self.signals)
        write_signals(self.signals)
        self.out = self.tmp.name
        r = subprocess.run([sys.executable, os.path.join(HERE, "build_signals.py"),
                            "--signals", self.signals, "--out", self.out],
                           check=True, capture_output=True, text=True)
        self.log = r.stdout
        self.sig_dir = os.path.join(self.out, "signals")
        self.pa = read_csv(os.path.join(self.sig_dir, "phase_average.csv"))
        self.sp = read_csv(os.path.join(self.sig_dir, "spectrum.csv"))
        with open(os.path.join(self.sig_dir, "signals.json")) as f:
            self.js = json.load(f)

    def tearDown(self):
        self.tmp.cleanup()

    def test_window_is_build_summary_window(self):
        js = self.js
        a, b = js["window_tU_over_L"]
        self.assertEqual(js["reference"], "cl")
        self.assertEqual(js["search_tU_over_L"], [100.0, 300.0])
        self.assertEqual(js["n_periods"], N_PERIODS)
        self.assertAlmostEqual(a, T_START, places=3)
        self.assertAlmostEqual(b, T_END, places=3)
        self.assertAlmostEqual(js["st"], ST, places=4)
        self.assertAlmostEqual(js["st"], N_PERIODS / (b - a), places=12)

    def test_phase_average_columns_and_shape(self):
        header, names, rows = self.pa
        self.assertEqual(names, ["phase"] + [k for s in build_signals.SIGNALS
                                              for k in (s + "_mean", s + "_sd")])
        self.assertEqual(rows.shape, (32, 13))
        np.testing.assert_allclose(rows[:, 0], np.arange(32) / 32, atol=1e-6)
        self.assertIn("| 32 periods, St 0.1650 | 32 phases |", header[0])
        for k in names:
            self.assertTrue(any(l.startswith("# column %s:" % k) for l in header), k)

    # phase 0 is at the upward crossing of cl - mean(cl): th + PH = 2 pi j / 32 at phase j.
    def test_phase_average_recovers_shape(self):
        header, names, rows = self.pa
        th = 2 * math.pi * rows[:, 0] - PH
        for s, shape in SHAPES.items():
            expected = np.array([shape(x) for x in th])
            got = rows[:, names.index(s + "_mean")]
            np.testing.assert_allclose(got, expected, atol=2e-3, err_msg=s)
            self.assertLess(rows[:, names.index(s + "_sd")].max(), 1e-3, s)
        # the harmonic of p1_v is kept: the average is not a pure sine
        v = rows[:, names.index("p1_v_mean")]
        self.assertGreater(abs(v[4] + v[20]), 0.3)

    def test_phase_sd_is_cycle_spread(self):
        d = os.path.join(self.tmp.name, "noisy")
        os.makedirs(d)
        write_signals(d, noise=5e-4)
        build_signals.build(d, d, log=lambda *a: None)
        header, names, rows = read_csv(os.path.join(d, "signals", "phase_average.csv"))
        th = 2 * math.pi * rows[:, 0] - PH
        for s, shape in SHAPES.items():
            expected = np.array([shape(x) for x in th])
            np.testing.assert_allclose(rows[:, names.index(s + "_mean")], expected,
                                       atol=1e-2, err_msg=s)
            sd = rows[:, names.index(s + "_sd")]
            self.assertLess(sd.max(), 5e-3, s)   # small with small noise
            self.assertGreater(sd.mean(), 1e-4, s)  # but it is the cycle spread, not zero

    def test_spectrum_peak_and_normalisation(self):
        header, names, rows = self.sp
        self.assertEqual(names, ["St", "psd_cd", "psd_cl", "psd_p1_v"])
        st = rows[:, 0]
        self.assertLessEqual(st.max(), 2.0 + 1e-9)
        self.assertGreater(st.max(), 1.9)
        for col, var, in (("psd_cl", 0.33 ** 2 / 2), ("psd_p1_v", (1 + 0.3 ** 2) / 2)):
            psd = rows[:, names.index(col)]
            k = int(np.argmax(psd))
            self.assertEqual(k, N_PERIODS)
            self.assertAlmostEqual(st[k], ST, places=4)
            self.assertAlmostEqual(psd.sum(), var, delta=0.01 * var)
        psd = rows[:, names.index("psd_p1_v")]
        self.assertAlmostEqual(psd[2 * N_PERIODS] / psd[N_PERIODS], 0.09, places=2)
        self.assertAlmostEqual(psd[N_PERIODS], 0.5, places=2)
        self.assertNotIn("peaks", self.js["files"]["spectrum.csv"])
        self.assertIn("peak at St = 0.165", self.log)
        self.assertIn("window St = n/(t_end - t_start)", self.log)
        # cd oscillates at twice the shedding frequency: its line falls on bin 2n
        psd = rows[:, names.index("psd_cd")]
        k = int(np.argmax(psd))
        self.assertEqual(k, 2 * N_PERIODS)
        self.assertAlmostEqual(st[k], 2 * ST, places=4)
        self.assertAlmostEqual(psd.sum(), 0.01 ** 2 / 2, delta=0.01 * 0.01 ** 2 / 2)
        self.assertLess(psd[N_PERIODS], 1e-3 * psd[k])
        self.assertIn("build_signals: cd peak at St = 0.33 (bin %d), 2 x window St"
                      % (2 * N_PERIODS), self.log)
        self.assertIn("(bin %d), diff = 0 bins" % (2 * N_PERIODS), self.log)

    # First line in the written form of SPEC 4.9.1: a, b with 3 decimals, St with 4.
    def test_headers_declare_formation(self):
        first = "# signals over tU/D %.3f to %.3f | 32 periods, St %.4f | " % (T_START, T_END, ST)
        self.assertEqual(self.pa[0][0], first + "32 phases | nondimensional")
        self.assertEqual(self.sp[0][0], first + "st_max 2 | nondimensional")
        for header, names, rows in (self.pa, self.sp):
            self.assertTrue(header[1].startswith("# window: 32 whole periods of cl"))
            cols = [l for l in header if l.startswith("# column ")]
            self.assertEqual([l.split(":")[0][len("# column "):] for l in cols], names)
        with open(os.path.join(self.sig_dir, "spectrum.csv")) as f:
            data = [l for l in f if not l.startswith("#")][1:]
        for v in data[1].strip().split(","):
            digits = v.replace("-", "").replace(".", "").split("e")[0].lstrip("0")
            self.assertLessEqual(len(digits), 6, v)

    # Flat JSON with exactly the keys of SPEC 4.9.1; columns repeat the header text column by column.
    def test_signals_json(self):
        js = self.js
        self.assertEqual(list(js), ["reference", "search_tU_over_L", "window_tU_over_L",
                                    "n_periods", "st", "phases", "st_max", "signals", "files"])
        self.assertEqual(js["phases"], 32)
        self.assertEqual(js["st_max"], 2.0)
        self.assertEqual(js["signals"], ["cd", "cl", "p1_v"])  # carried by both files
        self.assertEqual(self.pa[1], ["phase"] + [k for s in build_signals.SIGNALS
                                                   for k in (s + "_mean", s + "_sd")])
        self.assertEqual(self.sp[1], ["St"] + ["psd_" + s for s in js["signals"]])
        self.assertEqual(set(js["files"]), {"phase_average.csv", "spectrum.csv"})
        for fname, (header, names, rows) in (("phase_average.csv", self.pa),
                                             ("spectrum.csv", self.sp)):
            f = js["files"][fname]
            self.assertEqual(set(f), {"rows", "columns"})
            self.assertEqual(f["rows"], len(rows))
            self.assertEqual(list(f["columns"]), names)
            for k, text in f["columns"].items():
                self.assertIn("# column %s: %s" % (k, text), header)

    # The header names quantities, never the instrument that formed them.
    def test_headers_name_no_tools(self):
        for header, names, rows in (self.pa, self.sp):
            for line in header:
                for bad in ("numpy", "build_summary", "("):
                    self.assertNotIn(bad, line, line)

    def test_spectrum_unit(self):
        t = np.arange(1, 4001) * 0.02
        y = 2.0 + 0.5 * np.sin(2 * np.pi * 0.25 * t)
        st, psd, total = build_signals.spectrum(t, y, 4.0, 44.0)  # 10 periods of 4
        self.assertEqual(int(np.argmax(psd)), 10)
        self.assertAlmostEqual(st[10], 0.25, places=12)
        self.assertAlmostEqual(total, 0.125, places=6)
        self.assertAlmostEqual(psd[10], 0.125, places=6)


if __name__ == "__main__":
    unittest.main()
