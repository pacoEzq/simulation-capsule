# capsule_ahmed25_re1e6

Ahmed body with a 25 degree slant, no stilts, full model with no symmetry plane,
fixed ground. Steady RANS, k-omega SST, segregated, incompressible, at
$Re_L = 10^6$. Simcenter STAR-CCM+ 2606, 5.87 million cells.

Built for **Part 6 of the series, Visualizations Designed for LLMs**, and used again
in Part 10 for the disclosure audit.

## Contents

```
capsule_ahmed25_re1e6/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars, quality checks, caveats
└── views/           six renders, 1024 px, fixed colorbars
```

No `planes/`, no `samples.csv`, no `features.json`: the part this capsule belongs to
introduces the views layer, and the capsule carries what existed when it was built.
See [the note in this directory](README.md).

## The six views

Each answers a question no scalar in `summary.json` can:

| File | Question |
|------|----------|
| `view_slant_separation.png` | Does the flow stay attached over the slant? |
| `view_wake_topology.png` | What is the structure of the near wake? |
| `view_cpillar_symmetry.png` | Are the C-pillar vortices symmetric? |
| `view_floor_footprint.png` | Where does the body load the ground plane? |
| `view_limit_cycle_a.png` | What does one instant of the limit cycle look like? |
| `view_limit_cycle_diff.png` | How much does the solution move between instants? |

Titles are burned in, colorbars are fixed and lightness-monotonic, and cameras are
declared in the tutorial. The images carry no EXIF and no embedded metadata.

## What this capsule is honest about

The `caveats` block in `summary.json` is not decoration. Read it before quoting any
number:

- $C_d = 0.322$ against roughly 0.285 in experiment, about 13 percent high. Steady
  RANS over-separates the 25 degree slant, and the capsule says so rather than
  presenting the number bare.
- $C_l$ drifts within about $3 \times 10^{-4}$ across the limit cycle, so the fourth
  digit means nothing.
- The floor $y^+$ maximum of 4.03 sits at a domain corner, far from the body. A
  single worst-case scalar would have been misleading, which is why the location is
  declared and `view_floor_footprint.png` exists.
- The run was executed on a workstation rather than the cluster. Scalars reproduce
  within the declared bands.

## Convergence

Stopped at 6,000 iterations on the iteration limit, in a pseudo-transient limit
cycle rather than a converged steady state. The cycle is quantified: 0.135 percent
of pixels change between frames ten iterations apart, measured on the cropped and
resized image that ships in `views/`, against 0.121 percent in an earlier window.
The cycle is stationary, not decaying.

That number is the noise floor. A difference between this capsule and another has to
clear it before it means anything.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it.

## For `capsule_ahmed25_re1e6.md`

```markdown
## Known issues

Found by the [Part 6 probe test](../probes/part-6_ahmed25_re1e6.md) and by
`tools/check_capsule.py`. The capsule is frozen, so these are recorded rather
than patched.

- **No view title declares the regime.** The burned in titles carry the case,
  the plane, the field, the range and the orientation. One reader out of four
  read `view_limit_cycle_diff.png` as evidence of a time accurate run, inferring
  the regime from the filename because the image itself does not defend it, and
  every later error in that session followed. The view contract now requires the
  title to declare the regime and the iteration span.
- **Reference quantities ship as bare numbers.** `length_L`, `velocity_U`,
  `density_rho` and `viscosity_mu` carry no unit flag. The validator warns on
  all four.
- **The scalars are a phase, not a fixed point.** The capsule reports the limit
  cycle and its amplitude, so this is disclosed rather than hidden, but it bears
  restating next to the numbers: the last digit of any scalar here moves with
  the phase of a weak wake oscillation.
```

