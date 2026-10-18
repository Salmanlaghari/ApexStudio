#!/usr/bin/env python3
"""Verify the embedded commit, then record the exact APK hash and signer."""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path


def provenance(apk: Path, expected_commit: str, apksigner: str) -> dict:
    with zipfile.ZipFile(apk) as archive:
        identity = dict(
            line.split("=", 1)
            for line in archive.read("assets/build-identity.properties").decode().splitlines()
            if "=" in line
        )
    if identity.get("commit") != expected_commit:
        raise ValueError(f"Stale APK: embedded {identity.get('commit')} != checkout {expected_commit}")
    signer = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    with apk.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    metadata_file = apk.parent / "output-metadata.json"
    metadata = json.loads(metadata_file.read_text()) if metadata_file.exists() else {}
    variants = metadata.get("elements", [])
    version = next((item for item in variants if item.get("outputFile") == apk.name), {})
    return {
        **identity,
        "apk": apk.name,
        "applicationId": metadata.get("applicationId"),
        "variant": metadata.get("variantName"),
        "versionCode": version.get("versionCode"),
        "versionName": version.get("versionName"),
        "sha256": checksum,
        "signer": signer.strip(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = provenance(args.apk, args.commit, args.apksigner)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
