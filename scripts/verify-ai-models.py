#!/usr/bin/env python3
"""Verify redistributable offline AI assets against the committed model lock.

No network access or model downloads. Run from any working directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit


class ModelIntegrityError(ValueError):
    pass


def _is_revision(value: object) -> bool:
    return (isinstance(value, str) and len(value) in {40, 64}
            and all(character in "0123456789abcdef" for character in value.lower()))


def _url_has_revision(url: str, revision: str) -> bool:
    # A revision must be a URL path component, not arbitrary query text.
    return revision.lower() in urlsplit(url).path.lower().split("/")


def verify_models(root: Path, lock_path: Path) -> list[str]:
    root = root.resolve()
    document = json.loads(lock_path.read_text(encoding="utf-8"))
    if (not isinstance(document, dict) or document.get("version") != 1
            or not isinstance(document.get("models"), list) or not document["models"]):
        raise ModelIntegrityError("Expected version 1 and a non-empty model inventory")
    seen: set[str] = set()
    verified: list[str] = []
    for item in document["models"]:
        if not isinstance(item, dict):
            raise ModelIntegrityError("Each model inventory entry must be an object")
        relative = item.get("path", "")
        path = (root / relative).resolve()
        if not relative or Path(relative).is_absolute() or not path.is_relative_to(root) or path == root:
            raise ModelIntegrityError(f"Unsafe asset path: {relative!r}")
        if relative in seen:
            raise ModelIntegrityError(f"Duplicate asset: {relative}")
        seen.add(relative)
        digest = item.get("sha256", "")
        if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise ModelIntegrityError(f"Invalid SHA-256: {relative}")
        size = item.get("bytes")
        if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            raise ModelIntegrityError(f"Invalid asset size: {relative}")
        source = item.get("source", "")
        if not item.get("license") or not isinstance(source, str) or not source.startswith("https://"):
            raise ModelIntegrityError(f"Missing model provenance: {relative}")

        # immutableRevision pins the artifact URL. `revision` is a compatibility
        # alias. sourceRevision is an upstream code commit, checked against its
        # own immutable permalink and never against a separately hosted asset URL.
        immutable_revision = item.get("immutableRevision")
        legacy_revision = item.get("revision")
        if immutable_revision is not None and legacy_revision is not None and immutable_revision != legacy_revision:
            raise ModelIntegrityError(f"Conflicting immutable revisions: {relative}")
        artifact_revision = immutable_revision if immutable_revision is not None else legacy_revision
        if artifact_revision is not None:
            if (not _is_revision(artifact_revision)
                    or not _url_has_revision(source, artifact_revision)):
                raise ModelIntegrityError(f"Invalid/unpinned artifact URL revision: {relative}")
        source_revision = item.get("sourceRevision")
        source_revision_url = item.get("sourceRevisionUrl")
        if source_revision is not None or source_revision_url is not None:
            if (not _is_revision(source_revision) or not isinstance(source_revision_url, str)
                    or not source_revision_url.startswith("https://")
                    or not _url_has_revision(source_revision_url, source_revision)):
                raise ModelIntegrityError(f"Invalid/mismatched source-code revision permalink: {relative}")

        if "additionalLicenseFiles" not in item or not isinstance(item["additionalLicenseFiles"], list):
            raise ModelIntegrityError(
                f"Missing/invalid additionalLicenseFiles array (use [] when none): {relative}")
        license_files = [item.get("licenseFile"), *item["additionalLicenseFiles"]]
        if any(not isinstance(name, str) or not name for name in license_files):
            raise ModelIntegrityError(f"Missing license file provenance: {relative}")
        for license_name in license_files:
            license_path = (root / license_name).resolve()
            if (Path(license_name).is_absolute() or not license_path.is_relative_to(root)
                    or license_path == root or not license_path.is_file()):
                raise ModelIntegrityError(f"Missing/unsafe license file: {license_name!r}")
        if not path.is_file() or path.stat().st_size != size:
            raise ModelIntegrityError(f"Missing/wrong-size model: {relative}; expected {size} bytes")
        checksum = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                checksum.update(chunk)
        if checksum.hexdigest() != digest:
            raise ModelIntegrityError(f"SHA-256 mismatch: {relative}")
        verified.append(relative)
    # A forgotten, unreviewed binary must not silently become part of the APK.
    binaries = {p.relative_to(root).as_posix() for p in root.rglob("*")
                if p.is_file() and p.suffix.lower() in {".onnx", ".tflite", ".task"}}
    if binaries - seen:
        raise ModelIntegrityError(f"Unpinned model binaries: {sorted(binaries - seen)}")
    return verified


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path,
                        default=Path(__file__).resolve().parents[1] / "app/src/main/assets/models")
    args = parser.parse_args()
    try:
        verified = verify_models(args.root, args.root / "models.lock.json")
    except (ModelIntegrityError, OSError, ValueError, TypeError) as error:
        parser.exit(1, f"Offline AI asset verification failed: {error}\n")
    for model in verified:
        print(f"Verified offline asset: {model}")
    print(f"Verified {len(verified)} assets. SHA-256 pins checked-in bytes; this does not make mutable upstream URLs immutable or certify AI accuracy/moderation readiness.")


if __name__ == "__main__":
    main()
