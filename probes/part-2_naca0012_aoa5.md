# Probe record: Part 2, capsule_naca0012_aoa5

Part 2 added `summary.json`, so the capsule had two files and the probe could
test something new: whether a reader can hold two artifacts against each other
and notice when they disagree.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| Claude | Anthropic | frontier at the time | [author: date] | [shared](https://claude.ai/share/76f3cada-470a-4475-812d-2878ab491456) |

## The kit as issued

Round one:

```text
Attached are the two files of a Simulation Capsule, a self-contained,
token-budgeted distillation of a CFD case: setup.txt (how the case was
configured) and summary.json (how it came out). Using these files alone, and
quoting values exactly as written where they exist:
1. In two sentences: what is being simulated, and what are the headline
   results?
2. Did the run converge? List every piece of evidence you find, in either file.
3. Where exactly is the pitching moment evaluated? Quote the coordinates from
   setup.txt and check whether that point is consistent with the rest of the
   capsule.
4. Verify the Reynolds number using only values present in the capsule, showing
   your inputs.
5. If the lift and drag criteria had never been satisfied, when would the run
   have stopped? Quote every stopping criterion you find, with its settings.
```

Round two, the reviewer frame, with the same four part structure used in Part 1
and the same closing instruction not to invent values.

## Result

Five for five. Two answers are worth recording in detail.

**The moment origin.** The capsule reports `cm_quarter_chord` about an axis
origin at [0.2490487, -0.0217889, 0]. That is not the quarter chord of an
unrotated unit chord, and it looks wrong until you work out that the incidence
was built by rotating the airfoil rather than the flow. The reader recovered
that: rotating (0.25, 0) by 5 degrees about the leading edge reproduces the
origin to seven decimals. The angle of attack appears nowhere in `setup.txt`,
so this trigonometric coincidence is the only evidence inside the setup that
the case is at 5 degrees at all.

**Reynolds number.** Closed exactly from four values present in the capsule.

The reviewer round ran eleven independent closures, every one from file values
or from correlations it declared as external, and all eleven passed. Drag
decomposition to rounding, lift to drag exact, the near wall closure inverting
`yplus_avg` back to a skin friction coefficient inside the flat plate band, and
friction drag reconstructed from that coefficient and the wetted perimeter to
within two percent of the reported value.

## Defects found in the capsule

- `setup.txt` carries two different step limits, 3000 under the Steady solver
  and 5000 under Stopping Criteria. Neither affected the run, which stopped at
  2285 on the asymptotic criteria, but a capsule that can hold two contradictory
  values lowers the trust owed to any single sourced number in it.
- The angle of attack and the far field radius exist only in `summary.json`.
  `setup.txt` carries no geometry or extents, so domain size effects on lift
  cannot be checked from the capsule.
- The fifth decimal of `cl` sits inside the convergence band the run stopped
  on. The reader recommended quoting four.

## Audit note

Exemplary, and worth quoting as the origin of rule 3. The reviewer round opened
by naming the external correlations it was about to use, thin airfoil theory,
flat plate skin friction, Sutherland and the NACA 0012 wetted perimeter, and
stating that they are not in the files. Every later use was tagged as estimate
or plausibility rather than as a check. That separation was invented here, by a
reader, three parts before the series wrote it into a rule.
