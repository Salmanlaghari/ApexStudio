# Candy-9 fixed-shape ONNX pack

See [NOTICE.md](NOTICE.md) for exact artifact provenance, hashes, licenses, reviewed tensor contract and limitations. The model is bundled for evaluation; its presence is not moderation approval or an editor feature release.

The fixed ONNX interface is `input1`/`output1`, float32 NCHW RGB `[1,3,224,224]`, using `[0,255]` values. The adapter bilinearly resizes any input bitmap to 224×224 and returns a 224×224 bitmap; aspect ratio is not preserved. Original ONNX Model Zoo preprocessing and postprocessing specify RGB byte-range floats and clipping to `[0,255]`.

To run a host CPU smoke check from the repository root (requires Python `onnxruntime` and `numpy`):

```sh
python3 scripts/smoke-style-transfer-host.py
```

This uses a deterministic synthetic gradient, not personal/user imagery. It does not certify style quality, production performance or safety. `StyleTransferAdapter.stylize` stays guarded by global moderation, which remains fail-closed; no editor preview/export integration is enabled.
