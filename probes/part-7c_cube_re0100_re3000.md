# Probe record: Part 7c, the stagnation value the label does not give

Two readers from two families received the four cube capsules as one archive,
with no other context and no network, and were asked four questions: three
about the flow at Re = 100, one about the files. The trap sits in
`capsule_cube_re0100/summary.json`, which reports two maxima of `cp` on the
upstream face. `pressure.cp_stagnation` is 2.394, the maximum over the six cube
faces, and it sits in a corner cell, next to the side-face cell that holds the
minimum of -3.51. `plane_extremes.y0.cp[1]` is 1.0676, the maximum on the plane
y = 0 that crosses the middle of the face, and it matches a point probe of the
solver 0.001 D upstream of the face middle. The label says stagnation. The
number is a mesh maximum, and the [Side Run on the corner
singularity](../docs/cube-re100/README.md) shows it growing as the cells shrink
while the stagnation value stays at 1.070.

Both readers walked past the label. Each took the stagnation value from
`plane_extremes.y0.cp[1]`, named the field, and placed the 2.394 in a corner of
the upstream face. They parted on one question: which way the artifact moves
under refinement. A said up, which is right; B said down. A scored 4 of 4, B 3
of 4, and the naive reading of the labels 0 of 3.

The two runs are not a head to head. The tiers were not recorded, and the
grading compares each reader with the solver and with the naive reading, never
the readers with each other.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.1 | not recorded | 2026-10-16 | [shared](https://claude.ai/share/eab9616d-e229-4bea-b82d-dbe031468aa6) |
| B | xAI Grok | 4.7 | not recorded | 2026-10-16 | [shared](https://grok.com/share/c2hhcmQtMg_87804f7b-042a-4261-bc1c-a6b7b816aa0e) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked, and here they cannot be: the tiers are unknown.

The probe is also reported in Appendix B of the [Part 7c
post](https://community.sw.siemens.com/s/question/0D5Vb00001fgMSYKA2/preparing-cfd-output-for-large-language-models-7c10-integrity-a-capsule-sealed-to-a-commit).

## The kit as issued

Kit v3, frozen on 2026-10-16. One archive, `probe_inputs.zip`: the four
capsules `capsule_cube_re0100`, `capsule_cube_re0300`, `capsule_cube_re1000`
and `capsule_cube_re3000` as they stood at `de2a834`, 54 files, 2,382,824
bytes, the same archive as in Parts 7a and 7b. It was rebuilt from a public
clone of that commit with the same script and the same sha256, and checked
before the probe. It contains no Side Run: the refinement series was published
afterwards, so neither reader could have read the answer in the archive. The
prompt travelled with the archive; one follow-up was allowed, only if the
format was missing, and neither reader used it. One message each. The expected
answers and tolerances the operator graded against were never shown to the
reader and are not reproduced.

Kit v3 replaces v2, which was never run, and adds P3 to v2's P1 and P2. An
earlier kit of 2026-10-02 (three integrity questions) was retired before.

````markdown
The attached `probe_inputs.zip` contains four Simulation Capsules from one parameter sweep: `capsule_cube_re0100`, `capsule_cube_re0300`, `capsule_cube_re1000` and `capsule_cube_re3000`. Each capsule is a self-contained, token-budgeted distillation of one CFD case (a cube in a uniform stream), built for a language model to read. Read every file in all four before you answer.

The questions are about the Re = 100 case, `capsule_cube_re0100`, except the last one, which checks files in `capsule_cube_re0300`.

Rules:

1. Work only from the four capsules and your own knowledge, with no connectors. You may run code on the attached files; show it. Do not access the network. Show the arithmetic behind any number you compute. Do not ask questions; answer in one message.
2. Every numeric answer has a point value and an interval `[low, high]` that you believe contains the value the solver recorded. A narrow interval is worth more only if it is right.
3. Tag every supporting statement *capsule* (it rests on a file you read; name the file and field) or *prior knowledge* (it rests on anything else).
4. If you cannot answer a field, say so and give your widest honest answer anyway. Do not leave a field out.

Answer these, for Re = 100:

| # | Field | Kind |
|---|---|---|
| P1 | Pressure coefficient `cp` at the stagnation point, the middle of the cube's upstream face. Also name the file and field you take it from, or `none` if you infer it | point + interval + source |
| P2 | Where on the cube the value in `pressure.cp_stagnation` of `summary.json` comes from, one of: `upstream face, middle`, `upstream face, edge or corner`, `side, top or bottom face`, `downstream face`, `cannot tell` | one choice, exact |
| P3 | The capsule carries two maxima of `cp` for this case: `pressure.cp_stagnation` in `summary.json` and `plane_extremes.y0.cp[1]` in the same file. If the mesh around the cube were refined, with smaller cells at its edges and vertices, how would each value move, one of: `up`, `down`, `unchanged` (within 1%) | two choices, exact |

Control question, about the files rather than the flow:

- C1. In `capsule_cube_re0300`, how many of the files present in the folder have a sha256 in its `manifest.json`, and how many of those match the bytes you received?

Answer format: first a fenced `json` block with exactly the shape below; then the code you ran and the arithmetic behind each computed number; then at most 10 lines of justification, one per field, each line tagged *capsule* or *prior knowledge*.

```json
{
 "P1": {"value": 0.0, "interval": [0.0, 0.0], "source": ""},
 "P2": {"location": ""},
 "P3": {"cp_stagnation": "", "plane_max": ""},
 "C1": {"fingerprinted": 0, "matching": 0}
}
```
````

The follow-up, not needed by either reader:

````text
Please resend your answer in the requested format: the json block with the four keys P1, P2, P3 and C1, P1 with value, interval and source, P2 with one of the five locations, P3 with one of up, down or unchanged for each of its two fields, C1 with two integers, then at most 10 justification lines tagged *capsule* or *prior knowledge*. Do not change any answer you already gave.
````

The kit was frozen with the sha256 of each of its files, so a later holder can
check that nothing changed after the readers ran. The truth, the grader and the
naive reference are kept with the project records.

| File | sha256 |
|---|---|
| `probe_inputs.zip` | `66e75fbc5a07115b0bb1d3798580d84b819a7fdd8647c9c028ae28070fd7a583` |
| `probe_prompt.md` | `c142288475932f8387c957bd741c697205662ccd2cd711e620951ccb201315cb` |
| `probe_followup.md` | `7ab93a31d8aff6622dde90684de05d2f49b655b7d67c5543d4af9ad45791d34d` |
| `probe_rubric.md` | `52037ca0775357f58ddba335cb366d1760ce47f43ed1afb9c362e7d47b9840a0` |
| `truth.json` | `9c03b76bcfb6af117d1847eb73c0f3684e5e3995108c213d2adc2cb9d2bea55f` |
| `naive_reference.json` | `3fa12b5f34bde464c408cccca722c0180f23cc010503b5535554a70bcbcf5895` |
| `grade.py` | `560c2e6d41992dadd8fd3a07d657da3f5cae65fe3ed5b082521bfbf48a0fa097` |

What the solver recorded, frozen before either reader ran: a point probe at
(-0.501, 0, 0) m in the Re = 100 case, 0.001 D upstream of the middle of the
upstream face, reads `cp` = 1.0676; the maximum over the six cube faces sits in
a corner cell of the upstream face; and a refinement series at cell quality
1.00 gives a vertex-cell `cp` of 1.280, 2.247 and 3.820 at D/128, D/256 and
D/512, growing as the cell size to the power -0.7, with the stagnation value on
the plane y = 0 staying at 1.070. For the control, 9 of the 14 files of
`capsule_cube_re0300` carry a sha256 in its `manifest.json`, and all 9 match.

The naive reference takes `pressure.cp_stagnation` as the stagnation value and
both maxima as converged results: 2.394, the middle of the face, and unchanged
twice.

## Results

| | A | B | Naive |
|---|---|---|---|
| P1, stagnation `cp` | PASS | PASS | FAIL |
| P2, where the value sits | PASS | PASS | FAIL |
| P3, how the two maxima move | PASS | FAIL | FAIL |
| C1, fingerprints | PASS | PASS | not asked |
| Total | 4 of 4 | 3 of 4 | 0 of 3 |

```text
reader     P1                          P2                P3             C1
A          1.068 [1.03, 1.09]          edge or corner    up, same       9, 9
B          1.0676 [1.0676, 1.0677]     edge or corner    down, same     9, 9
solver     1.0676                      edge or corner    up, same       9, 9
naive      2.394                       middle            same, same
```

In P3, same means unchanged within 1 percent; P2 names the place on the
upstream face.

**P1.** Both hit the rounding band of the recorded value and both were closer
than the naive point, which is 2.394. Each took it from
`plane_extremes.y0.cp[1]` and named the field.

**P2.** Both placed the 2.394 in a corner of the upstream face from the same
two clues: that the plane samples a quarter of a side from the middle read
0.998, and that the `diff.json` of the Re = 300 variant notes how Re = 100
saturates at the edges.

**P3.** The readers parted here. A reasoned from the corner singularity: a
sharp convex edge in a viscous flow carries a pressure that grows without
bound, so a cell maximum there follows the cell size, upward. B knew the peak
depended on the mesh and still answered down, as if refinement would smooth it
away. Both answered unchanged for the maximum on the plane, which is right.

**C1.** Both ran sha256 over the folder: nine fingerprinted files, nine intact.

## Findings

**1. `pressure.cp_stagnation` is labelled stagnation and is a face maximum.**
It sits in a corner cell of the upstream face and grows with refinement,
1.280, 2.247 and 3.820 at D/128, D/256 and D/512, as roughly the cell size to
the power -0.7, while the stagnation value on the plane stays at 1.070. The
capsule says what the field is called, and not how the value was obtained.
Both readers got past the label; one read the direction of its drift wrongly.
From A and B.

**2. The truth of P1 travels in the capsule under another name.**
`plane_extremes.y0.cp[1]` is the maximum of `cp` on the plane y = 0, which
crosses the middle of the upstream face. Nobody told the readers the name; both
found it. The barrier is the name, not the channel. From A and B.

**3. Knowing that a number is an artifact is not knowing which way it moves.**
B said the peak was mesh dependent and chose down. The refinement series had
measured the opposite. A reasoned from the singularity and chose up. Two
readers disagreeing about one artifact is a finding about the artifact (rule
4): the capsule does not say that the value moves with the mesh, nor which way.
From the grading.

**4. The seal guarantees the bytes, not the labels.** The check on the
fingerprints was answered 9 of 9 by both and says nothing about a label. A
reader with the physics in front caught what a validator cannot, and one of the
two caught the direction of the artifact as well. The next version of the
contract has to say how a value like `cp_stagnation` came to be, beyond what it
is called. From the grading.

## Audit note

Only the shared chats and the summary in Appendix B of the post exist for this
probe: the answers were not stored as separate verbatim records, and no line by
line audit of the two transcripts was made. What follows is what the chats and
the summary support.

The tags of the two answers were not audited against the files, so nothing is
claimed here about what each reader brought in from training. The control
answers are checkable by anyone: 9 files fingerprinted, 9 matching, from a
sha256 over the folder. One statement without support in the files, from B:
that refinement would reduce the corner maximum. It is the reverse of what the
Side Run measured, and no file in the archive says either way.

## Conflict of interest

Reader A is a Claude model, and this record was written with the assistance of
a Claude model. The probes were issued, the sessions run and the scoring
approved by the author. The transcripts are linked. The capsule fields above
are checkable against `examples/capsule_cube_re0100/summary.json`; the
refinement series and the point probe are the solver's, and the Side Run holds
the former.
