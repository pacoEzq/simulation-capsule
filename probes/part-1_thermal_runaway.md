# Probe record: Part 1, setup only

At Part 1 the capsule was one file. The probe therefore tested the narrowest
claim the series makes: that a trimmed solver setup report, on its own, lets a
reader reconstruct a case it has never seen and review it as an engineer would.

The case is a thermal runaway venting event, an aluminium cell blowing hot fuel
rich gas into a sealed box, with FGM combustion and two Lagrangian particle
clouds. It is the only case in the series that is not a canonical validation
geometry, which is the point: nothing about it can be recalled, so everything
in the answer has to come from the file.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| Claude | Anthropic | frontier at the time | 2026-07-15 | [shared](https://claude.ai/share/908cc4f0-61c3-4fe9-ac5b-d68ec93b1e5c) |

## The kit as issued

Round one, five closed questions:

```text
Attached is setup.txt, the only file so far in a Simulation Capsule: a
self-contained, token-budgeted distillation of a CFD case. Using this file
alone, and quoting values exactly as written where they exist:
1. In two sentences: what is being simulated?
2. Which turbulence model and time-integration scheme are active, and what is
   the time step?
3. How are the ejected particles modeled, and under what exact condition is a
   particle removed from the simulation?
4. Which solid materials appear in the setup?
5. When does the run stop? List every stopping criterion you find.
```

Round two, the reviewer frame:

```text
You are a senior CFD engineer reviewing a colleague's setup before any results
from it are trusted. The attached setup.txt is the only artifact available.
1. Executive summary: five lines, written for a project manager.
2. Setup audit: list the settings you would question or verify before believing
   this run, with a one-line reason each. Consider physics couplings, boundary
   conditions, numerics, and stopping criteria.
3. Dimensionless context: estimate any dimensionless groups you can justify
   from values present in the file, showing the inputs you used.
4. Honest limits: name three questions about this case that this file cannot
   answer, and for each, the smallest artifact you would request.
Do not invent values. If something is not in the file, say it is not in the
file.
```

## Result

Five for five on the closed questions, including the two that a summary would
have flattened: the particle removal condition quoted as the field function it
is, and the stopping criteria enumerated with the disabled one identified as
disabled rather than omitted.

The reviewer round is where the file earned its place. The reader questioned
the two way coupling volume fraction of 0.75 against the dilute assumption,
the heat transfer coefficient hardcoded to a constant rather than taken from a
correlation, the boundary named Outlet whose type is Wall, five inner
iterations per time step on a coupled reacting transient, and the depletion
threshold of 200 m/s sitting above a velocity limiter of 20 m/s so that it may
never fire. None of that is in any results file. All of it is in the setup.

## What this record supports

The Part 1 thesis, that the setup report is the cheapest artifact with the
highest review value, and that it belongs in the capsule before any result
does.

## Audit note

The Biot number was computed from file values and the reader stated which
inputs it used. Where the aluminium particle diameter was needed and absent,
it said so instead of estimating. The lumped capacitance threshold and the
dilute flow limit are textbook and were named as such.

One limit of this session under the current rules: the capsule was one file,
so probes could not name files, and there is no cross artifact probe. The
result speaks to `setup.txt` alone and claims nothing about capsule assembly.
