# Side Runs

A Side Run is an article outside the tutorial series: one question about a
case, the runs that answer it, and the figures, data and scripts to repeat
them. Most start from a number in one of the capsules of
[`examples/`](../../examples/README.md) that needed a second look, and the
table names that capsule.

| Side Run | What it does | Capsule it starts from |
|---|---|---|
| [Steady vs Transient CFD of Vortex Shedding: a Cube at Re = 300](cube-re300/README.md) | measures what the `no_steady_state` flag of the capsule warns about | [`capsule_cube_re0300`](../../examples/capsule_cube_re0300.md) |
| [Pressure Singularity at a Sharp Corner: Mesh Refinement of a Cube at Re = 100](cube-re100/README.md) | tests the two stagnation values that the capsule reports | [`capsule_cube_re0100`](../../examples/capsule_cube_re0100.md) |
| [Cube Drag Coefficient from Re = 100 to 3000: Six Simulation Capsules Against the Literature](cube-literature-anchors/README.md) | anchors the cube capsules to Khan et al. 2020 and rules out the lateral walls as the cause of the Re 100 excess | the six cube capsules: [`capsule_cube_re200`](../../examples/capsule_cube_re200.md), [`capsule_cube_re0100`](../../examples/capsule_cube_re0100.md), [`capsule_cube_re0300`](../../examples/capsule_cube_re0300.md), [`capsule_cube_re0600`](../../examples/capsule_cube_re0600.md), [`capsule_cube_re1000`](../../examples/capsule_cube_re1000.md), [`capsule_cube_re3000`](../../examples/capsule_cube_re3000.md) |
| [Wake Symmetry Breaking in Transient CFD: How the Initial Condition Selects the Side-Force Plane of a Cube at Re = 300](cube-re300-symmetry-plane/README.md) | shows that the initial condition selects the plane in which the cube wake breaks symmetry, while drag and Strouhal numbers do not change | [`capsule_cube_re0300`](../../examples/capsule_cube_re0300.md), the same set-up and mesh |
| [Symmetry-Plane Selection in the Cube Wake at Re = 300: Rotated, Tilted and Wide-Domain Runs](cube-re300-turned/README.md) | turns and tilts the cube inside a fixed mesh and finds that the wake always oscillates in a plane through a face, while the mesh only decides which face | [`capsule_cube_re0300`](../../examples/capsule_cube_re0300.md), the same cube and Reynolds number |
| [Strouhal and Drag of a 2D Cylinder from Re = 50 to 180: the Two Per Cent That Was the Domain](cylinder-re-sweep/README.md) | sweeps the cylinder from Re 50 to 180 and traces a steady 2 per cent excess in Strouhal number to the side boundaries at 15 diameters, not to mesh or time step | [`capsule_cylinder_re0100`](../../examples/capsule_cylinder_re0100.md), the same cylinder at Re 100 |

The card of each capsule links back to its Side Runs.

Text is [CC BY 4.0](../../LICENSE-DOCS).

Personal project by [Francisco Ezquerra Larrodé](https://github.com/pacoEzq), published with the [simulation-capsule](https://github.com/pacoEzq/simulation-capsule) repository. Views are my own.
