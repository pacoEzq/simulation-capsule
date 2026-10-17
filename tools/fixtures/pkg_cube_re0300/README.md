# pkg_cube_re0300: neutral package fixture

This folder is the input of the Capsule Extractor, frozen for tests. It is
not a Simulation Capsule: it is what the solver adapter leaves behind before
the assembler builds one.

Origin: the same solved case as [examples/capsule_cube_re0300](../../../examples/capsule_cube_re0300),
taken from the working directory of the Part 7 chain. `setup.txt` and the
PNG files are byte for byte the published ones; `summary.json` is the
pre-build copy, without the `extensions` key.

By contract the package carries no `manifest.json` (the assembler writes it)
and no `object_audit.json` (a diagnostic, not an input). `adapter.json`
records the chain files with the hashes of the copies that actually ran,
which can differ from `macros/t7/` on `main`.

`SHA256SUMS` is verified from the parent folder: `sha256sum -c pkg_cube_re0300/SHA256SUMS`.