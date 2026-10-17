# capsule_cube_re0100

Files: [`capsule_cube_re0100/`](capsule_cube_re0100/)

Flow around a cube at $Re_D = 100$. Laminar, constant density, 3D, solved with the
steady segregated solver on 501,860 cells. It is the base of the Part 7 sweep at
$Re_D = 100$, 300, 1000 and 3000, built by the chain in `macros/t7/` at commit
`7ff5be1`; the variants pin it at commit `ee7ff59` in their `diff.json`.

The run converged: all four gated coefficients fell below the steady floor at
iteration 3,000, so its values are single values, not window statistics.

## Two stagnation values

`pressure.cp_stagnation` reads 2.394, and it is not the stagnation point. The report
behind it, inherited from the template, takes the largest cell value of $c_p$ over
the six faces of the cube, and at this Reynolds number that cell sits at a corner of
the cube. The symmetry-plane section passes through the stagnation point, and its
maximum, `plane_extremes.y0.cp`, is 1.068. The same section crosses the front edges
at mid-span and finds nothing higher, so the peak belongs to the corner, not to the
edges.

From $Re_D = 300$ up, the two numbers agree to within 0.001 in every capsule of the
sweep. Only at $Re_D = 100$ does the corner win.

The cause is the pressure singularity that viscous flow develops at a sharp corner,
which weighs more as the Reynolds number falls. A mesh-refinement study confirms it:
with good cells the corner value keeps rising as the mesh is refined, while drag and
the stagnation point converge (see [docs/cube-re100](../docs/cube-re100/README.md)).
Read 1.068 as the stagnation value of this capsule.

## Known issues

- The colormap of the planes and the view, `blue-red uniform perception`, is not
  lightness-monotonic as SPEC 3.3 requires: along its bar, L* rises from 37 to
  81 and falls back to 62, so two different values can share a lightness. Hue
  still tells them apart; a reader of lightness alone cannot. Later capsules use
  a lightness-monotonic map.
- **The capsule declares no domain extents.** The comparison with the literature
  needed them (10D by 10D, inlet 5D upstream, inherited from Part 3) and had to
  take them from Part 3. Against that literature the drag, 1.342, sits 34% above
  Khan et al. (2020) and 9% above the Haider and Levenspiel cube correlation; a
  lateral probe rules out the lateral walls, and the inlet distance and the
  reference itself remain open (see [docs/cube-literature-anchors](../docs/cube-literature-anchors/README.md)).
