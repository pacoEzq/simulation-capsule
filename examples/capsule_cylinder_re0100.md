# capsule_cylinder_re0100

Files: [`capsule_cylinder_re0100/`](capsule_cylinder_re0100/)

Flow past a circular cylinder at $Re_D = 100$. Laminar, constant density, 2D,
solved with the implicit unsteady solver at $\Delta t\, U/D = 0.02$ on 179,040
cells, run to $tU/D = 300$. The wake sheds a Kármán vortex street, and the run is
built by the chain in `macros/t8/` and assembled by `tools/extract_capsule.py`
from the extraction card beside this file,
[`capsule_cylinder_re0100.card.json`](capsule_cylinder_re0100.card.json).

This is the first capsule with `signals/`. Time is factored, not sampled: the
history of `cd`, `cl` and the probe velocities is folded onto 32 phases of one
shedding cycle in `phase_average.csv`, and its frequency content is in
`spectrum.csv`, both over the same 32 whole periods of `cl`. The shedding line of
the spectrum falls on bin 32, at St 0.1646.

The scalars in `summary.json` are time statistics over that window: `cd` is a
`time_mean`, `cl` carries its `time_rms`, and the `window` block compares the first
16 periods with the last 16. The plane `plane_z0` and the view `view_u_rms_z0` hold
the mean and RMS fields.

## Known issues

- `manifest.json` declares `spec_version` 0.7. The assembler that built it wrote
  that value whatever the SPEC; the capsule itself follows SPEC 0.11, `signals/`
  included, and `tools/check_capsule.py --strict` passes it.
- The plane and the view are statistics over `tU/D 100 to 300`, as their header
  and title say, while `summary.json` and `signals/` use the 32 whole periods from
  `tU/D 105.521 to 299.886`. SPEC 4.9.1 asks one window per capsule; the field
  statistics were accumulated by the solver from a fixed start, so they could not
  follow the period window.
