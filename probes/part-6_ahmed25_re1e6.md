# Probe record: Part 6, capsule_ahmed25_re1e6

Four models from four families read the same published archive, whole and
unedited, with nothing changed for any of them. Three recovered the regime, the
turbulence model, the convergence caveat and a quantitative wake measurement
from the capsule alone. The fourth failed on one artifact whose name and title
disagree with the setup report, and every later error followed from that.

Reader D ran first; its answers are the ones published in Appendix B of Part 6.
Model agnosticism is a design constraint of the series, not a result. Until
this session it had been asserted against one reader.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | xAI Grok | 4.6 | Expert | 2026-09-02 | [shared](https://grok.com/share/c2hhcmQtMg_cea13d6b-48f8-4b5e-92cf-af0aa88c0b37) |
| B | OpenAI GPT | 5.6 Luna | default | 2026-09-02 | [shared](https://chatgpt.com/share/6a97e20e-06a4-8328-b7ce-ed5f3a292e49) |
| C | Google Gemini | 3.1 Pro | default | 2026-09-02 | [shared](https://share.gemini.google/AxZbDujCMwxp) |
| D | Anthropic Claude | Fable 5 | Expert | 2026-08-30 | [shared](https://claude.ai/share/aa407360-42bd-4c50-bf60-eab8983d4d3a) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked.

## The kit as issued

```text
You are given a Simulation Capsule (attached, whole). Answer each probe using
only the files it names. Keep answers to the length requested.

Probe 1. From view_cpillar_symmetry.png alone: which side of the body carries
positive streamwise vorticity? One sentence.

Probe 2. From setup.txt and view_limit_cycle_diff.png together: is this run
steady or time accurate, and under which turbulence model? Then reconcile that
with what the diff view shows, and say what it implies for reading the scalars
in summary.json. Three sentences.

Probe 3. From view_wake_topology.png alone: estimate the streamwise extent of
reverse flow behind the body, in body lengths, and state your method in one
sentence.
```

## Results

| | A | B | C | D |
|---|---|---|---|---|
| 1, vorticity side | PASS | PASS | not scorable | PASS |
| 2, regime and model | PASS | PASS | FAIL | PASS |
| 3, reverse flow extent | PASS, 0.40 L | PASS, 0.50 L | FAIL, 0.25 L | PASS, 0.35 to 0.45 L |
| Free answer | traceable, two imports | traceable | contradicts the capsule | traceable |

**Probe 1.** A, B and D answered port, y greater than zero, which the burned in
PORT and STARBOARD anchors label. C answered "the right side" from an observer
position it chose for itself, so the answer cannot be scored without importing
an assumption the capsule does not make.

**Probe 2.** `setup.txt` declares steady segregated SIMPLE under SST Menter
k-omega. A quoted the continuum name and the Steady model; D named the state a
pseudo-transient limit cycle in which the solution never settled to a fixed
point; B called the diff a persistent limit cycle rather than exact iteration
to iteration steadiness. All three concluded the scalars are a phase of a weak
oscillation and the last digit means nothing. C answered time accurate under
URANS or DES with time averaged scalars, which `setup.txt` contradicts in every
clause.

**Probe 3.** The script returns 0.45 L, from x/L 1.453 minus a base at 1.004.

| Reader | Estimate | Delta | Method |
|---|---|---|---|
| D | 0.35 to 0.45 L | upper bound exact | pixel spans, 170 to 200 px against a 455 px body |
| A | 0.40 L | 11% low | span against body length |
| B | 0.50 L | 11% high | u/U zero crossing against body length |
| C | 0.25 L | 44% low | none named |

The shallow floor cell fades before the dark blue does, so a colour based
method lands low. B read the zero crossing instead and landed high. The reading
that fell far outside is the one that named no anchor.

## Findings

**1. A view has to declare its regime, not just its field.** C did not invent
the unsteady run. It inferred it from the name `view_limit_cycle_diff.png`, a
reasonable inference from a filename and the wrong one here. The burned in
title declares case, plane, field, range and orientation, and nothing about the
regime. The view contract gains a rule: the title declares the regime and the
iteration or time span it was taken from. Three of four readers recovered the
regime from `setup.txt` anyway, which is not a defence of the view. It means
the view travelled leaning on a neighbour, and the probe found the one reader
that did not lean.

**2. The visual measurement band is eleven percent.** Recorded above, from four
readings, replacing the single reader estimate in Appendix B.

**3. The kit needs a file manifest.** Asking the reader to list the files it
opened would have surfaced finding 1 in one line, before any answer was read.

## Audit note

B and D trace entirely to the files, including the drag split, the C-pillar
integrals to six digits, the 13% drag excess, and 0.135% for the pixel change,
which is the capsule's own figure for the cropped and resized image in `views/`.

A imports twice. It places the 25 degree slant on the low drag side of the
critical angle, where the literature puts 25 degrees in the high drag
configuration and 35 on the low drag side. It also reports the limit cycle
amplitude as already present at iteration 3000 at the same value, where the
capsule declares 0.121% early against 0.135% at the end.

C's free answer is contradicted rather than unsupported: it describes a lateral
asymmetry while `summary.json` reports a C-pillar asymmetry near 5e-4. That
follows from the Probe 2 error, not from a separate one.

## Conflict of interest

Reader D is a Claude model, and this record was written with the assistance of
a Claude model. The probes were issued, the sessions run and the scoring
approved by the author. The transcripts are linked and the scoring is
checkable against the capsule, which is the only reason it need not be taken on
trust. The same holds for Parts 1 to 5, where the single reader is from that
family throughout.
