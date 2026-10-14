# capsule_cube_re1000

Files: [`capsule_cube_re1000/`](capsule_cube_re1000/)

Flow around a cube at $Re_D = 1000$. Laminar, constant density, 3D, solved with the
steady segregated solver on 501,860 cells: the template and the mesh of the Part 7
sweep at $Re_D = 100$, 300, 1000 and 3000, built together by the chain in
`macros/t7/` at commit `7ff5be1`.

`convergence.status` is `stationary`: the window statistics settled at 5,000
iterations, the first check that compares two windows, and the published values are
window statistics over the last 4,000 iterations. `convergence.regime_expected`
declares the flow at this Reynolds number unsteady, so these are iteration means of
a steady solver, not time averages, as `convergence.time_basis` says.

`diff.json` compares this capsule with the base of the sweep, `capsule_cube_re0100`,
pinned at commit `ee7ff59`. The grayscale frame it measures sits in
`diffsrc/capsule_cube_re1000/`, outside the capsule.

## Known issues

- The colormap of the planes and the view, `blue-red uniform perception`, is not
  lightness-monotonic as SPEC 3.3 requires: along its bar, L* rises from 37 to
  81 and falls back to 62, so two different values can share a lightness. Hue
  still tells them apart; a reader of lightness alone cannot. Later capsules use
  a lightness-monotonic map.
