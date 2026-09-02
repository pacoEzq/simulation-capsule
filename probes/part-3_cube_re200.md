# Probe record: Part 3, capsule_cube_re200

Part 3 added `planes/`, the CSV and PNG pairs. The probe could now ask the
question the part exists to answer: what does the image give you that the table
does not, and the other way round.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| Claude | Anthropic | frontier at the time | 2026-07-24 | [shared](https://claude.ai/share/323cc2bd-ca93-4a2e-9431-94fe098bba13) |

## The kit as issued

Round one asked six questions covering geometry and regime, bubble closure from
`plane_y0.csv`, that closure against the `summary.json` value, a deficit ranking
across the three transverse planes, mirror symmetry, and a provenance question
requiring the reader to name the file behind every previous answer.

Round two:

```text
You are a senior CFD engineer reviewing this capsule before trusting any
conclusion drawn from it. Using only the attached files:
1. Five lines for a project manager: what was simulated and how it came out.
2. What can you say about this flow that is in the images but not in the
   tables, and what is in the tables but not in the images?
3. Name three questions about this case these files cannot answer, and for
   each, the smallest artifact you would ask for.
4. Anything here that looks inconsistent, or that you would want confirmed
   before believing it. Do not invent values. If something is not in the files,
   say so.
```

## Result

Six for six. The closure measurement is the one that matters: interpolating the
centreline sign change in `plane_y0.csv` gives 2.18 D behind the rear face,
against 2.156 D in `summary.json`. The reader did not call that a discrepancy.
It read `setup.txt` to find how the summary number was made, a threshold part
tagging reversed cells at 0.0625 D wake refinement, worked out that the two
estimators should differ by up to about one wake cell, and showed that 0.026 D
is half of one. Two estimators of the same quantity, agreeing.

Question 2 produced the answer this part was written to produce. In the images
and not the tables: the near wall band inside the 0.25 D gap to the first node,
the realized mesh, and a slightly wider window. In the tables and not the
images: three of the four shipped fields, since every PNG is `u_U`; the sign
change at closure, invisible in a colormap; and the fact that no sampled value
reaches the colorbar clip limits, which an image reader cannot know.

## Defects found in the capsule

- **The transverse plane PNGs carry no spatial anchor.** No geometry and no
  axes in frame, so their window cannot be verified against the tables. The
  `y0` view is calibratable from the cube; the others are not. This is the
  direct ancestor of the declared camera and spatial anchor rule in the Part 6
  view contract.
- The `y0` grid blanks one node whose mirror is present, a harmless but
  asymmetric dropout.
- An orphan Plane Section derived part duplicating the `y0` orientation rides
  along in `setup.txt`.
- `x_reattach` takes the extreme x over all reversed cells in the whole domain,
  unguarded, so any stray reversed cell near the outlet would inflate it
  silently. The value is sound here because the plane data agrees.
- The trimmed setup states no domain extents and no cube coordinates. The cube
  at origin is inferred from a report definition.

## Audit note

The reader opened by saying it would mark the places where it leaned on outside
knowledge, and marked two: that Reynolds 200 sits near where compact bluff body
wakes lose steadiness, and a boundary layer scaling used to judge the prism
stack. Both were labelled as outside the files, and the first was named the
single biggest threat to every number in the capsule, which is a judgement the
capsule itself cannot make.
