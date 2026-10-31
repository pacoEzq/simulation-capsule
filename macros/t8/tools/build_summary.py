#!/usr/bin/env python3
# build_summary.py
# Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1). Reads the CSVs left by
# export_signals in work/signals_raw/ (cd.csv, cl.csv, cells.csv) and writes summary.json in --out.
# Inside tU/D in [t0, t1] the upward crossings of cl - mean(cl) fix a window of n whole periods,
# from the first crossing at or after t0 to the last at or before t1. On that window: mesh.cells,
# forces.cd and forces.cl (time_mean, time_rms of the fluctuation), a temporal block with St = n /
# (t_end - t_start) and the cl amplitude, and a window block comparing the first n//2 periods with
# the last n//2. reference holds the unit values the run was set up with. environment (SPEC 4.2.1)
# comes from --environment (a JSON with the block), else from the Version: line of --setup, else
# five nulls and a warning in the log. Standard library only.

import argparse
import bisect
import csv
import json
import math
import os
import re
import sys

T0 = 100.0
T1 = 300.0
HALVES_TOL = 0.001
MIN_PERIODS = 4

# Run set up with rho = U = D = 1 and mu = 0.01, so re = rho U D / mu = 100.
REFERENCE = {
    "length_scale_D_m": 1.0,
    "velocity_m_per_s": 1.0,
    "density_kg_per_m3": 1.0,
    "dynamic_viscosity_pa_s": 0.01,
    "re": 100,
    "note": "Only dimensional block in this file. Unit values: D is the cylinder diameter and"
            " re = rho U D / mu. All other scalars are nondimensional groups.",
}

SOLVER = "Simcenter STAR-CCM+"
ENV_KEYS = ("solver", "version", "build", "platform", "precision")
VERSION_FIELDS = (("version", "PresentationVersion"), ("build", "ReleaseNumber"),
                  ("platform", "BuildArch"))


# environment from the Version: line of setup.txt, recipe of SPEC 4.2.1: PresentationVersion,
# ReleaseNumber and BuildArch copied as printed; BuildEnv ending in -r8 is double, else mixed.
def environment_from_setup(path):
    with open(path) as f:
        lines = [m.group(1) for m in (re.match(r"\s*Version:\s*(.*)", ln) for ln in f) if m]
    if not lines:
        raise SystemExit("build_summary: no Version: line in %s." % path)
    fields = {}
    for item in lines[0].split("|"):
        key, sep, value = item.partition(":")
        if sep:
            fields[key.strip()] = value.strip()
    need = [k for k in ("PresentationVersion", "ReleaseNumber", "BuildArch", "BuildEnv")
            if not fields.get(k)]
    if need:
        raise SystemExit("build_summary: Version: line of %s lacks %s." % (path, ", ".join(need)))
    env = {"solver": SOLVER}
    for key, field in VERSION_FIELDS:
        env[key] = fields[field]
    env["precision"] = "double" if fields["BuildEnv"].endswith("-r8") else "mixed"
    return env


# environment block taken as is from a JSON file holding {"environment": {...}}.
def environment_from_json(path):
    with open(path) as f:
        data = json.load(f)
    if not isinstance(data, dict) or not isinstance(data.get("environment"), dict):
        raise SystemExit("build_summary: %s has no environment object." % path)
    return data["environment"]


# --environment over --setup; with neither, five nulls and a warning, never assumed values.
def environment(setup=None, env_json=None):
    if env_json:
        return environment_from_json(env_json), None
    if setup:
        return environment_from_setup(setup), None
    return dict.fromkeys(ENV_KEYS), ("build_summary: warning: no --setup or --environment,"
                                     " environment written with null values.")


def read_signal(path):
    with open(path, newline="") as f:
        rows = list(csv.reader(f))
    if len(rows) < 2 or len(rows[0]) != 2 or rows[0][0] != "tU_D":
        raise SystemExit("build_summary: %s is not a tU_D,<col> signal." % path)
    t = [float(r[0]) for r in rows[1:] if r]
    y = [float(r[1]) for r in rows[1:] if r]
    return t, y


def read_cells(path):
    with open(path, newline="") as f:
        rows = [r for r in csv.reader(f) if r]
    if len(rows) != 2 or rows[0] != ["cells"]:
        raise SystemExit("build_summary: %s is not a cells file." % path)
    return int(round(float(rows[1][0])))


# Samples with t0 <= t <= t1; the signal must cover the whole window.
def window(t, y, t0, t1):
    dt = t[1] - t[0]
    tol = 1e-6 * dt
    if t[0] > t0 + 0.5 * dt or t[-1] < t1 - 0.5 * dt:
        raise SystemExit("build_summary: signal spans tU/D %g to %g, window is %g to %g."
                         % (t[0], t[-1], t0, t1))
    pairs = [(ti, yi) for ti, yi in zip(t, y) if t0 - tol <= ti <= t1 + tol]
    return [p[0] for p in pairs], [p[1] for p in pairs]


def mean(y):
    return math.fsum(y) / len(y)


# Root mean square of the fluctuation about the mean, not of the raw signal.
def rms_fluct(y, m):
    return math.sqrt(math.fsum((v - m) ** 2 for v in y) / len(y))


# Times of the upward zero crossings, linearly interpolated between samples.
def upward_crossings(t, y):
    out = []
    for i in range(1, len(y)):
        if y[i - 1] < 0.0 <= y[i]:
            out.append(t[i - 1] + (t[i] - t[i - 1]) * (-y[i - 1]) / (y[i] - y[i - 1]))
    return out


# Signal on [a, b]: values interpolated at a and b plus the samples strictly between.
def segment(t, y, a, b):
    def at(x):
        i = min(max(bisect.bisect_left(t, x), 1), len(t) - 1)
        return y[i - 1] + (y[i] - y[i - 1]) * (x - t[i - 1]) / (t[i] - t[i - 1])
    lo = bisect.bisect_right(t, a)
    hi = bisect.bisect_left(t, b)
    return [a] + t[lo:hi] + [b], [at(a)] + y[lo:hi] + [at(b)]


def trapz(t, y):
    return math.fsum(0.5 * (y[i] + y[i - 1]) * (t[i] - t[i - 1]) for i in range(1, len(t)))


# time_mean and time_rms of the fluctuation over [a, b], trapezoidal in time.
def period_stats(t, y, a, b):
    ts, ys = segment(t, y, a, b)
    m = trapz(ts, ys) / (b - a)
    r = math.sqrt(trapz(ts, [(v - m) ** 2 for v in ys]) / (b - a))
    return m, r


# Peak-to-peak value of each full cycle between upward crossings, averaged.
def peak_to_peak(t, y, crossings):
    p2p = []
    for a, b in zip(crossings[:-1], crossings[1:]):
        ys = segment(t, y, a, b)[1]
        p2p.append(max(ys) - min(ys))
    return mean(p2p)


# Half the peak-to-peak value of each full cycle between upward crossings, averaged.
def amplitude(t, y, crossings):
    return 0.5 * peak_to_peak(t, y, crossings)


def formed(name, statistic, a, b, n):
    return {
        name + "_statistic": statistic,
        name + "_t_start": a,
        name + "_t_end": b,
        name + "_n_periods": n,
    }


def window_stats(name, m, r, a, b, n):
    out = {
        name: m,
        name + "_sd": r,
        name + "_window_tU_over_L": [a, b],
        name + "_n_windows": 1,
        name + "_rms": r,
        name + "_rms_window_tU_over_L": [a, b],
        name + "_rms_n_windows": 1,
    }
    out.update(formed(name, "time_mean", a, b, n))
    out.update(formed(name + "_rms", "time_rms", a, b, n))
    return out


def rel_diff(first, second, whole):
    return abs(second - first) / abs(whole) if whole else float("inf")


# First n//2 periods against the last n//2: relative change of cd mean and cl rms.
def halves(t_cd, cd, t_cl, cl, crossings, cd_mean, cl_rms):
    n = len(crossings) - 1
    out = {"n_periods_each": n // 2, "threshold": HALVES_TOL, "first": None, "second": None,
           "rel_diff": None, "pass": False, "reason": None}
    if n < MIN_PERIODS:
        out["reason"] = "n_periods = %d < %d" % (n, MIN_PERIODS)
        return out
    h = n // 2
    for key, (a, b) in (("first", (crossings[0], crossings[h])),
                        ("second", (crossings[n - h], crossings[n]))):
        out[key] = {"t_start": a, "t_end": b,
                    "cd_mean": period_stats(t_cd, cd, a, b)[0],
                    "cl_rms": period_stats(t_cl, cl, a, b)[1]}
    d = {q: rel_diff(out["first"][q], out["second"][q], w)
         for q, w in (("cd_mean", cd_mean), ("cl_rms", cl_rms))}
    out["rel_diff"] = d
    out["pass"] = d["cd_mean"] < HALVES_TOL and d["cl_rms"] < HALVES_TOL
    if not out["pass"]:
        out["reason"] = "rel_diff cd_mean %.3g, cl_rms %.3g, threshold %g" % (
            d["cd_mean"], d["cl_rms"], HALVES_TOL)
    return out


# Window of whole periods of cl inside [t0, t1]: upward crossings of cl - mean(cl), with mean(cl)
# over the samples in [t0, t1]. Returns (crossings, n_periods, t_start, t_end).
def period_window(t_cl, cl, t0, t1):
    ts, ys = window(t_cl, cl, t0, t1)
    crossings = upward_crossings(ts, [v - mean(ys) for v in ys])
    if len(crossings) < 2:
        raise SystemExit("build_summary: fewer than two upward zero crossings of cl - mean(cl)"
                         " in tU/D %g to %g." % (t0, t1))
    return crossings, len(crossings) - 1, crossings[0], crossings[-1]


def build(signals_dir, t0=T0, t1=T1, env=None):
    span = "in tU/D %g to %g" % (t0, t1)
    cells = read_cells(os.path.join(signals_dir, "cells.csv"))
    t_cd, cd = read_signal(os.path.join(signals_dir, "cd.csv"))
    t_cl, cl = read_signal(os.path.join(signals_dir, "cl.csv"))
    window(t_cd, cd, t0, t1)
    crossings, n, a, b = period_window(t_cl, cl, t0, t1)
    whole = "%d whole periods of cl, tU/D %.6g to %.6g" % (n, a, b)

    cd_mean, cd_rms = period_stats(t_cd, cd, a, b)
    cl_mean, cl_rms = period_stats(t_cl, cl, a, b)
    forces = {}
    forces.update(window_stats("cd", cd_mean, cd_rms, a, b, n))
    forces.update(window_stats("cl", cl_mean, cl_rms, a, b, n))
    forces["formed"] = "time_mean over " + whole

    p2p = peak_to_peak(t_cl, cl, crossings)
    temporal = {
        "st": n / (b - a),
        "st_n_cycles": n,
        "st_window_tU_over_L": [a, b],
        "st_method": "n / (t_end - t_start), upward crossings of cl - mean(cl) " + span,
        "cl_amplitude": 0.5 * p2p,
        "cl_amplitude_window_tU_over_L": [a, b],
        "cl_amplitude_method": "mean over whole periods of half the peak-to-peak cl",
        "cl_peak_to_peak": p2p,
        "formed": "upward crossings of cl - mean(cl) " + span + ", " + whole,
    }
    temporal.update(formed("st", "whole_periods_per_time", a, b, n))
    temporal.update(formed("cl_amplitude", "mean_half_peak_to_peak", a, b, n))
    temporal.update(formed("cl_peak_to_peak", "mean_peak_to_peak", a, b, n))

    return {
        "reference": dict(REFERENCE),
        "mesh": {"cells": cells},
        "environment": env if env is not None else dict.fromkeys(ENV_KEYS),
        "forces": forces,
        "temporal": temporal,
        "window": {
            "search_tU_over_L": [t0, t1],
            "method": "first upward crossing of cl - mean(cl) at or after t0 to the last at or"
                      " before t1; mean(cl) over the samples in [t0, t1]",
            "t_start": a,
            "t_end": b,
            "n_periods": n,
            "halves": halves(t_cd, cd, t_cl, cl, crossings, cd_mean, cl_rms),
        },
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description="Write summary.json from work/signals_raw/.")
    ap.add_argument("--signals", default=os.path.join("work", "signals_raw"))
    ap.add_argument("--out", required=True, help="folder of the .sim; summary.json goes here")
    ap.add_argument("--t0", type=float, default=T0)
    ap.add_argument("--t1", type=float, default=T1)
    ap.add_argument("--setup", help="setup.txt of the run; environment from its Version: line")
    ap.add_argument("--environment", help="JSON with the environment block; wins over --setup")
    args = ap.parse_args(argv)

    env, warning = environment(args.setup, args.environment)
    if warning:
        print(warning)
    summary = build(args.signals, args.t0, args.t1, env)
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, "summary.json")
    with open(path, "w") as f:
        json.dump(summary, f, indent=2)
        f.write("\n")
    print("build_summary: wrote " + path)


if __name__ == "__main__":
    main(sys.argv[1:])
