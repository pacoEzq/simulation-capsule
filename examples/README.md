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

| Capsule | Case | Layers present | From |
|---------|------|----------------|------|
| `capsule_ahmed25_re1e6` | Ahmed body, 25 degree slant, Re 1e6 | `setup.txt`, `summary.json`, `views/` | [Part 6](capsule_ahmed25_re1e6.md) |

More to follow as they are prepared.

Each capsule has a case card beside it, `<capsule_name>.md`, describing the run and
what the capsule is honest about. The card sits outside the capsule directory on
purpose: a capsule contains what SPEC.md defines and nothing else, so an example
that carried its own documentation inside would no longer be an example of the
format.

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
