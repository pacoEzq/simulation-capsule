# Capsules as published

This directory holds every example capsule exactly as it shipped with its part
of the tutorial series, byte for byte, from Part 2 on. Nothing in it changes
after publication, and each new part adds its capsule here when it is
published. Read in series order, it shows the format arriving one layer at a
time.

`../frozen.sha256` lists every file here with its hash, and
`tools/check_frozen.py` fails the CI run if a file is changed, removed or added.

The capsules under `../` are the current ones, validated with `--strict`.

For Parts 2 to 6b the current copy was derived from the one here, so that it
conforms to the SPEC as the format moved on. The tag `spec-0.1` marks the last
commit before that for Parts 2 to 6, so the whole repository as those posts
linked it stays addressable.

From Part 7 on the copy under `../` is the same capsule, byte for byte. A
later capsule of the same case that holds something else carries another name.

Do not fix anything in this directory. If a published capsule has a defect,
the defect is part of the record; fix it in the current copy and say so in its
note.
