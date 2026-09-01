# capsule_cube_re200

Flow around a cube at $Re_D = 200$. Laminar, steady, constant density, 3D, 405,673
cells, domain from $-5D$ to $15D$ downstream and $\pm 5D$ laterally.

Built for **Part 3 of the series, Sections and Planes: the CSV plus Image Pair**.

## Contents

```
capsule_cube_re200/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars
└── planes/          four sections, CSV and PNG per plane
```

The symmetry plane plus three downstream stations at $x/D = 1$, 2 and 4. One
symmetry plane shows the separation and the recirculation bubble; the transverse
stations show what happens to the wake as it travels.

## The pair is the unit

Each plane ships twice. `plane_y0.csv` carries the numbers, `plane_y0.png` carries
the topology. Neither alone is the artifact, which is the whole argument of the part
this capsule belongs to.

The CSV headers are self-describing and say so before the first row: the plane held
constant, the grid and its spacing, how many nodes carry data out of how many were
requested, the print precision, and the word `nondimensional`. A model reading
`plane_y0.csv` never has to be told what it is looking at.

Note that `plane_y0.csv` reports 419 of 429 nodes. Ten grid points fall inside the
cube and carry no data. The header says so rather than silently shipping a shorter
file.

## What the capsule measures

The recirculation length is $2.156\,D$ from the rear face, defined by the threshold
$u/U < 0$ and declared with that definition beside it. A number like this means
nothing without its criterion, so the criterion travels with it.

$c_l$ and $c_y$ are $4 \times 10^{-5}$ and $1.3 \times 10^{-4}$, and the capsule
labels them as what they are: symmetry checks expected near zero, not results.

The `cp` note is worth reading. The gauge zero sits at the pressure outlet, so the
stagnation value carries the domain pressure drop and lands at 1.045 rather than
exactly 1. Declared rather than quietly rounded.

## No turbulence block

There is no `wall` block and no $y^+$. The capsule says why: a laminar solve has no
turbulence model and no wall treatment to audit. An absent key with a stated reason
is better than a key filled with a placeholder.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it.
