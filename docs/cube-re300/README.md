# Steady Solver, Unsteady Wake: a Cube at $\mathrm{Re} = 300$

*Same mesh, two solvers, and a side force that only one of them gets right.*

Francisco Ezquerra Larrodé · October 2026

## The question

Run a steady solver on a flow that is not steady and it will still hand you numbers. The monitors fall into some kind of rhythm, the residuals stop dropping, and sooner or later somebody averages the last few thousand iterations and calls that the answer. How far off is it? The test case had to be small enough to repeat: a cube in uniform flow at $\mathrm{Re} = 300$, close to where the literature places the onset of vortex shedding, between $\mathrm{Re} = 255$ and 305. I ran it twice on the same mesh, once with the steady segregated solver in Simcenter STAR-CCM+ and once with an implicit unsteady run that started where the steady one stopped.

## Same mesh, two runs

The cube has side $D$ and sits in a uniform stream $U$. The flow is laminar, with $\mathrm{Re} = UD\text{/}\nu = 300$. The mesh has 501,860 cells, a base size of $0.5D$ and surface cells down to $0.05D$. It is the mesh of the Reynolds sweep in [Part 7b](https://community.sw.siemens.com/s/question/0D5Vb00001dtHuCKAU/preparing-cfd-output-for-large-language-models-7b10-four-reynolds-numbers-the-cube-that-would-not-sit-still) of my series on preparing CFD output for language models. No mesh-independence study stands behind it.

The steady run stopped at 15,000 iterations. From its last iterate the transient run switched the time model to implicit unsteady, second order in time, with $\Delta t = 0.05D\text{/}U$ and ten inner iterations per step, and covered 300 time units: 6,000 steps, or 60,000 more iterations.

## What the steady solver reports

Drag behaves. Seven windows of 2,000 iterations give $C_d$ between 0.8796 and 0.8802, a mean of 0.880.

The side force does something else. Its two components, $C_l$ and $C_y$, swing slowly a quarter turn apart, so the force vector, about 0.05 in magnitude, turns around the stream axis once every 3,870 iterations or so, 3.6 turns in all; [Part 7b](https://community.sw.siemens.com/s/question/0D5Vb00001dtHuCKAU/preparing-cfd-output-for-large-language-models-7b10-four-reynolds-numbers-the-cube-that-would-not-sit-still) shows that loop in detail. Averaged over the iterations, it comes out as $C_l = -0.007$ and $C_y = +0.001$. According to the steady solver, the cube feels no mean side force.

The fast flutter has a shape in the wake. I let the same steady run continue past 15,000 iterations and, from iteration 31,703 on, saved a frame every 7 iterations. The surface where $u\text{/}U = 0.5$ trails behind the cube, bends, and pinches off a blob at its tail; the next blob leaves about 63 iterations later, the same period as the flutter in $C_l$. Each frame is an iteration, not an instant.

![Four views of the u/U = 0.5 isosurface behind a cube at Re 300 over 120 iterations of a steady solver, its tail bending and pinching off](cube-re300-steady-wake-iterations.gif)
*Steady solver, iterations 32,123 to 32,242, one frame every 7 iterations. Isosurface u/U = 0.5 coloured by x/D from 0.5 to 5, seen from downstream, in isometric view, from above and from the side.*

![Ninety side views of the wake isosurface of a cube at Re 300, one every 7 steady-solver iterations, with blobs leaving the tail at regular intervals](cube-re300-steady-wake-mosaic.jpg)
*Ninety frames of the same isosurface, one every 7 iterations from 31,703 to 32,326. A blob leaves the tail about every 63 iterations.*

## What the flow does

The transient run disagrees. Within the first 75 time units the force stops turning: $C_y$ falls from 0.04 to below 0.002 and stays there, under 0.0005 after $t \cdot U\text{/}D = 150$, while $C_l$ climbs and settles around a positive mean. Over $t \cdot U\text{/}D = 75$ to 300 the time means are $C_d = 0.883$ (rms 0.008), $C_l = 0.082$ (rms 0.028) and $C_y = 0.0000$. Split that window in two and the halves agree to 0.003 in $C_l$ and 0.002 in $C_d$. Not once does $C_l$ cross zero: for 225 time units it stays between 0.026 and 0.141.

![Side-force vector of a cube at Re 300 in the cl-cy plane: the steady solver's iterates trace a ring around the origin, the transient run settles on the cl axis at 0.082](cube-re300-steady-vs-transient-side-force.png)
*The steady iterates circle the origin; the transient force leaves the ring and stays on one axis.*

In the cross-flow plane the two runs look like different cases. The steady iterates trace a ring of radius about 0.05, and their mean sits in the middle of it, 0.012 or more from every iterate after the first thousand. The flow, given a clock, picks a plane, here the plane of $C_l$, and keeps pushing the cube towards one side of it. Nothing in the geometry prefers $+C_l$ over the other three lateral directions, so a different starting state would probably pick a different one with the same magnitude.

| | steady, iterations 1,000 to 15,000 | transient, $t \cdot U\text{/}D$ 75 to 300 |
|---|---|---|
| $C_d$ | 0.880 | 0.883 |
| $C_l$ | -0.007 | 0.082 |
| $C_y$ | 0.001 | 0.000 |
| side force | about 0.05, turning | 0.082, fixed |
| time scales | 3,870 and 63 iterations (pseudo-time) | $\mathrm{St} = 0.098$ and 0.031 |

Drag came out of the steady run 0.3% low. Nobody would reject a drag value for that. The side force came out as zero instead of 0.082, and that is no rough estimate of the time mean. It is a different quantity, the average over a rotation the real wake never performs.

## Two clocks

Iterations are pseudo-time. Their 3,870 per turn is not a frequency, and only the transient run can say how fast anything happens. Its history of $C_l$ carries two time scales. A fast oscillation at $\mathrm{St} = fD\text{/}U = 0.098$, a period of about 10 time units, rides on a slow modulation at $\mathrm{St} = 0.031$, about 32 time units. The slow one holds 83% of the fluctuation energy of $C_l$.

That slow scale decides the run length. The first 150 time units hold fewer than five slow periods, which is why the run went on to 300. Even then, the 225 time units of statistics cover only seven.

![cl and cy of a cube at Re 300 against iteration in a steady run and against time in a transient run on the same mesh](cube-re300-steady-vs-transient-histories.png)
*Top: C<sub>l</sub> and C<sub>y</sub> against iteration in the steady run. Bottom: the same coefficients against t·U/D in the transient run.*

Whether the slow component is physics or a property of this mesh and this time step, one run cannot tell. It sits a factor of three below the shedding frequency, it shows in the drag too, and it does not decay over 300 time units. That is all I can claim for it.

## Setting up the transient run

Starting from the steady solution spares the spin-up from uniform flow, and the switch in the STAR-CCM+ GUI takes a few minutes:

- Physics continuum, Models: deselect Steady, select Implicit Unsteady.
- Solvers, Implicit Unsteady: Time-Step 0.05 s and Temporal Discretization 2nd-order. With $D = 1$ m and $U = 1$ m/s, a second is one $D\text{/}U$.
- Stopping Criteria: Maximum Inner Iterations 10, Maximum Physical Time on, Maximum Steps off.
- Report monitors: trigger on Time Step. Left on Iteration, they log every inner iteration and the history mixes converged and unconverged values.
- Save under a new name and run in batch. Raising Maximum Physical Time on the saved state extends the run.

The step gives about 200 samples per fast period.

> [!WARNING]
> Neither the step nor the ten inner iterations have been tested for sensitivity, so both Strouhal numbers belong to this mesh and this step.

## What to take away

When the two side-force monitors of a steady run swing out of phase and never settle, the solver is telling you that the wake is unsteady. Its iteration means then mix two kinds of number: some survive the averaging, like drag here, and some do not, like the side force. The steady run alone cannot tell you which is which. A transient run on the same mesh, started from the steady solution, can. Here it cost 60,000 iterations, four times the steady run.

The steady run is published as a Simulation Capsule, [`capsule_cube_re0300`](https://github.com/pacoEzq/simulation-capsule/blob/b7828d370c11cdf8729ab29db553e452d35763b0/examples/capsule_cube_re0300/manifest.json), in the `examples/` folder of the [simulation-capsule repository](https://github.com/pacoEzq/simulation-capsule). Its [`summary.json`](https://github.com/pacoEzq/simulation-capsule/blob/b7828d370c11cdf8729ab29db553e452d35763b0/examples/capsule_cube_re0300/summary.json) already marks the run `no_steady_state` and warns that iteration means are not time averages; this article is the measurement behind that warning.

---

The figures are drawn by [`fig_cube_re300_side_force.py`](fig_cube_re300_side_force.py) and [`fig_cube_re300_histories.py`](fig_cube_re300_histories.py) from [`cube_re300_steady_monitors.csv`](cube_re300_steady_monitors.csv) and [`cube_re300_transient_monitors.csv`](cube_re300_transient_monitors.csv), all in this folder. Text, figures and data are [CC BY 4.0](../../LICENSE-DOCS); the scripts are [MIT](../../LICENSE).

Personal project by [Francisco Ezquerra Larrodé](https://github.com/pacoEzq), published with the [simulation-capsule](https://github.com/pacoEzq/simulation-capsule) repository. Views are my own.
