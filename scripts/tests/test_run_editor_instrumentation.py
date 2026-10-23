import importlib.util
import json
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "run-editor-instrumentation.py"
ROOT = SCRIPT.parent.parent
spec = importlib.util.spec_from_file_location("run_editor_instrumentation", SCRIPT)
runner = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(runner)


class RunEditorInstrumentationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.apk = self.directory / "app.apk"
        self.test_apk = self.directory / "app-androidTest.apk"
        self.output = self.directory / "report"
        self.write_apk(self.apk, "head123")
        self.test_apk.write_bytes(b"test APK with no build identity")
        self.args = SimpleNamespace(
            apk=self.apk, test_apk=self.test_apk,
            output_dir=self.output, serial="emulator-5554",
            test_class=runner.DEFAULT_CLASS, adb="adb", aapt="aapt",
        )

    @staticmethod
    def write_apk(path, commit):
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("assets/build-identity.properties", f"commit={commit}\nref=test\nrun=1\n")

    def git_response(self, command):
        if command[:3] == ["git", "rev-parse", "--show-toplevel"]:
            return subprocess.CompletedProcess(command, 0, str(ROOT) + "\n", "")
        if command[:3] == ["git", "status", "--porcelain=v1"]:
            return subprocess.CompletedProcess(command, 0, "", "")
        if command[:3] == ["git", "rev-parse", "HEAD"]:
            return subprocess.CompletedProcess(command, 0, "head123\n", "")
        if command[:2] == ["git", "ls-files"]:
            return subprocess.CompletedProcess(command, 0, "", "")
        return None

    def successful_device_mock(self, *, pulled_from_input=True, instrumentation="INSTRUMENTATION_STATUS_CODE: -1\nOK (1 test)\n"):
        def run(command, **kwargs):
            git = self.git_response(command)
            if git:
                return git
            if command[:3] == ["aapt", "dump", "badging"]:
                package = "com.apexstudio.app.test" if str(self.test_apk) in command else "com.apexstudio.app"
                return subprocess.CompletedProcess(command, 0, f"package: name='{package}' versionCode='1'\n", "")
            adb_index = command.index("adb")
            adb = command[adb_index + 1:]
            if adb[:2] == ["-s", "emulator-5554"]:
                adb = adb[2:]
            if adb[:1] == ["install"]:
                return subprocess.CompletedProcess(command, 0, "Success\n", "")
            if adb[:3] == ["shell", "pm", "path"]:
                return subprocess.CompletedProcess(command, 0, "package:/data/app/pkg/base.apk\n", "")
            if adb[:1] == ["pull"]:
                source = self.apk if pulled_from_input else self.directory / "other.apk"
                if not pulled_from_input:
                    self.write_apk(source, "other-head")
                Path(adb[2]).write_bytes(source.read_bytes())
                return subprocess.CompletedProcess(command, 0, "1 file pulled\n", "")
            if adb[:3] == ["shell", "getprop", "ro.build.fingerprint"]:
                return subprocess.CompletedProcess(command, 0, "device/fingerprint\n", "")
            if adb[:3] == ["shell", "getprop", "ro.build.version.sdk"]:
                return subprocess.CompletedProcess(command, 0, "35\n", "")
            if adb[:3] == ["shell", "getprop", "ro.product.cpu.abilist"]:
                return subprocess.CompletedProcess(command, 0, "arm64-v8a,x86_64\n", "")
            if adb[:4] == ["shell", "pm", "list", "instrumentation"]:
                self.assertNotIn("-f", adb, "-f prefixes APK paths and changes the component format")
                return subprocess.CompletedProcess(command, 0, "instrumentation:some.other.test/androidx.test.runner.AndroidJUnitRunner (target=com.apexstudio.app)\ninstrumentation:com.apexstudio.app.test/androidx.test.runner.AndroidJUnitRunner (target=com.apexstudio.app)\n", "")
            if adb[:3] == ["shell", "am", "instrument"]:
                return subprocess.CompletedProcess(command, 0, instrumentation, "")
            self.fail(f"Unexpected command: {command}")
        return run

    @patch.object(runner.subprocess, "run")
    def test_success_records_device_hashes_and_commands_without_wiping_data(self, run):
        run.side_effect = self.successful_device_mock()
        report = runner.execute(self.args)
        self.assertEqual("passed", report["status"])
        self.assertEqual(1, report["tests_passed"])
        self.assertEqual("device/fingerprint", report["device"]["fingerprint"])
        self.assertEqual("35", report["device"]["sdk"])
        self.assertEqual("arm64-v8a,x86_64", report["device"]["abi"])
        self.assertEqual(report["main_apk_sha256"], report["installed_base_apk"]["sha256"])
        self.assertEqual(runner.sha256(self.test_apk), report["test_apk_sha256"])
        self.assertTrue((self.output / "instrumentation.log").is_file())
        persisted = json.loads((self.output / "provenance.json").read_text())
        self.assertEqual("passed", persisted["status"])
        commands = [item["command"] for item in report["commands"]]
        self.assertFalse(any("uninstall" in command or "clear" in command for command in commands))
        self.assertTrue(any("install" in command and command[command.index("install"):command.index("install") + 2] == ["install", "-r"] for command in commands))
        am_calls = [command for command in commands if "am" in command and "instrument" in command]
        self.assertEqual(1, len(am_calls))
        self.assertIn("-e", am_calls[0])

    @patch.object(runner.subprocess, "run")
    def test_dirty_worktree_fails_before_package_inspection_or_adb(self, run):
        def dirty(command, **kwargs):
            response = self.git_response(command)
            if response and command[:3] == ["git", "status", "--porcelain=v1"]:
                return subprocess.CompletedProcess(command, 0, "?? scripts/changed.py\n", "")
            if response:
                return response
            self.fail(f"Device or package command ran before clean check: {command}")
        run.side_effect = dirty
        report = runner.execute(self.args)
        self.assertEqual("failed", report["status"])
        self.assertIn("not clean", report["error"])
        self.assertFalse(any("adb" in item["command"] or "aapt" in item["command"] for item in report["commands"]))

    @patch.object(runner.subprocess, "run")
    def test_stale_input_apk_fails_before_any_install(self, run):
        self.write_apk(self.apk, "oldcommit")
        run.side_effect = self.successful_device_mock()
        report = runner.execute(self.args)
        self.assertEqual("failed", report["status"])
        self.assertIn("Stale main APK", report["error"])
        self.assertFalse(any("install" in item["command"] for item in report["commands"]))

    @patch.object(runner.subprocess, "run")
    def test_installed_hash_mismatch_fails_before_test_install_or_am(self, run):
        run.side_effect = self.successful_device_mock(pulled_from_input=False)
        report = runner.execute(self.args)
        self.assertEqual("failed", report["status"])
        self.assertIn("SHA256 mismatch", report["error"])
        commands = [item["command"] for item in report["commands"]]
        self.assertEqual(1, sum("install" in command for command in commands))
        self.assertFalse(any("am" in command and "instrument" in command for command in commands))

    def test_instrumentation_result_requires_explicit_success_and_rejects_failures(self):
        self.assertEqual(12, runner.parse_test_result("random output\nOK (12 tests)\n"))
        for text in (
            "INSTRUMENTATION_FAILED: process crashed",
            "FAILURES!!! Tests run: 1,  Failures: 1",
            "INSTRUMENTATION_STATUS_CODE: 0",
            "OK (0 tests)\n",
            "OK (1 test)\nINSTRUMENTATION_FAILED: runner failure",
        ):
            with self.subTest(text=text), self.assertRaises(runner.ValidationError):
                runner.parse_test_result(text)

    def test_output_directory_is_excluded_from_git_status_pathspec(self):
        commands = []
        with patch.object(runner.subprocess, "run") as run:
            run.side_effect = lambda command, **kwargs: self.git_response(command)
            runner._check_clean_checkout(ROOT, ROOT / "test-output", commands)
        status_command = next(item["command"] for item in commands if item["command"][:3] == ["git", "status", "--porcelain=v1"])
        self.assertIn(":(exclude)test-output", status_command)

    def test_output_directory_cannot_hide_tracked_source_changes(self):
        def response(command, **kwargs):
            if command[:2] == ["git", "ls-files"]:
                return subprocess.CompletedProcess(command, 0, "scripts/tracked.py\n", "")
            return self.git_response(command)
        with patch.object(runner.subprocess, "run", side_effect=response):
            with self.assertRaises(runner.ValidationError):
                runner._check_clean_checkout(ROOT, ROOT / "scripts", [])


if __name__ == "__main__":
    unittest.main()
