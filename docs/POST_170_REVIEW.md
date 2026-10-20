# Mobile editor follow-up — review before merge

This follow-up addresses the regressions shown on build `f06ada2a` (PR #170). It does not authorize a merge or release. A physical-device test and the owner's explicit approval are required before any merge.

## Scope stays narrow

Processing is limited to existing videos in the current project. No free-form prompt input, standalone still-image AI, live gesture/camera processing, or image/video generation is enabled. Backend inventory is not an editor action. Missing, corrupt, unverified or unsupported models must fail closed; downloads require an explicit user action. Original media, projects and edit history must remain intact.

## Current official Google Play policy

The [AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en) defines generated content in terms of generative models and user prompts. Its [official explanation](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en) gives limited-scope examples, but does **not** give all video editors a blanket exemption. Local speech recognition, segmentation/compositing, sampled analysis and bounded ESPCN enhancement are not permission to enable generative features. Privacy, restricted-content, deceptive-behavior and other applicable policies still require review of the actual shipped app and SDKs. Google's [safeguarding guidance](https://support.google.com/googleplay/android-developer/answer/16353813?hl=en) also places responsibility for generative safeguards on developers.

For apps that generate content using AI, Google requires reporting/flagging to developers **inside the app without exiting it**. The email-draft contact in this follow-up is a user-controlled support route for the current non-generative scope, **not** a claim that an external email draft or a local flag satisfies that generative-AI reporting requirement. Any future generation release would need a genuine in-app developer reporting/moderation path and a fresh policy review. No Google Play approval is guaranteed.

Reports / Suggestions use `laghariprince2@gmail.com`. The app opens an `ACTION_SENDTO` draft containing a reason and project/clip identifiers, with no media attachment and no automatic send. A draft opening is not proof of sending or delivery. If no email app can open, the existing local-only journal is used with an explicit not-sent explanation.

## The 2:59 source is still outside ESPCN's safe limits

The 30-second / 900-frame source cap remains. This change does **not** make the owner's 2:59 clip upscalable. The actual upscale panel now shows the selected source duration and the limit at the top and disables Upscale before processing is queued. The controller independently checks the same duration before creating an output or scheduling work. A shorter timeline trim does not shorten the underlying source: this route processes the whole source to preserve trim/history, so a separately exported source of 30 seconds or less is needed. Frame size, frame rate, SDR, AAC, codec and storage limits still apply. Removing the cap without device performance/thermal validation would not be a safe claim.

## Physical-device acceptance gate

No physical Android phone is attached to the development sandbox. JVM/Compose tests and compilation are not physical-device acceptance. Before approval, test the exact PR-head APK on the owner's phone:

- Verify the editor Build badge matches the intended source commit. Do not uninstall an existing app blindly on a signature conflict; preserve projects and media.
- Open an existing multi-minute video project. The timeline is useful immediately, with no giant unoccupied track area; preview remains reachable. Test small screen, landscape, large font, PiP/audio/text lanes, scrolling, trim, undo/redo and project reopening.
- Open Optional AI models, scroll to every model and its details, and verify Done/close remains reachable with navigation/status insets and large fonts. Check explicit download, cancellation, integrity errors and offline restart.
- Test allowed local-video features with missing models, valid installed models and corrupt assets. Unsupported live gesture/camera controls must remain informational, and failures must identify the relevant backend rather than implying every model failed.
- Open the owner's 2:59 clip in the upscale panel. Its duration-specific unsupported warning must be visible immediately, Upscale disabled, and no processing queued. Verify the 30-second boundary and explain that a shorter timeline trim alone does not bypass the whole-source limit.
- Upscale valid bounded SDR/AAC source fixtures with longer, equal and shorter audio. Inspect actual output stream/sample durations and A/V sync, selected nonzero trim, preview/export agreement and undo. Oversized or otherwise unsupported sources must remain honestly rejected.
- Change editor resolution, enter/leave Export, and verify picker, CTA, settings and actual encoded dimensions/FPS agree. Check migrated settings and unsupported codec configurations; do not interpret preflight as a promise of 8K support.
- Open a report draft: verify recipient, reason and references, no attachment, and no automatic send. Cancel without a false submission confirmation. Test a phone without an email app and confirm the local-only fallback says not sent.

The owner must review results and explicitly approve before merge. This PR must not be auto-merged.
