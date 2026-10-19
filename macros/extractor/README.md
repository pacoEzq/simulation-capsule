# extractor

The Simcenter STAR-CCM+ adapter of the Capsule Extractor. Run on an already solved `.sim` with its
extraction card (`card.json`) next to it, it leaves a neutral package under `work/<alias>/`: plane
tables and images, views, `render.json`, `object_audit.json` and `adapter.json`.
`tools/extract_capsule.py` builds, checks and measures the capsule from that package.

Order: `output_exporter`, `object_audit`, `manifest_writer`, each as `starccm+ -batch <macro>.java <case>.sim`.
`summary.json` is the case macro's, in the session directory, when it exists; otherwise the one in `work/<alias>/`.

v0 limitations:
- View scenes are named `view_<s>`, `disp_<s>`, `title_<s>`; plane scenes `LLM_plane_<field>`, `LLM_disp_plane_<field>`, `LLM_body_plane_<field>`, `LLM_title_plane_<field>`. The exporter creates in memory any that the `.sim` lacks, and never saves them.
- A station's image shows `image_field`, else `fields[0]`; the plane CSVs carry no comment line.
- `adapter.json` has a `mesh` block (`cells` only) only when `card.references` has `key: cells` with `from: "report <name>"`.
- `object_audit` lists Derived Parts, station tables, body boundaries and the fluid region; scenes are not on its list.
