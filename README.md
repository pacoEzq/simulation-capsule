[![validate](https://github.com/pacoEzq/simulation-capsule/actions/workflows/validate.yml/badge.svg)](https://github.com/pacoEzq/simulation-capsule/actions/workflows/validate.yml)

# Simulation Capsule

**A Simulation Capsule is a self-contained, token-budgeted distillation of a
simulation case, designed to be consumed by a language model.**

A Simulation Capsule is to a CFD case what `llms.txt` is to a website.

---

## The problem

A finished CFD case is gigabytes. Cells, fields, reports, scenes, a solver history,
a mesh nobody wants to look at again. A language model reads a context window.

The two obvious moves both fail. Exporting raw data spends the budget on structure
the model cannot use, and runs out long before it reaches anything interesting.
Pasting a screenshot throws away every number that could have anchored an answer,
and leaves the model guessing at magnitudes from pixel colours.

The capsule is the artifact in between. A fixed directory of small files, each one
an answer to a question somebody would actually ask about the case, sized so the
whole thing fits in a context window with room left over for the conversation.

## Design rules

**One artifact, one question.** Nothing goes in the capsule because it was easy to
export. A plane is in there because someone wants to know what the wake looks like
at that station. A view is in there because it answers something a scalar cannot.

**Nondimensional by default.** Every scalar, coordinate, time and frequency is
expressed as a dimensionless group: $C_p$, $C_d$, $x/c$, $St$, $tU/D$. Anything
dimensional carries an explicit flag. A model comparing two cases should not have to
guess whether the numbers share units.

**The budget is stated, not hoped for.** Each artifact declares what it costs in
tokens and what it bought. The series works to roughly 60,000 tokens for a complete
capsule, and [`examples/token-ledger.md`](examples/token-ledger.md) records what the
published capsules actually cost.

**Exposure is a designed property.** Artifacts differ in how much they leak about
the underlying geometry and setup. A features file leaks least, a rendered view
leaks most. The capsule makes that gradient explicit rather than leaving it to
whoever packs the zip.

## Structure

```
capsule_<alias>/
├── setup.txt           trimmed solver setup report
├── summary.json        global scalars
├── planes/             plane sections, CSV and image in pairs
│   ├── plane_*.csv
│   └── plane_*.png
├── samples.csv         importance-weighted volume sample
├── features.json       named flow features with their properties
├── views/              renders, each answering a declared question
│   └── *.png
├── diff/               image differences between capsules
│   └── *.png
├── run_macro.java      reproduction
├── manifest.json       seeds, colorbar ranges, versions
├── signals/            time series and spectra
├── modes/              POD and DMD decomposition
├── snapshots/          phase-locked renders (optional)
└── disclosure.json     what is safe to send outside
```

Not every case needs every layer. A steady RANS case has no `signals/`. The layers
that are present must follow [SPEC.md](SPEC.md).

## The tutorial series

The capsule is developed one layer at a time in **Preparing CFD Output for Large
Language Models**, a ten-part series on the Simcenter community forum. Each part
introduces one idea, produces one artifact, and uses a reference case chosen so the
technique reveals something the case would not otherwise show.

Recipes are written for Simcenter STAR-CCM+. The principles are solver-agnostic and
model-agnostic; nothing here depends on a particular frontier model.

The series starts here: [Part 1, What does an LLM consume well?](https://community.sw.siemens.com/s/question/0D5Vb00001OziUAKAZ/preparing-cfd-output-for-large-language-models-110-what-does-an-llm-consume-well)

## Repository contents

| Path | What it holds |
|------|---------------|
| `SPEC.md` | The capsule contract: directory layout, per-artifact rules, naming |
| `examples/` | Complete capsules from the series reference cases |
| `probes/` | Probe test records: what models could and could not read from each capsule |
| `tools/` | Python for building and checking capsule artifacts |
| `macros/` | Java macros for Simcenter STAR-CCM+ export |
| `assets/` | Figures used by the series |

## Validation

A capsule gets checked twice, by a program and by a reader.

The program is [`tools/check_capsule.py`](tools/check_capsule.py), which turns the
mechanical parts of the SPEC into fourteen checks: standard library only, nothing to
install. Every capsule in `examples/` runs through it on every push.

```bash
python3 tools/check_capsule.py examples/*/          # errors fail, warnings show
python3 tools/check_capsule.py examples/*/ --strict # warnings fail too
python3 tools/capsule_ledger.py examples/*/ --markdown
```

The validator carries its own regression test.
[`tools/test_check_capsule.py`](tools/test_check_capsule.py) asserts that the jet
capsule, as it was first published, fails in exactly two places, and that every
check fires on at least one fixture. A check that has never failed has not been
verified, it has only been quiet.

The reader is a language model. Before a capsule is published it is handed whole to
one, in a fresh conversation, and asked narrow questions whose answers can be
checked against the files. [`probes/`](probes/README.md) holds those sessions and
the nine capsule defects they caught, including the one that this validator now
catches automatically.

The published capsules pass with warnings. Those warnings are schema divergence
across capsules written before the schema was closed, listed in
[`examples/README.md`](examples/README.md) and settled by the automation part. They
are left visible rather than patched.

## Status

The specification tracks the series and is incomplete by design: layers are
specified as their tutorial publishes. Parts 1 to 6 are out, so everything from
`setup.txt` through `views/` is settled. The transient layers and the disclosure
audit are still moving.

Breaking changes to settled layers get a version bump and a note in `SPEC.md`.

## Contributing

Issues are welcome, particularly ports to other solvers and cases where the format
breaks down. If you build a capsule for a case of your own and something in the spec
did not fit, that is worth an issue even without a fix attached.

## License

Two licenses, because this repository holds two kinds of thing.

| What | License |
|---|---|
| `tools/`, `macros/` | [MIT](LICENSE) |
| `SPEC.md`, `README.md`, `examples/`, `probes/`, `assets/` | [CC BY 4.0](LICENSE-DOCS) |

The code is MIT so you can run it, fork it and ship it inside your own
workflow without asking anyone. The specification, the example capsules and
the documentation are CC BY 4.0, which asks only that you credit the source
when you reproduce or adapt them. `CITATION.cff` says how.

Both licenses cover what is in this repository. Neither reaches the capsules
you build by following the specification. Those are your data, and nothing
here claims a share of them.

---

Personal project by [Francisco Ezquerra Larrodé](https://github.com/pacoEzq). The
tutorial series it accompanies is published on the Simcenter community forum. Views
are my own.
