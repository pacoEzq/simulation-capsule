# Tools

Python that builds capsule artifacts, and the diagnostics that were needed when an
artifact came out wrong.

Standard library plus numpy. No packaging, no install: each script runs on its own
and prints what it did.

## Producers

These write files that go into a capsule.

| Script | Produces | From |
|--------|----------|------|
| `sample_capsule_volume.py` | `samples.csv` | [Part 4](../examples/capsule_jet_r2_re100.md) |
| `extract_features_delta.py` | `features.json` | [Part 5](../examples/capsule_delta65_a13p3_re1e6.md) |

`sample_capsule_volume.py` draws an importance-weighted sample of a volume export,
without replacement, using the Efraimidis-Spirakis exponential-key method. The
`--alpha 0` case reproduces volume-uniform sampling, which is the naive baseline
worth running once to see what the weighting buys.

`extract_features_delta.py` turns a thresholded cell cloud into labeled vortex-core
polylines and per-station scalars. It carries the two-threshold rule the
specification requires: an export floor that bounds file size, and a per-station
relative threshold that defines the set actually integrated.

## Diagnostics

These write nothing. They answer a question about a case or a method when the
producers disagree with expectation.

| Script | Answers |
|--------|---------|
| `diagnostics/check_mirror_mesh.py` | Is this mesh actually a mirror, or only symmetric in bulk? |
| `diagnostics/diag_station_clusters.py` | Did the extractor split a vortex, or did the tracking pair a station wrong? |

Both exist because of a specific failure. `check_mirror_mesh.py` was written after a
chamfer applied to two leading edges at once came out asymmetric, and it separates
three things a single symmetry number confuses: bulk weight per side, where along
the span the difference lives, and whether the two halves pair point to point.

`diag_station_clusters.py` reuses the extractor's own functions, so the clusters it
reports are exactly the ones the extractor saw. That matters: a diagnostic that
re-derives the data can disagree with the tool it is meant to debug.

## Running them

Every script prints its own version and the parameters it ran with. When a capsule
artifact is reproduced later, that banner is what identifies which build made it.

```
python sample_capsule_volume.py volume_raw.csv samples.csv --n 800 --alpha 1.0 --seed 42
python extract_features_delta.py features_cloud.csv --beta 0.10 --z-cut 0.005
python diagnostics/check_mirror_mesh.py wing_faces.csv
python diagnostics/diag_station_clusters.py features_cloud.csv --x 0.30 --neighbours
```

The seed is not optional. A capsule that cannot say which draw produced its sample
is not reproducible, whatever else it carries.
