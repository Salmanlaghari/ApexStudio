import importlib.util
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "apk_provenance", Path(__file__).resolve().parents[1] / "apk-provenance.py"
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApkProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.apk = Path(self.directory.name) / "test.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("assets/build-identity.properties", "commit=abc\nref=feature/capcut-real\nrun=123\n")

    @patch.object(module.subprocess, "run")
    def test_hash_and_signer_are_recorded(self, run):
        run.return_value.stdout = "Signer #1 certificate SHA-256 digest: certificate\n"
        result = module.provenance(self.apk, "abc", "apksigner")
        self.assertEqual("abc", result["commit"])
        self.assertEqual(64, len(result["sha256"]))
        self.assertIn("certificate", result["signer"])
        self.assertTrue(run.call_args.kwargs["check"])

    @patch.object(module.subprocess, "run")
    def test_stale_apk_fails_before_signer_execution(self, run):
        with self.assertRaisesRegex(ValueError, "Stale APK"):
            module.provenance(self.apk, "different", "apksigner")
        run.assert_not_called()

    @patch.object(module.subprocess, "run")
    def test_unsigned_or_invalid_apk_fails(self, run):
        run.side_effect = subprocess.CalledProcessError(1, "apksigner")
        with self.assertRaises(subprocess.CalledProcessError):
            module.provenance(self.apk, "abc", "apksigner")
