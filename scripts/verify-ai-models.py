#!/usr/bin/env python3
"""Verify redistributable offline AI assets against the committed model lock.

No network access or model downloads. Run from any working directory.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


class ModelIntegrityError(ValueError):
    pass


def verify_models(root: Path, lock_path: Path) -> list[str]:
    root = root.resolve()
    document = json.loads(lock_path.read_text(encoding="utf-8"))
    if document.get("version") != 1 or not isinstance(document.get("models"), list) or not document["models"]:
        raise ModelIntegrityError("Expected version 1 and a non-empty model inventory")
    seen: set[str] = set()
    verified: list[str] = []
    for item in document["models"]:
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
        if not item.get("license") or not str(item.get("source", "")).startswith("https://"):
            raise ModelIntegrityError(f"Missing model provenance: {relative}")
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
    print(f"Verified {len(verified)} assets. This checks integrity/provenance, not AI accuracy or moderation readiness.")


if __name__ == "__main__":
    main()
