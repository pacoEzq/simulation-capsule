# Probe record: Part 8a, the history the statistics stand in for

Two readers from two families received the cylinder capsule at Re = 100 as one
archive, with no other context and no network, and were asked six questions
about a time history they never saw, plus two about provenance and limits. The
capsule holds statistics over one window of 32 whole periods of `cl`, tU/D
105.521 to 299.886, and not the history. The questions ask for what only the
history has: upward crossings of `cl` in the fixed span 100 to 300, `cl` at two
instants, the rms of `cl` over that fixed span and the largest `cd` in the
window. The sixth uses the capsule instead of checking it, for a rod nobody
simulated. The prompt told both readers to leave `signals/` aside, since that
folder belongs to Part 8b.

Both rebuilt the lift as a sine from three fields, the ends of the window, the
amplitude and St, and both put `cl` at tU/D 200 and 300 inside their intervals.
The trap sits in F4. `forces.cl_rms` is right for the window and not for the
span 100 to 300, which holds 32.93 periods. A corrected for the stray fraction
of a period; B called the change negligible and kept the window value. A
scored 6 of 6 intervals and B 5 of 6, both 1 of 1 on the control. The naive
reading gives points only, and on F1 to F5 both readers' points sit nearer the
truth than it does. The two runs are not a head to head: the grading compares
each reader with the solver and with the naive reading, never the readers with
each other.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.1 | maximum effort | 2026-11-07 | [shared](https://claude.ai/share/b7c2e7ed-ccdd-4eeb-a031-e97ae2592b6f) |
| B | xAI Grok | 4.7 | Expert mode | 2026-11-07 | [shared](https://grok.com/share/c2hhcmQtMg_b40a88fa-5544-489d-a662-254b3035fc24) |

A answered first and B second, each in a fresh chat with no connectors and no
memory, in the highest reasoning mode each offers. Letters are used below. The
table exists so the reading can be checked, not so the readers can be ranked,
and here they cannot be: the tiers differ. Reader A is a Claude model and
shares a family with the Claude model that helped me write this record.

The probe is also reported in Appendix B of the [Part 8a post](https://community.sw.siemens.com/s/question/0D5Vb00001imOEMKA2/preparing-cfd-output-for-large-language-models-8a10-transient-fields-i-time-as-statistics-one-window-of-whole-periods).

## The kit as issued

Kit v5, frozen on 2026-11-07 at 22:00 UTC, before the first run. Kits v1 to v4
were replaced before any run. One archive, `capsule_cylinder_re0100.zip`: the
capsule `capsule_cylinder_re0100` as it stood in `examples/` at `a873ff6`,
complete with `signals/`, 9 files. The archive attached to both sessions was the
zip of loose files, sha256
`2e570dfe398de5afb1d9f77ced082f48d9211635a30751126165a178778c2d5a`, not the
`d7860961...` archive that the kit and Appendix B cite, and its content is
identical to the capsule at `a873ff6`. The capsule travelled whole; the prompt
fenced off `signals/`, so the claim narrows to `setup.txt`, `summary.json`,
`planes/`, `views/` and `manifest.json`. The prompt travelled with the archive;
one follow-up was allowed, only if the format was missing, and neither reader
needed it. One message each. The expected answers and tolerances I graded
against were never shown to the reader.

````markdown
The attached `capsule_cylinder_re0100.zip` contains one Simulation Capsule, `capsule_cylinder_re0100`: a self-contained, token-budgeted distillation of a CFD case built for a language model to read. The case is the laminar two-dimensional flow past a circular cylinder at Re = 100, run as a transient for tU/D = 0 to 300 with a time step of 0.02. Read every file in the capsule before you answer.

The capsule holds statistics, a phase average and a spectrum over one window, not the time history. The solver recorded the drag coefficient `cd`, the lift coefficient `cl` and the cross-stream velocity `p1_v` at a probe at every time step, and that history is not in the capsule. Infer what it says from the capsule and from your knowledge of the flow.

Rules:

1. Work only from the capsule and your own knowledge, with no connectors. Leave the folder `signals/` aside; answer from the other files only. You may run code on the attached files; show it. Do not access the network. Show the arithmetic behind any number you compute. Do not ask questions; answer in one message.
2. Every numeric answer has a point value and an interval `[low, high]` that you believe contains the recorded value. A narrow interval is worth more only if it is right.
3. Tag every supporting statement *capsule* (it rests on a file you read; name the file and field) or *prior knowledge* (it rests on anything else).
4. If you cannot answer a field, say so and give your widest honest answer anyway. Do not leave a field out.

Answer these:

| # | Field | Kind |
|---|---|---|
| F1 | Number of upward zero crossings of `cl` (a step from a negative value to zero or above) between tU/D = 100 and 300 | point + interval |
| F2 | `cl` at tU/D = 300.00, the last time step | point + interval |
| F3 | `cl` at tU/D = 200.00 | point + interval |
| F4 | rms of the fluctuation of `cl` over the fixed span tU/D = 100 to 300, as the trapezoid integral of the history over that span | point + interval |
| F5 | Largest value `cd` reaches inside the window | point + interval |
| F6 | A steel rod 5 mm in diameter stands in a water stream (kinematic viscosity 1.0e-6 m²/s) at the same Reynolds number. How many cycles of cross-stream (lift) loading does it undergo per hour? | point + interval |

Control questions, about provenance and limits rather than physics:

- C1. Over which span were the statistics in `summary.json` formed, how many periods does it hold, and which file and field say so?
- C2. Name one engineering decision this capsule cannot support on its own, and the file or field that would have to be added for it. At most three lines.

Answer format: first a fenced `json` block with exactly the shape below; then the code you ran and the arithmetic behind each computed number; then at most 10 lines of justification, one per field, each line tagged *capsule* or *prior knowledge*.

```json
{
 "F1": {"value": 0.0, "interval": [0.0, 0.0]},
 "F2": {"value": 0.0, "interval": [0.0, 0.0]},
 "F3": {"value": 0.0, "interval": [0.0, 0.0]},
 "F4": {"value": 0.0, "interval": [0.0, 0.0]},
 "F5": {"value": 0.0, "interval": [0.0, 0.0]},
 "F6": {"value": 0.0, "interval": [0.0, 0.0]},
 "C1": {"t_start": 0.0, "t_end": 0.0, "n_periods": 0, "source": ""},
 "C2": {"answer": ""}
}
```
````

The follow-up, not needed by either reader:

````text
Please resend your answer in the requested format: the json block with all eight keys F1 to F6, C1 and C2, every F field with value and interval, then at most 10 justification lines tagged *capsule* or *prior knowledge*. Do not change any answer you already gave.
````

The kit was frozen with the sha256 of its files, so a later holder can check
that nothing changed after the readers ran. The truth, the grader and the naive
reference are kept with the project records.

| File | sha256 |
|---|---|
| `capsule_cylinder_re0100.zip` (as the kit cites it; see above) | `d7860961bce8780333f4ad009bd146ed9a59f5bd55b96421eca6d3ba32311d82` |
| `naive_reference_t8a_v3.json` | `13a16e453fc938da8100b4fdd6dc84985ea33d87ebfd4eea51f6fb26f630fa7b` |
| `grade_t8_v3.py` | `937b864fee3f7dd809bc11650f31a6139fe097822fb35cbd9b7c01ae392d17db` |
| `truth_t8_v3.py` | `02eea2f1386b486972b584bf7cb2ab047e53a2b36bdaf4bf51bb83ca4a658f49` |
| `mkzip.py` | `2d5c6bb30601bb0a12dbf30338d0c3a98f3aba9c159d15b3463016e9b634f233` |
| `t8x_hist_L4.zip` (raw history, grader only) | `5e0c208621250d3b374af5715c655e2dcd613742ef6b141e15027292a64aa1b4` |

What the solver recorded, frozen with the kit before either reader ran: the
history of `cd`, `cl` and `p1_v` at every time step, 15,000 samples each from
tU/D 0.02 to 300.0. Read with the `build_summary.py` of `a873ff6`, it
reproduces the capsule to the last digit: window 105.52137787386212 to
299.88592254061, 32 periods, `cd` 1.3208721889932558, `cl_rms`
0.22145725077823092. From it `truth_t8_v3.py` gives 33 upward crossings of `cl`
between tU/D 100 and 300; `cl` = 0.0366115 at 300.00 and -0.1049359 at 200.00;
an rms of the fluctuation of `cl` over the fixed span of 0.2216196; a largest
`cd` in the window of 1.3296247; and 2370.803 lift cycles per hour for the rod
(U = Re nu / D = 0.02 m/s, U/D = 4 1/s). For the control, the window is tU/D
105.521 to 299.886 with 32 periods, in `summary.json` under `window.t_start`,
`window.t_end` and `window.n_periods`. A field whose justification cited
`signals/` would have been marked contaminated; none did.

The naive reference takes `summary.json` at face value: every signal is its
time mean, every frequency is `st`, every rms is the published one. It gives
F1 = `st` times the span of 200 = 32.928, F2 = F3 = `forces.cl` = -0.00012,
F4 = `forces.cl_rms` = 0.22146, F5 = `forces.cd` = 1.32087, and F6 = `st` times
U/D times 3600 = 2370.80, which matches the truth by design. It carries points
and no intervals, so it is compared by its delta, not by PASS or FAIL.

## Results

| | A | B |
|---|---|---|
| F1, upward crossings of `cl`, 100 to 300 | PASS | PASS |
| F2, `cl` at tU/D 300.00 | PASS | PASS |
| F3, `cl` at tU/D 200.00 | PASS | PASS |
| F4, rms of `cl`, fixed span 100 to 300 | PASS | FAIL |
| F5, largest `cd` in the window | PASS | PASS |
| F6, lift cycles per hour, 5 mm rod | PASS | PASS |
| Total, intervals | 6 of 6 | 5 of 6 |
| C1, window and periods | PASS | PASS |
| C2, what is missing | not scored | not scored |

```text
field  truth       A point [interval]           B point [interval]             naive
F1     33          33 [33, 33]                  33 [33, 33]                    32.928
F2     0.03661     0.0365 [0.0335, 0.0395]      0.0368 [0.034, 0.040]          -0.00012
F3     -0.10494    -0.1054 [-0.112, -0.098]     -0.106 [-0.110, -0.102]        -0.00012
F4     0.22162     0.2216 [0.2210, 0.2222]      0.22146 [0.22140, 0.22150]     0.22146
F5     1.32962     1.3296 [1.3284, 1.3310]      1.3296 [1.3290, 1.3302]        1.32087
F6     2370.80     2371 [2330, 2410]            2370.8 [2370.5, 2371.1]        2370.80
C1     105.521 to 299.886, 32 periods           A and B: 105.52137787386212 to 299.88592254061, 32

delta  A           B           naive           (point minus truth)
F1     0           0           -0.072
F2     -0.00011    +0.00019    -0.03673
F3     -0.00046    -0.00106    +0.10482
F4     -0.00002    -0.00016    -0.00016
F5     -0.00002    -0.00002    -0.00875
F6     +0.2        -0.003      0.000
```

A PASS means the truth fell inside the reader's interval, ends included. In F4
the truth sits 0.00012 above the upper end of B's interval.

**F1.** Both counted 33: `window.n_periods` is 32, and both ends of the window
are upward crossings, so 32 periods carry 33 crossings, with the previous and
the next one outside tU/D 100 to 300 (A placed them at 99.45 and 305.96). A also
checked that the mean of -0.00012 moves a crossing of `cl` by 0.0004 tU/D, far
below the step. The naive 32.93 counts periods, not crossings.

**F2 and F3.** Neither value is written anywhere in the capsule. Both treated
`cl` as a sine, on the evidence that `temporal.cl_amplitude` over
`forces.cl_rms` is 1.4145, close to the square root of 2, and phased it on
crossings the capsule declares. A phased F2 on `window.t_end` and F3 on
`window.halves.second.t_start`, the 17th crossing at 202.706, with amplitude
`temporal.cl_amplitude` and a correction for the mean over the span. B phased
both on `window.t_start` and `window.t_end`, with amplitude `forces.cl_rms`
times the square root of 2. The errors are 0.3% and 0.4% for A, 0.5% and 1.0%
for B. The naive reading puts `cl` at its mean twice and misses by 0.037 and
0.105.

**F4.** The truth over the fixed span is 0.22162, 0.07% above the window value
0.22146: 200 time units hold 32.93 periods, not 32. A computed where the stray
0.93 of a period begins, at phase 0.57 rad, where the square of the sine runs
above its mean, raised the rms by 0.08% and answered 0.2216 [0.2210, 0.2222],
an interval that also holds the window value. B wrote that the partial periods
change the rms by much less than 0.00005, kept 0.22146 and drew the interval 0.22140 to
0.22150 around it. The naive reading gives the window value too.

**F5.** Both answered `forces.cd` plus the square root of 2 times
`forces.cd_sd` (B used `forces.cd_rms`, the same number): 1.32955, given as
1.3296, against a truth of 1.32962. The capsule carries no extreme of `cd`; the
step from a standard deviation to a peak rests on a sinusoidal `cd` at twice the
shedding frequency, which both tagged as prior knowledge. A also noted the drift
of 9.5e-5 between `window.halves.first.cd_mean` and
`window.halves.second.cd_mean`. The naive mean misses by 0.00875.

**F6.** Both took the leap in scale right: U = Re nu / D = 100 x 1.0e-6 / 0.005
= 0.02 m/s, f = St U / D = 0.6586 Hz, 2370.8 cycles per hour. A drew its
interval over St 0.162 to 0.167, from measured shedding on real rods; B drew
plus or minus 0.3 cycles. The naive script gets this right by design. What the
probe shows is that the leap costs one line of arithmetic, because the capsule
states `reference.re` and `temporal.st` and says over which window St was
formed.

**C1.** Both gave 105.52137787386212 to 299.88592254061 with 32 periods, from
`window.t_start`, `window.t_end` and `window.n_periods` of `summary.json`. A
added `forces.formed` and `temporal.formed`; B added `forces.cl_t_start` and
`forces.cl_n_periods`. All of these fields exist and agree.

**C2.** Not scored. Both answers are in the audit note.

## Findings

**1. A statistic is right for its window and silent about any other span.**
`forces.cl_rms` is formed over 32 whole periods. Over the fixed span 100 to 300
the rms is 0.07% higher, because the span keeps 0.93 of a stray period. The
capsule states the window, which is what made the correction computable, and
says nothing about how far the value moves on a span that is not whole periods.
The two answers differ on whether that shift matters at this precision; two
readers disagreeing about one artifact is a finding about the artifact (rule
4). From the grading.

**2. The instantaneous lift is not in the capsule and comes back from three
fields.** `window.t_start` and `window.t_end`, which are upward crossings,
`temporal.cl_amplitude` and `temporal.st` were enough to put `cl` at tU/D 200
and 300 within about 1%, once `cl_amplitude` over `cl_rms` had shown the lift
to be a sine. Whole periods paid for this: they make the ends of the window
crossings of known phase. From A and B.

**3. The capsule carries two spans.** The scalars in `summary.json` are formed
over the window, tU/D 105.521 to 299.886. The plane fields are formed over the
search span, tU/D 100 to 300: `manifest.json` `planes[0].formation` and the
header of `planes/plane_z0.csv` say so. The fields therefore average 32.93
periods, the very span F4 asks about, while the scalars average 32. From A, in
its C1 justification. While checking the fields for this record I found that
`manifest.json` `body.formation.scalars` also reads "time_mean over tU/D 100 to
300", which disagrees with `forces.formed` in `summary.json`; that half is mine,
not a reader's.

**4. No file carries an extreme of `cd` or `cl`.** `summary.json` gives means,
standard deviations, rms values and the mean half peak to peak of `cl`. The
largest `cd` was recovered only by assuming a sinusoidal waveform. From A, in
F5 and C2.

## Audit note

The literal answers of both readers are kept with the project records, next to
the shared chats, and I read them line by line against the capsule in
`examples/capsule_cylinder_re0100`. Every justification line carries a tag, as
the kit asked. No field
cites `signals/`: A's code states that `signals/` was never opened, and B's C2
line names the folder only to say it was left unused.

What traces to the files. Every field the readers named exists and holds the
value they used: `window.t_start`, `window.t_end`, `window.n_periods`,
`window.method`, `window.halves.second.t_start`, the two `halves` values of
`cd_mean`, `temporal.st`, `temporal.st_n_cycles`, `temporal.cl_amplitude`,
`forces.cl`, `forces.cl_rms`, `forces.cd`, `forces.cd_sd`, `forces.cd_rms`,
`forces.formed`, `temporal.formed`, `reference.re`, and in A's C1 line
`manifest.json` `planes[0].formation` and the `plane_z0.csv` header.

What the readers brought from training, tagged as such: that `cl` at Re = 100
is close to a pure sine (both, checked against the ratio of 1.4145); that `cd`
oscillates near sinusoidally at twice the shedding frequency (both); U = Re nu
/ D and f = St U / D (both); the band of St 0.162 to 0.167 on real rods with
small three-dimensional effects (A). One statement under a *capsule* tag has no
support in any file or in shown arithmetic: B's F4 line, that the partial
periods change the rms by much less than 0.00005. The code B showed has no F4
block, and the truth moved by 0.00016. A's untagged prose is tool narration
before the answer and one interval check that assumes a third harmonic of 1 to
3%, which is prior knowledge. A's interface line reports five files read; which
five is not in the literal answer.

**C2, A.** "Sizing the rod against peak or fatigue loads": `summary.json`
holds no `cd_max`, `cl_max` or cycle by cycle amplitudes, which traces to the
file, and `temporal.cl_peak_to_peak` is itself a mean over periods. The fields
it proposes, `forces.cd_max/cd_min` and `cl_max/cl_min` or a per-cycle extremes
table, are its own names; none exists. Fatigue as the decision at stake is
engineering judgement from training. Its line on `manifest.json` recording only
mean and rms formations traces to `body.formation` and `planes[0].formation`.

**C2, B.** "Exact instantaneous force at an arbitrary time": the claim that the
history is absent traces to the prompt and to `summary.json`, which holds only
windowed statistics. Its examples, peak-stress timing and a non-stationary
start-up, come from training. The remedy it names, the raw history or "a
phase-average explicitly anchored to a known time origin", is in part already in
the archive: `signals/phase_average.csv` puts phase 0 at each upward crossing of
`cl`, and `summary.json` gives `window.t_start` as the first one. B was told to
leave `signals/` aside and did, so it could not see this. That gap is what Part
8b fills.

## Conflict of interest

Reader A is a Claude model, and a Claude model helped me write this record. I
issued the probes, ran the sessions and approved the scoring. The transcripts
are linked. The capsule fields above are checkable against
`examples/capsule_cylinder_re0100/summary.json` and `manifest.json`; the truth
comes from the solver's history, which does not travel with the capsule.
