# Token cost of the example capsules

Measured on 2026-09-11 with `tools/capsule_ledger.py`, which shares its
directory walk with `tools/check_capsule.py` so the two cannot disagree about
what a capsule contains. The `summary.json` row was measured again on
2026-09-24, after the reference keys were renamed to the suffix grammar of SPEC
3.1; nothing else moved.

Regenerate with:

```bash
python3 tools/capsule_ledger.py examples/capsule_*/ --markdown
```

Character counts are exact. Tokens are a conversion, declared per artifact
type: 3.5 characters per token for the setup report and source, 4.0 for JSON,
2.5 for numeric CSV, and a flat 1,200 tokens per 1024 px image. The image
figure is model dependent. The honesty band across tokenizers is about 20%.

The counts below were taken on files with LF endings, as the repository stores
them. The same capsules measured in a working tree that checked out CRLF come
out about 4 percent higher in every text row, which is one byte per line and
nothing else. A ledger is only reproducible if it says which of the two it
counted.

| Artifact | Part | ahmed25_re1e6 | cube_re200 | delta65_a13p3_re1e6 | jet_r2_re100 | naca0012_aoa5 | naca0012_aoa7 |
|---|---|---|---|---|---|---|---|
| `setup.txt` | T1 | 9,117 | 6,864 | 11,857 | 6,311 | 5,664 | 6,411 |
| `summary.json` | T2 | 865 | 382 | 518 | 1,594 | 385 | 464 |
| `planes/` | T3 |  | 19,211 | 29,056 | 38,171 |  |  |
| `samples.csv` | T4 |  |  |  | 19,032 |  |  |
| `features.json` | T5 |  |  | 7,864 |  |  |  |
| `views/` | T6 | 7,200 |  |  |  | 2,400 | 2,400 |
| `diff/` | T6b |  |  |  |  |  | 2,400 |
| `diff.json` | T6b |  |  |  |  |  | 1,487 |
| Text, measured | | 9,980 | 21,655 | 45,693 | 61,506 | 6,047 | 8,360 |
| Images, estimated | | 7,200 | 4,800 | 3,600 | 3,600 | 2,400 | 4,800 |
| **Total** | | **17,000** | **26,000** | **49,000** | **65,000** | **8,400** | **13,000** |
| Share of a 60,000 token budget | | 29% | 44% | 82% | 109% | 14% | 22% |

The `planes/`, `views/` and `diff/` rows include their images. Subtracting them
gives the cost of the tables alone: 14,411 for the cube, 25,456 for the delta,
34,571 for the jet.

## What the numbers say

**Numeric data is the expensive layer, not the pictures.** Images account for
5% of the jet capsule, 7% of the delta and 18% of the cube. Numeric CSV
accounts for 82% of the jet, once `samples.csv` is added to the plane tables.
The intuition that images are what blows a context budget is wrong at this
scale, and it is wrong by a factor of ten.

**Six views cost 7,200 tokens.** Less than a third of one set of plane tables,
and they carried a four reader probe test in which three readers recovered the
regime, the turbulence model, the convergence caveat and a quantitative wake
measurement. Per token, the visual layer is the cheapest evidence in the
capsule.

**The setup report is a bounded cost.** Between 5,664 and 11,857 tokens across
six cases that share no geometry, no regime and no physics. Whatever else
grows, `setup.txt` does not.

**One capsule is over budget.** The jet sits at 109%, and it got there by
carrying both a full plane set and an 800 point volume cloud. The importance
sampled cloud is not the problem in itself: at 19,032 tokens it buys volumetric
coverage that no set of planes provides. The problem is carrying both at full
resolution in one capsule, which is a packaging decision and belongs to Part 7.

**A comparison costs less than the cases it compares.** The variant capsule
describes its own case in 8,360 tokens of text and adds 3,887 on top for the
comparison: 2,400 for the two difference images and 1,487 for `diff.json`. The
pair together is 21,000 tokens, 36% of the budget, and between them they answer
a question neither one answers alone.

**`diff.json` is heavy for a manifest.** At 1,487 tokens it costs nearly four
times the `summary.json` beside it, for eight hashes and six notes written in
prose. Worth knowing before the format grows more manifests of its kind.

## Cross-check

Part 3 reported 36,830 characters for its four plane tables, counted by hand
before this tool existed. The tool now measures 14,411 tokens for the same
tables, which at 2.5 characters per token is 36,028 characters, 2.2% below the
published figure.

That gap is not drift in the tool. The plane CSV headers changed when the
capsules were brought onto one reference schema, and the character counts moved
with them: down for the cube and the jet, up for the delta. The 0.35% agreement
this file recorded on 2026-09-02 was measured against the headers as Part 3
published them, and it is kept here because a cross-check that quietly follows
the data it checks is not a cross-check.
