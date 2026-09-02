# probes

A probe test asks a language model a small number of narrow questions about a
capsule, each one naming the files it may use, and then checks the answers
against the capsule itself. It is the review step of this project, not a demo.
Defects have been caught here that human review had already missed twice.

The object under test is the capsule. It is never the model.

That distinction decides everything else in this directory. A probe record
says whether the format carried the answer, so it reports what each reader
could and could not recover from the files. It does not score models against
each other, and it never puts a product name in the subject of a finding.
Provenance is a different matter: family, version and date are recorded, and
the shared conversation is linked, because a claim nobody can go and check is
not evidence.

## Rules

1. The capsule travels whole. No file is withheld, none is edited to help.
2. Each probe names the files it may use. A probe that says "from view_x.png
   alone" is testing whether that one view answers its own question.
3. Answers are scored PASS or FAIL against the capsule, with the delta stated
   when a number is involved. There is no aggregate score and no ranking.
4. Every free answer gets an audit note that separates what is traceable to
   the files from what the model brought in from training. Both happen. Only
   the first is evidence about the format.
5. A disagreement between two readers of the same view is a finding about the
   view, and usually the most valuable thing in the session.
6. A wrong answer from a mid tier model is ambiguous, since the capsule and the
   reader are confounded. A wrong answer that any reader would have given, such
   as an orientation read backwards or a number that the capsule never anchored,
   counts regardless of tier.

## Kit template

Issue the framing line, then the probes, then the free question. Keep the
probes narrow enough that a wrong answer points at one artifact.

```text
You are given a Simulation Capsule (attached, whole).

Before answering, list the files you opened.

Answer each probe using only the files it names. Keep answers to the length
requested.

Probe 1. From <file> alone: <question>. One sentence.
Probe 2. From <file> and <file> together: <question>. Three sentences.
Probe 3. From <file> alone: <question>, and state your method in one sentence.

Then, using the whole capsule, what is the most interesting thing you can tell
me about this flow?
```

The opening instruction is the newest part of the kit and it earns its line.
On 2026-09-02 one reader answered the regime question from the name of a view
rather than from the setup report, and that only became visible after the fact.
A list of files opened turns a suspicion into an observation, before anyone
reads a single answer.

## Records

| Date | Capsule | Readers | Record |
|---|---|---|---|
| 2026-09-02 | `capsule_ahmed25_re1e6` | 3 | [2026-09-02_ahmed25_re1e6.md](2026-09-02_ahmed25_re1e6.md) |
