# Probe record: Part 5, capsule_delta65_a13p3_re1e6

Part 5 added `features.json`, the named semantic layer. The probe had to test
something harder than retrieval: whether a reader can tell a property of the
flow from an artefact of the extraction method that produced the file.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| Claude | Anthropic | frontier at the time | [author: date] | [shared](https://claude.ai/share/feab090c-2f45-4615-9810-18d5a6b4e54a) |

## The kit as issued

Nine probes in three rounds of three. Round one on the whole capsule, round two
from `features.json` alone, round three cross checking `plane_x06.csv` against
`features.json`. The framing line required the reader to name the file and the
key, field or row behind every answer.

The decisive probe:

```text
1b. verification.symmetry reports rel_diff = 0.086 at x = 0.30, while
rel_diff_station_sum there is 0.0024. Is the flow asymmetric at that station,
or is the extraction? Explain the mechanism using the method block.
```

## Result

Nine for nine, with no fabricated values.

Probe 1b is the one the part was built around. The reader answered that the
extraction is asymmetric and the flow is not, then derived the mechanism from
the `method` block rather than asserting it: the per station relative threshold
anchors to whichever side is momentarily stronger, so the weaker side is
clipped harder at the same absolute level, and the sign partition rule sends
the clipped fragment into a separate cluster. The primary loses circulation,
the side does not. It then corroborated with the threshold free and tracking
free chain, agreeing to about 0.1%.

Probe 3b tested the same discrimination in the other direction. The feature row
carries a peak Q of 5507 while nothing in the plane exceeds about 3060. The
reader reconciled them from the plane header alone: every plane value is an
average over roughly 47 vertices, a core peak is about one bin wide, and
attenuation to 55% is what a coarse export does to a sharp peak. Same core, two
resolutions.

Probe 3c asked why an intense near surface vorticity sheet gets no feature. The
reader quoted the three settings responsible, the Q criterion excluding shear
dominated regions with negative Q, the z cut, and the post clustering floors,
and noted that where the sheet does roll up into rotation dominated structure
it is captured, as the secondary vortex pair.

## Defects found in the capsule

- The `u_ax_core` scalars in `summary.json` are reports whose probe location is
  not declared anywhere in the capsule. The reader showed the reported value
  sits near the x = 0.30 station rather than the x = 0.60 cluster, and said the
  capsule does not specify which. Registered as a Part 7 debt.

## Audit note

Nothing imported. Every number is quoted with its file and key, every derived
quantity shows its inputs, and where the reader computed a mean over ten
stations it flagged that the file's own all station mean differs and why.

One limit under the current rules: an earlier version of this kit attached only
part of the capsule and was corrected mid session to attach all eight files.
The record above is the corrected run. The rule that a capsule travels whole
was written because of this session.
