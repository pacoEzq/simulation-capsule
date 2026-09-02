# Probe record: capsule_ahmed25_re1e6

Four frontier models from four families read the same capsule and answered the
same three probes plus one free question. The capsule was the published
archive, whole and unedited, in every session.

Reader D ran first and its answers are the ones published in Appendix B of
Part 6. The other three ran three days later against the same kit, on the same
archive, with nothing changed for any of them. The capsule is solver agnostic
and model agnostic by construction, which is a design constraint of the series
rather than a result to be discovered. Until this session it had been asserted
and not tested: one capsule, one reader, one result. Three more families is
the test.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | xAI Grok | 4.6 | Expert | 2026-09-02 | [shared](https://grok.com/share/c2hhcmQtMg_cea13d6b-48f8-4b5e-92cf-af0aa88c0b37) |
| B | OpenAI GPT | 5.6 Luna | default | 2026-09-02 | [shared](https://chatgpt.com/share/6a97e20e-06a4-8328-b7ce-ed5f3a292e49) |
| C | Google Gemini | 3.1 Pro | default | 2026-09-02 | [shared](https://share.gemini.google/AxZbDujCMwxp) |
| D | Anthropic Claude | Fable 5 | Expert | 2026-08-30 | [shared](https://claude.ai/share/aa407360-42bd-4c50-bf60-eab8983d4d3a) |

Letters are used everywhere below. The table exists so the reading can be
checked, not so the readers can be ranked.

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
| Probe 1, vorticity side | PASS | PASS | not scorable | PASS |
| Probe 2, regime and model | PASS | PASS | FAIL | PASS |
| Probe 3, reverse flow extent | PASS, 0.40 L | PASS, 0.50 L | FAIL, 0.25 L | PASS, 0.35 to 0.45 L |
| Free answer | traceable, one import | traceable | contradicts the capsule | traceable |

### Probe 1

A, B and D answered port, y greater than zero, which is what the view shows and
what the burned in PORT and STARBOARD anchors label. D committed to the
viewpoint before committing to a side, noting first that the view looks from
downstream, which is the reasoning the anchors exist to support.

C answered "the right side", from an observer position it chose for itself. The
answer cannot be scored without importing an assumption the capsule does not
make. Not a wrong answer. An answer that declined the only labels that would
have made it checkable.

### Probe 2

The setup report says steady segregated SIMPLE with SST Menter k-omega. A cited
the continuum name and the Steady model straight out of the file. D resolved
the tension between a steady solver and a moving diff on its own, naming the
state a pseudo-transient limit cycle in which the solution never settled to a
fixed point. B reached the same reading from the other end, calling the diff a
persistent limit cycle rather than exact iteration to iteration steadiness.
All three concluded that the scalars are a phase of a weak wake oscillation
rather than a fixed point, and that the last digit carries no meaning.

C answered that the run is time accurate under an unsteady model, URANS or DES,
and that the scalars in `summary.json` must therefore be read as time averages.
Every clause of that is contradicted by `setup.txt`, and the error propagates
through the whole free answer. Finding 1 is about why it happened, since the
cause is in the capsule.

### Probe 3

The script that measures reverse flow on this view returns 0.45 L, from
x/L 1.453 minus a base at 1.004. Against that:

| Reader | Estimate | Delta | Method declared |
|---|---|---|---|
| D | 0.35 to 0.45 L | upper bound exact | yes, pixel spans, 170 to 200 px against a 455 px body |
| A | 0.40 L | 11% low | yes, span against body length |
| B | 0.50 L | 11% high | yes, u/U zero crossing against body length |
| C | 0.25 L | 44% low | no anchor named |

Three readers declared a method and landed between 0.35 and 0.50 L around a
measured 0.45. The two point estimates sit eleven percent either side. That is
a band, measured by four independent readings rather than argued, and it is the
number to quote from now on when this series claims that a view can be read
quantitatively.

The reading that fell far outside is also the one that named no anchor. The
shallow floor cell fades before the dark blue does, which is why an honest
method tends to land low; B is the exception, and it read the zero crossing
rather than the colour.

## Findings

### 1. A view has to declare its regime, not just its field

C did not invent the unsteady run. It inferred it from the name
`view_limit_cycle_diff.png`, which is a reasonable inference from a filename
and the wrong one here. The capsule holds the truth in `setup.txt`, but the
view pulls the other way, and its burned in title does not defend it: the title
declares case, plane, field, range and orientation, and says nothing about the
regime.

So the view contract gains a rule. **The title declares the regime and the
iteration or time span it was taken from.** A steady run that ships a diff
between two iterations must say so inside the image, because the image is the
artifact that travels furthest from its context and the filename is the part a
reader sees first. A view that can be read as transient without being transient
is a view that misleads by omission.

Three readers out of four recovered the regime from `setup.txt` anyway. That is
not a defence of the view. It means the view was carried by a neighbour, and
the probe found the one reader that did not lean on it.

### 2. The visual measurement band is eleven percent

Recorded above. It replaces the single reader estimate from Appendix B with a
bracket built from four readings.

### 3. The kit needs a file manifest

Adding "before answering, list the files you opened" to the top of the kit
would have surfaced finding 1 in one line, before any answer was read. It is
now part of the template.

## Audit note

Everything in B and D traces to the files. B quotes the drag split, the
C-pillar vorticity integrals to six digits, the 13% drag excess against
experiment, and 0.135% for the pixel change, which is the capsule's own figure
for the cropped and resized image that ships in `views/`. D tied the drag
error, the limit cycle and the location of the unsteadiness into a single
statement that no one file contains, which is the thing this part of the series
was arguing for.

Two claims in A come from training rather than from the files. A places the 25
degree slant on the low drag side of the critical angle; the Ahmed body
literature puts 25 degrees in the high drag configuration and 35 degrees on the
low drag side of the transition, so the claim is not traceable to anything in
the capsule. A also reports the limit cycle amplitude as already present at
iteration 3000 at the same value, where the capsule declares 0.121% in the
earlier window against 0.135% at the end. Close, and not the same number.

C's free answer is contradicted rather than merely unsupported. It describes a
lateral asymmetry and an alternating vortex dominance, while `summary.json`
reports a C-pillar asymmetry near 5e-4, symmetric to four digits. That follows
from the Probe 2 error and not from a separate one.

## Conflict of interest

Reader D is a Claude model, and the audit above was written by a Claude model.
D's conversation is linked like every other, the scoring is checkable against
the capsule by anyone, and D was scored three days before the other sessions
existed. Even so, the reader that most deserves an independent check is the one
from the family doing the checking. Read D's row with that in mind, and note
that the same applies to the records for Parts 1 to 5, where the single reader
is from that family throughout.

## What this record supports

Three readers out of four, from three families, recovered the regime, the
turbulence model, the convergence caveat and a quantitative wake measurement
from the capsule alone. The fourth failed on one artifact whose name and title
disagreed with the setup report, and every later error followed from that one.

The claim this supports is about the format, not about any reader. It is also
the reason the finding above is worth more than the score line.
