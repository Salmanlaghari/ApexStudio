# Existing-project video tools

## Available controls

Select an existing **main-track VIDEO** in the current project and open **AI tools**. These workflows do not accept prompts, still images, camera input, or image/video-generation requests.

**Enhance, templates + filters · not ML AI** contains fixed local color enhancement, video-template combinations, three pre-made text-motion titles, and Warm Film / Cool Fade / Monochrome filters. These are deterministic editing presets, not learned AI. No artistic ML filter or learned enhancement is advertised as available. Text templates insert fixed words and motion; they are not text-to-video generation. Manual text editing elsewhere in the editor remains ordinary text editing, not an AI prompt.

Color presets persist a per-clip ID and use the same color matrix and `ColorMatrixGlEffect` in decoder preview and export. They do not depend on the legacy approximate Compose color overlay. Templates and color changes are reversible through undo. Unknown choices, images, audio, overlays, invalid trims and stale captured project/clip snapshots are rejected before mutation.

Single-video export includes titles through the existing text-overlay renderer. Sequential multi-video export supports color effects, but its existing text-composition limitation still rejects unsupported titled compositions rather than silently omitting titles. Export an individual titled clip where that restriction applies.

## AI Video Upscale

**AI tools → AI Video Upscale · ESPCN x3** is learned, non-generative luma super-resolution on an existing project video. The small 240,078-byte Apache-2.0 ONNX Model Zoo model is bundled in default builds and SHA-256 verified; no cloud inference or automatic download is used. Source and license notices remain in `assets/models/super_resolution/` and the artifact digest is pinned in `models.lock.json`.

This is a bounded offline pre-render, not realtime ML preview, a generic photo upscaler, 4K restoration, or a claim that missing source detail is recovered accurately. Source dimensions must be even and at most 640×360 (360×640 portrait), the whole source must be at most 30 seconds and 30 fps (900 frames), and output is exactly 3× each edge, up to 1920×1080 or portrait equivalent. Only SDR 8-bit sources with silent video or a single AAC track are supported; an audio track extending beyond video duration is rejected rather than silently dropped or allowed to extend the rendered timeline. Unsupported media, codecs, integrity failures and resource limits produce an explicit error instead of a no-op or a deterministic fallback called AI. Processing has a 30-minute wall-time bound, bounded codec waits and cancellation checks; an in-flight native model call is not forcibly interruptible.

The renderer writes a new video, preserves source presentation timestamps and audio, and only commits if the same project, selection and exact clip snapshot still match. The original source file is never overwritten. The clip's trim and other edits remain in project state; one undo restores the original media URI. Preview and export consume the **same rendered video** after processing. Cancellation, failures and stale requests remove unpublished output. Reopening the panel is required after selection changes.

## Google Play policy, not a custom all-frame gate

The owner's updated scope supersedes the earlier custom requirement for offline sexual/violent/hateful/OCR classification on every frame. [Google Play's AI-Generated Content policy](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en) defines generated content as content created by generative AI models based on user prompts, and requires developer-facing in-app reporting for apps that generate such content. It does **not explicitly require** frame-by-frame offline moderation for these non-generative local video effects.

No generative workflow is introduced. A missing full-frame classifier is no longer the reason these video tools are blocked. `GlobalModerationEnforcement` is retained as a compatibility boundary: raw standalone image/model actions stay denied, while the validated video renderer receives a narrow coroutine-scoped permission for ESPCN frame inference only. There is no global approval flag, no user override, and no permission for style transfer, arbitrary photos, or camera inference.

Other ML backends are not automatically enabled by model-file presence. Artistic style transfer remains unavailable because license/provenance review and full-video integration are incomplete. Learned enhancement and generative templates/filters are not implemented. Other legacy tools remain outside this release's selected tool scope rather than being falsely presented as requiring Google's all-frame classifier. Existing local report journaling is **not** a developer reporting submission and must not be advertised as satisfying a generative-app reporting obligation. See [reporting limitations](ai-content-reporting.md).

These changes are not a guarantee of whole-app Google Play approval: other Play policies, listing accuracy, privacy disclosures, and release review still apply. Earlier moderation audit documents describe the superseded stricter scope, not a current Google Play requirement.

## Verification limits

Host unit/Compose tests cover scope denial, coroutine permission isolation, fixed/no-prompt choices, target provenance, undo, persistence, processing limits and preview/export effect resolution. The offline Python ESPCN smoke executes the real pinned model and checks tiled x3 output geometry; it is not Android codec acceptance or image-quality certification. No physical-device performance, HDR handling quality, encoder compatibility or pixel-perfect full-video acceptance is claimed without device testing.
