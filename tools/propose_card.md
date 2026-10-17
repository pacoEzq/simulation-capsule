You are given the setup record of one CFD simulation (setup.txt) and, if
available, its scalar summary (summary.json). Propose an extraction card:
the JSON object defined below that tells an extractor what to pull from the
solved case so that a language model can reason about it later.

Work from the setup only. Reason in this order and write one line per
decision:
1. Equations and models: steady or unsteady, laminar or turbulent, which
   turbulence model, compressible or not, single or multiphase.
2. Boundaries: inlet, outlet, walls, symmetry, periodic; which boundaries
   form the body of interest; the reference length and velocity the case
   itself implies (an inlet speed, a characteristic size named in the
   parameters, a Reynolds number in the parameters).
3. Regime: from 1 and 2, the dimensionless group that governs the case and
   its value, and whether a steady solution is physically expected or the
   run is a steady solver on an unsteady flow.
4. Where the information is: from 3, the stations (plane sections) most
   likely to hold structure (wake, shear layer, separation, recirculation)
   and the questions a reader would ask of the body surface.

Then fill the card:
- body: alias (neutral, lowercase, snake_case, built from case and
  governing group, never a project or customer name), length, velocity,
  group, regime, formation, fluid_region, body_boundaries, colormap.
- references: the solver reports that should become scalars.
- stations: 1 to 4 plane sections, each with axis, value (in units of the
  reference length), grid, spacing and fields.
- views: 1 to 3 renders of the body, each with the question it answers,
  the field, the parts, a fixed colorbar range and a camera.

Rules:
- Every entry carries "source": "read" when the value is written in the
  setup, "inferred" when you derived it. Every inferred entry carries
  "why": one line.
- Everything is nondimensional. Never propose a dimensional quantity.
- Never invent a boundary, a region, a report or a parameter that is not
  in the setup. If the setup lacks a reference velocity or length, say so
  in "why" and propose the most defensible choice.
- Colormap: one of viridis, grayscale, cividis, inferno, magma.
- Output only the JSON object, no prose before or after.

This card is a plausible starting point, not a verified one. The user
reviews it before extraction; the manifest will record that it was
proposed, not read.
