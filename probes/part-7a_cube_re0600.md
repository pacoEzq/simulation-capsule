# Probe record: Part 7a, capsule_cube_re0600 predicted before it was built

Two readers from two families received the four cube capsules of the Reynolds
sweep as one archive, with no other context and no network, and were asked what
a fifth capsule, at Re = 600, would contain. The run had not started. Their
answers were stored before the point was built, and the capsule was graded
against them afterwards.

Both held the interval on all six numeric fields, named the status, got the
side of the wake deficit right on the second plane and wrong on the first, and
answered the four provenance questions correctly. Each scored 12 of 13, with
the same miss. The interpolation in log Re that serves as the naive reference
was outside the chain's reach on the iteration count and missed both wake
sides.

The two runs are not a head to head. A ran at maximum effort and B in Expert
mode, on the same day, under the same kit.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.1 | maximum effort | 2026-10-07 | [shared](https://claude.ai/share/72695dbd-d85f-4797-a34a-f9e601814c05) |
| B | xAI Grok | 4.7 | Expert | 2026-10-07 | [shared](https://grok.com/share/c2hhcmQtMg_042c6426-45f5-41a0-9bb0-a7e9becf78c9) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked, and here they cannot be: the tiers differ.

The probe is also reported in Appendix B of the [Part 7a
post](https://community.sw.siemens.com/s/question/0D5Vb00001dUIzpKAG/preparing-cfd-output-for-large-language-models-7a10-automation-the-macro-that-builds-the-capsule).

## The kit as issued

Kit v4. One archive, `probe_inputs.zip`: the four capsules `capsule_cube_re0100`,
`capsule_cube_re0300`, `capsule_cube_re1000` and `capsule_cube_re3000` as they
stood at `de2a834`, 54 files, 2,382,824 bytes. The prompt travelled with the
archive; one follow-up was allowed, only if the format was missing, and neither
reader used it. The expected answers and tolerances the operator graded against
were never shown to the reader and are not reproduced.

A first attempt under kit v3 was voided before any data existed. The chain at
`7ff5be1` accepted only Re 100, 300, 1000 and 3000, so a fifth point could not
be built, and the predictions of that attempt were not reused. Kit v4 declares
the chain opened to any point at `6ef427e` and redoes the control questions C1
to C4 for an open sweep; P1 to P9, the naive reference and the follow-up did not
change.

````markdown
The attached `probe_inputs.zip` contains four Simulation Capsules from one parameter sweep: `capsule_cube_re0100`, `capsule_cube_re0300`, `capsule_cube_re1000` and `capsule_cube_re3000`. Each capsule is a self-contained, token-budgeted distillation of one CFD case (a cube in a uniform stream), built for a language model to read. Read every file in all four before you answer.

A fifth point of the same sweep will be run at Re = 600. It uses the same template and the same mesh as the four capsules. The automation chain is the one that built the four capsules, extended at a new commit to accept new points. Only the Re 600 point is run and built; the four existing capsules are not rebuilt. Its baseline is Re 100. The run has not started. Predict what its capsule, `capsule_cube_re0600`, will contain.

Rules:

1. Work only from the four capsules and your own knowledge, with no connectors. You may run code on the attached files; show it. Do not access the network. Show the arithmetic behind any number you compute. Do not ask questions; answer in one message.
2. Every numeric prediction has a point value and an interval `[low, high]` that you believe contains the value the capsule will publish. A narrow interval is worth more only if it is right.
3. Tag every supporting statement *capsule* (it rests on a file you read; name the file and field) or *prior knowledge* (it rests on anything else).
4. If you cannot predict a field, say so and give your widest honest interval anyway. Do not leave a field out.

Predict these fields:

| # | Field | File | Kind |
|---|---|---|---|
| P1 | `convergence.status` | `summary.json` | one value, exact |
| P2 | `convergence.iterations` | `summary.json` | integer, point + interval |
| P3 | `forces.cd` | `summary.json` | point + interval |
| P4 | `forces.cl` | `summary.json` | point + interval |
| P5 | `forces.cy` | `summary.json` | point + interval |
| P6 | `pressure.cp_base` | `summary.json` | point + interval |
| P7 | `wake.recirculation_length_D` | `summary.json` | point + interval |
| P8 | Wake deficit side on plane x1: the signs of `y_D` and `z_D` at the node with the lowest `u_over_U` in `planes/plane_x1.csv`, each one of `-`, `0`, `+` | `planes/plane_x1.csv` | sign pair, exact |
| P9 | Same as P8 on plane x2 | `planes/plane_x2.csv` | sign pair, exact |

Control questions, about provenance rather than physics. For `capsule_cube_re0600`:

- C1. Will `manifest.json` `chain.repo.commit` be the same as in the four capsules, or different?
- C2. `manifest.json` `sweep.values` and `sweep.siblings`.
- C3. What `diff.json` will say about its base: `base.capsule` and `base.commit`.
- C4. `summary.json` `mesh.cells` and `mesh.identical_across_sweep`.

Answer format: first a fenced `json` block with exactly the shape below; then the code you ran and the arithmetic behind each computed number; then at most 15 lines of justification, one per field, each line tagged *capsule* or *prior knowledge*.

```json
{
 "P1": {"value": ""},
 "P2": {"value": 0, "interval": [0, 0]},
 "P3": {"value": 0.0, "interval": [0.0, 0.0]},
 "P4": {"value": 0.0, "interval": [0.0, 0.0]},
 "P5": {"value": 0.0, "interval": [0.0, 0.0]},
 "P6": {"value": 0.0, "interval": [0.0, 0.0]},
 "P7": {"value": 0.0, "interval": [0.0, 0.0]},
 "P8": {"y_sign": "", "z_sign": ""},
 "P9": {"y_sign": "", "z_sign": ""},
 "C1": {"same_as_capsules": true},
 "C2": {"values": [], "siblings": []},
 "C3": {"base_capsule": "", "base_commit": ""},
 "C4": {"cells": 0, "identical_across_sweep": true}
}
```
````

The follow-up, never used:

````text
Please resend your answer in the requested format: the json block with all thirteen keys P1 to P9 and C1 to C4, every numeric field with value and interval, then at most 15 justification lines tagged *capsule* or *prior knowledge*. Do not change any prediction you already gave.
````

The kit was frozen with the sha256 of each of its files, so a later holder can
check that nothing changed after the readers ran. The grader and the naive
reference are kept with the project records.

| File | sha256 |
|---|---|
| `probe_inputs.zip` | `66e75fbc5a07115b0bb1d3798580d84b819a7fdd8647c9c028ae28070fd7a583` |
| `probe_prompt.md` | `9373ded9f44739718f883ffafb1a9bf507405bf37c6b121566dc0e2db76ef328` |
| `probe_followup.md` | `f0fb923e137a2f44014dbcdfb5ac2d79d571dafc5e8b0693d1a5d4561a98175e` |
| `probe_rubric.md` | `e3cfce48b120dbdd8dc088b9797ec3067dab8109737845d9f9a739373897c13c` |
| `naive_reference.json` | `503b2bd649b4ae1631e8fbfb4f903d3969d223b4321cd36357b17e5ef353183f` |
| `grade.py` | `e8869b63eec241ffc088eb119deed7afbe42e1b9edae0b650cab2f26e18910fc` |

The naive reference is a linear interpolation in log Re between Re 300 and
Re 1000 for the numeric fields, with weight 0.575717 on Re 1000, and the
nearest point in log Re for the categories and the wake sides.

Order of events, UTC, 2026-10-07: prompt and grader frozen at 14:55, answers
stored at 15:17 (A) and 15:19 (B), driver first started at 15:22. The graded
run started at 15:51.

## Results

| | A | B |
|---|---|---|
| P1, status | PASS | PASS |
| P2 to P7, intervals | 6 of 6 | 6 of 6 |
| P8, wake side on x1 | FAIL | FAIL |
| P9, wake side on x2 | PASS | PASS |
| C1 to C4, provenance | 4 of 4 | 4 of 4 |
| Total | 12 of 13 | 12 of 13 |

The values, against what `capsule_cube_re0600` published:

```text
field        measured   A                     B                     naive
status       stationary stationary            stationary            stationary
iterations   5000       5000 [5000, 15000]    5000 [5000, 15000]    9243
cd           0.98690    0.97 [0.86, 1.09]     0.98 [0.90, 1.06]     0.98990
cl           0.00956    0.0 [-0.05, 0.05]     0.0 [-0.02, 0.02]     0.00079
cy          -0.00288    0.0 [-0.05, 0.05]     0.0 [-0.02, 0.02]    -0.00435
cp_base     -0.31373   -0.28 [-0.39, -0.17]  -0.30 [-0.40, -0.20]  -0.29903
recirc. L/D  2.40391    2.40 [2.05, 2.95]     2.36 [2.15, 2.55]     2.35780
wake x1      (-, +)     (0, -)                (0, -)                (0, +)
wake x2      (0, -)     (0, -)                (0, -)                (+, -)
```

The naive reference hits P1 and misses both wake sides. Its P2 is a count the
chain cannot produce.

**Numeric fields.** Every interval held the value. On the point alone, A was
closer than the naive reference on P2 and P7, B on P2, P6 and P7, and the
naive reference on the others, including all of P3, P4 and P5 for both.

**Wake sides.** Both missed x1 and hit x2; the naive reference missed both.
Both said before the run that the sign was not identified by the files, and A
put its own chance on x1 at about 15 percent.

**Provenance.** Both answered that the manifest would name another commit than
the four capsules, list five values and four siblings, pin the base to the
frozen Re 100 capsule at `ee7ff59`, and declare 501,860 cells, identical
across the sweep. The capsule names the chain at `ccaf03d`.

## Findings

**1. The iteration count follows from a rule, not from a trend.** Interpolation
gives 9,243, a number the chain cannot produce: after discarding 1,000
iterations the gate checks at the end of each window of 2,000, so it stops at
3,000, 5,000, 7,000 and so on up to the cap of 15,000. Both readers read that
rule in `summary.json` and answered 5,000, the first check that compares two
windows, with the cap as the far end of the interval. A recomputed the
gate's drift over standard error from the published window statistics and
matched them to three decimals. From A and B.

**2. A length is predictable when its definition travels.** A read in
`setup.txt` that the chain measures the recirculation length from the farthest
cell with reversed flow, and answered 2.40 against 2.404. B answered 2.36.
From A.

**3. Where two capsules bracket a smooth trend, arithmetic is enough.** On cd,
cl and cy the interpolation's point was closer than either reader's, and B's
own numbers there were interpolations in log Re. What a reader adds is an
honest interval. Not a defect; a limit on what a prediction probe can show.
From the grading.

**4. The side of the wake deficit is not in the files.** Each plane table holds
the last iteration of a wake that does not settle, so the side of the velocity
minimum belongs to that instant, and the time-averaged field is not published.
Both readers said so before the run. The miss on x1 and the hit on x2 are what
a guess does. Under rule 5 this is a miss any reader would have given. From A
and B.

**5. Provenance in an open sweep is readable.** All eight answers to C1 to C4
were right, from a manifest that records the sweep as it stood at the build and
a `diff.json` that names its base. From A and B.

### Faults found by the run, not by the readers

Three faults surfaced when the point was built, and none could have been seen
from the four capsules. The chain kept its regimes in a closed dictionary and
refused any fifth point; `6ef427e` opened it. The template still held objects
from four cut views retired in an earlier version of the specification, and
the run stopped on 16 unlisted objects; `ccaf03d` removes them from each
point's copy. The run folder held a stale build of the base capsule, and
`check_capsule.py --strict` turns an unreachable base into a failure outside
the clone. They belong to the chain, not to the capsule, and are listed here
because the first one voided the first attempt.

## Audit note

Both answers were audited from the stored verbatim texts, not from the live
chats.

A tagged prior knowledge in P1, P6, P8 and P9: that the cube wake loses planar
symmetry and turns irregular by about Re 350 (Klotz et al. 2014; Meng et al.
2021), that base suction deepens as the wake becomes chaotic, that the same
mesh, partition and initial field seed the first symmetry breaking, and that
the deflection in a hairpin wake persists over more than one diameter. Left
untagged: the scenario weights 0.35, 0.35 and 0.30 and the three cp_base values
behind them, which the text sends to a justification that does not give them;
the front cp of 0.713 held constant above Re 300; and the assumption that the
four old manifests are not rebuilt. The confidence on P1 was stated, about 65
percent.

B tagged prior knowledge in P1, P7 and C1: that the cube wake is unsteady by
Re 600, that the recirculation peaks near the periodic regime and then
shortens, and that the chain is extended at a new commit. Left untagged: the
bounds of 0.90 to 1.06 on P3 and of 2.15 to 2.55 on P7, which have no basis in
the text, and the extrapolation of a zero bias at Re 100 to Re 600 in P5. The
answer contains two JSON blocks, the first the empty template and the second
the predictions.

One inconsistency in A: a friction value quoted as about -0.022 where its own
code gives -0.0234.

## Conflict of interest

Reader A is a Claude model, and this record was written with the assistance of
a Claude model. The probes were issued, the sessions run and the scoring
approved by the author. The transcripts are linked, and every measured figure
above is checkable against `examples/capsule_cube_re0600/`: the `summary.json`
and `manifest.json` published there carry the sha256 prefixes of the graded
files, `39f38ccc` and `0b113dbc`.
