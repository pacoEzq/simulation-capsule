# capsule_naca0012_aoa7

NACA 0012 at 7 degrees incidence, $Re_c = 10^6$, $M = 0.043$. Steady RANS, k-omega
SST (Menter), incompressible. The mesh is the one from `capsule_naca0012_aoa5`, cell
for cell: the two extra degrees are applied by rotating the freestream at the inlet,
not by rotating the aerofoil.

Built for **Part 6b of the series, Case Comparison via Image Differencing**.

This is a variant capsule. It describes its own case like any other capsule, and it
carries `diff.json` and `diff/` on top, which describe a relation to a base capsule
rather than a case. It declares that with `"extensions": ["diff"]` in `summary.json`,
per section 3.4 of [SPEC.md](../SPEC.md).

## Contents

```
capsule_naca0012_aoa7/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars, environment, extensions
├── views/           two renders under the shared view contract
├── diff.json        what was compared, and against what
└── diff/            the two measured differences
```

No `planes/`, no `samples.csv`, no `features.json`. The part this capsule belongs to
is about comparing two cases, so the capsule carries what a comparison needs and
nothing else. See [the note in this directory](README.md).

## The pair

| File | Question |
|------|----------|
| `diff/diff_cp_nearfield.png` | Where does the pressure field change with two degrees more incidence? |
| `diff/diff_u_over_u_wake.png` | Where does the velocity field change, and how far downstream does it persist? |

The base is `capsule_naca0012_aoa5` at commit `3b434e7`, the revision that gave it
its `views/`. `diff.json` names that commit and the as-published copy beside it, so
the comparison survives the base being touched again.

## What the diff measures

Both capsules render the same camera, the same frame, the same fixed ranges, the
same width and the same filenames. Without that contract the difference measures the
renderer.

The measurement runs on grayscale copies, not on the published images. A perceptual
colormap is not a linear scale, so a difference taken in it reports palette distance
rather than field distance. The published view is blue to red; the instrument is
grey.

The top 56 rows carry the burned title, which differs between cases, so they are
cropped before anything is compared, leaving 683 rows of 739. Every non grey pixel
is then masked out, which removes the magenta body and its antialiased edge. That
leaves 690,125 pixels evaluated and 9,267 excluded.

A pixel counts as changed when it moves at least three quantisation levels, which is
$0.047$ in $c_p$ and $0.023$ in $u/U_\infty$. On that threshold the pressure field
changes over 6.17 percent of the frame and the wake over 5.88 percent, with peaks of
$0.95$ in $c_p$ and $0.20$ in $u/U_\infty$.

The instrument does not travel. The grayscale frames live in
[`diffsrc/`](../diffsrc) at the root of the repository, outside every capsule, and
`diff.json` carries the recipe to regenerate them. It also carries four hashes per
pair: two for the published views and two for the grayscale frames. All four are
checked before a measurement runs, and a mismatch stops it. A stale number returned
in silence is the failure that closes.

## The capsule checks itself

As in the base, every derived quantity recomputes from the values beside it:

- The drag decomposition closes:
  $c_{d,p} + c_{d,f} = 0.0063796 + 0.007813 = 0.0141926$ against $c_d = 0.014193$.
- $c_l / c_d = 0.73268 / 0.014193 = 51.62$, the declared lift-to-drag.
- The Reynolds number recovers from the reference block, exactly as at 5 degrees.
- The scalar deltas in `diff.json` recover from the two summaries to five decimals:
  $0.73268 - 0.52776 = 0.20492$ against `delta_cl` $= 0.2049151$.

Two degrees raise total drag by 15 percent, pressure drag by 51 percent, and lower
friction drag by 3 percent. A single drag scalar would have hidden that split, which
is the argument for carrying the decomposition.

## What this capsule is honest about

The `known_differences` array in `diff.json` is where a comparable pair admits what
it does not hold constant. Read it before quoting a pixel fraction:

- **The far field partition is rotated with the freestream**, so the inlet and
  outlet halves stay aligned with the flow. Leaving it perpendicular admitted inflow
  through the pressure outlet and moved pressure drag by 8 percent without any
  monitor complaining.
- **`wake_refine` follows the wake of the base**, so the wake of the variant leaves
  the refined band further downstream.
- **$c_p$ at the stagnation point is 1.00071**, above the incompressible bound of 1,
  measured at residuals of $2.5 \times 10^{-9}$. Structural, not convergence noise.
  26 percent is attributed to the pressure datum measured at the inlet, 0.0249 Pa;
  the remainder is unattributed between discrete stagnation point resolution and
  finite domain.
- **The two runs stopped at different iteration counts**, 2,285 for the base and
  2,567 here, both on the asymptotic monitor criteria rather than an iteration cap.
- **Both ran in double precision.** An earlier pass in single gave changed pixel
  fractions of 6.47 and 6.14 percent, because the rounding floor pushed pixels just
  past the threshold. Arithmetic precision is an invariant of a comparable pair, like
  the mesh, the camera, the ranges and the colormap.

## Convergence

Converged in 2,567 iterations with monitors flat and residuals between
$2.6 \times 10^{-8}$ and $1.1 \times 10^{-11}$. Mass imbalance is
$4.1 \times 10^{-14}$ of the through-flow. A genuine steady state, as in the base,
so the digits reported are meaningful and the diff is not measuring a phase.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it. The measurement itself
reproduces without a licence: `tools/diff_capsule_views.py` run over the frames in
[`diffsrc/`](../diffsrc) regenerates both images in `diff/` and both rows of
numbers.

## Known issues

Recorded rather than patched, as with every capsule in this directory.

- **No probe record yet.** The Part 6b probe kit ships as an appendix to the
  tutorial; the transcript is not in [`probes/`](../probes/README.md), so the column
  in the directory table is empty for this capsule.
- **The two runs were not executed by the same build.** The base ran on 2602 under
  win64 and this one on 2606 under linux. The `renders` subobject of the base
  records that its views were re-exported on the build used here and reproduce the
  published run exactly, the same 2,285 iterations with five matching residuals, so
  the pair is comparable in the one place the diff reads, which is the images.
- **`base.commit` is a 7 character short SHA.** Short SHAs are ambiguous in
  principle and the full 40 characters are the fix, due with the next revision of
  the specification.
