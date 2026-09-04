# Simulation Capsule specification

**Version 0.2. Draft.**

This document defines what a Simulation Capsule contains and how its parts are
named, so that a capsule built from one solver and one case is legible to a model
that has never seen either.

Layers marked **settled** have shipped with their tutorial and should not change
without a version bump. Layers marked **provisional** are subject to change as
their part is written.

The repository ships a validator, `tools/check_capsule.py`, whose checks cite the
section of this document they enforce. Where a check has no section to cite, the
check is wrong, not the document.

---

## 1. Scope

A capsule describes one simulation case. Comparisons between cases live in
`diff/`, which references two capsules rather than merging them.

The capsule is a directory, not an archive. It may be zipped for transport, in
which case the archive expands to `capsule_<alias>/` and subdirectories are
preserved. Flattening `views/*.png` into the archive root breaks the contract.

## 2. Naming

**Directory root.** `capsule_<alias>/`

The alias is neutral: never a project name, a customer, or a program. It follows
the case, not the geometry, so a case run at two Reynolds numbers gets two aliases.
The published examples use `capsule_ahmed25_re1e6`, not `capsule_ahmed25`.

**Files.** `snake_case`, lowercase, with the extension declaring the format. No
spaces, no dates in filenames, no `_final_v2`.

**Keys.** `snake_case` throughout JSON. Keys are stable across cases where the
quantity is the same; a capsule may omit a key it has no value for, but must not
rename it.

**Minimum.** `setup.txt` and `summary.json`. Every other layer may be absent. A
directory without those two is not a capsule, because nothing in it anchors the
case and nothing in it can be checked.

**Nothing else.** The capsule contains the artifacts defined in section 4 and no
others. No file may be present that this specification does not name, no file may
be empty, and nothing inside is addressed to the author: no placeholders, no
`TODO` markers, no residue of the draft that produced it. Documentation about a
capsule, notes to the reader, license files and anything else addressed to a human
belong outside the directory, because the capsule travels whole and everything
inside it is spent from the token budget. A `README.md` sitting next to
`summary.json` is a contract violation, not a courtesy.

## 3. Global rules

### 3.1 Nondimensionalization

Every scalar, coordinate, time and frequency is a dimensionless group. In practice:
coefficients rather than forces, $x/c$ or $x/D$ rather than meters, $St$ rather than
hertz, $tU/L$ rather than seconds. Deltas are dimensionless too; a difference
between two coefficients is a coefficient.

The reference quantities used to nondimensionalize are themselves part of the
capsule, in the `reference` block of `summary.json`. A model that cannot recover
$U_\infty$, $L$ and $\rho$ cannot check anything.

A dimensional quantity is allowed only where no meaningful group exists, and it is
declared by where it lives and how it is named:

- It lives in `reference`. A key with a unit suffix anywhere else in the capsule
  is an error.
- The key carries the unit as a suffix: the SI symbol in lowercase, joined with
  underscores, `_per_` for division and a trailing digit for a power. The value
  is a bare number.

```json
"reference": {
  "length_scale_D_m": 0.05,
  "u_inf_m_per_s": 12.0,
  "rho_kg_per_m3": 1.184,
  "mu_pa_s": 1.855e-5,
  "re_D": 38300
}
```

Dimensionless groups such as `re_D` may sit in the same block and carry no suffix.
Angles are groups already: `alpha_deg` declares the convention, not a dimension,
and may appear anywhere in the capsule. Machine time, such as solver seconds,
is not a flow quantity and belongs in `manifest.json` (4.8), not here.
No other encoding is accepted: not a `{value, unit}` object, not a bare number with
the unit in prose, not a separate map of units. The suffix is the one form that
survives every place a key can end up, including a CSV header, a table in a report
or a model quoting the key back.

The capsules published with Parts 2 to 6 predate this rule: two use an object
form for their reference quantities, three carry bare numbers. They are kept as
published, byte for byte, under `examples/as-published/`, and the validator
reports their form as a warning. Their migrated copies, derived by script and
subject to `--strict`, are the ones under `examples/`. The pair is the record of
how the format got here, and the transformation between them is reproducible.

### 3.2 Character set

UTF-8. Plain hyphen only: en dashes, em dashes and the Unicode minus sign
(U+2212) break expression parsers in several solvers, including STAR-CCM+, and have
no place in a machine-read artifact.

### 3.3 Images

PNG, 1024 px on the long edge. PNG files carry no text chunks: software, author and
path metadata leak provenance and cost nothing to strip.

Every image carries a burned-in title declaring the case, the plane or camera, the
field, the range, the orientation, the regime and the span. Regime is steady or
unsteady. Span is the iteration range a steady render represents, or the
nondimensional time window of an unsteady one. The title is part of the artifact:
a view whose scale is only in the surrounding prose stops being self-contained the
moment someone forwards the file.

Regime belongs in the title for the same reason. A filename travels without
`setup.txt`, and a name that mentions a limit cycle can be read as a transient run
when the case converged to a steady limit cycle of the iterative solver. One of
four readers in the Part 6 probe made that inference, and every later error in its
answers followed from it.

Colormaps are lightness-monotonic. Rainbow maps invent structure that is not in the
data and mislead a model reading pixel values as magnitudes.

Colorbar ranges are fixed and declared, never autoscaled per image. Two images of
the same field with different autoscaled ranges cannot be compared, and a model has
no way to detect the mismatch.

---

## 4. Artifacts

### 4.1 `setup.txt` (settled)

A trimmed solver setup report. The full report from STAR-CCM+ runs 400 to 900 KB of
HTML, roughly 100k to 200k tokens; trimming keeps the sections a reviewer audits
and removes those that style the scene, for a reduction of 90 to 95 percent.
Measured over the five published capsules, the trimmed file weighs 5.7k to 11.9k
tokens; the spread follows the number of regions, boundaries and reports the case
carries. The per-case figures are in `examples/token-ledger.md`.

This is the highest-exposure artifact in the capsule. It names boundaries, models,
material properties and often the geometry. It is the first thing to audit before a
capsule leaves the building.

Produced by the `TrimReportForAI` macro in `macros/`.

### 4.2 `summary.json` (settled)

Global scalars: the case anchored in numbers a model can quote and check.

The schema is case-driven, not fixed. Keys map one to one onto solver report names,
so a case with a moment report has a moment key and a case without does not. What is
fixed is that every key is nondimensional per section 3.1, and that the `reference`
block is present.

A single scalar that aggregates a worst case is an antipattern. Where a distribution
matters, expose the distribution: a minimum cell quality alone says nothing useful
without threshold counts at several levels.

### 4.3 `planes/` (settled)

Plane sections, exported as a CSV and PNG pair sharing a stem:
`plane_y0.csv` and `plane_y0.png`.

The pair is the unit. The CSV carries the numbers a model can compute with; the
image carries the topology it would take thousands of rows to convey. Neither alone
is the artifact.

Wide CSVs are reformatted rather than shipped raw: column order and precision are
chosen for reading, not for whatever the solver emitted. The header comment
declares the plane, the node count and the spacing, so the table can be read
without the image beside it.

### 4.4 `samples.csv` (settled)

An importance-weighted sample of the volume. Points are dense where the flow bends
and sparse where nothing happens, which is the only way a few hundred points can
describe a domain of millions of cells.

The weight travels with the data, one column per point, so the bias is documented
rather than hidden. A model reading the cloud can recover what the sampling
emphasized and discount accordingly.

There is no universal point count. Eight hundred points kept every structure of the
jet-in-crossflow case nameable at 19k tokens by the repository ledger. Part 4 quoted
14k for the same file: same 47,579 characters, converted at 3.5 characters per token
for everything, where the ledger uses 2.5 for numeric CSV, which is what the
tokenizer does to columns of digits. The check is whether banded statistics
computed from the cloud still match the solver's own reports.

### 4.5 `features.json` (settled)

Named flow features with their properties: what is in the flow, where, and how
strong, rather than the field it was extracted from.

This is the lowest-exposure artifact in the capsule. A vortex core position and
circulation say a great deal about the flow and almost nothing about the geometry
that produced it.

Extraction thresholds are part of the artifact. A feature set is only reproducible
if the threshold that produced it travels with it.

**Caveat.** Geometric singularities dominate criterion-based thresholds. On a sharp
leading edge, Q reaches values some two orders of magnitude above the vortex core,
so a per-station relative threshold anchors itself to the edge rather than the
feature. Fix it with a geometric cut before taking the per-station maximum, and
declare the cut.

### 4.6 `views/` (settled)

Renders, each answering a declared question.

`views/` is an interrogation plan under a token budget, not a gallery. A view earns
its place by answering something no scalar in `summary.json` and no plane in
`planes/` can answer. Six views at 1024 px was enough for a full automotive case.

The view contract, in full:

- Each view has a **named question** it answers, recorded with the view.
- The **camera is declared**: position, target, bounds in nondimensional
  coordinates.
- The **title is burned into the image**, per section 3.3.
- The **regime and span are in the title**: steady or unsteady, and the iteration
  range or nondimensional time window the render represents. A filename hints; a
  title declares.
- **Colorbars are fixed** at the view-contract level, not per render, and are
  lightness-monotonic.
- **Filenames are snake_case** and describe the question, not the software state:
  `view_cp_nearfield.png`, not `Scene_1.png`.

When a view exists to be compared against another capsule, its colorbar range is the
union of both cases, fixed before either is rendered.

### 4.7 `diff/` (provisional)

Image differences between two capsules sharing a camera.

**Resize, then diff.** Diffing at full resolution and downsampling the result
reports a number that describes an image nobody receives. The published figure has
to describe the published image.

Elements that change between the two renders for reasons other than the flow
inflate the raw pixel count and must be cropped before measuring. The title band is
one of them: it carries the span, and the span differs between two instants of the
same run.

A worked baseline: for a converged case with a limit cycle, 0.135 percent of pixels
changed between two instants, which is the noise floor against which a real
difference has to stand out.

Full schema pending publication of the corresponding part.

### 4.8 `run_macro.java` and `manifest.json` (provisional)

Reproduction and provenance. The manifest carries seeds, colorbar ranges, solver and
tool versions, and the measured token weight of each artifact.

Macros are idempotent, fail loudly on missing preconditions, and never invent object
names. Each macro declares the STAR-CCM+ build it was verified against.

Specified in Part 7.

### 4.9 `signals/`, `modes/`, `snapshots/` (provisional)

Transient layers. `signals/` holds time series and their spectra; `modes/` holds a
modal decomposition with its own summary; `snapshots/` holds optional phase-locked
renders under a fixed colorbar.

The shared principle is to factor time rather than sample it. A transient case
becomes a small set of stationary derived fields plus the frequency content that
explains the rest.

Specified in Parts 8 and 9.

### 4.10 `disclosure.json` (provisional)

The audit: which artifacts are present, what each exposes, and what is cleared to
leave the building.

Specified in Part 10.

---

## 5. Validation

A capsule is not finished when it is exported. It is finished when a model that has
never seen the case can answer questions about it correctly from the capsule alone.

Hand the whole capsule to a frontier model in a fresh conversation, ask a fixed set
of questions, and grade every answer against the files. This catches defects human
review misses. The records in `probes/` cover Parts 1 to 6 and list nine defects
found this way and not by the author, three of them in the Part 3 capsule alone.

The capsule travels whole. Attaching part of it and describing the rest tests
nothing.

`tools/check_capsule.py` runs the mechanical half of this: naming, encoding,
pairing, image geometry and the rules of section 3. It does not read archives, so
run it on the expanded directory.

---

## 6. Version history

| Version | Change |
|---------|--------|
| 0.1 | First public draft. Layers through `views/` settled; transient and disclosure layers provisional. Capsule contents closed to the artifacts named in section 4. |
| 0.2 | Published capsules frozen under `examples/as-published/`, migrated copies under `examples/` (3.1). Dimensional quantities take a single form: unit suffix on the key, inside `reference` (3.1). Burned-in titles declare regime and span (3.3, 4.6). Minimum capsule, empty files and draft residue stated (2). PNG text chunks prohibited (3.3). Token figures for `setup.txt` and `samples.csv` replaced by ledger measurements (4.1, 4.4). Section 5 links `probes/` and the validator. |
