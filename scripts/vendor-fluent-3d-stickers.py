#!/usr/bin/env python3
"""Vendor the curated, pinned Microsoft Fluent Emoji 3D PNGs, unchanged, under MIT.
Developer-only network fetch; the Android picker and exports use bundled files offline.
"""
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path
from urllib.parse import quote
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/assets/stickers"
CATALOG = ROOT / "scripts/fluent-3d-catalog.json"


def main():
    from PIL import Image
    catalog = json.loads(CATALOG.read_text())
    revision = catalog["commit"]
    raw_base = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/" + revision + "/"
    destination = BASE / "fluent3d"
    destination.mkdir(parents=True, exist_ok=True)

    def fetch(entry):
        url = raw_base + quote(entry["sourcePath"], safe="/")
        with urlopen(url, timeout=60) as response:
            data = response.read()
        image = Image.open(io.BytesIO(data))
        image.load()
        assert image.format == "PNG" and image.mode == "RGBA", entry["name"]
        assert image.width <= 1024 and image.height <= 1024
        assert image.getchannel("A").getextrema()[0] == 0, "True transparency required"
        assert image.getchannel("A").getextrema()[1] > 0, "Sticker cannot be empty"
        path = BASE / entry["file"]
        temporary = path.with_suffix(".tmp")
        temporary.write_bytes(data)
        temporary.replace(path)
        return {"id": entry["id"], "file": entry["file"], "url": url,
                "sha256": hashlib.sha256(data).hexdigest(), "sizeBytes": len(data),
                "width": image.width, "height": image.height, "alpha": True}

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        records = list(pool.map(fetch, catalog["stickers"]))
    with urlopen(raw_base + "LICENSE", timeout=60) as response:
        license_data = response.read()
    assert b"MIT License" in license_data and b"Microsoft Corporation" in license_data
    (destination / "LICENSE.txt").write_bytes(license_data)
    lock = {"repository": catalog["repository"], "commit": revision,
            "license": "MIT", "licenseSha256": hashlib.sha256(license_data).hexdigest(),
            "modified": False, "assets": records}
    (destination / "SOURCES.json").write_text(json.dumps(lock, indent=2) + "\n")

    manifest_path = BASE / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    manifest["stickers"] = [e for e in manifest["stickers"] if not e["id"].startswith("fluent3d-")]
    manifest["stickers"] += [{k: v for k, v in e.items() if k != "sourcePath"} for e in catalog["stickers"]]
    manifest["pack"] = "Twemoji + Microsoft Fluent Emoji 3D"
    manifest["license"] = "CC-BY 4.0 (Twemoji); MIT (Microsoft Fluent Emoji 3D)"
    old_attribution = "Emoji artwork from Twemoji (https://twemoji.twitter.com), Copyright 2020 Twitter, Inc and other contributors, licensed under CC-BY 4.0 (https://creativecommons.org/licenses/by/4.0/)"
    manifest["attribution"] = old_attribution + "; 3D artwork: Microsoft Fluent Emoji, Copyright Microsoft Corporation, MIT (bundled fluent3d/LICENSE.txt)."
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Vendored {len(records)} transparent unchanged 3D PNGs, {sum(r['sizeBytes'] for r in records)} bytes; upstream {revision}")


if __name__ == "__main__":
    main()
