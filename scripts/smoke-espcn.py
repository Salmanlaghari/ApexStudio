#!/usr/bin/env python3
"""Offline CPU inference smoke for the pinned ONNX Model Zoo ESPCN x3 artifact.

Requires numpy, Pillow and onnxruntime installed in the host Python environment.
This verifies real model execution and output geometry, not image quality or device accuracy.
"""
from pathlib import Path
import hashlib
import sys

import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "app/src/main/assets/models/super_resolution/super-resolution-10.onnx"
FIXTURE = ROOT / "app/src/androidTest/assets/super_resolution/espcn_fixture.png"
EXPECTED_SHA256 = "85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e"
TILE = 224
STEP = 192
SCALE = 3


def starts(length: int) -> list[int]:
    if length <= TILE:
        return [0]
    out = []
    pos = 0
    while pos + TILE < length:
        out.append(pos)
        pos += STEP
    last = length - TILE
    if out[-1] != last:
        out.append(last)
    return out


def main() -> None:
    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"model SHA-256 mismatch: {digest}")
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    inputs, outputs = session.get_inputs(), session.get_outputs()
    assert len(inputs) == len(outputs) == 1
    assert (inputs[0].name, inputs[0].type, inputs[0].shape) == ("input", "tensor(float)", ["batch_size", 1, 224, 224])
    assert (outputs[0].name, outputs[0].type, outputs[0].shape) == ("output", "tensor(float)", ["batch_size", 1, 672, 672])

    image = Image.open(FIXTURE).convert("RGB")
    width, height = image.size
    ycc = image.convert("YCbCr")
    y_plane, cb_plane, cr_plane = ycc.split()
    y = np.asarray(y_plane, dtype=np.float32) / 255.0
    y_out = np.zeros((height * SCALE, width * SCALE), dtype=np.float32)
    xs, ys = starts(width), starts(height)
    tile_count = 0
    for yi, top in enumerate(ys):
        for xi, left in enumerate(xs):
            tile_rows = np.minimum(top + np.arange(TILE), height - 1)
            tile_cols = np.minimum(left + np.arange(TILE), width - 1)
            patch = y[np.ix_(tile_rows, tile_cols)]
            predicted = session.run(["output"], {"input": patch[None, None]})[0][0, 0]
            assert predicted.shape == (TILE * SCALE, TILE * SCALE)
            region_left = 0 if xi == 0 else (xs[xi - 1] + TILE + left) // 2
            region_right = width if xi == len(xs) - 1 else min(width, (left + TILE + xs[xi + 1]) // 2)
            region_top = 0 if yi == 0 else (ys[yi - 1] + TILE + top) // 2
            region_bottom = height if yi == len(ys) - 1 else min(height, (top + TILE + ys[yi + 1]) // 2)
            dst_x0, dst_x1 = region_left * SCALE, region_right * SCALE
            dst_y0, dst_y1 = region_top * SCALE, region_bottom * SCALE
            src_x0, src_y0 = (region_left - left) * SCALE, (region_top - top) * SCALE
            y_out[dst_y0:dst_y1, dst_x0:dst_x1] = predicted[
                src_y0 : src_y0 + dst_y1 - dst_y0,
                src_x0 : src_x0 + dst_x1 - dst_x0,
            ]
            tile_count += 1

    out_size = (width * SCALE, height * SCALE)
    out_y = Image.fromarray(np.clip(np.rint(y_out * 255.0), 0, 255).astype(np.uint8))
    cb_scaled = cb_plane.resize(out_size, Image.Resampling.BICUBIC)
    cr_scaled = cr_plane.resize(out_size, Image.Resampling.BICUBIC)
    result = Image.merge("YCbCr", (out_y, cb_scaled, cr_scaled)).convert("RGB")
    rgb = np.asarray(result)
    assert result.size == out_size
    assert rgb.dtype == np.uint8 and np.isfinite(rgb).all()
    assert int(rgb.std()) > 8 and int(rgb.max()) - int(rgb.min()) > 64
    print(f"PASS host ESPCN CPU inference: {FIXTURE.name} {width}x{height} -> {result.width}x{result.height}, tiles={tile_count}, sha256={digest}")
    print("Note: runtime smoke only; not image-quality evaluation, Android device accuracy certification, or safety approval.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise
