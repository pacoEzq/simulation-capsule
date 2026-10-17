# capsule_cube_re0300

Files: [`capsule_cube_re0300/`](capsule_cube_re0300/)

Flow around a cube at $Re_D = 300$. Laminar, constant density, 3D, solved with the
steady segregated solver on 501,860 cells: the template and the mesh of the Part 7
sweep at $Re_D = 100$, 300, 1000 and 3000, built together by the chain in
`macros/t7/` at commit `7ff5be1`.

The steady solver reaches no steady state here. `convergence.status` is
`no_steady_state`: the run stopped at the cap of 15,000 iterations with `cl` failing
the stationarity gate, and the published values are window statistics over the last
14,000 iterations. They are iteration means, not time averages, as
`convergence.time_basis` says, and `convergence.regime_expected` declares the flow
at this Reynolds number periodic.

`diff.json` compares this capsule with the base of the sweep, `capsule_cube_re0100`,
pinned at commit `ee7ff59`. The grayscale frame it measures sits in
`diffsrc/capsule_cube_re0300/`, outside the capsule.

## Known issues

- The colormap of the planes and the view, `blue-red uniform perception`, is not
  lightness-monotonic as SPEC 3.3 requires: along its bar, L* rises from 37 to
  81 and falls back to 62, so two different values can share a lightness. Hue
  still tells them apart; a reader of lightness alone cannot. Later capsules use
  a lightness-monotonic map.
- **The capsule declares no domain extents.** The comparison with the literature
  needed them and had to take them from Part 3. Against Khan et al. (2020) the
  iteration-mean drag, 0.880, is 6% high, and the transient run on the same mesh
  gives 0.883 (see [docs/cube-literature-anchors](../docs/cube-literature-anchors/README.md)).
