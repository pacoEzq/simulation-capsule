# capsule_cube_re0600

Flow around a cube at $Re_D = 600$. Laminar, constant density, 3D, solved with the
steady segregated solver on 501,860 cells: the template and the mesh of the Part 7
sweep at $Re_D = 100$, 300, 1000 and 3000.

Built for **Part 7a of the series**, as a fifth point of that sweep. It was run after
the other four were published, to check a prediction made from them.

## How it differs from its siblings

The other four capsules were built together, by the chain in `macros/t7/` at commit
`7ff5be1`. This one was built later, by the same chain at commit `ccaf03d`, after the
chain was opened to points outside the original four. `manifest.json` names that
commit and the fingerprint of every piece.

Each manifest records the sweep as it stood when its capsule was built. This one lists
five values, 100, 300, 600, 1000 and 3000, and the four earlier capsules as siblings.
Those four were not rebuilt, so their manifests still list four values and do not name
this capsule. Neither side is wrong: a capsule describes the build it came from.

`diff.json` compares this capsule with the base of the sweep, `capsule_cube_re0100`,
pinned at commit `ee7ff59`. The grayscale frame it measures sits in
`diffsrc/capsule_cube_re0600/`, outside the capsule, as for the other variants.
