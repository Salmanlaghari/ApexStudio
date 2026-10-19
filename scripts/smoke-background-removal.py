#!/usr/bin/env python3
"""Offline host CPU smoke for the pinned MODNet photographic ONNX artifact.

Requires numpy, Pillow and onnxruntime. Uses a deterministic generated RGB test pattern,
executes actual ONNX Runtime inference, and verifies the declared dynamic matte contract.
This is a runtime/geometry smoke, not a segmentation-quality or Android-device assessment.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys

import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/assets/models/background_removal/modnet_photographic.onnx"
EXPECTED_SHA256 = "5069a5e306b9f5e9f4f2b0360264c9f8ea13b257c7c39943c7cf6a2ec3a102ae"
EXPECTED_BYTES = 25_969_398
ALIGN = 32


def input_dimensions(width: int, height: int) -> tuple[int, int]:
    longer, shorter = max(width, height), min(width, height)
    scale = 512 / shorter if longer < 512 or shorter > 512 else 1.0
    out_w, out_h = max(1, int(width * scale)), max(1, int(height * scale))
    bound = min(1.0, 2048 / max(out_w, out_h), (1_048_576 / (out_w * out_h)) ** 0.5)
    out_w = int(out_w * bound // ALIGN) * ALIGN
    out_h = int(out_h * bound // ALIGN) * ALIGN
    if min(out_w, out_h) < ALIGN:
        raise ValueError("aspect ratio is too extreme for bounded MODNet input")
    return out_w, out_h


def make_fixture(width: int = 640, height: int = 360) -> Image.Image:
    """Create repeatable RGB pixels; inference remains the pinned learned ONNX graph."""
    yy, xx = np.mgrid[0:height, 0:width]
    rgb = np.empty((height, width, 3), dtype=np.uint8)
    rgb[..., 0] = (xx * 3 + yy) % 256
    rgb[..., 1] = (yy * 2 + 40) % 256
    rgb[..., 2] = 180
    ellipse = ((xx - width // 2) / 150) ** 2 + ((yy - height // 2) / 100) ** 2 < 1
    rgb[ellipse] = (220, 110, 45)
    return Image.fromarray(rgb, "RGB")


def main() -> None:
    if MODEL.stat().st_size != EXPECTED_BYTES:
        raise SystemExit(f"model byte-size mismatch: {MODEL.stat().st_size}")
    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"model SHA-256 mismatch: {digest}")

    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    inputs, outputs = session.get_inputs(), session.get_outputs()
    if len(inputs) != 1 or len(outputs) != 1:
        raise AssertionError(f"expected one input/output; got {len(inputs)}/{len(outputs)}")
    if (inputs[0].name, inputs[0].type, inputs[0].shape) != (
        "input", "tensor(float)", ["batch_size", 3, "height", "width"]
    ):
        raise AssertionError(f"unexpected input contract: {inputs[0].name}, {inputs[0].type}, {inputs[0].shape}")
    if (outputs[0].name, outputs[0].type, outputs[0].shape) != (
        "output", "tensor(float)", ["batch_size", 1, "height", "width"]
    ):
        raise AssertionError(f"unexpected output contract: {outputs[0].name}, {outputs[0].type}, {outputs[0].shape}")

    fixture = make_fixture()
    original_width, original_height = fixture.size
    width, height = input_dimensions(original_width, original_height)
    resized = fixture.resize((width, height), Image.Resampling.BILINEAR)
    rgb = np.asarray(resized, dtype=np.float32) / 255.0
    nchw = ((rgb - 0.5) / 0.5).transpose(2, 0, 1)[None].astype(np.float32)
    matte = session.run(["output"], {"input": nchw})[0]
    if matte.shape != (1, 1, height, width):
        raise AssertionError(f"unexpected runtime output shape: {matte.shape}")
    alpha = matte[0, 0]
    if not np.isfinite(alpha).all():
        raise AssertionError("matte contains NaN or infinity")
    alpha = np.clip(alpha, 0.0, 1.0)
    restored = Image.fromarray(np.rint(alpha * 255).astype(np.uint8), "L").resize(
        (original_width, original_height), Image.Resampling.BILINEAR
    )
    restored_array = np.asarray(restored)
    if restored.size != fixture.size or restored_array.std() <= 0:
        raise AssertionError("actual model inference did not produce a valid matte image")
    print(
        "PASS host MODNet CPU inference: "
        f"{fixture.width}x{fixture.height} -> tensor {width}x{height} -> matte {restored.width}x{restored.height}; "
        f"runtime={ort.__version__}; model_bytes={EXPECTED_BYTES}; sha256={digest}"
    )
    print("Note: actual learned graph executed; generated fixture only; no segmentation-quality or Android-device certification.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise
