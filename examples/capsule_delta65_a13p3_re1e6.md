# capsule_delta65_a13p3_re1e6

Sharp-edged delta wing, 65 degree leading-edge sweep, $\alpha = 13.3°$,
$Re_{c_r} = 10^6$. Steady RANS, k-omega SST, incompressible, full model with no
symmetry plane, 6,491,106 cells. Nondimensional by construction with
$\rho = U_\infty = c_r = 1$.

Built for **Part 5 of the series, Semantic Feature Extraction**.

## Contents

```
capsule_delta65_a13p3_re1e6/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars
├── planes/          three chordwise stations, CSV and PNG per plane
└── features.json    named vortex cores with their properties
```

The first capsule to ship `features.json`, and the reason the layer exists.

## Why the full model

No symmetry plane, at twice the cell count. The reason is verification: with both
halves solved independently, port and starboard symmetry becomes a measurement
rather than an imposition.

It holds. Circulation at $x/c_r = 0.6$ is $+0.26414$ starboard against $-0.26377$
port, and the full-plane sum is $-1.2 \times 10^{-4}$. Core axial velocities agree
to five decimals, 1.30790 against 1.30761. Nothing forced those numbers to match.

## What features.json carries

Four vortices: two counter-rotating primaries plus the secondary pair. The adjacency
of primary and secondary is the point, because it is what makes the threshold
failure mode real rather than hypothetical.

Both thresholds travel with the artifact. The export floor bounds file size; the
per-station relative threshold $\beta$ defines the set that is actually integrated.
Reporting one without the other would make the features unreproducible.

The extraction is in
[`tools/extract_features_delta.py`](../tools/extract_features_delta.py), with two
diagnostics beside it for when a station misbehaves.

## The caveat the capsule declares about itself

$c_{p,\max}$ on the wing is 1.302, above the incompressible stagnation bound of 1.
The capsule flags it rather than clipping it: the sharp leading edge is a geometric
singularity, and the value is a known limitation of resolving it on a finite mesh.

The same singularity is why the feature extraction needs a geometric cut before
taking the per-station maximum. On the edge, $Q$ reaches values orders of magnitude
above the vortex core, so a relative threshold anchored to the station maximum would
lock onto the edge and never see the feature.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it.
