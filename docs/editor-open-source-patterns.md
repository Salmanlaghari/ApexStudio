# Public engineering references behind this follow-up

These sources informed architecture and test ideas only. This follow-up did not copy their code, shaders, LUTs, artwork or presets, and did not import a dependency from them. The new filter recipes and VFX shaders are authored in this repository.

## One effect model for preview and export

The official [Media3 composition guide](https://developer.android.com/media/media3/transformer/composition) describes per-item effects and sequential edited items. [CompositionPlayer](https://developer.android.com/media/media3/transformer/compositionplayer) can preview the same composition passed to Transformer, but its documented experimental status matters: it is a useful future path, not proof that this app already has a complete composition preview. Media3's [primary license](https://github.com/androidx/media/blob/release/LICENSE) is Apache-2.0; Android documentation prose is CC BY 4.0 and its code samples Apache-2.0.

Applied idea: keep range/strength/settings in immutable workspace data, construct preview/export effects through the same `WorkspaceEffectGlEffect.createEffects` factory, and test source-clock conversion explicitly. The Color source-frame comparison is not mislabeled as a full composed-timeline comparison.

## Paused frames need their own test

[Media3 issue #1393](https://github.com/androidx/media/issues/1393) discusses delayed runtime effect replacement. [Issue #2363](https://github.com/androidx/media/issues/2363) and [follow-up #2940](https://github.com/androidx/media/issues/2940) discuss paused redraw, mutable parameters and frame replay. These are version-specific developer discussions, not proof of this app's bug or a universal fix.

Applied idea: separate model invalidation from actual Player effect binding, then request a same-source-position frame refresh when a replacement chain is installed while paused and ready. Device playback and paused-frame behavior still need testing on the supplied APK.

## Keep both range edges explicit

The [Material Slider guide](https://github.com/material-components/material-components-android/blob/master/docs/components/Slider.md) documents two-thumb range selection; its [primary license](https://github.com/material-components/material-components-android/blob/master/LICENSE) is Apache-2.0. It is a generic control, not a ready-made video timeline.

Applied idea: expose start, end and actual duration separately from the playhead, clamp them to the real project duration, and map logical text spans into clip-local render windows using the export timeline math. The original editor layout is retained.

## Older preview/export examples

[GPUVideo-android](https://github.com/MasayukiSuda/GPUVideo-android) demonstrates filter preview and MP4 export through separate paths. Its [primary license](https://github.com/MasayukiSuda/GPUVideo-android/blob/master/LICENSE) is MIT. It is an older, non-Media3 project, so its architecture is comparative evidence rather than an imported implementation.

Applied idea: verify pixel output and explicit effect/time settings rather than assuming that matching UI labels prove preview/export parity. Host GLES tests are recorded separately and are not Android-device certification.
