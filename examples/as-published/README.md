# Capsules as published

This directory holds the five example capsules exactly as they shipped with
Parts 2 to 6 of the tutorial series, byte for byte. Nothing in it changes after
publication.

`../frozen.sha256` lists every file here with its hash, and
`tools/check_frozen.py` fails the CI run if a file is changed, removed or added.
The tag `spec-0.1` marks the last commit before the format moved on, so the
whole repository as the posts linked it stays addressable.

The capsules under `../` are the current ones: derived from these by script,
conforming to the SPEC version stated in `SPEC.md`, and validated with
`--strict`. The pair is the record of how the format got here. Each capsule's
note in `../` lists both paths and the keys that changed between them.

Do not fix anything in this directory. If a published capsule has a defect,
the defect is part of the record; fix it in the current copy and say so in the
note.
