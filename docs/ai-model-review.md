# Offline AI model review: current support and validation boundary

## Runtime behavior today

The ONNX image pipeline is a real local ONNX Runtime CPU inference path: it decodes RGB pixels, builds explicit NCHW float tensors, checks ONNX tensor type and shape, runs inference, and refuses missing or incompatible model files. It now also requires a task-matched reviewed artifact record and compares SHA-256 of installed bytes before ONNX readiness or inference. The record must name the source, license, version and intended task, and its exact digest must be present in a code-reviewed allowlist. That allowlist is currently empty; caller-supplied metadata alone cannot mark a model ready. Shape compatibility and a filename are not treated as proof of model identity.

No background-removal, super-resolution or style-transfer ONNX weights are bundled or configured. Therefore those adapters are not ready in the product. No download or network fallback occurs. A model file that merely has the expected dimensions will fail the digest/manifest gate unless its exact digest has been reviewed and supplied.

## Candidate model review

- **Background removal:** BRIA RMBG-1.4 is documented as an IS-Net saliency model and specifies 1024×1024 RGB preprocessing, but its own card says the weights are available under a non-commercial license; commercial use requires a separate agreement. It is not a redistributable default for this app. [BRIA model card](https://huggingface.co/briaai/RMBG-1.4)
- **Background removal:** withoutBG Open Weights ONNX has a technically detailed pipeline and explicit input/output specs, but its published routed bundle is about 1.5 GB and includes DINOv3 and other third-party terms. That is unsuitable for this modest offline feature pack and needs component-license review. [withoutBG model card](https://huggingface.co/withoutbg/withoutbg-openweights-onnx)
- **Upscaling:** Qualcomm's Real-ESRGAN-x4plus page reports a 63.9 MB float model, 128×128 input and BSD-3-Clause model license. The upstream Real-ESRGAN repository documents weights/inference, but the referenced official artifact is not an Android-ready ONNX pack with a validated input/output contract in this repository. The generic adapter is not enough to claim this exact model works here. [Qualcomm model page](https://aihub.qualcomm.com/iot/models/real_esrgan_x4plus) [Upstream repository](https://github.com/xinntao/Real-ESRGAN)
- **Style transfer:** no specific small, openly redistributable ONNX artifact with verified provenance, Android-compatible graph, exact preprocessing and output contract was established for this change. No style model is bundled or claimed operational.

## Moderation adapter and limitations

There is an offline ONNX adapter for `OwenElliott/image-safety-classifier-xs`, pinned to the published ONNX SHA-256 `8c28c49d9075f3ad15ebdc2961f02d5b3f99be944815b848b49c9f0e6f3fb689`. The model card declares MIT, 3.5M parameters, 224×224 RGB input with float pixel values in [0,255], baked-in normalization/softmax, and output order `[NSFL, NSFW, SFW]`. The ONNX file is about 13.1 MB per the repository file metadata. The adapter validates the actual runtime input/output tensors and normalized three-class result, then exposes only NSFL as a narrow gore/violence signal and NSFW as a sexual-content signal. [Model card and ONNX usage](https://huggingface.co/OwenElliott/image-safety-classifier-xs) [File listing and digest](https://huggingface.co/api/models/OwenElliott/image-safety-classifier-xs/tree/main?recursive=true)

This is **not validated moderation for this app**. The model card describes a publisher evaluation on a proprietary dataset; it notes NSFL is underrepresented. That does not establish an application-specific false-negative rate, calibration, demographic robustness or acceptable thresholds. No model binary is bundled. Most importantly, the model has no hateful-content output. The adapter returns only sexual and violent scores. It cannot claim hateful semantics from pixels or from an NSFW/NSFL label.

The protected-action gate now rejects incomplete category coverage, malformed metadata, and any classifier artifact whose SHA-256 is not present in an explicit reviewed-approval catalog. That catalog is intentionally empty: there is currently no validated all-category model. Thus even low or all-zero arbitrary scores and plausible model filenames cannot authorize protected operations. Sexual/violence signals from the candidate adapter are informational only until a separate app-specific validation process and policy approval exist.

A future model approval must pin the exact model bytes; document label-to-policy semantics and preprocessing; evaluate an independently curated representative set with per-category recall/false-negative analysis and calibrated thresholds; and specifically validate hateful content with sufficient text/OCR, context and policy evidence. Image-only classification cannot infer textual or contextual hate reliably. Research on hate detection documents definitional ambiguity, annotator disagreement and the importance of surrounding context. [MacAvaney et al., 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6701757/)

## References

- [BRIA RMBG-1.4 model card](https://huggingface.co/briaai/RMBG-1.4)
- [withoutBG Open Weights ONNX model card](https://huggingface.co/withoutbg/withoutbg-openweights-onnx)
- [Qualcomm Real-ESRGAN-x4plus model page](https://aihub.qualcomm.com/iot/models/real_esrgan_x4plus)
- [Real-ESRGAN upstream repository](https://github.com/xinntao/Real-ESRGAN)
- [OwenElliott safety classifier XS model card](https://huggingface.co/OwenElliott/image-safety-classifier-xs)
- [ONNX repository file metadata](https://huggingface.co/api/models/OwenElliott/image-safety-classifier-xs/tree/main?recursive=true)
- [MacAvaney et al. hate speech detection review](https://pmc.ncbi.nlm.nih.gov/articles/PMC6701757/)

## English acoustic word timing follow-up

The repository now bundles a pinned Apache-2.0 wav2vec2-base-960h quantized ONNX model and its vocabulary/preprocessor configuration. See the [asset notice](../app/src/main/assets/models/alignment/NOTICE.md) for exact provenance, hash and preprocessing. The model binary adds 95,212,816 bytes. The complete model inventory now has 11 entries totaling 216,430,433 bytes before APK compression, including token/config assets; private materialized copies add storage as used.

Text → Captions offers **Phrase timing** (default, multilingual Whisper), **Word by word**, and opt-in **Full-phrase karaoke** (both English-only measured-timing experiments). It decodes real audio locally, normalizes bounded per-phrase windows, runs actual CTC acoustic inference and Viterbi forced alignment, then maps measured word intervals through the same speed-aware editable text-overlay preview/export path. Karaoke preserves the original Whisper phrase and highlights only the acoustically measured word. Real gaps retain the unhighlighted phrase instead of flickering or inventing word times. Phrase-to-word mapping is validated and ambiguous groups fail closed. It never divides phrase duration into equal word slots. Generated-caption preview uses clip-output time (including later V1 clips and explicit overlay offsets), independently of project-level sticker timing. Changing language or timing mode invalidates an in-flight request; stale project/source/trim/caption completions cannot replace current text. The Generate action no longer resets the selected language before transcribing.

Alignment rejects non-English token vocabularies/digits, malformed or overlapping cue intervals, network URI schemes, missing/corrupt weights, invalid tensors and low-confidence/no-path results without replacing existing captions. Each Whisper phrase must be at most 14.4 seconds so 300 ms of context on either side fits the 15-second model window. Whole-audio decoding remains bounded by the existing 30-minute extractor and is not constant-memory streaming. A file/content URI may still be backed by a user-selected content provider; no direct HTTP fallback, model download or media upload is added here.

Host CPU inference on a licensed natural LibriSpeech fixture and recorded-real-emission JVM regressions are reproducible with `scripts/verify-english-aligner.py`. Recognition errors on that fixture are printed and retained in metadata, not hidden. The 0.08 confidence floors are engineering rejection heuristics, not app-calibrated accuracy thresholds. A separate real Android inference/codec smoke test is supplied; compiling it is not evidence it ran. Android latency/RAM, noisy speech/accent accuracy, pixel-identical device preview/export and multilingual word alignment remain unvalidated.

**This does not complete validated sexual/violent/hateful moderation.** Existing captions/background processing remain outside the protected-switch approval contract, just as before; this follow-up must not be described as globally moderated AI. The protected word-timing AI switch remains OFF and points to the limited experimental Text workflow. Learned TFLite tools, reviewed ONNX artistic/upscaling packs, live gestures and full frame-based AI preview/export remain release blockers.

## Geometry and inference reliability

Overlay source crop now contributes to the displayed/exported aspect ratio, so cropped images and PiP video are not stretched into their old bounds. Malformed non-finite crop rectangles fall back to a valid full frame. Signal-analysis results are invalidated when the project/source/trim/speed changes, including before review and preview, not only at apply.

Face-track review offers horizontal **9:16**, **1:1** and **16:9** targets using the same real sampled detections. Changing the target recalculates review geometry and clears review marks without mutating clips/history. These remain sampled suggestions, not crop-track application or video export.

Portrait processing serializes inference/close, validates masks and preserves caller-owned bitmaps; native work cannot be interrupted mid-call. ONNX image and TFLite tensor allocations are checked and bounded. The generic ONNX image adapter currently caps each input/output image at 4,194,304 pixels and does not provide a tiled 4K pipeline. None of these memory/lifecycle checks establish model accuracy or moderation approval.
