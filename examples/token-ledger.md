# Token cost of the example capsules

Measured on 2026-09-02 with `tools/capsule_ledger.py`, which shares its
directory walk with `tools/check_capsule.py` so the two cannot disagree about
what a capsule contains.

Regenerate with:

```bash
python3 tools/capsule_ledger.py examples/*/ --markdown
```

Character counts are exact. Tokens are a conversion, declared per artifact
type: 3.5 characters per token for the setup report and source, 4.0 for JSON,
2.5 for numeric CSV, and a flat 1,200 tokens per 1024 px image. The image
figure is model dependent. The honesty band across tokenizers is about 20%.

| Artifact | Part | ahmed25_re1e6 | cube_re200 | delta65_a13p3_re1e6 | jet_r2_re100 | naca0012_aoa5 |
|---|---|---|---|---|---|---|
| `setup.txt` | T1 | 9,117 | 6,864 | 11,857 | 6,311 | 5,664 |
| `summary.json` | T2 | 830 | 399 | 494 | 1,615 | 283 |
| `planes/` | T3 |  | 19,583 | 28,997 | 38,962 |  |
| `samples.csv` | T4 |  |  |  | 19,032 |  |
| `features.json` | T5 |  |  | 8,209 |  |  |
| `views/` | T6 | 7,200 |  |  |  |  |
| Text, measured | | 9,947 | 22,046 | 45,957 | 62,320 | 5,947 |
| Images, estimated | | 7,200 | 4,800 | 3,600 | 3,600 | 0 |
| **Total** | | **17,000** | **27,000** | **50,000** | **66,000** | **5,900** |
| Share of a 60,000 token budget | | 29% | 45% | 83% | 110% | 10% |

The `planes/` and `views/` rows include their images. Subtracting them gives
the cost of the tables alone: 14,783 for the cube, 25,397 for the delta,
35,362 for the jet.

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
five cases that share no geometry, no regime and no physics. Whatever else
grows, `setup.txt` does not.

**One capsule is over budget.** The jet sits at 110%, and it got there by
carrying both a full plane set and an 800 point volume cloud. The importance
sampled cloud is not the problem in itself: at 19,032 tokens it buys volumetric
coverage that no set of planes provides. The problem is carrying both at full
resolution in one capsule, which is a packaging decision and belongs to Part 7.

## Cross-check

Part 3 reported 36,830 characters for its four plane tables, counted by hand
before this tool existed. The tool measures 14,783 tokens for the same tables,
which at 2.5 characters per token is 36,958 characters. The two disagree by
0.35%. That is the ledger checking itself against a published figure it had no
part in producing.
