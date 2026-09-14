# Probe record: Part 6b, capsule_naca0012_aoa5 against capsule_naca0012_aoa7

Two readers from two families received both archives in one prompt, whole and
with no other context, and worked through the same five rounds. Both anchored
the comparison from `diff.json` key by key, reproduced the lift slope and the
aerodynamic centre with the arithmetic on show, said where the pressure field
would change before opening `diff/`, named the same refinement band as the
likely bias in the instrument, and refused the four questions the files cannot
answer. Neither made an arithmetic error.

They parted on one question. Asked for the changed fraction under a strict cut,
one reader recovered it from the levels of the difference image, which is
published at gain 6, and the other declined for want of a histogram, with that
gain listed in its own inventory. That is a difference of resolution, not of
correctness.

The two runs are not a head to head. A ran at medium effort and B at Expert, on
different days, under the same kit. A run in A's family at high effort is
pending.

## Provenance

| Reader | Family | Version | Tier | Date | Conversation |
|---|---|---|---|---|---|
| A | Anthropic Claude | Fable 5.1 | medium | 2026-09-13 | [shared](https://claude.ai/share/723597ca-b5c9-4c28-9bd8-a00c912df53c) |
| B | xAI Grok | 4.6 | Expert | 2026-09-14 | [shared](https://grok.com/share/c2hhcmQtMg_8286d16e-65d7-4560-9fee-b9cfa3bc051d) |

Letters are used below. The table exists so the reading can be checked, not so
the readers can be ranked, and here they cannot be: the tiers differ.

## The kit as issued

Kit v2. Two archives, base first: `capsule_naca0012_aoa5.zip` with
`summary.json`, `setup.txt` and `views/`, then `capsule_naca0012_aoa7.zip` with
the same plus `diff/` and `diff.json`. The opening instruction travelled with
the archives; the rounds followed. The expected answers and tolerances the
operator graded against were never shown to the reader and are not reproduced.

```text
You are reviewing two CFD cases you cannot open. Attached are their Simulation
Capsules. Each has summary.json (global scalars, nondimensional), setup.txt
(trimmed configuration report, the only file in native units) and views/
(renders under a fixed view contract). The second capsule also carries diff/
and diff.json, a comparison layer that names one capsule as base and the other
as variant. Ground rules: answer only from the files; if a value cannot be
derived from them, say so instead of estimating from general knowledge; show
the arithmetic behind any number you compute; when two files can answer the
same question, use both and say whether they agree. Begin by listing every file
you received, in the order you read it, and what each claims to be.

Round 1, anchoring.
1.1 Which capsule is the base, which the variant, and what single parameter
    separates them, in what units?
1.2 By how much did total, pressure and friction drag change, and which one
    moved the other way?

Round 2, the reader audits the physics.
2.1 Compute the lift slope from the two capsules and compare it with thin
    airfoil theory.
2.2 Pressure drag rose 51% and friction drag fell 3%. Is that coherent with two
    more degrees of incidence? Give the mechanism behind each sign.
2.3 delta_cm about the quarter chord is minus 0.0021 for delta_cl of +0.205.
    What does that say about the aerodynamic center?
2.4 The two capsules stopped at 2285 and 2567 iterations. Does that difference
    matter for the comparison?

Round 3, predict, then look.
3.1 Without opening diff/, say where you expect the changed Cp pixels to
    concentrate, and why. Then open diff_cp_nearfield.png and compare.
3.2 6.17% of the Cp field changed but only 5.88% of the u/U field. Why would
    pressure respond more widely than velocity?
3.3 The largest Cp excursion is 61 levels, 0.953 in Cp. Where in the field
    would you expect it, and can you confirm it from the files?

Round 4, the reader audits the instrument.
4.1 Which entry in known_differences could bias the changed pixel fraction, and
    how would you test it with a third capsule?
4.2 Which image was the subtraction measured on, and where does that image
    live?
4.3 The threshold is 3 levels with the rule at or above. What fraction would
    you expect with a strict above rule, and why does the manifest declare the
    rule?

Round 5, traps, double weight.
5.1 At what angle does this airfoil stall?
5.2 What is the shedding frequency in the wake of the variant?
5.3 Which of the two meshes is finer near the trailing edge?
5.4 By what fraction did the colored views change between the two capsules?

Epilogue, not graded. If you had the solver, what would you check first, and
why?
```

## Results

| | A | B |
|---|---|---|
| 1, anchoring | PASS | PASS |
| 2, physics audit | PASS | PASS, with a frame caveat |
| 3, predict then look | PASS | PASS |
| 4.1, bias and its test | PASS | PASS |
| 4.3, strict cut | answered, 2.56% and 2.24% | declined, bounded |
| 5, traps | 4 of 4 refused | 4 of 4 refused |
| Epilogue | traceable | traceable, one claim beyond the files |

**Round 1.** Both named `aoa5` as base and `aoa7` as variant, the parameter
`angle_of_attack_deg` from 5.0 to 7.0, implemented as the freestream direction
on an identical mesh, and the drag split from `scalar_deltas`: total up 15.3%,
pressure up 51.2%, friction down 3.4%.

**Round 2.** Both turned 0.2049 over 2 degrees into 0.1025 per degree, 5.87 per
radian, 93% of 2 pi, and placed the aerodynamic centre at x/c = 0.2603 from
dCm/dCl. B added that the moment origin in `setup.txt` is
[0.2490487, -0.0217889, 0], the quarter chord of the airfoil already inclined,
and that at 7 degrees `cl` follows the wind axes while `cm` is taken about
laboratory z, so that slope mixes frames. Both read the stopping criteria
rather than the iteration counts.

**Round 3.** Both predicted the suction lobe on the upper surface, its mirror
on the lower surface, the leading edge and the wake, and both said afterwards
that the concentric rings at the nose were not predictable from the coloured
views: they show only when the difference is stretched at gain 6. Both took
6.17% and 5.88% from `diff.json` together with their thresholds.

**Round 4.** Both chose the `wake_refine` band as the likely bias and proposed
a third capsule with the band rotated. A added a fourth run at 5 degrees on
that mesh so that `mesh_identical` stays true; B proposed a wide box for both
angles, which is the same idea. B noted that the mask removes the magenta body
but not the faces of the refined band in the wake, which is why a pair with no
change of incidence is the control.

**4.3, the strict cut.** The kit expected a refusal. A recovered the levels of
the two difference images, published at gain 6, and answered 2.56% for Cp and
2.24% for u/U, with 3.60% and 3.64% of the pixels sitting exactly at three
levels. B declined: the manifest publishes no histogram of deltas by level. It
bounded the answer, the fraction at or above three minus the row at exactly
three, and argued that the row is populated. It had `output_gain: 6` in its own
inventory and did not use it.

**Round 5.** Four refusals from each reader: stall angle, shedding frequency,
which mesh is finer, and the changed fraction in the coloured views. B added
that a band aligned with the base wake does not make one mesh finer than the
other, since the band occupies the same place in space.

## Findings

**1. A field named `u_over_u` reads as streamwise and is defined on laboratory
x.** `setup.txt` defines it as `$$Velocity[0] / ${u_ref}`. The variant's
freestream is rotated 2 degrees, so its view is not streamwise. The burned
title says as much; the field name says otherwise. From B.

**2. `cl` and `cm` live in different frames.** The moment origin is
[0.2490487, -0.0217889, 0], the quarter chord of the airfoil inclined 5
degrees, not [0.25, 0]. At 7 degrees `cl` is taken along the wind axes and `cm`
about laboratory z, so dCm/dCl, and the aerodynamic centre both readers derived
from it, mix frames. From B.

**3. The provenance sentence in `diff.json` overreaches.** It says both cases
ran in double precision on 2606. The base `setup.txt` is a 2602 win64 session
of 16 July. The sentence has to narrow to the instrument and to the variant.
From B.

**4. The step limits differ and are undeclared.** Maximum Steps is 3000 and
5000 in the base against 12000 in the variant, and `known_differences` does not
list it. Neither limit was reached. From B.

**5. The inlet lists no part surfaces.** In both `setup.txt` the inlet carries
`Part Surfaces: []` and the outlet `[fluid.freestream]`, 38 faces each. Whether
the trim or the mesh dropped it is open. From A.

**6. A scalar the summary lacks.** The minimum normal velocity on the outlet
faces, so that a boundary admitting backflow shows up as a number. The global
mass imbalance, 1e-13, does not catch it. From A, and first in B's epilogue.
A candidate, not a defect.

**7. The two thresholds are one cut.** 2 times 0.0234375 is 0.046875, because
the two ranges are in ratio 2 and dCp is -2 d(u/U) near the freestream under
linearised Bernoulli. That is why 6.17% and 5.88% land so close. The 0.29 point
residual is the suction peak, where the real multiplier is 3.35 and 3.96. Not a
defect; an explanation the record did not have before. From B.

## Audit note

Both readers trace to the files: Re as 1.18 times 15 over 1.77e-5, which gives
1e6; the variant inlet vector at 15.000000 m/s and 2.000 degrees; force axes
[cos 2, sin 2] and [-sin 2, cos 2]; q = 132.75 Pa; cd equal to cd_pressure plus
cd_friction in both capsules; 15.3%, 51.2% and -3.4%; 1024 by 683 = 699,392 =
690,125 + 9,267; level sizes 0.015625 and 0.0078125; thresholds 0.046875 and
0.0234375; maxima 0.953125 and 0.203125; and the 26% of `cp_stagnation` traced
to the pressure datum, 0.0249 over 132.75 = 1.876e-4 against 0.26 times 7.1e-4
= 1.846e-4. Not one arithmetic error in either transcript.

One statement without support in the files, from B: that the two thresholds
were matched on purpose under Bernoulli. The factor of 2 may be by design or by
chance. The mathematics holds; the intent is not recorded anywhere in the
capsules.

## Conflict of interest

Reader A is a Claude model, and this record was written with the assistance of
a Claude model. The probes were issued, the sessions run and the scoring
approved by the author. The transcripts are linked, and every figure above is
checkable against the two capsules under `examples/`.
