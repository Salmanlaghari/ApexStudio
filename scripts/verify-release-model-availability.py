#!/usr/bin/env python3
"""Verify the model-availability contract in a built, standard Release APK.

The normal Release APK intentionally keeps large Whisper/alignment weights out of
its download. It must retain the small offline models, model lock and licenses,
and the user-initiated model chooser for the two optional subtitle packs. This
checks APK contents only; it does not claim inference or device acceptance.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile


class ReleaseModelAvailabilityError(ValueError):
    """Raised when a release artifact violates the documented model contract."""


MODELS_ASSET_PREFIX = "assets/models/"
LOCK_ASSET = MODELS_ASSET_PREFIX + "models.lock.json"
BUILD_IDENTITY_ASSET = "assets/build-identity.properties"
ALWAYS_BUNDLED = (
    "mediapipe/selfie_segmenter.tflite",
    "super_resolution/super-resolution-10.onnx",
)
ON_DEMAND_WEIGHTS = (
    "whisper/tiny-encoder.int8.onnx",
    "whisper/tiny-decoder.int8.onnx",
    "whisper/tiny-tokens.txt",
    "alignment/wav2vec2-base-960h-int8.onnx",
)
REQUIRED_NOTICES = (
    "whisper/LICENSE",
    "whisper/README.md",
    "alignment/LICENSE",
    "alignment/NOTICE.md",
    "alignment/README.md",
)
REQUIRED_DEX_STRINGS = (
    "whisper-tiny-multilingual",
    "english-word-alignment",
    "Whisper tiny multilingual captions",
    "English word-timing alignment",
    "Optional AI models",
)


def _lock_records(document: object) -> dict[str, dict[str, object]]:
    if (not isinstance(document, dict) or document.get("version") != 1
            or not isinstance(document.get("models"), list)):
        raise ReleaseModelAvailabilityError("Invalid models.lock.json in source or APK")
    records: dict[str, dict[str, object]] = {}
    for row in document["models"]:
        if not isinstance(row, dict) or not isinstance(row.get("path"), str):
            raise ReleaseModelAvailabilityError("Invalid model record in models.lock.json")
        path = row["path"]
        if path in records:
            raise ReleaseModelAvailabilityError(f"Duplicate model lock path: {path}")
        records[path] = row
    return records


def _parse_build_identity(data: bytes) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ReleaseModelAvailabilityError("Release APK build identity is not UTF-8") from error
    properties: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.lstrip().startswith("#"):
            continue
        if "=" not in line:
            raise ReleaseModelAvailabilityError("Malformed Release APK build identity")
        key, value = line.split("=", 1)
        if key in properties:
            raise ReleaseModelAvailabilityError(f"Duplicate Release APK identity field: {key}")
        properties[key] = value
    identity = {key: properties.get(key, "") for key in ("commit", "ref", "run")}
    if not re.fullmatch(r"[0-9a-fA-F]{40,64}", identity["commit"]):
        raise ReleaseModelAvailabilityError("Release APK build identity has no valid source commit")
    if not identity["ref"] or not identity["run"]:
        raise ReleaseModelAvailabilityError("Release APK build identity is missing its source ref or run ID")
    return identity


def verify_release_apk(apk_path: Path, models_root: Path) -> list[str]:
    """Check bundled essentials, stripped download-only weights, and APK UI strings."""
    models_root = models_root.resolve()
    source_lock_path = models_root / "models.lock.json"
    try:
        source_lock = json.loads(source_lock_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseModelAvailabilityError(f"Cannot read source models.lock.json: {error}") from error
    source_records = _lock_records(source_lock)

    try:
        apk = zipfile.ZipFile(apk_path)
    except (OSError, zipfile.BadZipFile) as error:
        raise ReleaseModelAvailabilityError(f"Cannot read Release APK {apk_path}: {error}") from error

    with apk:
        names = set(apk.namelist())
        if BUILD_IDENTITY_ASSET not in names:
            raise ReleaseModelAvailabilityError("Release APK is missing assets/build-identity.properties")
        identity = _parse_build_identity(apk.read(BUILD_IDENTITY_ASSET))
        if LOCK_ASSET not in names:
            raise ReleaseModelAvailabilityError("Release APK is missing assets/models/models.lock.json")
        try:
            packaged_lock = json.loads(apk.read(LOCK_ASSET).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ReleaseModelAvailabilityError(f"Release APK model lock is invalid: {error}") from error
        if packaged_lock != source_lock:
            raise ReleaseModelAvailabilityError("Release APK model lock differs from the checked-in source lock")
        packaged_records = _lock_records(packaged_lock)

        for path in REQUIRED_NOTICES:
            asset = MODELS_ASSET_PREFIX + path
            if asset not in names:
                raise ReleaseModelAvailabilityError(f"Release APK is missing model notice: {asset}")

        for path in ALWAYS_BUNDLED:
            row = source_records.get(path)
            if row is None:
                raise ReleaseModelAvailabilityError(f"Always-bundled model is absent from the source lock: {path}")
            asset = MODELS_ASSET_PREFIX + path
            if asset not in names:
                raise ReleaseModelAvailabilityError(f"Release APK is missing required offline model: {asset}")
            data = apk.read(asset)
            expected_size = row.get("bytes")
            expected_hash = row.get("sha256")
            if not isinstance(expected_size, int) or len(data) != expected_size:
                raise ReleaseModelAvailabilityError(f"Release APK model size differs from lock: {path}")
            if not isinstance(expected_hash, str) or hashlib.sha256(data).hexdigest() != expected_hash:
                raise ReleaseModelAvailabilityError(f"Release APK model SHA-256 differs from lock: {path}")

        for path in ON_DEMAND_WEIGHTS:
            if path not in source_records or path not in packaged_records:
                raise ReleaseModelAvailabilityError(f"Download-only model is absent from the locked pack inventory: {path}")
            if MODELS_ASSET_PREFIX + path in names:
                raise ReleaseModelAvailabilityError(
                    f"Large optional model must remain an explicit download, not a standard Release APK asset: {path}"
                )

        dex_names = sorted(name for name in names if re.fullmatch(r"classes\d*\.dex", Path(name).name))
        if not dex_names:
            raise ReleaseModelAvailabilityError("Release APK contains no DEX files to verify model download UI")
        dex = b"".join(apk.read(name) for name in dex_names)
        for text in REQUIRED_DEX_STRINGS:
            if text.encode("utf-8") not in dex:
                raise ReleaseModelAvailabilityError(f"Release APK is missing the explicit model-download entry: {text}")

    apk_hash = hashlib.sha256()
    with Path(apk_path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            apk_hash.update(chunk)
    return [
        f"Verified bundled model: {path}"
        for path in ALWAYS_BUNDLED
    ] + [
        f"Verified explicit-download model: {path}"
        for path in ON_DEMAND_WEIGHTS
    ] + [
        "Verified model chooser and both subtitle-pack entries are retained in Release DEX"
    ] + [
        f"APK build identity: commit={identity['commit']} ref={identity['ref']} run={identity['run']}",
        f"APK SHA-256: {apk_hash.hexdigest()}",
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="built standard Release APK")
    parser.add_argument(
        "--models-root", type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/assets/models",
        help="source model inventory directory (default: app/src/main/assets/models)",
    )
    args = parser.parse_args()
    try:
        results = verify_release_apk(args.apk, args.models_root)
    except (ReleaseModelAvailabilityError, OSError, ValueError, TypeError) as error:
        parser.exit(1, f"Release model-availability verification failed: {error}\n")
    for result in results:
        print(result)
    print("Release model-availability contract passed. This is static APK verification, not inference/device acceptance.")


if __name__ == "__main__":
    main()
