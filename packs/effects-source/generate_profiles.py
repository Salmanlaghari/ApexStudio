#!/usr/bin/env python3
"""Generate the original ApexStudio on-demand FX profile payload.

The payload carries numeric parameters only; shader source is never downloaded.
The Android app resolves each family through its allowlisted GLSL implementation.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "packs" / "effects" / "effects.json"

# 3 original, tuned variants for each existing procedural shader family.
EFFECTS = [
    ("aurora_prism", "Aurora Prism", "Prism & Light", "PRISM", 1.7, 0.13, 0.42, [0.18, 0.92, 0.78], False, 1.0),
    ("spectrum_glass", "Spectrum Glass", "Prism & Light", "PRISM", 3.1, 0.21, 0.57, [0.28, 0.68, 1.00], False, 1.0),
    ("rose_refraction", "Rose Refraction", "Prism & Light", "PRISM", 4.6, 0.27, 0.69, [1.00, 0.34, 0.55], False, 1.0),

    ("peach_duotone", "Peach Duotone", "Color & Tone", "SPLIT_TONE", 0.24, 0.38, 0.68, [1.00, 0.48, 0.30], False, 1.0),
    ("lagoon_duotone", "Lagoon Duotone", "Color & Tone", "SPLIT_TONE", 0.33, 0.47, 0.77, [0.10, 0.78, 0.82], False, 1.0),
    ("orchid_duotone", "Orchid Duotone", "Color & Tone", "SPLIT_TONE", 0.43, 0.56, 0.86, [0.72, 0.32, 1.00], False, 1.0),

    ("tidal_ripple", "Tidal Ripple", "Motion", "RIPPLE", 2.2, 0.013, 2.8, [0.08, 0.72, 0.92], True, 0.75),
    ("ember_ripple", "Ember Ripple", "Motion", "RIPPLE", 3.8, 0.021, 4.1, [1.00, 0.30, 0.08], True, 1.15),
    ("moonlit_ripple", "Moonlit Ripple", "Motion", "RIPPLE", 5.4, 0.017, 3.4, [0.48, 0.56, 1.00], True, 0.65),

    ("micro_mosaic", "Micro Mosaic", "Pixel & Pattern", "MOSAIC", 5.0, 7.0, 0.025, [0.80, 0.92, 1.00], False, 1.0),
    ("gallery_tiles", "Gallery Tiles", "Pixel & Pattern", "MOSAIC", 9.0, 6.0, 0.065, [1.00, 0.78, 0.46], False, 1.0),
    ("bold_mosaic", "Bold Mosaic", "Pixel & Pattern", "MOSAIC", 14.0, 11.0, 0.12, [1.00, 0.32, 0.42], False, 1.0),

    ("amber_crt", "Amber CRT", "Retro Display", "CRT", 120.0, 0.20, 0.12, [1.00, 0.58, 0.18], True, 0.80),
    ("cyan_crt", "Cyan CRT", "Retro Display", "CRT", 165.0, 0.34, 0.19, [0.12, 0.88, 1.00], True, 1.10),
    ("mono_crt", "Mono CRT", "Retro Display", "CRT", 230.0, 0.48, 0.26, [0.84, 0.90, 1.00], True, 0.90),

    ("honey_halation", "Honey Halation", "Glow", "HALATION", 0.30, 7.0, 0.62, [1.00, 0.58, 0.16], False, 1.0),
    ("berry_halation", "Berry Halation", "Glow", "HALATION", 0.44, 10.0, 0.72, [1.00, 0.18, 0.48], False, 1.0),
    ("ice_halation", "Ice Halation", "Glow", "HALATION", 0.62, 14.0, 0.84, [0.24, 0.72, 1.00], False, 1.0),

    ("pop_ink", "Pop Ink", "Illustration", "INK", 0.18, 0.52, 0.15, [1.00, 0.10, 0.28], False, 1.0),
    ("newsprint_ink", "Newsprint Ink", "Illustration", "INK", 0.28, 0.74, 0.24, [0.10, 0.12, 0.16], False, 1.0),
    ("pastel_ink", "Pastel Ink", "Illustration", "INK", 0.40, 1.02, 0.39, [0.32, 0.88, 0.70], False, 1.0),

    ("quad_mirror", "Quad Mirror", "Mirror & Kaleido", "MIRROR", 4.0, 0.00, 0.72, [0.26, 0.88, 1.00], False, 1.0),
    ("hex_mirror", "Hex Mirror", "Mirror & Kaleido", "MIRROR", 6.0, 0.18, 0.80, [1.00, 0.40, 0.72], False, 1.0),
    ("radial_kaleido", "Radial Kaleido", "Mirror & Kaleido", "MIRROR", 10.0, 0.42, 0.91, [0.56, 0.40, 1.00], False, 1.0),

    ("tangerine_signal", "Tangerine Signal", "Glitch & Signal", "GLITCH", 7.0, 0.006, 0.18, [1.00, 0.36, 0.08], True, 0.90),
    ("cobalt_signal", "Cobalt Signal", "Glitch & Signal", "GLITCH", 13.0, 0.013, 0.29, [0.12, 0.34, 1.00], True, 1.20),
    ("silver_signal", "Silver Signal", "Glitch & Signal", "GLITCH", 21.0, 0.020, 0.40, [0.72, 0.88, 1.00], True, 1.00),

    ("orbit_streak", "Orbit Streak", "Motion Trails", "STREAK", 0.10, 0.36, 0.66, [0.24, 0.76, 1.00], True, 0.80),
    ("solar_streak", "Solar Streak", "Motion Trails", "STREAK", 1.48, 0.62, 0.78, [1.00, 0.66, 0.18], True, 1.20),
    ("twilight_streak", "Twilight Streak", "Motion Trails", "STREAK", 2.42, 0.82, 0.92, [0.52, 0.38, 1.00], True, 0.70),
]


def main() -> None:
    assert len(EFFECTS) == 30
    ids = [effect[0] for effect in EFFECTS]
    assert len(set(ids)) == len(ids)
    payload = {
        "schemaVersion": 1,
        "attribution": "Original ApexStudio procedural FX profiles; shader code remains in the app.",
        "effects": [
            {
                "id": effect_id,
                "name": name,
                "category": category,
                "family": family,
                "a": a,
                "b": b,
                "c": c,
                "tint": tint,
                "animated": animated,
                "defaultSpeed": speed,
            }
            for effect_id, name, category, family, a, b, c, tint, animated, speed in EFFECTS
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    encoded = (json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    OUT.write_bytes(encoded)
    print(f"wrote {OUT} ({len(encoded)} bytes)")
    print(f"sha256 {hashlib.sha256(encoded).hexdigest()}")


if __name__ == "__main__":
    main()
