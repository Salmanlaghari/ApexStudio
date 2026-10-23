# Candy-9 ONNX model pack notice

This directory contains the unmodified 6,728,029-byte `candy-9.onnx` artifact from the immutable Hugging Face repository revision [`f24fcf558bd4564360f41cf3b5321975a33f962f`](https://huggingface.co/onnxmodelzoo/candy-9/tree/f24fcf558bd4564360f41cf3b5321975a33f962f). The Hugging Face LFS SHA-256 and the file SHA-256 both equal:

`9d11a3529d1e547da6ae07201d93484dbab2ec0a3614535752c8f40f0fe2968a`

The ONNX Model Zoo source documentation is pinned at [`c5cae0f1942c8d7727372c7b1f6b485aa39e4421`](https://github.com/onnx/models/blob/c5cae0f1942c8d7727372c7b1f6b485aa39e4421/validated/vision/style_transfer/fast_neural_style/README.md); that model-specific README declares SPDX `BSD-3-Clause`, identifies the fast-neural-style method, and states that the original models were trained on COCO 2014 training images. The Hub card metadata labels the repository `Apache-2.0`, while its model-card prose also says `BSD-3-Clause`. Both license texts are included here; this note does not decide which grant governs the weight artifact. Resolve that metadata inconsistency and review training-style-image provenance before any release.

## Reviewed tensor contract

- Input: `input1`, float32 `[1, 3, 224, 224]`, NCHW, RGB, byte-valued floats in `[0, 255]`.
- Output: `output1`, float32 `[1, 3, 224, 224]`, NCHW, RGB, byte-valued floats; clamp to `[0, 255]` before rendering.
- The actual ONNX graph fixes batch, height and width to `1 x 224 x 224`; it is not a dynamic-resolution model. The app adapter resizes to this square tensor, which can distort aspect ratio.
- ONNX Runtime CPU inference was smoke-tested on a deterministic synthetic RGB gradient (not user content), with the above shapes, finite output, and nonconstant output. This establishes runtime compatibility only—not artistic quality, Android-device performance, accuracy, safety or release readiness.

The model bundle is not permission to run it on user content. The app's global moderation gate remains fail-closed for `ONNX_STYLE_TRANSFER`; no UI, preview or export path is enabled by this pack.
