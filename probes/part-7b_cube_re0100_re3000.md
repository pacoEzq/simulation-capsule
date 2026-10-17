# Probe record: Part 7b, the four cube capsules from Re 100 to Re 3000

Two readers from two families received the four cube capsules as one archive,
with no other context and no network, and were asked for something no file in
them records: the side force `cl` and `cy` at the single iteration the planes
show. In three of the four capsules the published `cl` and `cy` are means over
many iterations, with magnitudes under 0.01 and standard deviations from 0.036
to 0.17, while the planes are one instant. The solver recorded the instant
values; the readers never received them.

Both held all three intervals and gave the iteration of every capsule,
3000, 15000, 5000 and 5000, from different files. A got all eight signs right
and B six. Taking the summary at face value, the naive reading, got three of
eight and was 9.5 to 43 times low on every magnitude. Both readers said that no
field of the capsule ties the planes to an iteration.

The two runs are not a head to head. A ran at maximum effort and B in Expert
mode, in separate new chats on the same day, under the same kit.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.1 | maximum effort | 2026-10-09 | [shared](https://claude.ai/share/77d9c15f-e444-4f21-93de-e191b5518626) |
| B | xAI Grok | 4.7 | Expert | 2026-10-09 | [shared](https://grok.com/share/c2hhcmQtMg_9317e835-aee4-4d9c-899c-29cf596f124b) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked, and here they cannot be: the tiers differ.

The probe is also reported in Appendix B of the [Part 7b
post](https://community.sw.siemens.com/s/question/0D5Vb00001dtHuCKAU/preparing-cfd-output-for-large-language-models-7b10-four-reynolds-numbers-the-cube-that-would-not-sit-still).

## The kit as issued

Kit v2, frozen on 2026-10-08. One archive, `probe_inputs.zip`: the four capsules
`capsule_cube_re0100`, `capsule_cube_re0300`, `capsule_cube_re1000` and
`capsule_cube_re3000` as they stood at `de2a834`, 54 files, 2,382,824 bytes, the
same archive as in Part 7a. The sha256 of the archive was checked before it was
uploaded. The prompt travelled with the archive; one follow-up was allowed, only
if the format was missing. The expected answers and tolerances I graded against
were never shown to the reader and are not reproduced.

An earlier single-reader probe of 2026-10-02 (kit v1, three questions about the
pedigree of the numbers) was retired before this kit and is not part of this
record.

````markdown
The attached `probe_inputs.zip` contains four Simulation Capsules from one parameter sweep: `capsule_cube_re0100`, `capsule_cube_re0300`, `capsule_cube_re1000` and `capsule_cube_re3000`. Each capsule is a self-contained, token-budgeted distillation of one CFD case (a cube in a uniform stream), built for a language model to read. Read every file in all four before you answer.

In each capsule the files in `planes/` show the flow at a single iteration of the run. The side-force coefficients in `summary.json`, `cl` along z and `cy` along y as `setup.txt` defines them, are not values at that iteration: for three of the four points they are means over many iterations. The solver recorded `cl` and `cy` at the iteration of the planes, and those values are not in the capsules. Infer them from the flow in the planes.

Rules:

1. Work only from the four capsules and your own knowledge, with no connectors. You may run code on the attached files; show it. Do not access the network. Show the arithmetic behind any number you compute. Do not ask questions; answer in one message.
2. Every numeric answer has a point value and an interval `[low, high]` that you believe contains the recorded value. A narrow interval is worth more only if it is right.
3. Tag every supporting statement *capsule* (it rests on a file you read; name the file and field) or *prior knowledge* (it rests on anything else).
4. If you cannot answer a field, say so and give your widest honest answer anyway. Do not leave a field out.

Answer these, all at the iteration of the planes:

| # | Field | Kind |
|---|---|---|
| S1 | Re 100: sign of `cy` and sign of `cl`, each `-`, `0` or `+`; `0` means a magnitude below 0.005 | sign pair, exact |
| S2 | Same for Re 300 | sign pair, exact |
| S3 | Same for Re 1000 | sign pair, exact |
| S4 | Same for Re 3000 | sign pair, exact |
| M1 | Re 300: magnitude of the side force, sqrt(cl^2 + cy^2) | point + interval |
| M2 | Same for Re 1000 | point + interval |
| M3 | Same for Re 3000 | point + interval |

Control question, about provenance rather than physics:

- C1. At which iteration were the planes of each capsule taken, and which file or field says so?

Answer format: first a fenced `json` block with exactly the shape below; then the code you ran and the arithmetic behind each computed number; then at most 12 lines of justification, one per field, each line tagged *capsule* or *prior knowledge*.

```json
{
 "S1": {"cy_sign": "", "cl_sign": ""},
 "S2": {"cy_sign": "", "cl_sign": ""},
 "S3": {"cy_sign": "", "cl_sign": ""},
 "S4": {"cy_sign": "", "cl_sign": ""},
 "M1": {"value": 0.0, "interval": [0.0, 0.0]},
 "M2": {"value": 0.0, "interval": [0.0, 0.0]},
 "M3": {"value": 0.0, "interval": [0.0, 0.0]},
 "C1": {"re0100": 0, "re0300": 0, "re1000": 0, "re3000": 0, "source": ""}
}
```
````

The follow-up, not needed by either reader:

````text
Please resend your answer in the requested format: the json block with all eight keys S1 to S4, M1 to M3 and C1, every M field with value and interval, then at most 12 justification lines tagged *capsule* or *prior knowledge*. Do not change any answer you already gave.
````

The kit was frozen with the sha256 of each of its files, so a later holder can
check that nothing changed after the readers ran. The grader and the naive
reference are kept with the project records.

| File | sha256 |
|---|---|
| `probe_inputs.zip` | `66e75fbc5a07115b0bb1d3798580d84b819a7fdd8647c9c028ae28070fd7a583` |
| `probe_prompt.md` | `99b8409dd015e4163e89a97bdf8dc0296197988b4e844f2e9c66ae9bea8af910` |
| `probe_followup.md` | `0c80e8dce0dc116a9f162667c625d8fcfe6f687f0985ca606e473642dfa5623b` |
| `probe_rubric.md` | `c9ec777f5af78f8f64f9a845ebf35aad90d719fffe334b3ffc6a99ad5548fd13` |
| `naive_reference.json` | `90edeb60920b23184ca3e5febc423c6cc0a01635cc8ccfd89ec2044b5b5fb5d4` |
| `grade.py` | `0501fe5e91637a01b601601bf6c7b03b2405dc88b7896b136328969c9e8ebc23` |

The truth is the last row of each run's `monitor_history_reNNNN.csv`, which is
the iteration of the planes. Those histories are kept with the runs, not in
this repository. The naive reference reads the published `forces.cy` and
`forces.cl` of each capsule as if they were the values at that iteration.

## Results

| | A | B |
|---|---|---|
| S1 to S4, signs | 8 of 8 | 6 of 8 |
| M1 to M3, intervals | 3 of 3 | 3 of 3 |
| C1, iteration of each capsule | PASS | PASS |

The values, against what the solver recorded at the iteration of the planes:

```text
                 solver    A                     B                      naive
S1 (cy, cl)      (0, 0)    (0, 0)                (0, 0)                 (0, 0)
S2 (cy, cl)      (+, +)    (+, +)                (+, 0)                 (0, -)
S3 (cy, cl)      (+, -)    (+, -)                (+, -)                 (-, +)
S4 (cy, cl)      (-, -)    (-, -)                (+, -)                 (-, 0)
M1 Re 300        0.06615   0.049 [0.03, 0.07]    0.013 [0.005, 0.13]    0.00695
M2 Re 1000       0.10434   0.055 [0.02, 0.13]    0.10 [0.03, 0.18]      0.01060
M3 Re 3000       0.27500   0.18 [0.09, 0.30]     0.13 [0.05, 0.30]      0.00636
```

**Signs.** The two signs B missed are S2 `cl`, read as 0 against +0.04755, and S4
`cy`, read as + against -0.20638. Both come straight from its integral on the
first plane (finding 4). At Re 100 the recorded values are both below 1.2e-5.

**Magnitudes.** Both beat the naive reading on all three, which is 9.5, 9.8 and
43.2 times below the recorded value. All six points are below the recorded
value: five by 26 to 80 percent and the sixth, B at Re 1000, by 4 percent.

**C1.** Both answered 3000, 15000, 5000 and 5000. A read it from the titles
drawn into the PNG images, and said that no CSV or JSON gives it. B read
`Continuum Iteration: 15000` in `setup.txt` for Re 300, checked it against
`convergence.iterations` in `summary.json`, and said that the planes have no
iteration field.

## Findings

**1. No file of the capsule ties the planes to an iteration.** At `de2a834` the header of
each `plane_*.csv` reads `# plane x_D = 1 | grid 13 x 13, spacing 0.25 D | ...`
and carries none, and `manifest.json` has no key for it. The PNG titles say
`snapshot at iteration N`, and `setup.txt` line 71 names the continuum
iteration. Both readers said so. From A and B.

**2. Taking the summary at face value gives the wrong answer for the right
reason.** A force that changes direction dozens of times averages out. After
discarding 1,000 iterations, sampled every 10, `cl` changes sign 47, 94 and 98
times and `cy` 39, 83 and 101 times at Re 300, 1000 and 3000, and the force
visits all four quadrants in each. The published means have a magnitude of
0.0070, 0.0106 and 0.0064, so the naive reading is a statistic read as an
instant. From the grading.

**3. The points all land low.** Five of the six by 26 to 80 percent, the sixth
by 4 percent. The intervals held anyway. The ratio of the upper to the
lower bound was 2.3, 6.5 and 3.3 for A and 26, 6 and 6 for B. From the grading.

**4. The plane is the answer.** B integrated the lateral momentum flux through
`plane_x1.csv`, `-2 * integral (u/U)(v/U) dA` for `cy` and the same with `w` for
`cl`. Rerun on the capsules at `de2a834`, the integral reproduces its numbers to
the last digit. The magnitude depends on which plane is taken: 0.013 on x1
against 0.125 on x2 at Re 300, 0.103 against 0.007 at Re 1000, 0.135 against
0.830 at Re 3000, and at Re 3000 x1 puts `cy` at +0.058 where the recorded value
is -0.206 (-0.20638). A plane is one instant of a wake that does not settle, and a flux
through it is not the force. From B, reproduced.

**5. Two routes to the same quantity.** A rebuilt the pressure on the three
visible faces of the cube from `view_cp_body.png`: it inverted the colour bar,
projected the faces with the camera stored in `manifest.json`, checked the
result against the nodes of `plane_y0.csv`, and integrated the difference
between opposite faces. The hidden bottom and +y faces came from y-z symmetry
at Re 300 and from the bottom centreline nodes at Re 1000 and 3000. From A.

### After the grading, outside the frozen kit

This scores nothing. The summary publishes a standard deviation beside each
mean, and when the means are near zero, `sqrt(cl_sd^2 + cy_sd^2)` is the
root-mean-square size of the side force. From `summary.json` at `de2a834`:

| Re | cl_sd | cy_sd | sqrt of the sum | Recorded | Error |
|---|---|---|---|---|---|
| 300 | 0.0362 | 0.0368 | 0.0516 | 0.06615 | -22.0% |
| 1000 | 0.0722 | 0.0843 | 0.1110 | 0.10434 | +6.3% |
| 3000 | 0.1458 | 0.1660 | 0.2209 | 0.27500 | -19.7% |

That is closer than either reader at Re 300 and 3000, and B is closer at
Re 1000. A printed those deviations as a consistency check on its own values
and then trusted its reconstruction. Three snapshots rank nothing.

## Audit note

Both answers were audited from the stored verbatim texts, not from the live
chats.

A tagged prior knowledge in S3, M1 and M3: that the side force points toward the
face with the stronger separated-bubble suction, a base-suction heuristic that
matched all three asymmetric capsules; that friction is negligible; and in M3
that the top-face mean equals its centreline mean. The M3 tag looks wrong: the
supporting numbers, -0.601 against -0.605, come from the capsule. Left
untagged: that the hidden faces are never seen and that bottom is taken as
equal to the side face and +y to the top by y-z symmetry, carried from Re 300 to
Re 1000 and 3000; an anti-diagonal relation `cl + cy ~ L - T` and a guessed
bottom value of -0.55 at x = 0.25 in M2; `cy ~ -0.05` in M3; the sign
convention. Its stated limits: `x = 0.25` unsampled, the spanwise distributions
of the hidden faces unobserved, `cy` poorly constrained between 0 and 0.15 at
Re 3000, and S4 the least certain sign.

B carries no prior knowledge tag anywhere: every field is tagged capsule. Yet
S2, S3, S4 and M1 to M3 all rest on an outside formula, `Cy ~ -2 integral
(u/U)(v/U) dA`, a wake momentum-flux survey for a steady flow with small wall
pressure, so those tags hide prior knowledge. Left untagged or untraced: the
0.005 sign threshold, never stated; the deficit centroid at y = +0.067 and
z = -0.058, which comes from no code shown; the shift of the side-face pressure
by about one colour-bar pixel in S2, with no code behind it; and the judgment
that the flux on x2, 0.83, is not trusted. On C1 it said the planes have no
iteration field and then assumed `setup.txt` gives the state they were exported
from.

The arithmetic traces where it was rerun: B's integral reproduces to the last
digit. A's reconstruction from the image was not rerun.

## Conflict of interest

Reader A is a Claude model, and a Claude model helped me write this record. I
issued the probes, ran the sessions and approved the scoring. The transcripts
are linked. The capsule figures above are checkable against `examples/`; the
recorded side forces are not, since the monitor histories they come from are not
in this repository.
