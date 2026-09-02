# macros

Simcenter STAR-CCM+ Java macros that produce capsule artifacts. Run them from
inside the simulation; they write files, they do not read the capsule back.

Macros here are reusable across cases. The per case `run_macro.java` that
Part 7 puts inside a capsule is a different thing: it records how one capsule
was built, and it travels with that capsule.

## TrimReportForAI.java

Turns the STAR-CCM+ Summary Report into `setup.txt`, the first artifact of
every capsule.

The Summary Report (File, Export, Summary Report) captures the whole
configuration: physics models, materials, boundary conditions, mesh counts,
solvers, field functions. It was written for a browser, not for a model, and
arrives as 400 to 900 KB of HTML, most of it styling. The macro keeps the
sections a reviewer audits, removes the ones that only style the scene
(rendering materials, palettes, layouts), and lands 90 to 95% lighter. Save
the resulting `*_AI.txt` as `setup.txt`.

Usage notes:

1. Export and trim in the same session. The macro picks the newest `.html` in
   the working directory, and a stale report would hand the model two
   versions of the truth.
2. The section lists are editable at the top of the file. Cut more if your
   case ships models you do not want described.
3. `setup.txt` is the one artifact that stays in solver native units,
   verbatim. That is deliberate: it is the audit record of what the solver
   was told, and the capsule contract flags it as dimensional.
4. Verify against your STAR-CCM+ version before trusting the output. The Java
   API drifted between 2602 and 2606 in this project, and a macro that
   compiled against one build is not guaranteed against the next.

Origin: published with
[Making AI Understand Your Simulations](https://community.sw.siemens.com/s/question/0D5Vb0000181bwjKAA/making-ai-understand-your-simulations)
and attached to Part 1 of the series.
