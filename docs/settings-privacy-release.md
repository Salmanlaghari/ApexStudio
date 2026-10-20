# Settings privacy copy and release checks

## In-app copy scope

The Settings **Policy** dialog summarizes source-verified behavior: projects/settings and working files are stored on-device; users choose media through Android's media picker; exports are user-initiated; music catalog/track requests go to Jamendo; pack catalog/payload requests go to GitHub and pack hosts; optional Camera Kit uses Snap's SDK and camera access. The base app has no account/project-sync, media-upload or crash-report upload implementation identified in this review. Android backup/device-transfer behavior remains subject to OS/device settings. The optional module and any future SDK/build changes require another review.

This summary is not a guarantee that data can never leave a device, an exhaustive legal notice, or a substitute for disclosures required by applicable law. No prompt-based generative-AI service was found in the inspected base app; that observation does not certify a policy classification or cover third-party/future features.

## Implemented video-only workflows

Whisper phrase captions, opt-in English acoustic word alignment, person-segmentation/background compositing, sampled face review and ESPCN upscaling are available through their existing-project-video editor paths, subject to their actual models, supported inputs and runtime readiness. Processor entry checks bind authorization to the matching source URI and action; raw calls, mismatched sources, standalone still-image AI, live-camera AI and generative operations are not unlocked. Background replacement can composite an existing project image behind a project video; it does not generate that image. Sampled face review does not apply edits: face-crop application stays unavailable because composition export rejects face-crop tracks. Artistic style transfer remains unavailable pending provenance/license review and full-video preview/export integration. An unavailable classifier alone is not treated as a Google Play requirement to block these non-generative transformations.

Mask shapes and recipes use the existing preview/export model and apply immediately on selection. Shape selection stamps complete default geometry at the playhead when animation keys exist, retaining the other keys; applying a recipe explicitly replaces its prior animation. Fine-tuning and hand-drawn paths remain optional/manual. No automatic object/face mask tracking is claimed. Theme choices affect dashboard/background accents and app controls; editor surfaces remain dark and timeline track colors intentionally retain their layer identities.

Only public interaction guidance was used as a design reference: [timeline splitting](https://www.capcut.com/resource/split-video-into-parts) and [mask shapes, feathering and optional adjustment](https://www.capcut.com/resource/add-mask-over-a-shape-layer-in-after-effects). No CapCut code, images or proprietary assets are included.

## Before a Google Play release

- Publish a **publicly accessible privacy-policy URL**, link it in Play Console, and keep the policy accessible in-app. The policy and in-app summary must describe actual practices and identify the developer/contact.
- Complete and verify **Data safety** in Play Console against the release build, all included and on-demand SDKs (including Snap Camera Kit), network endpoints, permissions, and any third-party handling. Re-audit when dependencies, manifests, optional modules, or features change; do not infer declarations only from the absence of an analytics library.
- Review the actual Android photo/media access scope and picker behavior for each shipped feature; request only access needed for the user's selected media and verify Play policy compatibility. Do not restore broad media permissions without a documented feature need and policy review.
- Reassess Google Play's generative-AI policies for every shipped AI feature. Current source inspection found local enhancement/recognition of user-selected media and no prompt-based generative endpoint, but do not claim blanket exemption—especially for video enhancement. If the app falls within the generative-AI policy scope, provide an in-app mechanism to report objectionable generated content and ensure reports reach the developer.
- Check the current Play target-API, signing, app integrity, store-listing, privacy, permissions, and testing requirements at release time. This checklist is not a Play Console submission and makes no claim of review or approval.

## Concrete platform findings

The app targets API 36, matching the official August 31, 2026 target requirement for ordinary new apps and updates. This is necessary, not sufficient, for release readiness.

The original PR #170 device APK contained 21 arm64 libraries; GPUImage 2.1.0 supplied `libyuv-decoder.so` with 4 KB `PT_LOAD` alignment while the other 20 had at least 16 KB alignment. The fix rebuilds that decoder from pinned, attributed upstream source rather than modifying ELF headers or hiding the library. GPUImage's Java API, resources and filtering workflows remain in place. APK and AAB checks must include every packaged native library, including the on-demand Camera Kit module, not just the base APK.

A broader RELRO diagnostic also found JNA 5.14.0's `libjnidispatch.so` sharing a non-16-KB RELRO boundary with writable data, despite passing the load-alignment check. JNA is updated to 5.19.1; upstream documents the Android 16 KB call fix in [5.17.0 / issue #1647](https://github.com/java-native-access/jna/issues/1647). This is why a `PT_LOAD`-only scan is insufficient. The updated library's static load/RELRO metadata must be rechecked alongside the decoder; device/runtime testing remains separate.

The [current official Android guidance](https://developer.android.com/guide/practices/page-sizes) requires 16 KB support for API 35+ apps on 64-bit devices and says unsupported app updates cannot release starting **February 1, 2027**. Do not describe the older November 2025 announcement as the current enforcement date or claim that Play has already rejected this app. Console-specific notices and release-time requirements still need checking.

Static ELF load alignment, APK ZIP alignment, AAB `PAGE_ALIGNMENT_16K` configuration and locally generated split checks are separate evidence. None proves correct runtime behavior, Play-generated delivery, production signing, or approval. Before claiming full compatibility, validate the final signed release AAB and delivered splits, then install and exercise GPU filters, bitmap readback, YUV conversion, preview/export and optional Camera Kit on an actual 16 KB device/emulator. Also retain a 4 KB-device regression run. No connected test device was available for the follow-up checks; owner device review and explicit approval remain required before merge. Do not uninstall or reset app data to work around signing differences.

## Official references

- [Google Play privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Google Play generative-AI policy](https://support.google.com/googleplay/android-developer/answer/13985936)
- [Google Play AI-generated content policy FAQ](https://support.google.com/googleplay/android-developer/answer/14094294)
- [Google Play photo and video permissions policy](https://support.google.com/googleplay/android-developer/answer/14115180)
- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Android 16 KB page-size support](https://developer.android.com/guide/practices/page-sizes)
