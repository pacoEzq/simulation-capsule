# colormaps

Lookup tables in the import format of Simcenter STAR-CCM+ 2606 (`LookupTableManager.importLookupTables`),
32 control points each. `output_exporter.java` imports one of them when `card.body.colormap` names it and
the .sim has no table of that name; it looks for `colormaps/<name>.colormap` next to the macro, then in
the session directory.

Origin: the perceptually uniform colormaps of matplotlib, resampled.

- `viridis`, `magma`, `inferno`: CC0 1.0 (public domain dedication by their authors, Stéfan van der Walt
  and Nathaniel Smith).
- `cividis`: Matplotlib license (BSD-compatible, PSF-based), full text in `LICENSE_matplotlib.txt`.
  Copyright (c) 2012- Matplotlib Development Team; All Rights Reserved.
  Cividis by Jamie R. Nuñez, Christopher R. Anderton and Ryan S. Renslow.
