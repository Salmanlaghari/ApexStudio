# Original downloadable LUT source

This pack contains 30 original procedural 3D LUTs generated specifically for ApexStudio. No third-party LUT files, brands, or network assets are used. The Python generator uses only the standard library and creates 17×17×17 `.cube` data with deterministic color transforms.

The downloadable archive is `remote-assets/filters/apexstudio-lut-filters-vol-01.zip`; `packs/manifest.json` pins its SHA-256 and exact size. Every LUT file is under 1 MiB, and the app verifies each file's SHA-256, byte count, path, and parsed cube values before listing or loading it.

Regenerate into a temporary directory with `python3 generate_luts.py /tmp/apexstudio-luts`. The report records the archive and per-file checksums. ZIP member timestamps and permissions are fixed to make the archive reproducible.
