# ApexStudio animated sticker assets

Standalone generated bundle for the ApexStudio animated-sticker feature. The 100 stickers are original vector artwork and animations generated specifically for ApexStudio using only shape primitives in `generator.py`; there are no external asset dependencies and no third-party assets were copied or downloaded. This bundle does not claim an external standard license.

## Contents
- 100 Lottie JSON stickers, transparent 256x256 canvas, 30 fps, 60-frame loop.
- 10 categories with 10 entries each; every manifest entry has a unique `motifKey` and geometry signature.
- Four ZIP packs, 25 stickers per pack. Each ZIP includes its own manifest with sticker SHA-256 hashes and byte sizes.

## Validation
- JSON parse and canvas/timing: PASS (100/100)
- Unique IDs: PASS (100)
- Unique motifKeys: PASS (100)
- Normalized geometry signatures (palette/layer-transform independent): PASS (100)
- Manifest entries: PASS (100 exactly once)
- Pack counts: PASS (4 x 25)
- Total uncompressed sticker bytes: 680,576
- Total compressed ZIP bytes: 109,116

## Pack hashes and sizes
- Pack 01: 27,820 bytes; SHA-256 `34cb5c563f6078e706ed064fdfc8676a1d99e44f8cc713f0548945d437151416`
- Pack 02: 28,707 bytes; SHA-256 `fcfeb9986bb54ee9edf0bbf217826bb11b6d2d4442a5947b4e16776a362c7f1e`
- Pack 03: 26,666 bytes; SHA-256 `58771ca38256620a5329dd90f1457daa4cdb00dd10b72d5e660added69114a93`
- Pack 04: 25,923 bytes; SHA-256 `5067c1e4b97fca16fed2cfcb98291334dbb78be4eaddab0f5b4ad593f98aa302`

## Compatibility caveat
The files use standard Bodymovin shape-layer structures and animated transform keyframes. Some renderers may differ in support for procedural star shapes; test with the target Lottie runtime.
