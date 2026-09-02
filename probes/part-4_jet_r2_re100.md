# Probe record: Part 4, capsule_jet_r2_re100

Part 4 added `samples.csv`, the importance weighted volume cloud. The probe
tested whether a reader can use the cloud for arithmetic rather than only for
looking at, and whether it knows what the cloud cannot do.

This is also the session that found the defect the validator now catches.

## Provenance

| Reader | Family | Version | Date | Conversation |
|---|---|---|---|---|
| Claude | Anthropic | frontier at the time | [author: date] | [shared](https://claude.ai/share/7f777966-930d-455d-b563-05febd1d1092) |

## The kit as issued

Three rounds. Round one asked for an inventory of the capsule, whether the case
is air at 1 m/s, and the law behind `samples.csv` including whether the draw
could be reproduced from the capsule alone.

Round two, the self audit:

```text
Now audit the capsule against itself. Same rules, same brevity.
The jet inlet velocity is set by the field function w_jet in setup.txt. Derive
the analytic mass flow through the orifice and compare it with the value the
capsule reports. Show the integral.
Two files describe how the mesh is refined along the jet path. Do they agree?
The samples.csv header claims the volume weights are self-normalized so that
their sum is the domain volume. Test that claim against another file.
```

Round three asked six questions about the spatial layer and its limits, from
core height at x/D = 10 through wall shear stress on the floor, closing with
three unanswerable questions, the smallest artifact for each, and one packaging
change.

## Result

Every round closed. The mass flow integral was derived by hand from `w_jet`,
giving pi over 2, matching the capsule's analytic value exactly and putting the
computed value 0.5% above it, which is the reported mass flow error. The volume
weight sum came to 1599.808 against a domain volume of 1600 from a different
file, and the extents behind that volume were corroborated from a third file
through boundary face counts.

Two answers are refusals and both are correct. Asked to redraw the sample, the
reader showed that the population weights are not shipped, only the winners, so
it cannot be done from the capsule alone. Asked for wall shear stress on the
floor, it declined to build a proxy: the first off wall row sits above both
prism layers, so a finite difference would systematically underestimate, exist
only on lines, and could never answer where the maximum is.

## Defects found in the capsule

- **A trailing comma after `provenance` makes `summary.json` fail strict JSON.**
  The reader found it in the first inventory, and named it again at the end as
  the one packaging change it would make: a file declaring a machine readable
  schema that stops `json.load` at line 121. It shipped anyway. That gap is why
  `tools/check_capsule.py` exists, and `json_parses` is its first check.
- The handedness of the coordinate triad is declared nowhere, and no PNG glyph
  shows the third axis, so the sign of the vortex pair circulation is
  interpretable only by assuming a right handed system.
- `reference.note` calls itself the only dimensional block, while `performance`
  carries a dimensional flag of its own. The capsule contradicts itself about
  its own nondimensional rule.
- `setup.txt` defines `u_U` as the streamwise component while `summary.json`
  reports a key of the same name built from velocity magnitude. Disclosed in
  the file, and still a trap.

## Audit note

Almost everything here is computed rather than recalled: the integral, the
sums, the face count reconciliation, the pixel measurements used to compare
scenes. Where the reader used outside knowledge it was to interpret, not to
supply values. The one place it declared an outright limit of the capsule, the
absence of population weights, is a property of the format and is now the
reason `manifest.json` exists in the Part 7 plan.
