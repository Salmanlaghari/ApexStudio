import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("ai_model_integrity", Path(__file__).parents[1] / "verify-ai-models.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ModelIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.asset = self.root / "test.onnx"
        self.asset.write_bytes(b"test fixture, not a real inference model")
        self.license_file = self.root / "LICENSE"
        self.license_file.write_text("fixture license notice")
        self.entry = {"path": "test.onnx", "bytes": self.asset.stat().st_size,
                      "sha256": hashlib.sha256(self.asset.read_bytes()).hexdigest(),
                      "source": "https://example.com/pinned-test-fixture", "license": "MIT",
                      "licenseFile": "LICENSE"}
        self.lock = self.root / "models.lock.json"

    def write_lock(self, entries=None):
        self.lock.write_text(json.dumps({"version": 1, "models": [self.entry] if entries is None else entries}))

    def test_valid_asset(self):
        self.write_lock()
        self.assertEqual(["test.onnx"], MODULE.verify_models(self.root, self.lock))

    def test_tampering_is_rejected(self):
        self.write_lock()
        self.asset.write_bytes(b"x" * self.entry["bytes"])
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "SHA-256 mismatch"):
            MODULE.verify_models(self.root, self.lock)

    def test_lfs_pointer_instead_of_model_is_rejected(self):
        self.write_lock()
        self.asset.write_text("version https://git-lfs.github.com/spec/v1\n")
        with self.assertRaises(MODULE.ModelIntegrityError):
            MODULE.verify_models(self.root, self.lock)

    def test_traversal_is_rejected(self):
        self.entry["path"] = "../outside.onnx"
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "Unsafe asset path"):
            MODULE.verify_models(self.root, self.lock)

    def test_duplicate_is_rejected(self):
        self.write_lock([self.entry, self.entry])
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "Duplicate"):
            MODULE.verify_models(self.root, self.lock)

    def test_unreviewed_model_is_rejected(self):
        self.write_lock()
        (self.root / "unreviewed.tflite").write_bytes(b"unreviewed")
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "Unpinned"):
            MODULE.verify_models(self.root, self.lock)

    def test_missing_provenance_is_rejected(self):
        self.entry["license"] = ""
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "provenance"):
            MODULE.verify_models(self.root, self.lock)

    def test_missing_license_text_is_rejected(self):
        self.entry["licenseFile"] = "absent/LICENSE"
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "license file"):
            MODULE.verify_models(self.root, self.lock)

    def test_revision_must_be_immutable_and_match_source(self):
        self.entry["revision"] = "a" * 40
        self.entry["source"] = f"https://example.com/{self.entry['revision']}/model.onnx"
        self.write_lock()
        self.assertEqual(["test.onnx"], MODULE.verify_models(self.root, self.lock))
        self.entry["revision"] = "latest"
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "revision"):
            MODULE.verify_models(self.root, self.lock)

    def test_empty_inventory_is_rejected(self):
        self.write_lock([])
        with self.assertRaises(MODULE.ModelIntegrityError):
            MODULE.verify_models(self.root, self.lock)


if __name__ == "__main__":
    unittest.main()
