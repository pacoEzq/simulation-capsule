# probes

A probe test asks a model narrow questions about a capsule and checks the
answers against the capsule. It is the review step of the series, not a demo.
Every part has been through one. The object under test is the capsule, never
the model.

## Rules

1. The capsule travels whole. Where a session ran on a subset, the record says
   so and the claim narrows to match.
2. PASS or FAIL against the capsule, with the delta when a number is involved.
   Counting probes passed within a session is the result. Scoring readers
   against each other is not, and is never done.
3. Every free answer gets an audit note separating what traces to the files
   from what the reader brought in from training.
4. Two readers disagreeing about one artifact is a finding about the artifact.
5. A wrong answer from a mid tier model is ambiguous, capsule and reader being
   confounded. A wrong answer any reader would have given counts regardless.
6. Where reader and record share a family, the record says so.

## Kits are versioned by the record

Each record carries its kit verbatim, the way each capsule carries what existed
when it was built. The rules above govern how a record is written and apply to
any session, since the transcripts exist. They say nothing about how an older
kit should have looked, so no session needs re-running.

## Current template

```text
You are given a Simulation Capsule (attached, whole).

Before answering, list the files you opened.

Answer each probe using only the files it names. Keep answers to the length
requested.

Probe 1. From <file> alone: <question>. One sentence.
Probe 2. From <file> and <file> together: <question>. Three sentences.
Probe 3. From <file> alone: <question>, and state your method in one sentence.

Then, using the whole capsule, what is the most interesting thing you can tell
me about this flow?
```

The opening instruction is the newest. In Part 6 a reader answered the regime
question from the name of a view rather than from the setup report, and that
became visible only after the fact.

## Records

| Part | Capsule at the time | Readers | Record |
|---|---|---|---|
| 1 | `setup.txt` | 1 | [part-1_thermal_runaway.md](part-1_thermal_runaway.md) |
| 2 | `+ summary.json` | 1 | [part-2_naca0012_aoa5.md](part-2_naca0012_aoa5.md) |
| 3 | `+ planes/` | 1 | [part-3_cube_re200.md](part-3_cube_re200.md) |
| 4 | `+ samples.csv` | 1 | [part-4_jet_r2_re100.md](part-4_jet_r2_re100.md) |
| 5 | `+ features.json` | 1 | [part-5_delta65_a13p3_re1e6.md](part-5_delta65_a13p3_re1e6.md) |
| 6 | `+ views/` | 4 | [part-6_ahmed25_re1e6.md](part-6_ahmed25_re1e6.md) |
| 6b, run 1 | `+ diff.json`, `diff/` | 1, A | [part-6b_naca0012_aoa5_aoa7.md](part-6b_naca0012_aoa5_aoa7.md) |
| 6b, run 2 | `+ diff.json`, `diff/` | 1, B | [part-6b_naca0012_aoa5_aoa7.md](part-6b_naca0012_aoa5_aoa7.md) |

Part 6b has one row per run. Its two runs were made on different days and at
different effort settings, medium for A and Expert for B, so the record is not
a head to head. A run in A's family at high effort is pending.

Records are named `part-<N>_<case>.md`, one file per part; a part that compares
two cases names the pair in full. The dated `2026-09-02_ahmed25_re1e6.md` was
the three reader draft of the Part 6 record. The four reader record replaced
it, and the draft is gone from the tree and kept in the history.

The single reader in Parts 1 to 5, reader D in Part 6 and reader A in Part 6b
share a family with the assistant that helped write these records. The conflict is in the
judgement, not in `tools/`, which is deterministic and runnable by anyone.
Every transcript is linked for the same reason.

## Defects the probes found

| Part | What the reader found | Where it went |
|---|---|---|
| 2 | Two step limits in `setup.txt`, 3000 under the solver and 5000 under Stopping Criteria | open |
| 2 | Angle of attack in `summary.json` only, evidenced in `setup.txt` solely by the moment origin trigonometry | open |
| 3 | Transverse plane PNGs carry no spatial anchor, so their window cannot be checked against the tables | became the declared camera rule of the Part 6 view contract |
| 3 | The `y0` grid drops one node whose mirror is present, and an orphan derived part rides along | fixed |
| 4 | A trailing comma makes `summary.json` fail strict JSON | now the `json_parses` check in `tools/check_capsule.py` |
| 4 | `reference.note` calls itself the only dimensional block while `performance` is flagged too | open |
| 4 | Handedness of the triad is nowhere declared, so the sign of the vortex pair circulation rests on an assumption | open |
| 5 | The probe location of the `u_ax_core` reports is not declared | Part 7 debt |
| 6 | A view title declares field, range and orientation, and says nothing about the regime | new rule in the view contract |
| 6b | `u_over_u` is `Velocity[0] / u_ref` on laboratory x, while the variant's freestream is rotated 2 degrees; the burned title says so and the field name says otherwise | open |
| 6b | The moment origin is the quarter chord of the inclined airfoil, [0.2490487, -0.0217889, 0], with `cl` in wind axes against `cm` about laboratory z, so dCm/dCl mixes frames | open |
| 6b | `diff.json` says both cases ran in double precision on 2606; the base `setup.txt` is a 2602 win64 session | open |
| 6b | Maximum Steps is 3000 and 5000 in the base against 12000 in the variant, and `known_differences` does not say so | open |
| 6b | The inlet carries `Part Surfaces: []` in both `setup.txt`, 38 faces each, while the outlet carries `fluid.freestream` | open |
| 6b | No scalar reports backflow at the outlet, and the global mass imbalance, 1e-13, cannot | not a defect; candidate scalar for `summary.json`, minimum normal velocity on the outlet faces |
| 6b | The Cp and u/U thresholds are one cut under linearised Bernoulli, which is why 6.17% and 5.88% nearly coincide | not a defect; explains the pair of fractions |

None came from human review. The Part 4 row is why the validator exists: a
reader named the trailing comma as the one packaging change it would make, and
the capsule shipped with it anyway.
