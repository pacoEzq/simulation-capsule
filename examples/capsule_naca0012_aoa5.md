# capsule_naca0012_aoa5

NACA 0012 at 5 degrees incidence, $Re_c = 10^6$, $M = 0.043$. Steady RANS,
k-omega SST (Menter), incompressible, 2D mesh of 65,885 cells with the farfield at
50 chords.

Built for **Part 2 of the series, Global Anchoring: the Case Summary in 50
Scalars**.

## Contents

```
capsule_naca0012_aoa5/
├── setup.txt        trimmed solver setup report
├── summary.json     global scalars, environment
└── views/           two renders under the shared view contract
```

Two files at first, and deliberately so: the part it belongs to introduces the idea
that a case can be anchored in scalars before any field data is shipped.
Twenty-eight kilobytes describe a converged aerofoil run well enough to argue about.

`views/` arrived later. Part 6b needed a base to compare against, and a comparison
needs two renders made under one view contract, so the two views were added here and
an `environment` block was added to `summary.json`. Neither changes what the capsule
says about the run: the scalars are the ones published with Part 2, and the renders
come from that same solution. What changed is what the capsule declares about itself.

Read next to `capsule_ahmed25_re1e6`, which carries six views and a caveats block,
it shows how much the format grew over four parts.

It is also the base of the first comparable pair in the repository.
`capsule_naca0012_aoa7` is the same case at 7 degrees, rendered under the same view
contract, and its `diff.json` measures the two against each other.

## The capsule checks itself

Every derived quantity here can be recomputed from the values beside it, which is
what makes the file worth trusting:

- The Reynolds number recovers exactly from the reference block:
  $\rho U c / \mu = 1.18 \times 15 \times 1.0 / 1.77 \times 10^{-5} = 10^6$.
- The drag decomposition closes to seven decimals:
  $c_{d,p} + c_{d,f} = 0.0042204 + 0.0080843 = 0.0123047$ against $c_d = 0.0123050$.
- $c_l / c_d = 0.52776 / 0.012305 = 42.89$, the declared lift-to-drag.
- $M = 0.043$ follows from 15 m/s and justifies treating the flow as incompressible.

A model handed this capsule can perform all four checks itself. That is the point of
carrying the reference quantities rather than only the coefficients.

## Dimensional reference, nondimensional everything else

The reference block is dimensional: chord in metres, velocity in metres per second,
density and viscosity in SI. Each value is a bare number and the unit rides on the
key: `chord_m`, `velocity_m_per_s`, `density_kg_per_m3`, `viscosity_pa_s`, `area_m2`.
The copy published with Part 2 wrapped each one in a `{value, unit_flag}` object; it
is kept byte for byte under `as-published/`, and this one was derived from it by
`tools/migrate_reference.py`. The first version of that script wrote `velocity_m_s`,
which the grammar of SPEC 3.1 reads as metres times seconds. Version 1.1 fixed the
suffixes, and the validator now checks that a velocity, a density, a viscosity and
a pressure carry the one their dimension calls for.

This is the exception the specification allows, and the reason for it. Coefficients,
$c_p$, $y^+$ and the mass imbalance fraction are all dimensionless, as required. But
the scales used to nondimensionalise them have to be recoverable, or none of the
checks above can be run. A capsule that ships only coefficients is a capsule nobody
can audit.

Note that `capsule_ahmed25_re1e6` solves the same problem the other way, by running
nondimensional from the start with $\rho = U = L = 1$. Both satisfy the rule, and
both now write it the same way, unit on the key and number bare. The base names still
follow each case, `chord_m` here and `length_L_m` there: the specification fixes how
a unit is declared, not what a length is called.

## Convergence

Converged in 2,285 iterations with monitors flat and residuals between $10^{-7}$ and
$10^{-11}$. Mass imbalance is $5 \times 10^{-13}$ of the through-flow.

Unlike the Ahmed body case, this one reaches a genuine steady state rather than a
limit cycle, so every digit reported is meaningful.

## Reproducing the case

The simulation file is attached to the tutorial, not to this repository. See
[SPEC.md](../SPEC.md) for why the capsule ships without it.

## For `capsule_naca0012_aoa5.md`

```markdown
## Known issues

Found by the [Part 2 probe test](../probes/part-2_naca0012_aoa5.md). The
capsule passes `tools/check_capsule.py` without warnings. It is frozen, so
these are recorded rather than patched.

- **Two step limits contradict each other.** `setup.txt` carries Maximum Steps
  3000 under the Steady solver and 5000 under Stopping Criteria. Neither fired,
  since the run stopped at 2285 on the asymptotic criteria, but a capsule that
  can hold two values for one setting lowers the trust owed to any single
  sourced number in it.
- **The angle of attack is not stated in `setup.txt`.** It appears only in
  `summary.json`. The sole evidence inside the setup is the moment axis origin,
  which reproduces the quarter chord rotated by 5 degrees to seven decimals.
  Exact, and indirect.
- **No geometry and no domain extents.** `farfield_radius_chords` exists only in
  `summary.json`, so blockage and induced angle cannot be checked from the
  capsule.
- **The drag is a fully turbulent value and is not labelled as one.** No
  transition model appears anywhere in `setup.txt`. It has to be inferred from
  an absence.
- **The fifth decimal of `cl` is inside the convergence band.** The run stopped
  on a normalized band of 1.0E-4 over 500 samples. Quote four figures.
```

