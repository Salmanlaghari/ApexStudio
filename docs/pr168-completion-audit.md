# PR #168: completion audit

This work stays in the existing `fix/pr168-editor-reliability` branch. It does not merge the PR, push to main, remove projects or clear app data. The owner's earlier screenshots show build `633e8504`, not the updated PR build; they cannot establish whether this branch fixes the reported symptoms.

## Export reliability

A concrete retry defect was found: selected-video, template and standalone-audio terminal callbacks left the active Transformer reference installed. The next export was refused by the busy guard. Terminal callbacks now retire only their own renderer. Cancelled/older runs cannot publish a late result over a replacement run. Project callbacks use the same identity protection.

Selected-video export previously ignored the selected frame-rate cap and automatic quality factor, and a normal 16:9 source could keep its original pixel dimensions despite a 720p selection. The final video chain now enforces the requested size/aspect and caps frames using Media3's frame-drop effect. Quality scales automatic bitrate presets; an explicit bitrate remains authoritative. Main-project export uses the same bitrate policy and frame-rate cap.

MP4 video exports explicitly request H.264/AAC and let Media3 negotiate a supported profile/level, rather than forcing AVC High Level 5.1 even for small outputs or HEVC sources. Codec fallback stays enabled. Hardware may choose a supported fallback size; host tests do not verify that output on a phone. Frame dropping caps video cadence; it does not synthesize frames to turn low-frame-rate input into genuine high-frame-rate footage.

An explicit photo canvas and automatic face reframe keep their own framed aspect when scaled. In particular, a 4:5 photo canvas must not be cropped again to a default 16:9 video setting. Original-canvas photo sessions retain the video aspect setting. 720p photo preparation also uses a smaller decode budget.

These are source-level defects and fixes, not proof of the owner's 0%-stall root cause. The device stall has not been reproduced here. Project export now also records bounded preparation/render/completion/error breadcrumbs without source filenames or user content.

## Native-resource and image reliability

Thumbnail extraction now attempts to release its native MediaMetadataRetriever even when setting a data source, reading metadata or decoding fails. Transition shaders release their owned linked GL program. These close definite leak paths, but neither is a confirmed diagnosis of the owner's process death. The added EGL lifecycle test requires a device and is not counted as executed merely because it compiles.

Photo decoding handles all eight EXIF orientations, including mirrored and transposed photos. Pixel regressions cover transforms and preservation of source bitmap ownership.

## Request-by-request acceptance

| Owner request | Current implementation and remaining acceptance |
| --- | --- |
| Export stops at 0% | Retry ownership, output settings, codec negotiation, decoder disposal and cancellable preparation are implemented. Actual first-export failure still needs the exact new build and failing project on the phone. Do not equate a preparation percentage with a root-cause fix. |
| Native app crash | Retriever/GL/player/recording cleanup and local crash-evidence sharing are implemented. Fatal signal/tombstone/backtrace from the exact build is still required to identify the process death. |
| Cover/Fit/ratios | Fit-first viewport, source-dimension reset, geometry clipping, crop ratios and actual PlayerView resize regressions exist. Fit/Cover is a preview viewport setting, not persisted video export Fit/Fill. Phone visual acceptance is pending. |
| Video, overlay, audio and text layers | Existing layer tools remain, with full-project undo snapshots and additional structural audio/text/layer history fixes in this continuation. This is not a complete replacement of every layer subsystem or a new full multilayer exporter. Test edit/undo/redo/reopen on the phone. |
| Initial timeline loading | Visible-first bounded thumbnail scheduling, indexed content lookups, async project loading and thumbnail-resource cleanup are implemented. Cold-import/first-frame/scrolling timings remain unmeasured on target hardware. |
| Preview sizing and undo/redo/next | Fullscreen preview and Undo/Redo exist, with broader history coverage. Previous/Next keyframe controls exist. A generic project-flow Next destination was not specified and is not represented by a dead button. |
| Image canvas | Original, 1:1, 4:5, 16:9, 9:16 and 3:2; Fit/Fill; backgrounds; persisted edits/shared renderer; EXIF mirrors and aspect-preserving canvas export are implemented. Preview/save/reopen/undo/render comparison is still a phone check. |
| Responsive mobile dashboard | Width/font-aware cards, system insets and asynchronous project loading are implemented. Narrow/large-font host tests exist; screenshot-matched polish and device appearance are not established. |
| Full whole-project composition | Sequential main-track videos, trim/speed/gain/denoise, global LUT/color/FX and canonical music postmix are supported. Photos, visible PiP, AR, GPU-only filters, crop, stickers and transitions still have explicit capability blocks to prevent edit loss. |
| Social page and Google sign-up | Not implemented. No configured production OAuth client/backend/social-service behavior is established; this work does not invent working accounts or credentials. |
| AI scope and safety | Current-project-only/no free-form generation policy is preserved. Protected AI stays locked: validated 100% offline sexual/violent/hateful moderation for text and image/video frames has not been established. Validated categories remain 0/3. No heuristic approval, cloud fallback or bypass was added. Model integrity tests are not moderation accuracy validation. |

## Phone checks before merge

Update-install the signed APK; confirm its build badge. If Android reports a signature conflict, stop rather than uninstalling or clearing data. Reopen the original project, complete an export and play the output, then export again without leaving the export screen. Test Cancel and immediate retry as well.

Compare 720p/30fps low/high/explicit-bitrate outputs and square/portrait framing. Check an explicit 4:5 photo canvas with a contrasting background. Test text/audio/layer edits through Undo/Redo and reopen the project. On a long timeline, compare import delay and scrolling. If the process dies, share Settings → View Crash Log → Share crash evidence, or capture the retained Fatal signal/tombstone/backtrace. Native crashes cannot be diagnosed from a composable-start breadcrumb alone.

The [official Media3 customization guidance](https://developer.android.com/media/media3/transformer/customization) documents codec fallback and encoder settings. Local JVM/pixel tests, Android-test compilation and APK provenance are reported in the PR separately from unexecuted device/GPU acceptance.
