# Example capsules

Complete capsules from the reference cases of the tutorial series, published as
they were built.

## Why these are not all the same shape

A capsule grows one layer at a time, and each of these was assembled for the
tutorial that introduced a layer. What you get is the capsule as its own part left
it: the layers that existed by then, plus the one it was built to demonstrate. The
Ahmed body capsule has six curated views and no plane sections, because the part it
belongs to is about views. The jet capsule has a point cloud and no views, for the
same reason in reverse.

This is worth saying plainly rather than apologising for, because it reflects how
the format is meant to work. Layers are optional. A steady case has no `signals/`. A
case nobody needs to compare has no `diff/`. A capsule is not incomplete when it
lacks a layer, it is incomplete when a layer it does carry breaks the contract in
[SPEC.md](../SPEC.md).

Reading them in series order also shows the format arriving: what the early capsules
could not yet answer is exactly what the later layers were built for.

## The capsules

| Capsule | Case | Layers present | From | Probe record |
|---------|------|----------------|------|--------------|
| `capsule_naca0012_aoa5` | NACA 0012, 5 degrees, Re 1e6 | `setup.txt`, `summary.json` | [Part 2](capsule_naca0012_aoa5.md) | [read](../probes/part-2_naca0012_aoa5.md) |
| `capsule_cube_re200` | Cube, Re 200, laminar | `+ planes/` | [Part 3](capsule_cube_re200.md) | [read](../probes/part-3_cube_re200.md) |
| `capsule_jet_r2_re100` | Jet in crossflow, r = 2, Re 100 | `+ samples.csv` | [Part 4](capsule_jet_r2_re100.md) | [read](../probes/part-4_jet_r2_re100.md) |
| `capsule_delta65_a13p3_re1e6` | Delta wing, 65 degrees, alpha 13.3 | `+ features.json` | [Part 5](capsule_delta65_a13p3_re1e6.md) | [read](../probes/part-5_delta65_a13p3_re1e6.md) |
| `capsule_ahmed25_re1e6` | Ahmed body, 25 degree slant, Re 1e6 | `setup.txt`, `summary.json`, `views/` | [Part 6](capsule_ahmed25_re1e6.md) | [read](../probes/part-6_ahmed25_re1e6.md) |

Read down the table and the format assembles itself: scalars, then sections, then a
volume sample, then named features, then curated views.

Every capsule here was handed whole to a language model and questioned about its
own case before it was published. The probe records hold the questions, the answers
and what the reader got wrong, and [`probes/README.md`](../probes/README.md) lists
the nine capsule defects those sessions caught.

## What they do not yet agree on

The five capsules declare their reference quantities three different ways. Two carry
`{value, unit_flag}` objects, one folds the unit into the key name, and two ship
bare numbers with no unit anywhere. They also disagree about where the block lives:
four use a `reference` object, while the delta wing splits its quantities between
`case/geometry` and `nondimensionalization/reference_quantities` and declares the
root chord in both.

The plane CSV header diverges too. The cube and the jet declare grid, spacing, nodes
returned over nodes requested, precision and the word nondimensional. The delta wing
omits the spacing and the node counts.

The specification says keys are stable across cases where the quantity is the same,
so these are divergences rather than variations, and they are left visible here on
purpose. They are also invisible until several capsules sit side by side, which is a
decent argument for publishing them together. The automation part is where it gets
settled; the jet capsule already carries the `schema` key that will do it.

The counts above are measured rather than eyeballed. Run
[`tools/check_capsule.py`](../tools/check_capsule.py) over this directory and it
reports each divergence as a warning, with the file and the key.

Each capsule has a case card beside it, `<capsule_name>.md`, describing the run and
what the capsule is honest about. The card sits outside the capsule directory on
purpose: a capsule contains what SPEC.md defines and nothing else, so an example
that carried its own documentation inside would no longer be an example of the
format.

## What they cost

Between 5,900 and 66,000 tokens, measured per artifact in
[token-ledger.md](token-ledger.md).

The headline is not the one most people expect. Images are cheap: they account for
5% of the jet capsule and 7% of the delta. Numeric tables are what fills a context
window, at 82% of the jet once the point cloud is added to the plane sections. Six
curated views cost less than a third of one set of plane tables.

## What is not here

The solver files. No `.sim`, no mesh, no field data.

That is the point of the exercise rather than an omission. If understanding a case
required shipping the case, the capsule would have failed at its one job. The
tutorials attach the simulation files where a reader wants to reproduce the run;
this directory holds what a language model needs, which is a different and much
smaller thing.

## Using one

Hand the whole directory to a model in a fresh conversation and ask it about the
case. That is the test each capsule was built to pass, and the way to find out
quickly whether the format earns its keep.

The capsule travels whole. Attaching `summary.json` and describing the rest tests
nothing.
