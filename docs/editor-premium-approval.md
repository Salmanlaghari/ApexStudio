# Approved premium editor follow-up

This work continues PR #173 from `f02c2623e90df2e11cafa4488e0756a33848759f`. It does not replace earlier commits, create another PR, merge, migrate project records, or require clearing app data.

## What changed

The normal editor starts with a small navy ApexStudio header, the real resolution menu and gradient Export action. The 16:9 editing viewport sits directly below it, rather than being centered in a large weighted empty area. Source footage remains FIT by default; no media crop is baked into the project to imitate the reference.

The real transport touches the preview. A selected main-video clip gets a bordered two-row inspector immediately above the timeline: selected type, keyframe toggle, Animation, and Position/Scale/Rotation/Opacity shortcuts. The shortcuts open the existing horizontally scrolling property editor with the requested filter. Animation retains the existing preset/customized route. Property selection is temporary UI state, not a saved-project field.

The timeline retains time zero, real thumbnails, waveform analysis, selection, seek, trim, drag, keyframes, and existing import routes. Its video lane is 36dp; occupied support lanes use dark rounded cyan/violet/indigo/teal treatments. Audio rows are compacted without removing their edge-trim or placement gestures. Empty projects do not gain fake clips, tracks, waveforms, or keyframe diamonds.

The bottom dock evenly distributes Media, Audio, Text, Overlay, Stickers, FX, Filters and More across the phone width. More still exposes the existing horizontal contextual actions. Camera/Lenses are not reintroduced into the editor.

## Deliberate differences from the picture

The picture is a visual reference, not project content. Its mountain footage, title, sample effect names and waveforms are not inserted into the owner's project. The selected-type chip says Video for a video selection, rather than pretending a Text selection. The existing selection-specific text/sticker tools remain available.

To preserve usable timeline width and the branch's zero-gutter gesture geometry, small lane identity chips live inside the lanes rather than reserving the picture's wide fixed left label column. They can cover the first few pixels of a clip; they do not consume pointer events or change its time origin. A separate wide gutter was not silently introduced.

System status/navigation insets are real, not painted into the app. On shorter phones or projects with many actual lanes, the timeline's existing vertical scroll and focus mode remain necessary; the dock and preview are not moved into separate portrait tool screens. No all-device pixel-perfect claim is made.

## Functionality and export boundaries

No export engine, project serializer, source URI or preflight is removed by this visual follow-up. Existing unsupported compositions must continue to fail clearly rather than silently omit visible content. In particular, visible PiP, project stickers and other unsupported whole-project combinations are still not made exportable merely by styling their timeline lanes. See [the existing action/device audit](editor-approved-layout-device-check.md) for the retained limitations.

## Verification and owner test

The PR status records the exact tested commit, test counts, APK hash, signer, embedded build identity and capture paths. Host Compose renders are labeled host renders; Android emulator captures are labeled emulator captures. Neither is a claim of physical-phone codec, color or audio parity.

`PremiumEditorInstrumentedTest` composes the production editor with deterministic test-only media and an in-memory project, checks real keyframe mutation and project preservation, and captures the installed UI. Its fixture does not read or overwrite owner project records. The host regressions cover 320dp dock visibility, property callback/filter routing, compact timeline geometry and the approved landscape viewport.

The owner must install the exact supplied arm64 APK without uninstalling/clearing data, check the full commit under the branded header's Installed build dialog, inspect existing projects, scrub/play across cuts, test the horizontal editing tools and review a supported export. PR #173 stays draft and unmerged pending that device test and explicit merge approval.
