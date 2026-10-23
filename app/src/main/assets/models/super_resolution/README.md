# ONNX Model Zoo ESPCN x3 (luma-only)

This directory bundles `super-resolution-10.onnx`, an unchanged pre-trained ONNX artifact distributed by the ONNX Model Zoo account. It is a learned sub-pixel CNN based on Shi et al. (2016), **not** RealESRGAN and not an RGB-input network.

## Artifact identity

- **Model id:** `onnxmodelzoo-super-resolution-10`
- **Upstream repository:** [onnxmodelzoo/super-resolution-10](https://huggingface.co/onnxmodelzoo/super-resolution-10)
- **Pinned repository revision:** `395472c4b6b92f788357237983d83fd913cfe7e3` (model artifact move from legacy models)
- **Exact download URL:** <https://huggingface.co/onnxmodelzoo/super-resolution-10/resolve/395472c4b6b92f788357237983d83fd913cfe7e3/super-resolution-10.onnx?download=true>
- **Size:** 240,078 bytes
- **SHA-256:** `85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e`
- **License:** Apache-2.0, as identified in the Hugging Face model card and the ONNX Model Zoo source README SPDX header. License text is in `LICENSE`.
- **Training provenance:** ONNX Model Zoo card says BSD300 crops from its 200 training images; it points to the PyTorch examples super-resolution training code.
- **Architecture provenance:** [Shi et al., “Real-Time Single Image and Video Super-Resolution Using an Efficient Sub-Pixel Convolutional Neural Network”](https://arxiv.org/abs/1609.05158).

## Verified tensor contract

Inspected and successfully opened with ONNX Runtime 1.31.0 on the host:

- Input name `input`, `tensor(float)`, NCHW `[batch, 1, 224, 224]`.
- Output name `output`, `tensor(float)`, NCHW `[batch, 1, 672, 672]`.
- The channel is luminance Y only; scale is **x3**. This does not mean x3 is a 720p-to-1080p exact scale.
- The ONNX Model Zoo inference instructions convert RGB to YCbCr, normalize Y to [0,1], infer Y, bicubic-resize Cb/Cr, and convert back to RGB.

The Android adapter packages fixed-size 224x224 luminance tiles with overlap-aware stitching and edge replication. It preserves the input aspect ratio and returns exactly 3x width and height; callers must accept those dimensions or do a separate intentional crop/resize. No alternate model, fabricated output, network download, or fallback is used.

The public user-content `upscale` API is fail-closed behind global moderation. Instrumentation uses an internal `inferForModelValidation` seam, not production authorization. Output is limited to 4,194,304 pixels; 4K processing is not implemented, and alpha is reconstructed as opaque rather than preserved. The fixed model is small, but this adapter is not a completed editor/export integration.

## Evidence boundaries

The artifact hash and tensor contract were checked, and a CPU host inference smoke is provided in `scripts/smoke-espcn.py`. An Android instrumentation smoke is provided under `app/src/androidTest`; in this task it is compiled, not executed on a device. These checks do **not** constitute image-quality evaluation, device accuracy certification, application image-model review, moderation approval, or global safety approval. The app-level image-model review catalog and all feature switches remain unchanged/off.
