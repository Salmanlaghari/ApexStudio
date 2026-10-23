# Moderation follow-up: validation scope and remaining blockers

## Current policy

Protected AI remains **locked**. `GlobalModerationEnforcement.readiness.approved` is false, its reviewed moderation artifact catalog is empty, and `requireApproved` has no caller-supplied approval/debug flag. The global policy requires validated coverage of **sexual, violent, and hateful** content. No moderation classifier has completed app-specific validation for any category: **0 validated categories out of 3** (sexual: 0, violent: 0, hateful: 0). These are validation counts, not claims about model performance.

This change adds `PORTRAIT_BACKGROUND_EFFECTS` to the protected action catalog and checks it in both `MediaPipePortraitEffects.open` and `BundledMediaPipeModels.openPortraitEffects`. The bundled entry checks before copying model bytes into app-private storage; the direct-file entry checks before inspecting/reading the file or constructing MediaPipe's native segmenter. Both checks use the global gate and cannot be overridden by a caller-provided debug flag. Since readiness is currently false, portrait segmentation cannot be opened for production inference.

Existing pure effect-composition tests continue to use a test `PortraitSegmenter` seam. Those tests exercise mask validation, bitmap ownership, compositing, serialization, close and cancellation behavior; they do not open the production model or establish that model-driven user-content output is approved. The tests for local face/scene review were not gated by this change: read-only analysis remains distinct from transformed or published output. In particular, local face review remains available, while the applied face-aware crop path retains its separate protected-action check.

## What the regressions establish

- The global enforcement test iterates every `ProtectedAiAction` and asserts that each fails closed while moderation readiness is false.
- New MediaPipe regressions call both the bundled and direct portrait-open entry points. They assert the protected-action error occurs and that the bundled model is not materialized. The direct-open test passes a nonexistent file, ensuring the moderation error takes precedence over model-file validation.
- Existing fake-segmenter tests continue to verify renderer/math and lifecycle behavior without invoking native inference.

These are **policy-ordering and code-path regressions only**. They do not run a moderation classifier, establish sexual/violent/hateful detection quality, validate model accuracy, calibrate a threshold, or demonstrate native MediaPipe segmentation quality. Bundled model hash/integrity checks are not moderation approval.

## Remaining moderation work

The fail-closed classifier boundary still has no approved artifact. Its existing default score cutoff is **0.20 for each category, uncalibrated**; it must not be described as a validated safety threshold. The currently available NSFW/NSFL classifier does not cover hateful content and is not app-validated, so it cannot unlock protected processing. Approval requires a pinned artifact and reproducible, app-specific evaluation for all three categories, with an explicitly justified threshold/policy and a reviewed app update. Until then, no protected AI inference or transformed output is authorized.

## Text and image coverage: no accuracy results to claim

The existing classifier API accepts image bytes only. This follow-up does **not** add a text classifier, ship a moderation model pack, approve an artifact, or unlock AI. Prompts, titles, captions, photos and video frames have not been evaluated with an app-approved classifier. Consequently, all protected production actions continue to deny even clean content; the clean-pass requirement is **not implemented**. No keyword list, synthetic score fixture, asset hash or renderer test is counted as classifier validation.

| Modality | Category | Real evaluation samples | Model pass / fail | Threshold | False-positive / false-negative observations |
|---|---|---:|---|---|---|
| Text | Sexual | 0 | 0 / 0; not run | Not configured | Not measurable |
| Text | Violent | 0 | 0 / 0; not run | Not configured | Not measurable |
| Text | Hateful | 0 | 0 / 0; not run | Not configured | Not measurable |
| Image/frame | Sexual | 0 | 0 / 0; not run | Existing gate default 0.20, uncalibrated | Not measurable |
| Image/frame | Violent | 0 | 0 / 0; not run | Existing gate default 0.20, uncalibrated | Not measurable |
| Image/frame | Hateful | 0 | 0 / 0; not run | Existing gate default 0.20, uncalibrated | Not measurable |

Clean examples and edge-case model evaluations: **0**. Policy-ordering unit tests are reported separately in the PR. They establish rejection with an empty reviewed catalog, not sensitivity, specificity, clean-content acceptance or end-to-end moderation. Bounded moderation timeout behavior and off-main-thread inference have **not** been implemented or validated. The current immediate deny path does not run a classifier at all. This is not an acceptable basis for claiming the requested AI unlock is complete.

## Candidate models reviewed, not approved

- [Content Safety Guard small ONNX](https://huggingface.co/onnx-community/content-safety-guard-small-ONNX) is a concrete text candidate: its card describes sexual, violence and hate labels, Apache-2.0 licensing and an approximately 268 MB int8 artifact. Android tokenizer integration, the publisher's decision/threshold contract, phone latency/memory and app-specific evaluation would still be needed. It is not bundled or approved here.
- [Image Safety Classifier XS](https://huggingface.co/OwenElliott/image-safety-classifier-xs) exposes NSFL/NSFW/SFW, not hateful-image detection. NSFL is a narrow graphic-content signal, not proof of comprehensive violent-content coverage. It cannot meet the all-category policy by itself.
- [ShieldGemma 2's model card](https://ai.google.dev/gemma/docs/shieldgemma/model_card_2) describes a 4B image model with sexual, dangerous and violence/gore policies, not a hate category. Size, redistribution terms and Android deployment also need separate review.
- [Llama Guard 4 12B](https://huggingface.co/meta-llama/Llama-Guard-4-12B) offers text/image hazard categories that overlap the requirement, but its 12B footprint and license are not evidence of a practical supported-phone deployment.

The reviewed sources did not establish a ready-to-ship, compact, freely redistributable Android combination with validated all-category text **and** visual coverage. That is a finding from this investigation, not proof that no such model can ever be built. OCR, text rules and nudity scoring must not be relabeled as contextual image-hate moderation.

Before approval, pin licensed model/tokenizer/config bytes, implement the full text and image contracts without network fallback, and run reproducible held-out evaluation covering true positives in every category, clean cases and benign edge cases (including medical imagery, art, identity mentions, symbols and memes). Record calibrated cutoffs, per-category counts and errors, and phone timeout/offline behavior. A finite sample can document observed errors; it cannot prove that false negatives are impossible. Only then should the production approval catalog and protected tool wiring be considered for an unlock.
