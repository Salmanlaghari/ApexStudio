# ApexStudio editor outcomes

- [x] The concrete incomplete “Future” flow is fixed and wired: the preview now exposes a Future button, opening the Upcoming Frames overlay starts thumbnail extraction even when it was already composed hidden, cards seek the real playhead, and Preview 3s plays the actual timeline snippet.
- [x] The timeline workspace has a smooth cinematic glass/depth treatment using the existing palette and remains accessible on compact portrait and landscape layouts.
- [x] The timeline keeps the existing project/export authority while providing CapCut-like selection, playhead-first navigation, snap/ripple visibility, smooth zoom/pan, and responsive trim feedback.
- [x] Trimming/reordering now normalizes stale transition junctions, including transitions whose overlap clamps to zero; focused regression coverage was added.
- [x] Text controls have a taller readable sheet and horizontally scrollable, minimum-width tabs so labels do not collapse on compact screens.
- [x] Sticker search now has a real Openverse HTTPS search path, thumbnail results, attribution, cached downloads, and honest loading/error states while bundled and animated packs remain available.
- [ ] Local focused Gradle tests are still blocked before compilation because this sandbox's Java 21 installation has no `javac`/`JAVA_COMPILER` capability; CI remains the authoritative build check.
- [x] No unsupported 4K/8K processing or native 3D rendering is claimed; “3D” editor options are existing compositing/effect presets and the visual pass is UI depth/polish.
