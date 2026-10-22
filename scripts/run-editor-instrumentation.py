#!/usr/bin/env python3
"""Safely install and run an Android instrumentation test against an exact APK."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Any

DEFAULT_CLASS = "com.apexstudio.app.ui.screens.editor.LandscapeEditorInstrumentedTest"
IDENTITY_ASSET = "assets/build-identity.properties"
ADB_COMMAND_TIMEOUT = 180
INSTRUMENTATION_TIMEOUT = 900


class ValidationError(RuntimeError):
    """An input artifact, device state, or test result failed validation."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def embedded_identity(apk: Path) -> dict[str, str]:
    try:
        with zipfile.ZipFile(apk) as archive:
            content = archive.read(IDENTITY_ASSET).decode("utf-8")
    except (OSError, KeyError, UnicodeDecodeError, zipfile.BadZipFile) as exc:
        raise ValidationError(f"Cannot read {IDENTITY_ASSET} from {apk}: {exc}") from exc
    identity: dict[str, str] = {}
    for line in content.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            identity[key.strip()] = value.strip()
    return identity


def parse_test_result(output: str) -> int:
    if re.search(r"FAILURES!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED", output, re.IGNORECASE):
        raise ValidationError("Instrumentation reported failures or a crashed process")
    match = re.search(r"(?:^|\n)OK\s+\((\d+)\s+tests?\)(?:\s|$)", output)
    if not match:
        raise ValidationError("Instrumentation output did not contain an explicit 'OK (n tests)' result")
    count = int(match.group(1))
    if count < 1:
        raise ValidationError("Instrumentation did not execute any tests")
    return count


def package_from_aapt(aapt: str, apk: Path, commands: list[dict[str, Any]]) -> str:
    result = run_command([aapt, "dump", "badging", str(apk)], commands, timeout=60)
    match = re.search(r"^package: name='([^']+)'", result.stdout, re.MULTILINE)
    if not match:
        raise ValidationError(f"Could not determine application package from {apk} using aapt")
    return match.group(1)


def run_command(command: list[str], commands: list[dict[str, Any]], *, timeout: int) -> subprocess.CompletedProcess[str]:
    """Run and retain command details in the JSON report, including failures/timeouts."""
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as exc:
        stdout = exc.stdout or ""
        stderr = exc.stderr or ""
        if isinstance(stdout, bytes):
            stdout = stdout.decode(errors="replace")
        if isinstance(stderr, bytes):
            stderr = stderr.decode(errors="replace")
        commands.append({"command": command, "timeout_seconds": timeout, "timed_out": True,
                         "returncode": None, "stdout": stdout, "stderr": stderr})
        raise ValidationError(f"Command timed out after {timeout}s: {' '.join(command)}") from exc
    commands.append({"command": command, "timeout_seconds": timeout, "timed_out": False,
                     "returncode": result.returncode, "stdout": result.stdout, "stderr": result.stderr})
    if result.returncode != 0:
        raise ValidationError(
            f"Command failed ({result.returncode}): {' '.join(command)}\n{result.stderr.strip()}"
        )
    return result


def _adb(adb: str, serial: str | None, args: list[str], commands: list[dict[str, Any]], *, timeout: int = ADB_COMMAND_TIMEOUT) -> subprocess.CompletedProcess[str]:
    command = [adb]
    if serial:
        command += ["-s", serial]
    return run_command(command + args, commands, timeout=timeout)


def _check_clean_checkout(root: Path, output_dir: Path, commands: list[dict[str, Any]]) -> str:
    top = run_command(["git", "rev-parse", "--show-toplevel"], commands, timeout=30).stdout.strip()
    repo_root = Path(top).resolve()
    if repo_root != root.resolve():
        raise ValidationError(f"Runner repository root mismatch: {repo_root} != {root.resolve()}")
    try:
        relative_output = output_dir.resolve().relative_to(repo_root).as_posix()
    except ValueError:
        relative_output = None
    if relative_output == ".":
        raise ValidationError("--output-dir must not be the repository root")
    status_command = ["git", "status", "--porcelain=v1", "--untracked-files=all"]
    if relative_output:
        tracked = run_command(["git", "ls-files", "--", relative_output], commands, timeout=30).stdout
        if tracked.strip():
            raise ValidationError("--output-dir must not contain tracked source files")
        # Exclude only the designated report directory; the rest of the tracked and
        # untracked worktree must be pristine before any device operation.
        status_command += ["--", ".", f":(exclude){relative_output}"]
    status = run_command(status_command, commands, timeout=30).stdout
    if status.strip():
        raise ValidationError(f"Git worktree is not clean (excluding output directory):\n{status.rstrip()}")
    commit = run_command(["git", "rev-parse", "HEAD"], commands, timeout=30).stdout.strip()
    if not commit:
        raise ValidationError("Unable to determine git HEAD")
    return commit


def execute(args: argparse.Namespace) -> dict[str, Any]:
    apk = args.apk.resolve()
    test_apk = args.test_apk.resolve()
    output_dir = args.output_dir.resolve()
    report: dict[str, Any] = {"status": "failed", "commands": [], "apk": str(apk),
                              "test_apk": str(test_apk), "output_dir": str(output_dir),
                              "serial": args.serial, "test_class": args.test_class}
    commands = report["commands"]
    try:
        if not apk.is_file() or not test_apk.is_file():
            raise ValidationError("Both --apk and --test-apk must name existing files")
        root = Path(__file__).resolve().parents[1]
        commit = _check_clean_checkout(root, output_dir, commands)
        identity = embedded_identity(apk)
        report["expected_commit"] = commit
        report["main_apk_identity"] = identity
        if identity.get("commit") != commit:
            raise ValidationError(f"Stale main APK: embedded commit {identity.get('commit')!r} != git HEAD {commit!r}")

        report["main_apk_sha256"] = sha256(apk)
        report["test_apk_sha256"] = sha256(test_apk)
        package = package_from_aapt(args.aapt, apk, commands)
        test_package = package_from_aapt(args.aapt, test_apk, commands)
        report["application_id"] = package
        report["test_application_id"] = test_package
        _adb(args.adb, args.serial, ["install", "-r", str(apk)], commands)

        path_output = _adb(args.adb, args.serial, ["shell", "pm", "path", package], commands).stdout
        path_match = re.search(r"(?:^|\n)package:(\S+base\.apk)\s*(?:\n|$)", path_output)
        if not path_match:
            raise ValidationError(f"Could not find installed base.apk path for {package}: {path_output.strip()}")
        installed_remote = path_match.group(1)
        pulled_apk = output_dir / "installed-base.apk"
        output_dir.mkdir(parents=True, exist_ok=True)
        _adb(args.adb, args.serial, ["pull", installed_remote, str(pulled_apk)], commands)
        pulled_hash = sha256(pulled_apk)
        report["installed_base_apk"] = {"device_path": installed_remote, "local_path": str(pulled_apk),
                                         "sha256": pulled_hash}
        if pulled_hash != report["main_apk_sha256"]:
            raise ValidationError(f"Installed base.apk SHA256 mismatch: {pulled_hash} != {report['main_apk_sha256']}")
        pulled_identity = embedded_identity(pulled_apk)
        report["installed_base_apk"]["identity"] = pulled_identity
        if pulled_identity.get("commit") != commit:
            raise ValidationError(f"Pulled installed APK has stale identity: {pulled_identity.get('commit')!r} != {commit!r}")

        fingerprint = _adb(args.adb, args.serial, ["shell", "getprop", "ro.build.fingerprint"], commands).stdout.strip()
        sdk = _adb(args.adb, args.serial, ["shell", "getprop", "ro.build.version.sdk"], commands).stdout.strip()
        abi = _adb(args.adb, args.serial, ["shell", "getprop", "ro.product.cpu.abilist"], commands).stdout.strip()
        if not abi:
            abi = _adb(args.adb, args.serial, ["shell", "getprop", "ro.product.cpu.abi"], commands).stdout.strip()
        report["device"] = {"fingerprint": fingerprint, "sdk": sdk, "abi": abi}

        _adb(args.adb, args.serial, ["install", "-r", str(test_apk)], commands)
        instruments = _adb(args.adb, args.serial, ["shell", "pm", "list", "instrumentation"], commands).stdout
        registered = re.findall(
            r"instrumentation:([^\s{}]+/[^\s{}]+)\s+\(target=([^\s)]+)\)", instruments
        )
        matching = [(component, target) for component, target in registered
                    if component.split("/", 1)[0] == test_package and target == package]
        if not matching:
            targets = sorted({target for component, target in registered
                              if component.split("/", 1)[0] == test_package})
            if targets:
                raise ValidationError(f"Test APK targets {', '.join(targets)}, but main APK package is {package}")
            raise ValidationError(f"No instrumentation runner for test APK package {test_package} targeting {package}")
        component = matching[0][0]
        report["instrumentation_component"] = component
        instrumentation = _adb(
            args.adb, args.serial,
            ["shell", "am", "instrument", "-w", "-e", "class", args.test_class, component],
            commands, timeout=INSTRUMENTATION_TIMEOUT,
        )
        test_output = instrumentation.stdout + ("\n" + instrumentation.stderr if instrumentation.stderr else "")
        (output_dir / "instrumentation.log").write_text(test_output, encoding="utf-8")
        report["test_output"] = test_output
        report["tests_passed"] = parse_test_result(test_output)
        report["status"] = "passed"
    except Exception as exc:
        report["error"] = f"{type(exc).__name__}: {exc}"
    finally:
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / "provenance.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True, help="Main application APK built from this checkout")
    parser.add_argument("--test-apk", type=Path, required=True, help="Instrumentation test APK")
    parser.add_argument("--class", dest="test_class", default=DEFAULT_CLASS, help="Fully qualified instrumentation test class")
    parser.add_argument("--serial", help="adb device serial; omit to use adb's default device")
    parser.add_argument("--output-dir", type=Path, required=True, help="Directory for pulled APK and provenance/log files")
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"), help="adb executable (default: adb or $ADB)")
    parser.add_argument("--aapt", default=os.environ.get("AAPT", "aapt"), help="aapt executable (default: aapt or $AAPT)")
    args = parser.parse_args(argv)
    report = execute(args)
    print(json.dumps(report, indent=2))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
