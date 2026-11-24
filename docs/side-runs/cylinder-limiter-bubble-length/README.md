# Limiter sensitivity of the recirculation length: 2D cylinder at Re 700

The case is a 2D circular cylinder at Re = 700 in a domain of height H/D = 200, meshed with 102 136 cells and solved steady, at second order, in Simcenter STAR-CCM+ 2606. Modified Venkatakrishnan limits the gradients, with TVB (total variation bounded) gradient limiting on. One setting changes between runs. It is the TVB factor ψ, set through `AcceptableFieldVariationFactor`, whose default in 2606 is 0.05.

Lr is the length of the recirculation bubble behind the cylinder, in diameters, read to the nearest cell face, and each Δ is taken against the runs with ψ ≥ 0.05, where the limiter has no effect.

> **Convergence warning.** None of these states meets the stopping criteria we set. We asked for an absolute mass balance below 1e-10 and for the minimum axial velocity to settle within 1e-8, and mass balance stopped between 1.1e-10 and 2.0e-9, even though residuals and Cd had levelled off. Read each row as unconverged, by those criteria.

| ψ | Cd | ΔCd (%) | Lr / D | ΔLr (%) |
|---|---|---|---|---|
| 1 to 0.05 | 0.29738 | 0.00 | 88.41 | 0.0 |
| 0.02 | 0.29739 | +0.01 | 87.02 | -1.6 |
| 0.01 | 0.29748 | +0.04 | 87.02 | -1.6 |
| 0.005 | 0.29744 | +0.02 | 85.66 | -3.1 |
| 0.002 | 0.29716 | -0.07 | 82.98 | -6.2 |
| 0.001 | 0.29678 | -0.20 | 80.37 | -9.1 |
| 0.0005 | 0.29631 | -0.36 | 77.85 | -12.0 |
| 0.0002 | 0.29565 | -0.58 | 75.40 | -14.7 |
| 0.0001 | 0.29519 | -0.74 | 74.20 | -16.1 |
| 0 | 0.29285 | -1.52 | 71.85 | -18.7 |
| TVB off | 0.29394 | -1.16 | 70.70 | -20.0 |

Each value from ψ = 1 down to 0.02 ran 50 000 iterations. Below that, 150 000.

## What the table says

- The default sits on the plateau. Between ψ = 1 and 0.05 Lr does not move and Cd shifts by less than 0.02%, so whatever happens further down is no flaw of the default setting.
- Below ψ = 0.01 the bubble shortens at every step, smoothly, until it has lost 19% of its length at ψ = 0 and 20% with TVB off, a last gap which tells you that ψ = 0 and TVB off are different settings.
- Cd hardly moves. Over the whole range it changes by less than 1.6%, and at the far end it even turns, with TVB off giving more drag than ψ = 0. Watch Cd alone and you would miss the limiter, which Lr gives away.
- No hysteresis. Stepping back up from ψ = 0 to 0.0001, 0.001 and 0.01 lands Lr on the same values as on the way down, to the nearest cell face, which leaves a single branch in which Lr depends on ψ alone and not on the path that led there.

Text and data are [CC BY 4.0](../../../LICENSE-DOCS).

Personal project by [Francisco Ezquerra Larrodé](https://github.com/pacoEzq), published with the [simulation-capsule](https://github.com/pacoEzq/simulation-capsule) repository. Views are my own.
