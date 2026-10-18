# Offline MediaPipe vision

The app ships Google-hosted MediaPipe Tasks Vision model files in `app/src/main/assets/models/mediapipe/`; inference does not need an app download or a network connection. Exact source URLs, byte sizes, model-card license statements and SHA-256 pins are in [`model-manifest.json`](../app/src/main/assets/models/mediapipe/model-manifest.json). The official model cards say **Apache License 2.0**; the upstream license text is included beside the model assets as `LICENSE`.

| Model asset | Size | Capability |
| --- | ---: | --- |
| `mediapipe-face-landmarker.task` | 3,758,596 bytes | Face detection, 478 face landmarks, blendshapes |
| `mediapipe-pose-landmarker.task` | 5,777,746 bytes | Lite body-pose task, 33 landmarks |
| `mediapipe-hand-landmarker.task` | 7,819,105 bytes | Palm detection and 21 hand landmarks |
| `selfie_segmenter.tflite` | 249,537 bytes | 256×256 person/background confidence mask |

The four binary models total **17,604,984 bytes** (about 16.8 MiB) before APK compression. `model-manifest.json` pins each downloaded byte sequence because the upstream source paths contain `latest`.

## Public Kotlin API

`com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels` is the public entry point:

```kotlin
val modelFile = BundledMediaPipeModels.modelFile(
    context, BundledMediaPipeModels.Model.FACE
) // verified, app-private file; no network access
val faceTask = BundledMediaPipeModels.openFace(context, maxFaces = 3)
val poseTask = BundledMediaPipeModels.openPose(context, maxPoses = 1)
val handTask = BundledMediaPipeModels.openHands(context, maxHands = 2)
val portraitTask = BundledMediaPipeModels.openPortraitEffects(context)
```

`materialize(context, model)` aliases `modelFile`. `modelFile` copies the packaged asset under `noBackupFilesDir/mediapipe/models` and verifies SHA-256 before returning. It repairs a missing or invalid cached copy from the bundled asset and fails closed on hash mismatch. Call model loading/inference off the main thread. Close each returned detector/effect with `use { ... }`; landmarker `detect(bitmap)` calls are synchronous IMAGE-mode inference and leave bitmap ownership with the caller.

### Face privacy and tracking

`MediaPipeLandmarkers.FaceDetector.detect(bitmap)` returns `FaceDetection` objects containing normalized face boxes and actual model mesh points. `FacePrivacy.pixelateFaces(bitmap, detections, blockSize)` returns a distinct mosaic-blurred bitmap using the detected mesh hull. It does not claim full-scene anonymization: missed, occluded, out-of-domain, or very small faces are not blurred. Review results before relying on them for privacy-sensitive publication.

For a video caller, feed each frame's actual detector result into one `FaceTrackSmoother.update(timestampMs, detections)`. IDs are short-lived local associations made by box IoU; they are not identities and are not persistent across process or tracker reset. The smoother does not predict or fabricate detections when no face was found.

`LocalFaceTrackAnalyzer.analyze(context, localUri, sourceStartMs, sourceEndMs)` now provides a full-range, bounded offline analysis backend. It decodes actual frames from local file/content/resource URIs, opens the pinned bundled face model and returns timestamped detections, local track IDs and crop suggestions. Defaults sample every 500 ms up to 180 scheduled samples, spread across the whole requested end-exclusive range when capped; inference images have a maximum dimension of 640 pixels. Custom output aspect ratios include 9:16 and 1:1. Decode/inference failures are not replaced with fabricated faces.

Progress is reported after scheduled timestamps, and coroutine or `AiCancellationToken` cancellation is checked around synchronous Android decode and MediaPipe calls. An already-running native call is not interruptible. Sparse sampling can miss fast motion or brief faces; the decoder may allocate a full-resolution frame before downscaling. This backend does not apply a crop track, encode a video, or enable the protected AI editor switch. Real-time preview/export, policy-approved moderation and device acceptance are still required.

### Background portrait effects

`MediaPipePortraitEffects.process(frame, PortraitEffect.Blur(strength))`, `.Replace(backgroundBitmap)`, or `.Solid(color)` runs the actual bundled selfie-segmentation model and composites its person confidence mask. The blur/replacement is applied only to the background. `MediaPipePortraitEffects.close()` releases the task. The existing video `BackgroundReplaceEngine` continues to do its established decode/segment/composite/re-encode pipeline and now resolves its model through the same bundled SHA-verified store.

### Pose, skeleton and hand primitives

`LandmarkOverlayRenderer.drawPose(...)` renders the conventional MediaPipe 33-point skeleton; `.drawHand(...)` renders the 21 hand landmarks. `GesturePrimitives` exposes extended-finger tests and `OPEN_PALM`, `FIST`, `POINTING`, `PINCH`, or `UNKNOWN` geometric classes over real hand landmarks. These are transparent geometry rules, not a separate learned classifier, and can be inconclusive when a hand is rotated, partially hidden, or landmarks are poor. Use the model's outputs as the basis and do not present these simple rules as validated universal gesture recognition.

## Model limitations and privacy

These models are on-device perception components, not a promise of perfect detection or an identity system. Official model cards describe expected smartphone/selfie or prominently displayed subjects and sensitivity to lighting, motion, occlusion, subject scale and orientation. Segmentation is 256×256 and may miss fingers or fine hair. The pose Lite model is designed for a single prominent person; hand tracking is experimental and can degrade in low light or with gloves/occlusion. No model output should be used for life-critical decisions or surveillance/identity recognition. User media is processed locally by these APIs; this code adds no upload, acquisition service, moderation bypass or heuristic substitute for inference.

## Verification

The manifest SHA-256 values were computed from the exact committed files. Model task APIs use the already declared `com.google.mediapipe:tasks-vision:0.10.35` dependency. Runtime performance and visual quality still require representative device testing; this change does not claim device benchmark or privacy-anonymization certification.
