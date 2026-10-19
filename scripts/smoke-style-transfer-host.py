#!/usr/bin/env python3
"""Run a CPU ONNX Runtime smoke test for the pinned Candy-9 model on synthetic pixels only."""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys

import numpy as np
import onnxruntime as ort

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/debug/assets/models/style_transfer/candy-9.onnx"
EXPECTED_SHA256 = "9d11a3529d1e547da6ae07201d93484dbab2ec0a3614535752c8f40f0fe2968a"


def main() -> int:
    actual = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"Candy-9 SHA-256 mismatch: {actual}")

    options = ort.SessionOptions()
    options.log_severity_level = 3
    session = ort.InferenceSession(str(MODEL), sess_options=options, providers=["CPUExecutionProvider"])
    inputs, outputs = session.get_inputs(), session.get_outputs()
    if len(inputs) != 1 or (inputs[0].name, inputs[0].shape, inputs[0].type) != (
        "input1", [1, 3, 224, 224], "tensor(float)"
    ):
        raise SystemExit(f"Unexpected input tensor contract: {[(x.name, x.shape, x.type) for x in inputs]}")
    if len(outputs) != 1 or (outputs[0].name, outputs[0].shape, outputs[0].type) != (
        "output1", [1, 3, 224, 224], "tensor(float)"
    ):
        raise SystemExit(f"Unexpected output tensor contract: {[(x.name, x.shape, x.type) for x in outputs]}")

    # Deterministic RGB gradients in the upstream-documented [0,255] input range.
    pixels = np.zeros((1, 3, 224, 224), dtype=np.float32)
    pixels[0, 0] = np.linspace(0, 255, 224, dtype=np.float32)[None, :]
    pixels[0, 1] = np.linspace(0, 255, 224, dtype=np.float32)[:, None]
    pixels[0, 2] = 91.0
    result = session.run(["output1"], {"input1": pixels})[0]
    if result.shape != (1, 3, 224, 224) or result.dtype != np.float32:
        raise SystemExit(f"Unexpected inference output: shape={result.shape}, dtype={result.dtype}")
    if not np.isfinite(result).all() or float(result.std()) <= 1.0:
        raise SystemExit("Smoke output is non-finite or lacks a nonconstant response")
    print(
        "Candy-9 host CPU smoke passed: synthetic 1x3x224x224 RGB [0,255] -> "
        f"{tuple(result.shape)} float32; finite={bool(np.isfinite(result).all())}; "
        f"std={float(result.std()):.3f}; sha256={actual}"
    )
    print("This runtime smoke does not certify model quality, safety, device performance, or release readiness.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
