# macros

Simcenter STAR-CCM+ Java macros that produce capsule artifacts. Run them from
inside the simulation; they write files, they do not read the capsule back.

Macros here are reusable across cases. `t7/` is the exception: it holds the
chain that builds the Part 7 capsules, and its `run_macro.java` belongs to
that one case. No capsule carries a copy of it; each capsule's
`manifest.json` names it by path and sha256.

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

5. Where a published `setup.txt` shows `<path>` and `<host>`, the report named
   the folder of the `.sim` file and the machine that ran it. Both values were
   removed by hand before publication. They are redactions, not placeholders
   waiting for a value, and nothing else in the capsule depends on them.
   This macro redacts nothing: on your own case it copies both as the report
   gives them, so remove them before the capsule leaves your machine.

Origin: published with
[Making AI Understand Your Simulations](https://community.sw.siemens.com/s/question/0D5Vb0000181bwjKAA/making-ai-understand-your-simulations)
and attached to Part 1 of the series.

## t7/

The chain behind the Part 7 capsules: one cube, swept over Reynolds number.
`run_macro.java`, `output_exporter.java`, `manifest_writer.java` and
`object_audit.java` run inside Simcenter STAR-CCM+, one macro per batch
session. `sweep_driver.sh` launches those sessions over the points of a
sweep file; `sweep.example.json` shows its shape. `check_window_stats.py`
guards the window statistics, and `build_capsule.py` assembles the capsules
with the tools in `../tools/`.

The chain starts from `cube_re200.sim`, the cube of
[Part 3](https://community.sw.siemens.com/s/question/0D5Vb00001QuRzDKAV/preparing-cfd-output-for-large-language-models-310-sections-and-planes-the-csv-plus-image-pair),
which `run_macro.java` turns once into the sweep template. Part 3 attaches
the `.sim`; neither it nor the template is in this repository, and no `.sim`
will be: a capsule is meant to be read without its simulation. The `.sim`
names in a capsule's `manifest.json` are file names, not paths into this
repository.

---

## Terms

The macros in this directory ask you to operate Simcenter STAR-CCM+, and they
were produced by pointing an AI tool at Siemens documentation. Readers who set
this up with their own AI tooling are responsible for their own plan and
settings.

Use of Siemens documentation and AI tools remains subject to your applicable Siemens terms.