"""Offline integrity checks; no runtime downloads or AI-generated assets."""
import hashlib
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/assets/stickers"


class FluentStickerIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lock = json.loads((BASE / "fluent3d/SOURCES.json").read_text())
        cls.catalog = json.loads((ROOT / "scripts/fluent-3d-catalog.json").read_text())
        cls.manifest = json.loads((BASE / "manifest.json").read_text())

    def test_exact_pinned_source_and_mit_license_are_bundled(self):
        self.assertEqual("MIT", self.lock["license"])
        self.assertEqual(self.catalog["commit"], self.lock["commit"])
        self.assertEqual(40, len(self.lock["commit"]))
        self.assertFalse(self.lock["modified"])
        license_bytes = (BASE / "fluent3d/LICENSE.txt").read_bytes()
        self.assertIn(b"Copyright (c) Microsoft Corporation", license_bytes)
        self.assertEqual(self.lock["licenseSha256"], hashlib.sha256(license_bytes).hexdigest())
        self.assertIn("Microsoft", self.manifest["attribution"])
        self.assertIn("CC-BY 4.0", self.manifest["attribution"])

    def test_all_64_transparent_pngs_are_packaged_and_match_source_hashes(self):
        assets = self.lock["assets"]
        self.assertEqual(64, len(assets))
        self.assertEqual(64, len({e["id"] for e in assets}))
        self.assertEqual(64, len({e["file"] for e in assets}))
        for entry in assets:
            with self.subTest(sticker=entry["id"]):
                path = BASE / entry["file"]
                self.assertTrue(path.resolve().is_relative_to((BASE / "fluent3d").resolve()))
                data = path.read_bytes()
                self.assertEqual(entry["sizeBytes"], len(data))
                self.assertEqual(entry["sha256"], hashlib.sha256(data).hexdigest())
                self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8])
                self.assertEqual(b"IHDR", data[12:16])
                width, height, depth, color = struct.unpack(">IIBB", data[16:26])
                self.assertEqual((entry["width"], entry["height"]), (width, height))
                self.assertEqual(8, depth)
                self.assertEqual(6, color, "Original PNG must retain RGBA")
                self.assertTrue(entry["alpha"])
                self.assertTrue(0 < width <= 1024 and 0 < height <= 1024)
                self.assertIn(self.lock["commit"], entry["url"])
                self.assertIn("/3D/", entry["url"])

    def test_search_catalog_and_existing_pack_are_preserved(self):
        bundled = [e for e in self.manifest["stickers"] if e["id"].startswith("fluent3d-")]
        original = [e for e in self.manifest["stickers"] if not e["id"].startswith("fluent3d-")]
        self.assertEqual(64, len(bundled))
        self.assertGreater(len(original), 100)
        self.assertEqual({e["id"] for e in self.lock["assets"]}, {e["id"] for e in bundled})
        for entry in bundled:
            self.assertEqual("3d", entry["category"])
            self.assertIn("3d", entry["tags"])
            self.assertTrue((BASE / entry["file"]).is_file())
        heart = next(e for e in bundled if e["id"] == "fluent3d-red-heart")
        for alias in ["dil", "heart", "دل"]:
            self.assertIn(alias, heart["tags"])


if __name__ == "__main__":
    unittest.main()
