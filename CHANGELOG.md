# Changelog

What changed in the Simulation Capsule, newest first: the specification, the validator, the extractor and the example capsules. Each release is a version of `SPEC.md`, tagged `spec-0.N`; tools, macros and capsules are listed under the version they were written for. Until 1.0 a version may change what counts as a valid capsule. When it does, section 6 of `SPEC.md` says how, and `tools/check_capsule.py` counts each new check from the version a capsule declares, so a capsule published earlier stays valid as published.

## Since 0.16 (on main)

### Why a Simulation Capsule, in Five Steps (2026-11-24)

`docs/why-a-capsule/` holds the article that argues for the format: the two halves of AI-assisted CFD, the token cost of the twelve example capsules against their mesh size, and what language models did with a capsule in the probe records, with the limits of that record. Figures, data and the scripts that draw them sit beside the text.

### Side Runs move to `docs/side-runs/` (2026-11-24)

The Side Runs moved from `docs/<name>/` to `docs/side-runs/<name>/`, and `docs/side-runs/README.md` lists them with the capsule each one starts from. A link to an old path no longer resolves.

## 0.16 (2026-11-24)

### Symmetry, lines and the domain

A case computed on half or a quarter of a symmetric body says so in `symmetry` and lists what the symmetry leaves out. `lines/` holds a station of one dimension less, a CSV with no image; in a two-dimensional case the stations across the flow are lines. `domain` gives the extent of the computational domain and `discretization.domain` a study of it. In an alias the domain is the first suffix, and a suffix never reuses the letter of a parameter. One new check, `line_tables`: `check_capsule.py` 1.17 runs twenty-eight.

### The extractor builds its own stations (2026-11-23)

`station_builder.java` is now the first of the four STAR-CCM+ macros of the extractor. It creates from the card what the other three need and the case lacks (the length parameter, `x_D`, `y_D`, `z_D`, station parts and tables) and saves `<name>_xtr.sim` next to the original, which it never overwrites. On a 2D region a station is a set of line probes gathered in one table.

### Colormaps travel with the extractor (2026-11-23)

The card can ask for `viridis`, `cividis`, `inferno` or `magma`. When the `.sim` has no table of that name, `output_exporter.java` imports it from `macros/extractor/colormaps/`: 32 control points resampled from Matplotlib, licences in that folder. View titles are drawn in `SansSerif-Plain`.

### `TrimReportForAI` keeps the depth of every tree node (2026-11-23)

The version attached to the article counted `|` marks and lost one level under every ancestor that is a last child. Depth now comes from the column of each node's connector. `setup.txt` files already published keep their indentation.

## 0.15 (2026-11-22)

### The capsule states what a reader would otherwise guess

A station carries its token and its `candidates`. A duration names its basis in its key; a window is one span, or a `windows_note` says why not; angles are averaged on the circle. A run judged on a single window is gated on its two halves, and `solver_regime_mismatch` has a stated meaning. Three new checks: `plane_formation`, `window_consistency` and `candidates`.

### Names, levels and provenance

An alias can carry two parameters and a replica. A signed field is drawn in an odd number of levels, 255. `manifest.json` records `spec_version`, which the assembler reads from the validator.

### Maintenance

- 2026-11-17: the Part 4 card and probe record now say that the stray comma in `capsule_jet_r2_re100` was published with the capsule, found by the probe reader afterwards and fixed when the capsule entered this repository.

## 0.14 (2026-11-14)

### Modes and snapshots

`modes/` and `snapshots/` join `signals/`, and `signals.json` gives the period cycle by cycle. `phase_mean` is the eighth `_statistic`. A signed field is drawn on a range symmetric about zero, and the optional `mesh.wall` says what a reader needs to judge a wall quantity. Two new checks: `transient_layers` and `signed_range`.

## 0.13 (2026-11-13)

### A compact `setup.txt`, 20 percent fewer tokens

An optional notation for `setup.txt`, lossless and opened by a fixed header. On the cylinder at Re 100 the probe reader answered 72 of 72 questions from the compact form and 66 of 72 from the plain one (`probes/setup-notation_cylinder_re0100.md`). The plain form stays valid. New check: `setup_notation`.

### Articles and Side Runs

- 2026-11-09: Side Run on the cylinder Reynolds sweep, Strouhal and drag against Re (`docs/cylinder-re-sweep/`).
- 2026-11-11: Parts 8a and 8b indexed, with their probe records.

## 0.12 (2026-10-31)

### A view shows a field

A view is measured on its pixels to show the field it names. New check: `view_field`.

### The Kármán cylinder

`capsule_cylinder_re0100`, the Part 8 base, with its card and its macros under `macros/t8/`. The assembler copies `signals/` into the capsule and reads `spec_version` from the validator.

### Articles and Side Runs

- 2026-10-23: the cube at Re 300 cut by its symmetry plane, with its macros (`docs/cube-re300-symmetry-plane/`).
- 2026-10-31: the cube at Re 300, turned (`docs/cube-re300-turned/`).

## 0.11 (2026-10-27)

### Signals

`signals/` is specified: `phase_average.csv` and `spectrum.csv` over one window of whole periods of a reference signal, each column declaring how it was formed, and `signals.json` with the same facts.

## 0.10 (2026-10-20)

### Every convergence gets a status

The band of observed order is declared (`order_band`). A fifth status, `above_order`, comes with `gci_order` and `gci_fs`. `behaviour` and `status` are separate statements, an oscillation that shrinks is `converged`, and an iteration error nobody measured is `null`. `cd` is the whole traction; its normal viscous part, when above 0.1 percent of `cd`, is carried as `cd_viscous_normal` so the split closes.

### Views from the card

The extractor draws the views the card asks for, with titles per SPEC 3.3 above the band so image differencing crops them away, and builds stations only when the card names them. The NACA 0012 card joins the examples.

### Maintenance

- 2026-10-23: `CITATION.cff` validated in CI; the author's family name keeps its accent.

## 0.9 (2026-10-19)

### Discretization declared

A capsule declares its discretization study in space, time or both: four states per quantity and the iteration error of each mesh, or `study: "none"` when nothing was measured. The decay of the slowest mode, `n_e`, comes with its source and window. A derived quantity states how it was sampled. One new check.

### A capsule can be built again

The README documents the extraction card, `card.json`: the body, its reference values, the stations and the views, each view with the question it answers. When there is no card, `extract_capsule.py --propose-card` lets a language model propose one, every entry marked `read` or `inferred`, and `manifest.json` records who wrote it and its sha256.

## 0.8 (2026-10-19)

### Time statistics

`time_mean`, `time_rms` and `_window_tU_over_L`, and one written form for a span. A colormap is a measurable property, named in `manifest.json` as the producing software names it. The Reynolds number is `re` in `reference`.

## 0.7 (2026-10-17)

### Naming a capsule, and the manifest as build record

The alias names what varies in the collection, with solver codes for the variant suffix. `manifest.json` is the build record, with the producing software under `recipe`; the scripts stay outside the capsule.

### Capsule Extractor v0

`tools/extract_capsule.py` assembles a capsule from a neutral package, and `macros/extractor/` is its STAR-CCM+ adapter: a solved `.sim` in, a capsule out. A frozen package of the cube at Re 300 serves as fixture.

## 0.6 (2026-10-14)

### How each value was formed

A file holding a field declares whether it is one instant or a statistic, and over which window. Planes carry their header with the nodes in the body. Force coefficients come with their pressure and friction split.

## 0.5 (2026-10-14)

### Definitions apart from recipes

Each artifact is defined in terms no solver owns, with the STAR-CCM+ steps under **Recipe**. A key names the quantity, never the instrument. The token budget is a design target, not a gate.

### Articles and Side Runs

- 2026-10-10: the cube at Re 300 as a Side Run; 2026-10-16: the corner singularity of the cube at Re 100.
- 2026-10-17: probe records for Parts 7a, 7b and 7c, and literature anchors for the cube from Re 100 to 3000.

## 0.4 (2026-09-30)

### Environment, window statistics and convergence

The root block `environment` records solver, version, build, platform and precision. A run not converged publishes window statistics for every scalar, and the `convergence` block says how the run was judged, with its `uncertainty`. `diff.json` signs every view and frame it measured, and `base.commit` is a full SHA. Validator 1.5.

### The Part 7 cube sweep (2026-10-01 to 2026-10-13)

Capsules of the cube at Re 100, 300, 600, 1000 and 3000 from one macro chain under `macros/t7/`, open to any sweep point. `tools/check_mirror.py` tests a mesh for mirror symmetry.

## 0.3 (2026-09-09)

### Declared extensions, and `diff`

A capsule may declare optional, additive extensions in `summary.json`. The first, `diff`, compares two capsules view by view on grayscale at 256 levels, with a threshold in physical units and a base declared by alias, repository, commit and path. `capsule_naca0012_aoa7` is the incidence variant with its diff against `aoa5`. Validator 1.4.

### Maintenance

- 2026-09-19: `llms.txt` and the series index; link to the Javadoc MCP server.

## 0.2 (2026-09-04)

### Frozen as published, migrated alongside

Capsules as published stay byte for byte under `examples/as-published/`; migrated copies live under `examples/`, and CI checks both. A dimensional quantity takes one form, a unit suffix on its key inside `reference`. Burned-in titles declare regime and span, and token figures come from the measured ledger. `tools/migrate_reference.py` moves a capsule to the new form; validator 1.1 to 1.3.

## 0.1 (2026-09-01)

First public draft, with the layers through `views/` settled. Example capsules for the Ahmed body, the NACA 0012, the cube at Re 200, the jet and the delta wing; `tools/check_capsule.py` with its self test, the token ledger with a budget gate, probe records for Parts 1 to 5 with the defect ledger, and CI that validates every example capsule.
