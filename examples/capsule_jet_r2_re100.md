# capsule_jet_r2_re100

Jet in crossflow at velocity ratio $r = 2$ and $Re_D = 100$, laminar and steady.
Jet issues along $+z$ from a floor orifice of unit diameter, crossflow along $+x$.
436,390 cells.

Built for **Part 4 of the series, Volumetric Importance-Based Sampling**.

## Contents

```
capsule_jet_r2_re100/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars, sampling law, verification chains
├── planes/          three sections, CSV and PNG per plane
└── samples.csv      800 importance-weighted volume samples
```

The richest `summary.json` of the collection, and the only one carrying a `schema`
key. It is also the first capsule to ship a volumetric layer.

## The sampling law travels with the sample

`samples.csv` holds 800 cells drawn without replacement with probability
proportional to $w_i = V_i \, g_i^{\alpha}$, where $g$ is the velocity gradient
magnitude and $\alpha = 1$. The field, the exponent, the count and the seed are all
in `summary.json` under `sampling`, so the draw is reproducible and the bias is
legible.

Each row carries a `vol_weight` value, self-normalized so that the column sums
to the domain volume. Writing $w_i$ for the weight of row $i$, $\sum_i w_i$
recovers the domain volume and $\sum_i f_i\,w_i$ estimates the volume integral
of $f$ over the domain. That makes the cloud usable for arithmetic rather than
only for looking at. Coverage before normalization is 0.815. At this $\alpha$
the far field is sampled thinly, which is the point, and the number says how
thinly.

## Verification chains

This capsule checks itself in three independent places:

- Jet mass flow of 1.5787 against the analytic $(\pi/4)r = 1.5708$ for the parabolic
  profile, a relative error of 0.5 percent.
- Net mass balance over all seven boundaries closes to $2.1 \times 10^{-11}$ of the
  through-flow.
- Counter-rotating vortex pair circulation at $x/D = 10$ with a full-plane symmetry
  check of $-7 \times 10^{-5}$, a ratio of $3.3 \times 10^{-5}$ against the value.

## A discarded candidate, kept in the file

`bl_thickness_D_at_x_minus1` has a null value and the status `discarded`, with a
note explaining that $u/U$ never reaches 0.99 on that line and the profile is not
monotonic.

This is worth more than it looks. A scalar that was tried and rejected, left in the
capsule with its reason, tells a reader something a clean file cannot: that the
quantity was considered and why it does not apply here.

## Two defects corrected on packaging

The version of this capsule attached to the published tutorial has two problems that
are fixed here:

- `summary.json` carried a trailing comma before its final brace, which makes the
  file invalid JSON and unreadable by any strict parser.
- The plane images were named `jet_r2_re100_scene_*.png` while their CSVs were named
  `plane_*.csv`, so the pairs did not share a stem as the specification requires.

Both are packaging slips rather than design decisions. The content is unchanged.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it.

## Known issues

Found by the [Part 4 probe test](../probes/part-4_jet_r2_re100.md) and by
`tools/check_capsule.py`. Two defects the probe found have since been fixed in
place; the rest are recorded rather than patched.

- **The handedness of the coordinate triad is nowhere declared.** No image glyph
  shows the third axis either, so the sign of the counter rotating pair
  circulation is only interpretable by assuming a right handed system.
- **Machine time left the capsule.** The `performance` block held
  `solver_cpu_time_s` 1077.774, `solver_elapsed_time_s` 1077.753 and
  `partitions` 1. Machine time is not a flow quantity, so it was removed here
  and belongs in `manifest.json` from Part 7 on. The published capsule still
  carries it, with a dimensional flag of its own, which contradicts the claim in
  `reference.note` that the reference block is the only dimensional one.
- **`u_U` means two different things.** `setup.txt` defines it as the streamwise
  velocity component; `summary.json` reports `u_U_at_5_0_2p5` built from
  velocity magnitude. Disclosed in the files, and still a trap.
- **The sample cannot be redrawn from the capsule alone.** Only the 800 winners
  ship, not the per cell volumes and gradients they were drawn from, so
  `coverage_prenormalization` cannot be checked here.
- **This capsule is over budget.** 66,000 tokens against a target of 60,000, and
  it got there by carrying a full plane set and an 800 point volume cloud at the
  same time. See [token-ledger.md](token-ledger.md).
- **Fixed since publication.** A trailing comma made `summary.json` fail strict
  JSON, and the plane images did not share a root with their tables. The probe
  reader named the first as the one packaging change it would make, and it
  shipped anyway. Both are now checks in `tools/check_capsule.py`.

