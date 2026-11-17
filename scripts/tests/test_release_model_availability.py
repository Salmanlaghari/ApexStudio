import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).parents[1] / "verify-release-model-availability.py"
SPEC = importlib.util.spec_from_file_location("release_model_availability", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleaseModelAvailabilityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.models_root = self.root / "source-models"
        self.models_root.mkdir()
        self.apk = self.root / "release.apk"
        self.records = {}
        self.contents = {}
        for path in (*MODULE.ALWAYS_BUNDLED, *MODULE.ON_DEMAND_WEIGHTS):
            data = ("fixture:" + path).encode()
            self.records[path] = {
                "path": path,
                "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
                "source": "https://example.test/pinned/model",
                "license": "MIT",
                "licenseFile": "fixture/LICENSE",
                "additionalLicenseFiles": [],
            }
            self.contents[path] = data
        self.lock = {"version": 1, "models": list(self.records.values())}
        (self.models_root / "models.lock.json").write_text(json.dumps(self.lock), encoding="utf-8")
        self._write_apk()

    def _write_apk(self, *, include=(), omit=(), lock=None, dex_strings=None, overrides=None, include_identity=True):
        included = set(MODULE.ALWAYS_BUNDLED) | set(include)
        included.difference_update(omit)
        strings = dex_strings if dex_strings is not None else MODULE.REQUIRED_DEX_STRINGS
        overrides = overrides or {}
        with zipfile.ZipFile(self.apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            if include_identity:
                archive.writestr(MODULE.BUILD_IDENTITY_ASSET,
                                 "commit=" + "a" * 40 + "\nref=fixture-branch\nrun=12345\n")
            archive.writestr(MODULE.LOCK_ASSET, json.dumps(self.lock if lock is None else lock))
            for path in MODULE.REQUIRED_NOTICES:
                archive.writestr(MODULE.MODELS_ASSET_PREFIX + path, "notice")
            for path in included:
                archive.writestr(MODULE.MODELS_ASSET_PREFIX + path, overrides.get(path, self.contents[path]))
            archive.writestr("classes.dex", b"\x00".join(item.encode() for item in strings))

    def test_standard_release_contract_passes(self):
        result = MODULE.verify_release_apk(self.apk, self.models_root)
        self.assertEqual(
            len(MODULE.ALWAYS_BUNDLED) + len(MODULE.ON_DEMAND_WEIGHTS) + 3,
            len(result),
        )
        self.assertIn("ref=fixture-branch run=12345", result[-2])
        self.assertRegex(result[-1], r"^APK SHA-256: [0-9a-f]{64}$")

    def test_missing_build_identity_fails(self):
        self._write_apk(include_identity=False)
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "build-identity.properties"):
            MODULE.verify_release_apk(self.apk, self.models_root)

    def test_missing_always_bundled_model_fails(self):
        self._write_apk(omit=(MODULE.ALWAYS_BUNDLED[0],))
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "missing required offline model"):
            MODULE.verify_release_apk(self.apk, self.models_root)

    def test_bad_hash_for_bundled_model_fails(self):
        path = MODULE.ALWAYS_BUNDLED[0]
        altered = bytes([self.contents[path][0] ^ 1]) + self.contents[path][1:]
        self._write_apk(overrides={path: altered})
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "SHA-256"):
            MODULE.verify_release_apk(self.apk, self.models_root)

    def test_large_whisper_or_alignment_weight_in_release_fails(self):
        path = MODULE.ON_DEMAND_WEIGHTS[0]
        self._write_apk(include=(path,))
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "explicit download"):
            MODULE.verify_release_apk(self.apk, self.models_root)

    def test_pack_entry_removed_from_minified_dex_fails(self):
        strings = [s for s in MODULE.REQUIRED_DEX_STRINGS if s != "english-word-alignment"]
        self._write_apk(dex_strings=strings)
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "english-word-alignment"):
            MODULE.verify_release_apk(self.apk, self.models_root)

    def test_model_lock_mismatch_in_release_fails(self):
        altered = {"version": 1, "models": [dict(row) for row in self.lock["models"]]}
        altered["models"][0]["sha256"] = "0" * 64
        self._write_apk(lock=altered)
        with self.assertRaisesRegex(MODULE.ReleaseModelAvailabilityError, "differs from the checked-in"):
            MODULE.verify_release_apk(self.apk, self.models_root)


if __name__ == "__main__":
    unittest.main()
