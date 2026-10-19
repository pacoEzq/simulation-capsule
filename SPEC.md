# Simulation Capsule specification

**Version 0.9. Draft.**

This document defines what a Simulation Capsule contains and how its parts are
named, so that a capsule built from one solver and one case is legible to a model
that has never seen either.

Layers marked **settled** have shipped with their tutorial and should not change
without a version bump. Layers marked **provisional** are subject to change as
their part is written.

Each artifact is defined by what it carries and why, in terms no solver owns.
Where a definition needs a solver to become concrete, a recipe follows it under
**Recipe (Simcenter STAR-CCM+)**. A recipe is one way to meet the definition, not
the definition: a capsule built with another solver meets the same definition by
its own route.

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

### 2.1 Naming a capsule

An alias is an identifier, not a record. It names what distinguishes this
capsule from the others it will sit beside; everything else about how it was
made is inside, in `environment` (4.2.1) and `manifest.json` (4.8). Four rules
cover every case so far.

**A capsule on its own** is `capsule_<case>`, the case with the parameter
that defines it: `capsule_naca0012_aoa5`, `capsule_cube_re0300`. Nothing
about the solver, the mesh or the build goes in the name. A capsule built with
another solver, alone in its repository, is still `capsule_cube_re0300`.

**A sweep** adds the swept variable to every alias, zero-padded to the width
of the largest value so the names sort in parameter order:
`capsule_cube_re0100` to `capsule_cube_re3000`. A decimal point is written
`p` (`a13p3`), a power of ten `e` (`re1e6`). A single case carries its number
unpadded, `capsule_cube_re200`.

**A pair or family that varies something other than the case** keeps the
alias on the reference and adds, on each variant, a suffix naming the
variable and its value: `_ofm` for a second solver, `_m2` for a second mesh,
`_single` for a second precision. Several suffixes go in a fixed order:
solver, mesh, precision (`_ofm_m2`). The suffix repeats what `summary.json`
already states, on purpose: the Reynolds number in `re0300` is also a key
inside, and the alias is where a reader sees the variable before opening
anything.

**A solver code** is the short form of `environment.solver`, three
lowercase letters. The table lists the codes defined so far; the product name
is written as its vendor writes it, the same string `environment.solver`
carries. A solver not listed here takes a code of the same shape, declared
in the capsule's case card until the table catches up.

| `environment.solver` | code |
|---|---|
| Simcenter STAR-CCM+ | `scm` |
| OpenFOAM | `ofm` |
| Ansys Fluent | `flu` |
| COMSOL Multiphysics | `cms` |

A suffix that does not name a variable is not an alias: `_b`, `_v2`,
`_final` and `_new` say that something differs without saying what, and the
validator may warn on them.

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
  underscores, and a trailing digit for a power. The value is a bare number.
- `_per_` for division is recommended, `velocity_m_per_s`, not required. Inside
  `reference` a suffix is accepted as written, `velocity_m_s` and `density_kg_m3`
  included: the block already declares its keys dimensional, and the root of the
  key names the quantity.

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

Colormaps are lightness-monotonic: along the bar, L* only rises or only falls.
Rainbow maps invent structure that is not in the data and mislead a model
reading pixel values as magnitudes. The property is what the capsule
promises; the map that delivers it is whatever the producing software has or
the adapter builds, and `manifest.json` names it as that software names it.
A name from another library proves nothing about the image: `viridis` in a
manifest means the solver shipped a map by that name or the chain built one
with those control points, not that the author meant something like it.

Colorbar ranges are fixed and declared, never autoscaled per image. Two images of
the same field with different autoscaled ranges cannot be compared, and a model has
no way to detect the mismatch.

### 3.4 Declared extensions

Some capsules carry an artifact that describes something other than their own case.
A capsule declares those with `extensions`, an array of names in `summary.json`:

```json
"extensions": ["diff"]
```

The declaration lives in `summary.json` and nowhere else. A validator reads that
file first, and discovery cannot depend on the file being discovered.

An extension is optional and additive. A capsule that declares none is a capsule of
section 4, unchanged, and every capsule valid under 0.2 is valid under 0.3. What an
extension adds is checked only when it is declared, with one exception stated in
4.7: an instrument directory is refused whether or not anything is declared.

Defined so far: `diff` (4.7).

### 3.5 Token budget

A capsule is token-budgeted: it is measured file by file, and the whole is held
to a declared target. The series works to 60,000 tokens for a complete capsule,
30 percent of a 200,000-token window, which leaves the rest of the window to the
conversation that uses it. Cost alone admits nothing: each file earns its place
by the question it answers.

The target is a design budget, not a gate. A capsule may exceed it when no layer
can be cut without losing its question; the ledger reports the excess and the
validator does not refuse it. Weights are measured by `tools/capsule_ledger.py`
at the rates it declares, and recorded per file in `manifest.json` where the
capsule carries one (4.8).

### 3.6 How each value was formed

Every value in the capsule says how it was formed, in the place a reader finds
it. A run that reached a fixed point publishes values. A run that did not
publishes statistics, and a statistic travels with its window (4.2.2). A file
that holds a field rather than a scalar, a plane, a sample or a view, declares
whether it is one instant or a statistic, and over which iterations or which
nondimensional time window: in the header comment of a CSV, in the burned-in
title of an image (3.3). The names are those of 4.2.2.
Written out, a span reads `over iterations <a>-<b>` for a steady run and
`over tU/L <a> to <b>` for an unsteady one, with the reference length named
as the case names it (`tU/D`, `tU/c`).

Two artifacts of one capsule may be formed differently, and often are: the
scalars of a run judged by window statistics are window means, while its planes
are a snapshot at the last iteration. That is not a defect. Leaving the reader
to guess which is which is.

---

## 4. Artifacts

### 4.1 `setup.txt` (settled)

The solver's own account of how the case was set up, trimmed to what a reviewer
audits: regions and boundaries with their conditions, physics models, material
properties, solver settings, stopping criteria and the reports that feed
`summary.json`. What styles the output rather than defines the run (scenes,
colours, layout) is removed.

It is the one artifact written in the solver's own vocabulary, on purpose. Every
other file translates the case into neutral terms; `setup.txt` is the original
those translations are checked against. A solver that keeps its setup in several
input files ships them as one text, each part under a line naming the file it
came from.

This is the highest-exposure artifact in the capsule. It names boundaries, models,
material properties and often the geometry. It is the first thing to audit before a
capsule leaves the building.

**Recipe (Simcenter STAR-CCM+).** The `TrimReportForAI` macro in `macros/` trims
the HTML summary report, 400 to 900 KB or roughly 100k to 200k tokens, by 90 to
95 percent. Measured over the five published capsules, the trimmed file weighs
5.7k to 11.9k tokens; per-case figures are in `examples/token-ledger.md`.

### 4.2 `summary.json` (settled)

Global scalars: the case anchored in numbers a model can quote and check.

The schema is case-driven, not fixed: a case that measures a moment has a moment
key and a case that does not has none. A key names the quantity, never the
instrument that measured it. `cd`, `cp_base` and `x_reattach_D` mean the same
thing whichever solver, report or script produced them, which is what makes two
capsules comparable key by key (section 2). The instrument is recorded beside the
value where it matters, as `probes.<name>.report` does. A probe is named by the
quantity and the place, `u_over_U_axis_x2D`; a prefix that belongs to the
instrument's own object names stays in `report`. Free-text values, such as
`convergence.solver`, may use the solver's own terms.

What is fixed is that every key is nondimensional per section 3.1, and that the
`reference` block is present.

**Force coefficients.** `cd` is the drag of the body over the dynamic pressure
and the reference area. Where it is split, `cd_pressure` integrates the pressure
over the wall, relative to the reference pressure, and `cd_friction` integrates
the wall shear stress, the tangential part of the viscous traction, both
projected on the drag direction, so that `cd = cd_pressure + cd_friction`. The
normal part of the viscous traction belongs to neither. In incompressible flow
it vanishes on a no-slip wall, so a nonzero value there is numerical, not
physical. The same split holds for every force coefficient a capsule carries.

**The Reynolds number** is `re` in `reference`, with the length scale it uses
named in the key of that scale (`length_scale_D_m`, `chord_m`). A sweep that
varies it also carries `sweep.parameter` and `sweep.value`, which repeat it on
purpose. Capsules published before 0.8 carry it as `reynolds_number`,
`reynolds_L` or `re_cr`; they stay as published, and a reader meets the
variants in `examples/README.md`.

**Sampling.** A derived quantity read off something coarser than the solver's
own cells declares it: `"cell"` for a cell or face value, or
`{"grid_spacing_D": 0.25}`, `{"line_points": 400}`, `{"wall_points": 180}`
for a value read off a sampled grid, line or set of wall points. In an
object such as `probes.<name>` the key is `sampling`; next to a scalar in a
flat block it is `<name>_sampling`. A derived quantity read off a grid cannot
show an order of convergence finer than that grid, and the reader needs to
know which it is before trusting a `p` in 4.2.4.

A single scalar that aggregates a worst case is an antipattern. Where a distribution
matters, expose the distribution: a minimum cell quality alone says nothing useful
without threshold counts at several levels.

#### 4.2.1 `environment`

A root block declaring the software and hardware the numbers came from. Two runs of
the same case on two builds are two measurements, and the Part 6b pair showed how
far apart they can land: the same comparison measured in single precision moved
both changed pixel fractions by about 0.3 percentage points.

```
environment { solver, version, build, platform, precision,
              renders { scope, version, build, platform, precision } }
```

- `solver`: the product name as its vendor writes it.
- `version`, `build`: the release identifiers the solver prints for the session,
  copied as printed.
- `platform`: operating system and architecture of the run, as the solver
  reports them.
- `precision`: `single`, `double` or `mixed`, the floating-point precision of
  the build that ran.
- `renders`: present only when the images were produced in another environment
  than the run. `scope` lists the capsule directories whose files that environment
  produced, `["views/"]` for instance, and every directory it names exists in the
  capsule. The other keys repeat those of the render session; `solver` may be
  omitted when it is the same product.

**Precision is read, not remembered.** It comes from what the build or its
environment records, never from what the installation is believed to be.

**Recipe (Simcenter STAR-CCM+).** `version`, `build` and `platform` are copied
from the `Version:` line of `setup.txt`: `PresentationVersion`, `ReleaseNumber`
and `BuildArch`. The same line carries `BuildEnv`; a build whose `BuildEnv` ends
in `-r8` is double precision, and without the suffix it is the mixed-precision
default. `BuildEnv: clang20.1vc14.2-r8` is `double`, `BuildEnv: clang20.1` is
`mixed`.

`environment` absorbs `numerics`. Precision is a property of the build, not of the
discretization, so it has one place, and a `numerics` block at the root is reported
as a leftover.

#### 4.2.2 Window statistics

A run that does not settle to a fixed point publishes statistics, not values, and a
statistic without its window is a number nobody can check. Wherever
`convergence.status` (4.2.3) is not `converged`, every published scalar in `forces`,
`pressure`, `wake`, `probes` and `mass` carries its window with it, as a quintet:

| Key | Holds |
|-----|-------|
| `<name>` | the published value |
| `<name>_sd` | standard deviation of the sampled values over the span: the spread of the signal, not the uncertainty of the value |
| `<name>_window_iterations` | iterations the statistic spans, all pooled windows together |
| `<name>_n_windows` | number of windows pooled |
| `<name>_statistic` | how the value was formed |

`_statistic` takes one of seven values. Five describe a steady run judged over
iterations, two describe an unsteady run judged over physical time:

- `iteration_mean`: mean over the sampled iterations of the span.
- `window_min`, `window_max`: the lowest or highest value reached at any sampled
  iteration of the span.
- `window_min_max`: both, as a `[min, max]` pair.
- `instantaneous`: the value at the last iteration, or at the last time step.
- `time_mean`: mean over the sampled time steps of the span.
- `time_rms`: root mean square of the fluctuation about `time_mean` over the
  same span; it is the spread of the signal, so a `time_rms` value carries no
  `_sd`.

Extremes carry no `_sd`: a minimum over a span has no spread of its own to report.
The quintet of `cp_min` is therefore four keys:

```json
"cp_min": -2.029315009840153,
"cp_min_window_iterations": 4000,
"cp_min_n_windows": 2,
"cp_min_statistic": "window_min"
```

`_window_iterations` counts the whole span, two windows of 2000 iterations give
4000; `convergence.window_iterations` is the length of one window.

**Time windows.** A statistic over physical time declares its span as
`<name>_window_tU_over_L: [a, b]`, the nondimensional start and end of the span
with the reference length of 3.1, and `<name>_n_windows` as before.
`_window_iterations` is for spans measured in iterations; a key carries one or
the other, never both, and which one is a property of the run (4.2.3
`time_basis`), not of the key. Probes and plane extremes carry the same
keys unprefixed.

**Probes.** `probes.<name>` is an object and carries the same keys unprefixed:
`value`, `sd`, `window_iterations`, `n_windows`, `statistic`, next to `position_D`
and the `report` it was read from.

**Plane extremes.** `plane_extremes.<plane>.<field>` is a `[min, max]` pair, and the
block carries `statistic`, `window_iterations` and `n_windows` once, for all of them.
The plane keys follow the stems in `planes/`: `y0` for `plane_y0`.

**Forces.** `forces` may carry `cd_pressure` and `cd_friction` next to `cd`, each
with its own quintet.

**Mass.** `mass` holds `mdot_in_over_rhoUD2` and `mdot_out_over_rhoUD2`, the mass
flow through inlet and outlet over $\rho U D^2$, signed with the outward normal so
that inflow is negative, and `mass_imbalance`, the net over the inflow. No
dimensional mass flow key appears anywhere in `mass`.

**Mesh.** `mesh.cells` is an integer. `mesh.designed_for_Re_range` is the pair of
integers `[low, high]` the mesh was sized for, and replaces `designed_for_Re`.

#### 4.2.3 `convergence`

How the run was stopped, and on what evidence. A run judged by window statistics
writes the block with the keys below and no others. Capsules published before 0.4
carry a shorter block of iterations and residuals; it stays valid, and a block
without `status` is read as predating this section.

| Key | Holds |
|-----|-------|
| `solver` | solver mode as run, in the solver's own terms (`steady segregated` in STAR-CCM+) |
| `criterion` | name of the stopping criterion, `window_stationarity` |
| `criterion_version` | integer version of that criterion |
| `time_basis` | one sentence stating what an iteration mean is and is not |
| `regime_expected` | `{ value, source, ref }`: the regime expected at this Reynolds number, `source` `declared` or `measured`, and the reference it comes from |
| `status` | `converged`, `stationary`, `not_stationary` or `no_steady_state` |
| `stop_reason` | one sentence, template below |
| `solver_regime_mismatch` | boolean verdict of the chain on the solver mode against the expected regime |
| `iterations` | iterations run |
| `discard_iterations` | initial iterations discarded before the first window |
| `window_iterations` | length of one window |
| `windows_closed` | windows completed |
| `sampling_interval` | iterations between two samples |
| `max_iterations` | iteration cap |
| `k_gate` | gate on `drift_over_se` |
| `k_sd` | factor of the tolerance on `sd_log_ratio_over_tol` |
| `min_cycles` | fewest signal cycles a window must hold |
| `uncertainty` | optional, `{ cd_relative, basis, source, note }`, below |
| `quantities` | one entry per gated quantity, keys below |

Each entry of `quantities`, named after the scalar it gates (`cd`, `cp_base`):

| Key | Holds |
|-----|-------|
| `window_means`, `window_sds` | mean and standard deviation of each closed window, in order |
| `n_cycles_last` | signal cycles counted in the last window |
| `period_iterations_last` | `window_iterations` over `n_cycles_last` |
| `drift_last` | last window mean minus the one before it |
| `drift_over_se` | `drift_last` over the standard error of that difference |
| `sd_log_ratio_over_tol` | absolute log ratio of the last two window standard deviations, over its tolerance |
| `pass` | boolean, the entry met every gate |
| `n_e` | optional: iterations per e-fold of the slowest decaying mode of the quantity |
| `n_e_source` | with `n_e`: `residual`, `quantity` or `eigenvalue`, what the fit was made on; a residual fit need not be asymptotic and a quantity fit need not see the slowest mode, so the source travels with the number |
| `n_e_window` | with `n_e`: the iteration range of the fit; when the slow mode oscillates, the window spans several of its periods or `n_e` is not declared |
| `residual_error_est` | with `n_e`: the extrapolated remaining change, `drift_last` times `n_e` over `window_iterations` |

`status` is `converged` when the run reached a fixed point and its values are
single values; `stationary` when every entry of `quantities` passes and the
published values are window statistics (4.2.2); `not_stationary` when some entry
of `quantities` fails the test of settled means and amplitudes, and `iterations`
is below `max_iterations`. Reaching `max_iterations` with a signal that is not flat
is `no_steady_state`. That status names the flow, not the gate. A periodic limit
cycle under a steady solver has no fixed point to reach, so the run is not on its
way to steady: it is not steady, and its published values are window statistics
all the same. `solver_regime_mismatch` is not a
comparison of `solver` and `regime_expected`: the reference sample pairs a steady
solver with an expected unsteady regime and writes `false`.
`converged` says the gate closed; `n_e` says how far from the fixed point it
closed, and a run whose `n_e` is of the order of the whole run has not
necessarily reached the limit.

`stop_reason` for `stationary`, with the gated names in the order of `quantities`,
the two windows compared as iteration ranges, and the largest `drift_over_se`
written in full:

```
Window statistics of <q1>, <q2>, ... stationary between iterations <a>-<b> and <c>-<d> (max drift/SE = <x>).
```

`Window statistics of cd, cl, cy, cp_base stationary between iterations 1000-3000
and 3000-5000 (max drift/SE = 0.8749704041357005).`

`stop_reason` for `no_steady_state`, with the quantity that failed the gate in the
last two windows and the one clause, of the three in brackets, that failed:

```
Reached <max_iterations> iterations; <q> failed the stationarity gate in windows <a>-<b> and <c>-<d> (n = <n> cycles < <min_cycles> | drift/SE = <x> > <k_gate> | sd log ratio = <x> over tolerance).
```

`n` is the smaller cycle count of the two windows. In the sd clause `x` is the raw
absolute log ratio of the two standard deviations and `over tolerance` means above
`k_sd` divided by the square root of `n`; it is not `sd_log_ratio_over_tol`.

**Uncertainty.** Optional. `convergence.uncertainty` declares the uncertainty of
the published values:

```
uncertainty { cd_relative, basis, source, note }
```

`cd_relative` is a fraction, `basis` says in one sentence how it was obtained, and
`source` is `measured` when it comes from runs of this case, `declared` when it is
taken from elsewhere. `note` is optional. The block appears only where the
uncertainty was measured or taken from a stated source; elsewhere the key is
absent, never null or estimated. It is not the gate statistic: the standard error
between windows decides when to stop and says nothing about how far the published
value sits from a second run of the same case.

#### 4.2.4 `discretization`

How far the published values sit from the continuum, as far as the author
measured it. A capsule that measured nothing writes the block with
`study: "none"` and nothing else; the key is never absent, because absence
would read as an oversight rather than a statement.

```
discretization { study, space, time }
space { family, cells, ratios, method, iteration_error, quantities }
time  { levels, deltas_tU_over_L, method, max_delta }
```

- `study`: `none`, `space`, `time` or `space_time`.
- `space.family`: the mesh labels, coarse to fine; `cells` their counts;
  `ratios` the refinement ratios between neighbours, as the method defines
  them; `method` the procedure and its reference, `Celik 2008, Fs 1.25`.
- `space.iteration_error`: optional, one entry per mesh in `family` order,
  the remaining iteration error of that run on the quantity the study leans
  on most (`residual_error_est` of 4.2.3, or the last drift where `n_e` was
  not fitted), with the key named. The finest mesh is usually the least
  converged, and a reader of the GCI needs to see that the iteration error
  sits well below the differences between meshes.
- `space.quantities`: one entry per published key the study covered, named
  by that key (`cd`, not `drag`):

| Key | Holds |
|-----|-------|
| `values` | the quantity on each mesh, in `family` order |
| `behaviour` | `monotone` or `oscillatory`, from the sign of successive differences |
| `p` | observed order |
| `expected_order` | optional: the order this quantity should show, where it differs from the scheme's (a wall derivative is first order by construction); the method's band applies otherwise |
| `extrapolated` | Richardson extrapolate, where `status` allows one |
| `gci_fine` | GCI on the finest mesh, as a fraction of the value; half the oscillation amplitude when `behaviour` is `oscillatory` |
| `status` | `converged`, `reduced_order`, `nominal` or `not_asymptotic` |
| `assumed` | `true` when `status` is `reduced_order`: three meshes cannot prove the order stable |
| `note` | one sentence when `status` is not `converged` |

`status` is `converged` when `p` sits in the expected band and the
extrapolate is meaningful; `reduced_order` when `p` sits below the band but
successive differences fall in a constant ratio, so the extrapolate is
published with `assumed: true`; `nominal` when `p` was computed but the
differences between meshes sit below the resolution of the instrument that
measured the quantity, so `p` is reported and not trusted; `not_asymptotic`
when the differences follow no order, in which case `extrapolated` and
`gci_fine` are absent and the published value says so in its own `note`.
An `oscillatory` quantity carries no extrapolate; its `gci_fine` is the
uncertainty the method assigns to oscillation.

- `time.levels` and `time.deltas_tU_over_L`: the time steps compared;
  `time.method` how, `two-level difference` when there are two;
  `time.max_delta`: the largest change in any published key between the two
  levels, with the key named.

The block measures the capsule's own discretization. Which mesh the
published values come from is `mesh.cells`; the study says how far that
mesh is from the limit. `mesh.note` free text about independence is retired
by this block.

### 4.3 `planes/` (settled)

Plane sections, exported as a CSV and PNG pair sharing a stem:
`plane_y0.csv` and `plane_y0.png`.

The pair is the unit. The CSV carries the numbers a model can compute with; the
image carries the topology it would take thousands of rows to convey. Neither alone
is the artifact.

Wide CSVs are reformatted rather than shipped raw: column order and precision are
chosen for reading, not for whatever the solver emitted. The header comment
declares the plane, the grid and its spacing, the nodes returned of the nodes
requested, how the values were formed (3.6) and the field precision, so the
table can be read without the image beside it:

```
# plane y_D = 0 | grid 33 x 13, spacing 0.25 D | 404 of 429 nodes, 25 in the body | instantaneous at iteration 15000 | fields %.3f | nondimensional
```

How the values were formed is written `instantaneous at iteration <n>` or
`iteration_mean over iterations <a>-<b>`, or, for an unsteady run,
`instantaneous at tU/D <t>` or `time_mean over tU/D <a> to <b>`.

**Nodes in the body.** A grid node inside the solid, or on its surface within
1e-6 of the reference length, is absent: its row is omitted, not filled, and the
header counts it. The rule is geometric, so every solver drops the same nodes.
Wall values belong to the views of the body, not to a plane: a plane node on a
sharp edge would carry the edge singularity into the table.

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
- The **regime and span are in the title**: steady or unsteady, and the statistic
  and its span as 3.6 writes them: `iteration_mean over iterations 1000-15000`,
  `time_mean over tU/D 50 to 150`, or `instantaneous at tU/D 150`. A filename
  hints; a title declares.
- **Colorbars are fixed** at the view-contract level, not per render, and are
  lightness-monotonic.
- **Filenames are snake_case** and describe the question, not the software state:
  `view_cp_nearfield.png`, not `Scene_1.png`.

When a view exists to be compared against another capsule, its colorbar range is the
union of both cases, fixed before either is rendered.

### 4.7 `diff/` and `diff.json` (settled, extension `diff`)

A variant capsule answers what a single case cannot: what a parameter does. It is a
capsule of section 4 (`setup.txt` and `summary.json` at minimum, plus `views/`)
that also carries `diff/` and `diff.json`, and declares `extensions: ["diff"]` per
section 3.4.

`diff.json` describes a relation, not a case. `summary.json` still describes the
variant's own case, and nothing moves out of it.

The base is declared by alias, repository, commit and path, and is never edited. If
the base needs a retrofit (`views/`, in this part), `base.commit` names the
revision that carries it, and `frozen_copy_path` points at the as-published copy.

**The commit locates, `source_sha256` proves.** `base.commit` says where to find the
base; the view hashes say that what was found is what was measured. `base.commit` is
the full 40-character SHA, never an abbreviation: a short form that is unique today
stops being unique as the history grows. It is provisional while the history that
carries it is only local, because a rebase or an amend rewrites the SHA and leaves
the hashes intact. So it is written, or re-verified against the pushed history, as
the last step before the push.

**Shared view contract.** Both capsules render the same camera, the same fixed
colorbar ranges, the same width, the same title band, and use the same filenames in
their `views/`. Without this the difference measures the renderer.

**Resize, then diff.** Diffing at full resolution and downsampling the result
reports a number that describes an image nobody receives. The published figure has
to describe the published image.

**Crop before measuring.** Elements that change between the two renders for reasons
other than the flow inflate the raw pixel count. The title band is one of them: it
carries the span, and the span differs between two instants of the same run.

**Measure on grayscale.** The comparison runs on a render of the field with a
`grayscale` colormap quantized to the 256 levels of the PNG, so that a pixel
distance is a field distance. `diff.json` names both the published colormap and the
measured one.

**Threshold in physical units.** The threshold is declared in the units of the field
together with its comparison sign, `>=`, and the equivalent in levels is derived
from it. A threshold expressed as a percentage of RGB describes the instrument, not
the flow. And the sign is not cosmetic: the same pair measured with `>` cuts at the
next level up and roughly halves the reported fraction.

**Declared masks.** The solid body, filled with a colour outside the colormap and
dilated by a declared number of pixels, and the colorbar box, declared in pixels.
Publish `pixels_evaluated`.

**Scalar deltas.** Absolute always; relative only where the base value is not
practically zero.

`known_differences[]` lists everything that differs between the two capsules and is
not the parameter under study.

**The instrument does not travel.** The grayscale renders live in `diffsrc/` while
the measurement runs and stay outside the capsule; `diff.json` declares the recipe
to regenerate them. A `diffsrc/` directory inside a capsule is an error whether or
not `diff` is declared.

`diffsrc/` lives at the repository root, one subdirectory per capsule, and each
frame keeps the filename of the view it was rendered from:
`diffsrc/capsule_naca0012_aoa5/view_cp_nearfield.png`. `pipeline.instrument_root`
names it.

**Every link of the chain is hashed.** The chain runs view, grayscale frame, diff.
For every pair, `diff.json` declares the sha256 of both measured views in
`source_sha256 { base_view, variant_view }` and of both grayscale frames in
`instrument_sha256 { base_frame, variant_frame }`. The tool hashes all four before
measuring and stops if any one does not match. A view re-exported without
regenerating its frame leaves the frame hash intact, and a run that checked only the
frames would return the previous number in silence.

Schema of `diff.json`:

```
schema_version
base      { capsule, case, repo, commit, path, frozen_copy_path }
variant   { capsule, case }
difference { parameter, base_value_deg, variant_value_deg, delta_deg,
             implementation, mesh_identical }
view_contract { camera, width_px, export_height_px, artifact_height_px,
                crop_rows_top, colorbar_levels, contour_style, body_fill,
                colormap_published, colormap_measured, colorbar { cp, u_over_u } }
pipeline  { order: "crop_then_mask_then_diff", body_dilation_px,
            colorbar_box_excluded_px, pixels_evaluated, tool, tool_version,
            instrument_root: "diffsrc" }
pairs[]   { name, question, base_view, variant_view,
            source_sha256 { base_view, variant_view },
            instrument_sha256 { base_frame, variant_frame }, output,
            threshold_physical, threshold_comparison: ">=", threshold_levels,
            level_size, changed_pixel_fraction, max_delta_levels,
            max_delta_physical }
scalar_deltas { delta_cl, delta_cd, delta_cd_pressure, delta_cd_friction,
                delta_cp_min, delta_cm_quarter_chord }
known_differences[]
```

A worked baseline: for a converged case with a limit cycle, 0.135 percent of pixels
changed between two instants of the same run. That is the noise floor a real
difference has to stand out against.

### 4.8 `manifest.json` (provisional)

The build record: what produced the capsule and with what. It carries the chain,
each script or macro by path and sha256, the seeds, the colorbar ranges and
colormaps, the solver and tool versions, and the measured token weight of each
artifact (3.5). The scripts themselves stay outside the capsule.

The manifest follows the preamble. Its keys describe the build in terms no
solver owns; whatever names an object, a call or a setting of the producing
software, a scene, a table, a font, an export call, a compiler version, goes in
a `recipe` object inside the block it belongs to. A reader comparing two
capsules reads the manifest and skips every `recipe`.

Macros are idempotent, fail loudly on missing preconditions, and never invent object
names. Each script or macro in the chain declares the solver build it was verified
against.

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
review misses. The records in `probes/` cover Parts 1 to 6b and list fourteen
defects found this way and not by the author, five of them in the Part 6b pair
alone.

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
| 0.3 | Declared extensions (3.4): optional, additive, named in `summary.json`. First extension `diff`, with `diff/` and `diff.json` settled (4.7): shared view contract, measurement on grayscale at 256 levels, threshold in physical units with its comparison sign, base declared by alias, repo, commit and path, `diffsrc/` refused inside a capsule. Additive over 0.2; every capsule valid under 0.2 is valid under 0.3. |
| 0.4 | Unit suffixes inside `reference` accepted as written, `_per_` recommended (3.1). Root block `environment` (4.2.1), window statistics (4.2.2), `convergence` with its `uncertainty` (4.2.3), view and frame hashes in `diff.json`, full-SHA `base.commit` (4.7). Not purely additive: see below. |
| 0.5 | Definition separated from recipe (preamble): each artifact is defined in terms no solver owns, with the STAR-CCM+ steps under **Recipe**; `setup.txt` (4.1) and `environment` (4.2.1) restated that way. A key names the quantity, never the instrument (4.2). Token budget as a design target, not a gate (3.5). Numbers and production suffixes in an alias (2). Additive over 0.4; every capsule valid under 0.4 is valid under 0.5. |
| 0.6 | How each value was formed (3.6): a file holding a field declares whether it is one instant or a statistic, and over which window. Plane header and nodes in the body (4.3). Force coefficients and the pressure and friction split (4.2). No check changes; capsules published before 0.6 stay valid as published: see below. |
| 0.7 | Naming a capsule (2.1): the alias names what varies in the collection, with solver codes for the variant suffix; the 0.5 paragraphs on numbers and suffixes are absorbed. `manifest.json` is the build record, with whatever names the producing software under `recipe` (4.8); the scripts stay outside the capsule. No new check: see below. |
| 0.8 | Time statistics: `time_mean`, `time_rms` and `_window_tU_over_L` (4.2.2), and the written form of a span (3.6, 4.3, 4.6). A colormap is a measurable property, named in `manifest.json` as the producing software names it (3.3). The Reynolds number is `re` in `reference` (4.2). No new check: see below. |
| 0.9 | Discretization declared (4.2.4): a study in space, time or both, four states per quantity and the iteration error of each mesh, or `study: "none"` when nothing was measured. Decay of the slowest mode, `n_e` with its source and window (4.2.3). Sampling of a derived quantity (4.2). One new check: see below. |

### 0.3 → 0.4

- 4.2.1: root block `environment` (`solver`, `version`, `build`, `platform`,
  `precision`, `renders.scope`) absorbs `numerics`; precision is read from the
  `BuildEnv` field of `setup.txt`.
- 4.7: `diffsrc/` lives at the repository root; `diff.json` declares the sha256 of
  every measured view and every grayscale frame, and the tool stops on a mismatch.
- 4.7: `base.commit` is the full 40-character SHA, provisional while its history is
  only local, written or re-verified as the last step before the push.
- 4.2.2: window statistics. Every published scalar of a run not `converged` carries
  `_sd`, `_window_iterations`, `_n_windows` and `_statistic`; extremes carry no
  `_sd`; probes and plane extremes carry the same keys.
  `mass` is nondimensional, `mesh.cells` an integer, `designed_for_Re_range`
  replaces `designed_for_Re`.
- 4.2.3: block `convergence` for runs judged by window statistics: its keys,
  `status` in `converged`, `stationary`, `not_stationary`, `no_steady_state`,
  the `stop_reason` templates, and `convergence.uncertainty`.
- 3.1: inside `reference`, unit suffixes are accepted as written (`_m_s`,
  `_kg_m3`); `_per_` for division is a recommendation, not a rule.
- Compatibility: a 0.3 capsule without `environment` now warns, and fails under
  `--strict`. Everything else a 0.3 capsule carries stays valid.

### 0.4 → 0.5

- Preamble: a definition says what an artifact carries and why; a recipe under
  **Recipe (Simcenter STAR-CCM+)** is one way to meet it.
- 4.1, 4.2.1: `setup.txt` and `environment` defined without naming a solver; the
  trimming macro and the `BuildEnv` reading of precision move under Recipe.
- 4.2: a key names the quantity, never the instrument; a probe is named by the
  quantity and the place, and the instrument goes beside the value, as
  `probes.<name>.report` does.
- 4.2.3, 4.8: the solver mode is written in the solver's own terms, and every
  script or macro of the chain declares the build it was verified against.
- 3.5: 60,000 tokens is the series' design budget; the ledger reports an excess and
  the validator does not refuse it.
- 2: decimal point `p`, power of ten `e`, sweeps zero-padded; two capsules of one
  case that differ in how they were produced share the alias plus a suffix naming
  the difference.
- Compatibility: no check changes. A probe key published before 0.5 that carries
  the instrument's prefix, `LLM_u_over_U_axis_x2D` in the five Part 7 cube
  capsules, stays valid as published.

### 0.5 → 0.6

- 3.6: every value says how it was formed, a CSV in its header comment and an
  image in its burned-in title, with the names of 4.2.2.
- 4.3: the plane header also declares how the values were formed and how many
  nodes fall in the body; a grid node inside the solid, or on its surface within
  1e-6 of the reference length, is omitted and counted.
- 4.2: `cd = cd_pressure + cd_friction`; friction is the tangential part of the
  viscous traction, and the normal part belongs to neither.
- Compatibility: no check changes. The five Part 7 cube capsules, published
  before 0.6, stay valid as published: their plane headers do not say how the
  values were formed, and `plane_y0.csv` keeps 11 nodes on the cube surface,
  415 of 429.

### 0.6 → 0.7

- 2.1: one principle for aliases, four rules (a capsule on its own, a sweep, a
  variant that differs in how it was produced, a solver code) and the table of
  solver codes. It restates the 0.5 paragraphs on numbers and suffixes, which
  it replaces; every published alias already follows it.
- 4.8: `manifest.json` names the chain by path and sha256 and keeps
  solver-specific names in `recipe` objects. `run_macro.java` is no longer a
  capsule artifact.
- Compatibility: no new check. The validator and the ledger no longer accept
  `run_macro.java` at the capsule root; no published capsule carries one. The
  five Part 7 manifests, published before 0.7, keep their form and carry no
  `recipe` object.

### 0.7 → 0.8

- 4.2.2: `_statistic` gains `time_mean` and `time_rms`; a `time_rms` value
  carries no `_sd`. A span over physical time is `_window_tU_over_L: [a, b]`,
  in place of `_window_iterations`, never next to it.
- 3.6, 4.3, 4.6: a span is written `over iterations <a>-<b>` or
  `over tU/L <a> to <b>`, with the reference length named as the case
  names it.
- 3.3: lightness-monotonic means L* only rises or only falls along the bar; the
  manifest names the map as the producing software names it.
- 4.2: the Reynolds number is `re` in `reference`; a sweep repeats it in
  `sweep.value`.
- Compatibility: no new check. The validator accepts the two time statistics
  and the time window. Capsules published before 0.8 keep their Reynolds keys,
  `reynolds_number`, `reynolds_L` or `re_cr`, as published; `examples/README.md`
  lists them.

### 0.8 → 0.9

- 4.2.4: `discretization` says how far the published values sit from the
  continuum. `study` is `none`, `space`, `time` or `space_time`; each quantity
  of a space study carries `values`, `behaviour`, `p`, `extrapolated`,
  `gci_fine` and a `status` of `converged`, `reduced_order`, `nominal` or
  `not_asymptotic`, and `iteration_error` may give each mesh its remaining
  iteration error. The block retires `mesh.note` as free text about
  independence.
- 4.2.3: an entry of `convergence.quantities` may carry `n_e`, with
  `n_e_source`, `n_e_window` and `residual_error_est`.
- 4.2: a derived quantity read off a sampled grid, line or set of wall points
  declares `sampling`, or `<name>_sampling` next to a scalar in a flat block.
- 0.7 → 0.8 above: the written span reads `over tU/L`, as 3.6 has it.
- Compatibility: one new check, inside `summary_blocks` and only where
  `discretization` exists: `status` takes one of the four values,
  `not_asymptotic` carries no `extrapolated` or `gci_fine`, an `oscillatory`
  quantity no `extrapolated`, `reduced_order` carries `assumed: true`, and
  `iteration_error` has as many entries as `family`. Capsules published
  before 0.9 carry no `discretization` block and keep `mesh.note`; they stay
  valid as published.
