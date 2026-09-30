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
| `check_mirror.py` | mirror figures for a capsule (JSON on stdout) | XYZ table of cell centroids and volumes, after meshing |

`sample_capsule_volume.py` draws an importance-weighted sample of a volume export,
without replacement, using the Efraimidis-Spirakis exponential-key method. The
`--alpha 0` case reproduces volume-uniform sampling, which is the naive baseline
worth running once to see what the weighting buys.

`extract_features_delta.py` turns a thresholded cell cloud into labeled vortex-core
polylines and per-station scalars. It carries the two-threshold rule the
specification requires: an export floor that bounds file size, and a per-station
relative threshold that defines the set actually integrated.

`check_mirror.py` measures whether a volume mesh is a mirror image of itself across
a plane, before any flow exists. Each off-plane cell is reflected and its exact
nearest cell on the other side found; the cell is paired, a level mismatch or
unpaired, and the paired distances are reported against the cell's own size. It
measures and reports; it is not a gate. `diagnostics/check_mirror_mesh.py` stays
as a diagnostic: its pairing figure against a global median size is what
`check_mirror.py --legacy-median` reproduces, for comparison only.

## Validator

These read a capsule and write nothing into it.

| Script | Checks |
|--------|--------|
| `check_capsule.py` | a capsule against [SPEC.md](../SPEC.md), twenty checks, each citing its section |
| `test_check_capsule.py` | that every one of those twenty checks fails on at least one fixture |
| `check_frozen.py` | that `examples/as-published/` still matches `examples/frozen.sha256` byte for byte |

`check_capsule.py` 1.5 follows SPEC 0.4 and needs only the standard library. A
capsule runs the checks its layers call for: fifteen without plane sections or a
comparison, seventeen with plane sections, eighteen with the `diff` extension and
twenty with both. A rule an older capsule could not have met, such as the
`environment` block of 0.4, warns, and fails under `--strict`.

```
python check_capsule.py ../examples/capsule_*/ --strict
python test_check_capsule.py
```

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
python check_mirror.py cells_volume.csv --axis y --json
python diagnostics/check_mirror_mesh.py wing_faces.csv
python diagnostics/diag_station_clusters.py features_cloud.csv --x 0.30 --neighbours
```

The seed is not optional. A capsule that cannot say which draw produced its sample
is not reproducible, whatever else it carries.
