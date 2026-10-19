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
                      "source": "https://example.com/models/latest/test.onnx", "license": "MIT",
                      "licenseFile": "LICENSE", "additionalLicenseFiles": []}
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

    def test_additional_license_files_key_is_required_and_misspellings_fail(self):
        self.entry.pop("additionalLicenseFiles")
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "additionalLicenseFiles"):
            MODULE.verify_models(self.root, self.lock)
        self.entry["additionalLicenceFiles"] = []
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "additionalLicenseFiles"):
            MODULE.verify_models(self.root, self.lock)

    def test_immutable_revision_must_match_artifact_url_path_component(self):
        self.entry["immutableRevision"] = "a" * 40
        self.entry["source"] = f"https://example.com/{self.entry['immutableRevision']}/model.onnx"
        self.write_lock()
        self.assertEqual(["test.onnx"], MODULE.verify_models(self.root, self.lock))
        self.entry["immutableRevision"] = "b" * 40
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "artifact URL revision"):
            MODULE.verify_models(self.root, self.lock)

    def test_source_revision_is_checked_against_its_own_permalink_not_asset_url(self):
        revision = "a" * 40
        self.entry["source"] = "https://github.com/example/model/releases/download/weights/model.onnx"
        self.entry["sourceRevision"] = revision
        self.entry["sourceRevisionUrl"] = f"https://github.com/example/model/tree/{revision}"
        self.write_lock()
        self.assertEqual(["test.onnx"], MODULE.verify_models(self.root, self.lock))
        self.entry["sourceRevision"] = "b" * 40
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "source-code revision"):
            MODULE.verify_models(self.root, self.lock)

    def test_mutable_artifact_url_is_allowed_when_content_hash_is_locked(self):
        # A mutable upstream alias (such as /latest/ or a release tag) cannot be
        # asserted immutable; the verifier still checks the exact local SHA-256.
        self.write_lock()
        self.assertEqual(["test.onnx"], MODULE.verify_models(self.root, self.lock))
        self.entry["immutableRevision"] = "a" * 40
        self.write_lock()
        with self.assertRaisesRegex(MODULE.ModelIntegrityError, "artifact URL revision"):
            MODULE.verify_models(self.root, self.lock)

    def test_empty_inventory_is_rejected(self):
        self.write_lock([])
        with self.assertRaises(MODULE.ModelIntegrityError):
            MODULE.verify_models(self.root, self.lock)

    def test_candy9_is_debug_only_and_release_model_inventory_remains_valid(self):
        repository = Path(__file__).resolve().parents[2]
        main_models = repository / "app/src/main/assets/models"
        debug_models = repository / "app/src/debug/assets/models"
        candy_path = "style_transfer/candy-9.onnx"

        self.assertFalse((main_models / candy_path).exists())
        self.assertTrue((debug_models / candy_path).is_file())
        main_entries = json.loads((main_models / "models.lock.json").read_text())["models"]
        self.assertNotIn(candy_path, {entry["path"] for entry in main_entries})
        self.assertEqual(["style_transfer/candy-9.onnx"], MODULE.verify_models(
            debug_models, debug_models / "style_transfer/models.lock.json"))
        MODULE.verify_models(main_models, main_models / "models.lock.json")


if __name__ == "__main__":
    unittest.main()
