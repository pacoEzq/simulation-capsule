# Probe record: Part 8b, the history handed back

Two readers from two families received the cylinder capsule at Re = 100 as one
archive, with no other context and no network, and were asked to hand back a
history they never saw. Six questions ask what `signals/` should return: the
frequency of the drag, the lag of the probe behind the lift, how far cycle 17
strays from cycle 3, the lift and the probe at tU/D = 250, and the drag
frequency of a 5 mm rod in water at the same Reynolds number. G7 asks for the
record itself: `cd`, `cl` and `p1_v` at twelve instants drawn by seed, 36
values the capsule does not store. G8 asks each reader to forecast its own
largest error in `cl`. The truth comes from the raw history of the solver,
which does not travel with the capsule. The trap sits in the clock: the period
is still shortening when the window opens, and `cl_sd` in
`signals/phase_average.csv` says nothing about that.

A answered G1 to G6 6 of 6, C1 1 of 1, rebuilt 36 of 36 values within
tolerance and caught its own error in G8. B answered G1 to G6 4 of 6, C1 1 of
1, rebuilt 34 of 36 and missed G8. The naive reading, which never folds time
back, scored 0 of 6 and 0 of 36. Reader A also found that the capsule carries
the residual transient in `summary.json`, a fact I had not noticed and the
raw history confirms (Finding 1).

## Provenance

| Reader | Family | Version | Tier | Date | Time to answer | Conversation |
|---|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.2 | high effort | 2026-11-11 | about 12 min | [shared](https://claude.ai/share/b1c7666e-97d5-474e-a79a-34f32600d6b5) |
| B | xAI Grok | 4.7 | Expert | 2026-11-11 | about 2 min | [shared](https://grok.com/share/c2hhcmQtMg_3ac6a72a-35d3-4c12-abe6-a6259a3b17ac) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked, and here they cannot be: the tiers differ.

The kit names Fable 5.1 at its highest mode. I ran Fable 5.2 at maximum effort
(stopped after 17 minutes, no answer) and at extra effort (stopped, no answer),
each in a fresh chat. The run at high effort was the first that answered, and
it is the one graded here. No stopped run produced an answer, so no answer was
chosen among answers. Neither reader needed the follow-up.

Reader A shares a family with the Claude model that helped me write this
record. Fable 5.2 is also the model that designed G7 and G8, in another chat
and with no memory shared with the reader.

The probe is also reported in Appendix B of the [Part 8b
post](https://community.sw.siemens.com/s/question/0D5Vb00001l7xXkKAI/preparing-cfd-output-for-large-language-models-8b10-transient-fields-i-time-as-statistics-the-cycle-through-its-phase-and-its-spectrum).

## The kit as issued

Kit v4, frozen on 2026-11-11 at 12:22 UTC, before the first answer. One
archive, `capsule_cylinder_re0100.zip`: the capsule `capsule_cylinder_re0100`
from `examples/` as it stood at `a873ff6`, complete with `signals/`, nine files,
151,459 bytes, built with `mkzip.py`. The prompt travelled with the archive;
one follow-up was allowed, only if the format was missing, and neither reader
used it. One message each. The expected answers and tolerances I graded
against were never shown to the readers.

Kit v4 replaces v3 of 2026-11-07, which was never run. It keeps G1 to G6, C1
and C2 of v3 with their grader, truth and naive reference untouched, and adds
G7 and G8 with a grader and a truth of their own.

The kit calls the archive the same zip as in Part 8a. The results file
corrects that: the Part 8a session of 2026-11-07 ran on another packaging of
the same capsule, 147,934 bytes, sha256
`2e570dfe398de5afb1d9f77ced082f48d9211635a30751126165a178778c2d5a`, files at
the root, the nine files identical to `a873ff6` by blob hash. The two parts
share the capsule bytes, not the archive.

````markdown
The attached `capsule_cylinder_re0100.zip` contains one Simulation Capsule, `capsule_cylinder_re0100`: a self-contained, token-budgeted distillation of a CFD case built for a language model to read. The case is the laminar two-dimensional flow past a circular cylinder at Re = 100, run as a transient for tU/D = 0 to 300 with a time step of 0.02. Read every file in the capsule before you answer.

The capsule holds statistics, a phase average and a spectrum over one window, not the time history. The solver recorded the drag coefficient `cd`, the lift coefficient `cl` and the cross-stream velocity `p1_v` at a probe at every time step, and that history is not in the capsule. Infer what it says from the capsule and from your knowledge of the flow.

Rules:

1. Work only from the capsule and your own knowledge, with no connectors. You may run code on the attached files; show it. Do not access the network. Show the arithmetic behind any number you compute. Do not ask questions; answer in one message.
2. Every numeric answer except the lists of G7 has a point value and an interval `[low, high]` that you believe contains the recorded value. A narrow interval is worth more only if it is right.
3. Tag every supporting statement *capsule* (it rests on a file you read; name the file and field) or *prior knowledge* (it rests on anything else).
4. If you cannot answer a field, say so and give your widest honest answer anyway. Do not leave a field out.

Answer these:

| # | Field | Kind |
|---|---|---|
| G1 | Dominant frequency of `cd` over the window, as a Strouhal number fD/U | point + interval |
| G2 | Lag of the crest of `p1_v` behind the crest of `cl` within one cycle, as a fraction of the cycle between 0 and 1 | point + interval |
| G3 | Count the cycles of the window from 1, starting at its opening crossing. The largest absolute difference in `cl` between cycle 17 and cycle 3 at the same phase, over the whole cycle | point + interval |
| G4 | `cl` at tU/D = 250.00 | point + interval |
| G5 | `p1_v` at tU/D = 250.00 | point + interval |
| G6 | A steel rod 5 mm in diameter stands in a water stream (kinematic viscosity 1.0e-6 m²/s) at the same Reynolds number. At what frequency in Hz does the drag force on it fluctuate? | point + interval |
| G7 | Rebuild the record. Give `cd`, `cl` and `p1_v` as the solver recorded them at each of these twelve instants tU/D, in this order: 117.18, 127.02, 141.72, 170.12, 173.18, 191.70, 213.14, 231.70, 245.50, 265.42, 268.32, 290.04 | three lists of twelve values |
| G8 | The largest absolute error you expect among your twelve `cl` values of G7, against the recorded history | point + interval |

Control questions, about provenance and limits rather than physics:

- C1. On which bin of the spectrum does the line of `cd` fall, and which file and field say so?
- C2. Name one engineering decision this capsule cannot support on its own, and the file or field that would have to be added for it. At most three lines.

Answer format: first a fenced `json` block with exactly the shape below; then the code you ran and the arithmetic behind each computed number; then at most 12 lines of justification, one per field, each line tagged *capsule* or *prior knowledge*.

```json
{
 "G1": {"value": 0.0, "interval": [0.0, 0.0]},
 "G2": {"value": 0.0, "interval": [0.0, 0.0]},
 "G3": {"value": 0.0, "interval": [0.0, 0.0]},
 "G4": {"value": 0.0, "interval": [0.0, 0.0]},
 "G5": {"value": 0.0, "interval": [0.0, 0.0]},
 "G6": {"value": 0.0, "interval": [0.0, 0.0]},
 "G7": {"cd": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        "cl": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        "p1_v": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]},
 "G8": {"value": 0.0, "interval": [0.0, 0.0]},
 "C1": {"cd_bin": 0, "source": ""},
 "C2": {"answer": ""}
}
```
````

The follow-up, not needed by either reader:

````text
Please resend your answer in the requested format: the json block with all ten keys G1 to G8, C1 and C2, every G field except G7 with value and interval, G7 with three lists of twelve values, then at most 12 justification lines tagged *capsule* or *prior knowledge*. Do not change any answer you already gave.
````

The kit was frozen with the sha256 of each of its files, so a later holder can
check that nothing changed after the readers ran. The truths, the graders, the
naive references, `mkzip.py` and the raw history are kept with the project
records: `truth_t8_v3.py` and `grade_t8_v3.py` for G1 to G6 and C1,
`truth_roundtrip_t8b_v1.py` and `grade_roundtrip_t8b_v1.py` for G7 and G8. They
are not reproduced here.

| File | sha256 |
|---|---|
| `capsule_cylinder_re0100.zip` | `d7860961bce8780333f4ad009bd146ed9a59f5bd55b96421eca6d3ba32311d82` |
| `naive_reference_t8b_v3.json` | `898e5d6a8b318cca5ae7b4b11447c388d2a23dfe41678a03d3e9e387c1b8600c` |
| `grade_t8_v3.py` | `937b864fee3f7dd809bc11650f31a6139fe097822fb35cbd9b7c01ae392d17db` |
| `truth_t8_v3.py` | `02eea2f1386b486972b584bf7cb2ab047e53a2b36bdaf4bf51bb83ca4a658f49` |
| `naive_roundtrip_t8b_v1.json` | `d38db882285c3cdea0b98dc9e599ca5e49656633af545f416e001784bd4f7e18` |
| `grade_roundtrip_t8b_v1.py` | `64cafed84af871995b22d6727dda1da856859640cf43e1b683599483e6974dad` |
| `truth_roundtrip_t8b_v1.py` | `2d5c4f2c2ecf431d034ed58c73e42177eca6c4c8cca0d9d78d23d1d4145e33f7` |
| `mkzip.py` | `2d5c6bb30601bb0a12dbf30338d0c3a98f3aba9c159d15b3463016e9b634f233` |
| `t8x_hist_L4.zip` (raw history, grader only) | `5e0c208621250d3b374af5715c655e2dcd613742ef6b141e15027292a64aa1b4` |

The rubric: a numeric field of G1 to G6 passes if the recorded value falls in
the reader's interval, ends included; G2 is compared on the circle. Each value
of G7 passes if it lies within 1% of the swing (maximum minus minimum) of its
signal over the window: 0.00018 in `cd` (swing 0.018013), 0.0063 in `cl` (swing
0.626592) and 0.0111 in `p1_v` (swing 1.106771). G8 passes if the reader's own
largest error in its twelve `cl` values, as the grader measures it, falls in
its interval. C1 is the bin index, exact; its `source` is read, not graded. C2
is read, not graded.

What the solver recorded, from the frozen history through the frozen truth
scripts, which neither reader saw. G1, the peak of the FFT of `cd` over the
window, is St = 0.329286. G2, the mean over the 32 cycles of the crest phase of
`p1_v` minus that of `cl`, is 0.592677 of a cycle. G3, the largest difference
in `cl` between cycle 17 and cycle 3 over 400 phases, is 0.000375173. At
tU/D = 250.00, `cl` is -0.304861 and `p1_v` is 0.499575. G6, the St of G1 times
U/D = 4 1/s (U = Re nu/D = 0.02 m/s), is 1.31714 Hz. The line of `cd` falls on
bin 64. The twelve instants of G7, one in each twelfth of the window, drawn on
the solver's time grid with `numpy.random.default_rng(8)`, are 117.18, 127.02,
141.72, 170.12, 173.18, 191.70, 213.14, 231.70, 245.50, 265.42, 268.32 and
290.04; the 36 recorded samples stay in the history.

The kit's self-test, run before the freeze, graded four reference readers that
all used one period for the 32 cycles. Linear interpolation between phases
scored 33 of 36 (`cd` 9 of 12, worst 1.19% of its swing); Fourier
interpolation 36 of 36; the naive reading 0 of 36. It also measured the period
between 6.0731 and 6.0760 from cycle to cycle, and a real error in `cl` of
0.0012 to 0.0015 at the twelve instants, about ten times `cl_sd` (0.00012). It
adds that the capsule publishes the mean St and `_sd` at fixed phase, not that
variation. That last sentence is inexact; the kit stays as issued, and the
correction is Finding 2.

The naive reference never folds time back. Every signal is its time mean at
every instant (`cd` 1.32087, `cl` -0.00012, `p1_v` -0.01043), `cd` shares the
frequency of `cl`, the cycles are identical and the signals are in phase. It
answers 0.16464, 0, 0, -0.00012, -0.01043 and 0.65856 Hz for G1 to G6, and
expects an error of 0 in G8.

## Results

| | A | B | Naive |
|---|---|---|---|
| G1, St of `cd` | PASS | PASS | FAIL |
| G2, lag of `p1_v` behind `cl` | PASS | PASS | FAIL |
| G3, cycle 17 against cycle 3 | PASS | PASS | FAIL |
| G4, `cl` at 250.00 | PASS | FAIL | FAIL |
| G5, `p1_v` at 250.00 | PASS | FAIL | FAIL |
| G6, drag frequency of the rod | PASS | PASS | FAIL |
| G7, record rebuilt | 36 of 36 | 34 of 36 | 0 of 36 |
| G8, own largest error in `cl` | PASS | FAIL | not graded |
| C1, bin of the `cd` line | PASS | PASS | not asked |
| C2, a decision the capsule cannot support | read | read | not asked |
| Total | 6 of 6, 1 of 1, 36 of 36, G8 PASS | 4 of 6, 1 of 1, 34 of 36, G8 FAIL | 0 of 6, 0 of 36 |

The naive reference gives points without intervals. Each misses the recorded
value by the delta below. In G8 it expects an error of 0, and its worst `cl`
is off by 49.4% of the swing; the results file does not grade it.

```text
field  reader  point       interval              recorded    delta       in interval
G1     A       0.32928     [0.3290, 0.3296]      0.329286    -0.000006   yes
G1     B       0.329278    [0.32927, 0.32929]    0.329286    -0.000008   yes
G1     naive   0.164639                          0.329286    -0.164647
G2     A       0.593       [0.586, 0.600]        0.592677    +0.000323   yes
G2     B       0.59375     [0.59, 0.60]          0.592677    +0.001073   yes
G2     naive   0                                 0.592677    -0.592677
G3     A       0.00025     [0.00016, 0.00040]    0.000375    -0.000125   yes
G3     B       0.00035     [0.00005, 0.0012]     0.000375    -0.000025   yes
G3     naive   0                                 0.000375    -0.000375
G4     A       -0.30483    [-0.3052, -0.3044]    -0.304861   +0.000031   yes
G4     B       -0.30383    [-0.3044, -0.3033]    -0.304861   +0.001031   no
G4     naive   -0.000120                         -0.304861   +0.304741
G5     A       0.4997      [0.4989, 0.5005]      0.499575    +0.000125   yes
G5     B       0.49787     [0.4975, 0.4983]      0.499575    -0.001705   no
G5     naive   -0.010429                         0.499575    -0.510004
G6     A       1.3171 Hz   [1.310, 1.325]        1.31714     -0.000040   yes
G6     B       1.3171 Hz   [1.316, 1.318]        1.31714     -0.000040   yes
G6     naive   0.658556                          1.31714     -0.658584
C1     A, B    64                                64          0           exact
```

```text
G7     tolerance   A hits  A worst            B hits  B worst            naive hits  naive worst
cd     0.00018     12/12   4.87e-5 (0.27%)    10/12   2.15e-4 (1.20%)    0/12        48.0%
cl     0.0063      12/12   1.73e-4 (0.03%)    12/12   1.55e-3 (0.25%)    0/12        49.4%
p1_v   0.0111      12/12   1.62e-4 (0.01%)    12/12   3.43e-3 (0.31%)    0/12        49.4%
total              36/36                      34/36                      0/36

G8     forecast    interval            measured    delta (forecast - measured)
A      0.00016     [0.00005, 0.0006]   0.000173    -0.000013
B      0.0003      [0.0001, 0.0006]    0.001553    -0.001253
naive  0           none                0.31        -0.31
```

Percentages in G7 are of the swing of each signal over the window. The naive
G8 figure is 49.4% of the `cl` swing, 0.626592.

**G1.** Both found the drag line at twice the shedding frequency, from the
spectrum and from the column description of `St` in `signals/signals.json`. A
gave 64/T = 0.32928; B gave 0.329278 with an interval 0.00002 wide that holds
the recorded value. The naive point is the frequency of the lift.

**G2.** A interpolated the crests of `cl_mean` and `p1_v_mean` in
`signals/phase_average.csv` through the 32 phases and read 0.59307. B took the
discrete maxima, phases 0.25 and 0.84375, and read 0.59375. Both intervals hold
0.592677.

**G3.** A modelled the difference as the decaying transient of Finding 1 plus a
fitted jitter and noise, and reported 0.00025, low by 0.000125, inside its
interval. B bounded it by `cl_sd` and the halves of `summary.json` and reported
0.00035 with a wide interval that holds the recorded value.

**G4 and G5.** A placed tU/D = 250 at cycle 24, phase 0.78659, on a clock that
carries the transient, cross-checked from the second-half crossing at 202.70645
and the second-half period 6.0737168; both points sit within 0.000125 of the
recorded values. B mapped time to phase with one period for all 32 cycles and
interpolated linearly between phases. Its points were off by 0.001031 and
0.001705, and its intervals, 0.0011 and 0.0008 wide, left the recorded values
outside by 0.000461 and 0.001275.

**G6.** Both computed U = Re nu/D = 0.02 m/s and f = St U/D = 1.3171 Hz from the
St of `cd`. The naive point, 0.659 Hz, is the frequency of the lift.

**G7.** A rebuilt the record on its drifting clock with trigonometric
interpolation through the 32 phases; worst errors 0.27%, 0.03% and 0.01% of the
swings of `cd`, `cl` and `p1_v`. B rebuilt it on one period with linear
interpolation, the method of the self-test's linear reference reader; worst
errors 1.20%, 0.25% and 0.31%, and two misses in `cd`, at 127.02 (1.11%) and
265.42 (1.20%). The naive reading is off on all 36, by up to half a swing.

**G8.** A forecast 0.00016 from its fitted jitter and noise and from the
sensitivity of `cl` to the decay time; the grader measured 0.000173. B
estimated its error from `cl_sd` alone and forecast 0.0003; the grader measured
0.001553, 5.2 times the forecast, outside an interval that ends at 0.0006.

**C1.** Both answered bin 64 and named `signals/signals.json`, the column `St`
of `spectrum.csv`, and the header of `signals/spectrum.csv`. A added that row
64, St 0.329278, holds 99.97% of the published `psd_cd`, which the file
supports: 3.76493e-05 of a sum of 3.76606e-05.

**C2.** Not graded. A named the use of `cd`, `cl_rms` and St as mesh and time
step independent reference values, for instance to validate another code: the
capsule holds one mesh of 179,040 cells and one time step of 0.02, and would
need a convergence block in `summary.json` from at least two further mesh and
time step levels. B named a decision on the cycle to cycle force at one
particular passage, for fatigue or control input, which would need the raw time
series of `cd` and `cl`.

## Findings

**1. The window opens on a residual transient, and the halves test lets it
through.** `summary.json` publishes `window.halves.first.t_end` = 202.70645, so
the first 16 cycles last 97.18507531 and the last 16 last 97.17946936: the mean
period of the first half is 0.00035 longer than that of the second. From that
one crossing, A deduced that the vortex street has not quite settled when the
window opens, and fitted a decay time of 17.0 plus or minus 0.5 D/U. The raw
history agrees: the halves last 97.185075308 and 97.179469359; the period falls
from 6.07600 in cycle 1 to 6.07338 in cycle 8; the amplitude of `cl` is 0.312821
in cycle 1 against 0.313287 in cycle 32, a deficit of 1.49e-3 against the last
eight cycles (A: 1.29e-3 at the window opening); the mean of `cd` is 1.320461
in cycle 1 against 1.320916 in cycle 32 (deficit 4.6e-4; A: 5.39e-4). Crude
exponential fits over the first twelve cycles give 19.8 D/U from the mean of
`cd` and 19.5 from the amplitude of `cl`: same sign, same order. The transient
is described in neither the Part 8a nor the Part 8b body. The halves test
passes it because its two figures, `rel_diff.cd_mean` 7.2e-5 and
`rel_diff.cl_rms` 2.2e-4, sit under the threshold of 0.001. From A, checked
against the history.

**2. The kit says the capsule does not publish how much the period varies. That
is inexact.** The self-test of kit v4 states it. `summary.json` publishes the
crossing that splits the window into halves, and the 0.00035 difference in mean
period follows from it. The capsule does not publish the spread of the period
from cycle to cycle, but that one figure is enough for a one parameter model,
as A showed. The kit stays as issued, frozen; the correction lives in the
results. Open: `signals/signals.json` does not publish the period variation
directly, so a reader finds it only by doing arithmetic on `window.halves`.
From the results file.

**3. `cl_sd` is not an error bar for an instant.** `cl_sd` is the spread at a
fixed phase after every cycle has been cut at its own crossings, as the
`phase` column of `signals/signals.json` says, so it says nothing about a clock
that drifts. The self-test measured a real error in `cl` about ten times
`cl_sd` for any reconstruction on one period. A read `cl_sd` as one input
among the halves and the sidebands; B read it as the error itself and
forecast 0.0003 against a measured 0.001553. Two readers disagreeing about one
artifact is a finding about the artifact (rule 4): the capsule says how
`cl_sd` was formed and not what it leaves out. Open. From the grading.

**4. Thirty-two phases carry the record back; `cd` sits at the edge of the
tolerance.** On one period with linear interpolation, `cl` and `p1_v` stay
within 0.31% of their swings, and `cd`, which swings by only 0.018, reaches
1.1% and 1.2% at two instants. The self-test's linear reader missed `cd` in
the same way (9 of 12, worst 1.19%). Not a defect: the tolerance is relative to
a swing of 0.018 around a mean of 1.32. From the grading and the self-test.

**5. The drag line lives only in `signals/`.** `summary.json` carries
`temporal.st`, the shedding frequency of `cl`. That the line of `cd` falls on
bin 64 is said only in `signals/signals.json` and the header of
`signals/spectrum.csv`. Without them the naive reading puts the drag of the rod
at 0.659 Hz, half the recorded 1.317 Hz. Both readers took the line from the
spectrum. Not a defect; it is what `signals/` adds. From the grading.

**6. Two limits named, neither graded.** A asked for a convergence block in
`summary.json` (one mesh, `manifest.json` `adapter.mesh.cells` 179040, and one
time step, `setup.txt` `Time-Step: 0.02 s`). B asked for the raw history of
`cd` and `cl` for a decision at one passage of the wake, which the capsule
leaves out by design. Open, as candidates. From C2.

## Audit note

Both answers exist as verbatim records, read from the innerText of the shared
pages on 2026-11-11, with the json block, the code, the arithmetic and the
justification lines. I checked every capsule field below against
`examples/capsule_cylinder_re0100` at `4e37eb4`, the head of a shallow public
clone. The kit's commit `a873ff6` is not reachable in that clone, so the identity
of bytes with what the readers received rests on the sha256 of the zip.

**A.** Traces to named files and fields: `summary.json` `window.t_start`,
`window.t_end` and `window.halves.first.t_end` (the two half durations, the
second-half period 6.0737168, the crossing at 202.70645); `summary.json`
`temporal.st` 0.164639; `signals/spectrum.csv` row 64 and the sum of `psd_cd`;
the sidebands of `psd_cl` at bins 31 and 33 (2.26e-8 and 3.62e-8, as in the
file); `signals/phase_average.csv` `cl_mean`, `p1_v_mean`, `cl_sd`, `cd_sd`
and `p1_v_sd`; the column description of `St` in `signals/signals.json`, quoted
correctly; `setup.txt` time step 0.02; `manifest.json` `adapter.mesh.cells`
179040. Its whole-window checks, `cd` 1.3208722, `cl` rms 0.2214573 and `cl`
amplitude 0.3132498, match `forces.cd`, `forces.cl_rms` and
`temporal.cl_amplitude` of `summary.json`, though the answer does not name the
fields. Its mean of `cl` over 100 to 300, -3.439e-4, matches `cl_mean` at phase
0 in `phase_average.csv`, unnamed. Brought from training and tagged so: that
the approach to the limit cycle is a single exponential; that the fluctuation
of `cd` scales as the square of the amplitude; that drag fluctuates at twice
the shedding frequency and St depends only on Re; an experimental St "near
0.164 at Re 100"; that a reference value needs a refinement series. Untagged:
the form of the transient model and its parameters (a fixed factor g = 2.0, a
crossing jitter, a per-sample noise), modelling choices fitted to capsule
fields and found in no file. The answer says all nine files were read and
both PNGs viewed; the shared page reports 11 commands run and 7 files read. I
report both as they stand.

**B.** Traces to named files and fields: `summary.json` `window.t_start` and
`window.t_end`, copied to full precision into the code; `summary.json`
`temporal.st`; the discrete maxima of `cl_mean` and `p1_v_mean` in
`signals/phase_average.csv`, at phases 0.25 and 0.84375, as in the file; `cl_sd`
of about 1e-4 (the file's maximum is 1.22e-4); the column description of `St`
in `signals/signals.json` and the header of `signals/spectrum.csv`. Brought from
training and tagged so: the definition of Re, giving U = Re nu/D. Tagged
*capsule* but resting on the reader's inference: in G3 and G8, that `cl_sd`
bounds the error at an instant (the field exists; the bound is the reader's,
and Finding 3 is why it fails); in C2, that raw histories are absent by design,
tagged with "manifest/summary" and no field, while the absence is stated in the
prompt. The engineering example in C2, fatigue or control at one passage, is
the reader's own. The code shown has 17 lines.

## Conflict of interest

Reader A is a Claude model, and a Claude model helped me write this record. The
same model as A, Fable 5.2, designed G7 and G8 in another chat, with no memory
shared with the reader. I issued the probes, ran the sessions and approved the
scoring. The transcripts are linked. The capsule fields above are checkable
against [`examples/capsule_cylinder_re0100`](../examples/capsule_cylinder_re0100/);
the truths, the graders and the raw history are kept with the project records,
and their sha256 are listed above.
