# Simulation Capsule specification

**Version 0.1. Draft.**

This document defines what a Simulation Capsule contains and how its parts are
named, so that a capsule built from one solver and one case is legible to a model
that has never seen either.

Layers marked **settled** have shipped with their tutorial and should not change
without a version bump. Layers marked **provisional** are subject to change as
their part is written.

---

## 1. Scope

A capsule describes one simulation case. Comparisons between cases live in
`diff/`, which references two capsules rather than merging them.

The capsule is a directory, not an archive. It may be zipped for transport, in
which case the archive expands to `capsule_<alias>/` and subdirectories are
preserved. Flattening `views/*.png` into the archive root breaks the contract.

## 2. Naming

**Directory root.** `capsule_<alias>/`

The alias is neutral: never a project name, a customer, or a programme. It follows
the case, not the geometry, so a case run at two Reynolds numbers gets two aliases.
The published examples use `capsule_ahmed25_re1e6`, not `capsule_ahmed25`.

**Files.** `snake_case`, lowercase, with the extension declaring the format. No
spaces, no dates in filenames, no `_final_v2`.

**Keys.** `snake_case` throughout JSON. Keys are stable across cases where the
quantity is the same; a capsule may omit a key it has no value for, but must not
rename it.

## 3. Global rules

### 3.1 Nondimensionalisation

Every scalar, coordinate, time and frequency is a dimensionless group. In practice:
coefficients rather than forces, $x/c$ or $x/D$ rather than metres, $St$ rather than
hertz, $tU/L$ rather than seconds.

A dimensional quantity is allowed where no meaningful group exists, on two
conditions: the key carries an explicit unit suffix, and the capsule declares it.
Deltas are dimensionless too; a difference between two coefficients is a
coefficient.

The reference quantities used to nondimensionalise are themselves part of the
capsule, in `summary.json`. A model that cannot recover $U_\infty$, $L$ and $\rho$
cannot check anything.

### 3.2 Character set

UTF-8. Plain hyphen only: en dashes, em dashes and the Unicode minus sign
(U+2212) break expression parsers in several solvers, including STAR-CCM+, and have
no place in a machine-read artifact.

### 3.3 Images

PNG, 1024 px on the long edge unless the artifact says otherwise. Every image
carries a burned-in title declaring case, plane or camera, field, range and
orientation. The title is part of the artifact: a view whose scale is only in the
surrounding prose stops being self-contained the moment someone forwards the file.

Colormaps are lightness-monotonic. Rainbow maps invent structure that is not in the
data and mislead a model reading pixel values as magnitudes.

Colorbar ranges are fixed and declared, never autoscaled per image. Two images of
the same field with different autoscaled ranges cannot be compared, and a model has
no way to detect the mismatch.

---

## 4. Artifacts

### 4.1 `setup.txt` — settled

A trimmed solver setup report. The full report from STAR-CCM+ runs 400 to 900 KB of
HTML, roughly 100k to 200k tokens; trimming brings it to about 22 KB, near 6k
tokens, for a reduction of 90 to 95 percent.

This is the highest-exposure artifact in the capsule. It names boundaries, models,
material properties and often the geometry. It is the first thing to audit before a
capsule leaves the building.

Produced by the `TrimReportForAI` macro.

### 4.2 `summary.json` — settled

Global scalars: the case anchored in numbers a model can quote and check.

The schema is case-driven, not fixed. Keys map one to one onto solver report names,
so a case with a moment report has a moment key and a case without does not. What is
fixed is that every key is nondimensional per section 3.1, and that the reference
quantities are present.

A single scalar that aggregates a worst case is an antipattern. Where a distribution
matters, expose the distribution: a minimum cell quality alone says nothing useful
without threshold counts at several levels.

### 4.3 `planes/` — settled

Plane sections, exported as a CSV and PNG pair sharing a stem:
`plane_y0.csv` and `plane_y0.png`.

The pair is the unit. The CSV carries the numbers a model can compute with; the
image carries the topology it would take thousands of rows to convey. Neither alone
is the artifact.

Wide CSVs are reformatted rather than shipped raw: column order and precision are
chosen for reading, not for whatever the solver emitted.

### 4.4 `samples.csv` — settled

An importance-weighted sample of the volume. Points are dense where the flow bends
and sparse where nothing happens, which is the only way a few hundred points can
describe a domain of millions of cells.

The weight travels with the data, one column per point, so the bias is documented
rather than hidden. A model reading the cloud can recover what the sampling
emphasised and discount accordingly.

There is no universal point count. Eight hundred points kept every structure of the
jet-in-crossflow case nameable at 14,000 tokens. The check is whether banded
statistics computed from the cloud still match the solver's own reports.

### 4.5 `features.json` — settled

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

### 4.6 `views/` — settled

Renders, each answering a declared question.

`views/` is an interrogation plan under a token budget, not a gallery. A view earns
its place by answering something no scalar in `summary.json` and no plane in
`planes/` can answer. Six views at 1024 px was enough for a full automotive case.

The view contract, in full:

- Each view has a **named question** it answers, recorded with the view.
- The **camera is declared**: position, target, bounds in nondimensional
  coordinates.
- The **title is burned into the image**, per section 3.3.
- **Colorbars are fixed** at the view-contract level, not per render, and are
  lightness-monotonic.
- **Filenames are snake_case** and describe the question, not the software state:
  `view_cp_nearfield.png`, not `Scene_1.png`.

When a view exists to be compared against another capsule, its colorbar range is the
union of both cases, fixed before either is rendered.

### 4.7 `diff/` — provisional

Image differences between two capsules sharing a camera.

**Resize, then diff.** Diffing at full resolution and downsampling the result
reports a number that describes an image nobody receives. The published figure has
to describe the published image.

Elements that change every iteration, such as a burned-in iteration counter, inflate
the raw pixel count and must be cropped before measuring.

A worked baseline: for a converged case with a limit cycle, 0.135 percent of pixels
changed between two instants, which is the noise floor against which a real
difference has to stand out.

Full schema pending publication of the corresponding part.

### 4.8 `run_macro.java` and `manifest.json` — provisional

Reproduction and provenance. The manifest carries seeds, colorbar ranges, solver and
tool versions, and the measured token weight of each artifact.

Macros are idempotent, fail loudly on missing preconditions, and never invent object
names.

Specified in Part 7.

### 4.9 `signals/`, `modes/`, `snapshots/` — provisional

Transient layers. `signals/` holds time series and their spectra; `modes/` holds a
modal decomposition with its own summary; `snapshots/` holds optional phase-locked
renders under a fixed colorbar.

The shared principle is to factor time rather than sample it. A transient case
becomes a small set of stationary derived fields plus the frequency content that
explains the rest.

Specified in Parts 8 and 9.

### 4.10 `disclosure.json` — provisional

The audit: which artifacts are present, what each exposes, and what is cleared to
leave the building.

Specified in Part 10.

---

## 5. Validation

A capsule is not finished when it is exported. It is finished when a model that has
never seen the case can answer questions about it correctly from the capsule alone.

Hand the whole capsule to a frontier model in a fresh conversation, ask a fixed set
of questions, and grade every answer against the files. This catches defects human
review misses; three separate defects in the Part 3 capsule were found by the model
and not by the author.

The capsule travels whole. Attaching part of it and describing the rest tests
nothing.

---

## 6. Version history

| Version | Change |
|---------|--------|
| 0.1 | First public draft. Layers through `views/` settled; transient and disclosure layers provisional. |
