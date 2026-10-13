# capsule_cube_re0100

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

The likely cause, not tested here, is the pressure singularity that viscous flow
develops at a sharp corner, which weighs more as the Reynolds number falls. No
mesh-refinement study was run. Read 1.068 as the stagnation value of this capsule.
