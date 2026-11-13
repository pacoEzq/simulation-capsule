# Probe record: the compact notation of setup.txt, cylinder at Re = 100

SPEC 0.13 lets `setup.txt` be written in a compact notation (SPEC 4.1) that
decodes to the plain form byte for byte. This probe asked whether a reader
answers questions about the setup at least as well from the compact form as
from the plain one. It is not a part of the series. The object under test is
the notation, on one capsule, `capsule_cylinder_re0100`.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| A | Anthropic | Sonnet 5.5, `claude-sonnet-5-5` | 2026-11-04 | not shared |

The reader shares a family with the model that designed the notation and helped
write this record (rule 6). The transcripts are not linked; the raw answers are
kept with the project records.

## The kit

One file per arm, and nothing else:

| Arm | File | Bytes | Tokens |
|---|---|---|---|
| plain | `examples/capsule_cylinder_re0100/setup.txt` as published | 24,697 | 12,340 |
| compact | the same file encoded by `tools/setup_v2.py`, notation header first | 19,526 | 9,854 |

The notation header was inside `setup.txt`, so it was part of the capsule the
reader received, not part of the prompt. The prompt did not explain the
notation.

24 questions, the same in both arms: 12 on values (a setting quoted from the
file), 9 on sections (which part of the setup holds a setting) and 3 on direct
parents (which node a node sits under). Each arm ran 3 times, so 72 answers per
arm. This record does not carry the questions and the expected answers
verbatim; they are kept with the project records.

## Result

| Questions | Plain | Compact |
|---|---|---|
| Values | 36 of 36 | 36 of 36 |
| Sections | 21 of 27 | 27 of 27 |
| Direct parents | 9 of 9 | 9 of 9 |
| Total | 66 of 72 | 72 of 72 |

The compact form is 20 percent fewer tokens. All six misses of the plain form
were section questions, and the compact form did not repeat them. No question
answered right from the plain form was answered wrong from the compact one.
Why the plain form missed was not probed.

## Header changed since the probe

The probe ran with an earlier header. Its template line read `@k=0..N template
: N+1 sibling lines; in each {a:b} slot, line k holds the number a + k*b.`
The header SPEC 0.13 quotes says what the encoder writes when a number does
not change along the run: it is written plainly and is the same on every line.
The body of the compact file is unchanged; only the header differs, and the
current file is 19,585 bytes. The 72 of 72 was measured on the earlier text and
has not been rerun on the current one.

## Limits

- One case, one `setup.txt`.
- One reader, from the same family as the notation's designer.
- Only 3 direct-parent questions, which is the question type the
  indentation rule most directly affects.
