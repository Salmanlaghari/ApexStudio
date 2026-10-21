# Offline creator packs and export size

## 64 searchable 3D-look stickers

The sticker library bundles 64 transparent 256×256 PNG renders from [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji), pinned to `1ffb34c752ecf5d402f04cfb4b392c77f57c54bc`, under MIT. Copyright, full license and per-file source URLs/SHA-256 records ship with the app. Existing sticker packs remain available. Search matches bundled names/tags; no account, network search, generated assets or remote inference is involved. These are 3D-rendered flat stickers, not rotatable meshes or face-tracking lenses.

Adding a result uses the existing undoable clip sticker edit. Preview and selected/sequential video export use the existing sprite renderer. Premultiplied-alpha blending avoids dark transparent edges. Unsupported project-wide sticker/PiP combinations retain their explicit export gate rather than losing stickers in a successful file.

## 120 text design–motion combinations

The Presets tab has 12 original editable designs × 10 supported production animation modes. Search finds a design/motion combination. Applying one preserves the text's ID, actual content, position, range and keyframes, and changes the real style/motion payload in one undoable project transaction. These are 120 combinations, not a claim of 120 independent fonts or motion engines. They use built-in fonts and the shared text sprite/animation backend, with no generated image or cloud call. Tests rasterize every combination using the production renderer.

## 12 original ranged color looks

Filters opens a compact bottom workspace with a horizontal catalog. Select an explicitly selected main-track video, choose a look, adjust Strength and optional Exposure/Contrast/Saturation, and choose its source-time range. Add another range to stack or sequence separate saved looks. The timeline shows labeled colored source-range bands mapped through clip timing. Preview changes are temporary until Apply; Cancel and draft Undo preserve the project. Apply validates the same project/clip snapshot and records one history step. Existing effects remain in the chain.

All 12 looks are original GLES math, not downloaded proprietary presets or a claim to reproduce another app's engine. Both video export scopes and preview use the same canonical shader and persisted parameters. Zero strength and inactive ranges are identity operations, including customized colors. Exposure/contrast/saturation defaults preserve older saved projects. Malformed IDs/ranges/non-finite parameters fail export preflight. Overlay/PiP targets are intentionally unsupported and display a reason.

## Export size is not a fixed placeholder

Before encoding, the screen labels its number **estimated video payload, not total file size**. It uses the actual selected export scope's rendered duration and the same requested video bitrate calculation as the encoder. Unconfigured AAC bitrate, container metadata, rate control and possible audio pass-through prevent an honest exact pre-export total. The screen explains what is excluded instead of inventing an audio bitrate or calling this exact.

After export, the screen reads actual bytes from the completed app export file or content-provider metadata/read-only descriptor, showing decimal MB and exact byte count. A measured zero remains zero; missing, denied or unknown-size outputs show their actual unavailable reason, never a fake zero. Sharing uses the last completed file, not a newly edited but unexported project.

Resolution, frame rate, aspect ratio, Auto/fixed bitrate and quality controls feed the real export configuration. Quality is disabled while a fixed bitrate takes precedence; settings are locked during an active export. Native path capability checks, output-scope gates and failure messages remain authoritative. H.264/MP4 is labeled honestly; no unsupported H.265 selector is offered.

## Testing boundary

Host EGL pixel tests compile and execute the production workspace, sticker and text GLSL; native Robolectric tests exercise picker actions, text rasterization, draft cancellation, persistence/history and target changes. These are not physical-phone GPU, codec, touch, Android share recipient or Play approval tests. Owner device testing remains required, and PR #172 stays draft and unmerged.
