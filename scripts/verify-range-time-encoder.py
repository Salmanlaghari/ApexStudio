#!/usr/bin/env python3
"""Check packaged FFmpeg's configured encoder; this is static evidence, not a device smoke test."""
import argparse
import json
from pathlib import Path
import zipfile


def verify(apk: Path) -> dict:
    evidence = []
    with zipfile.ZipFile(apk) as archive:
        libraries = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith("/libavcodec.so")]
        if not libraries:
            raise ValueError("APK contains no FFmpeg libavcodec.so")
        for name in libraries:
            data = archive.read(name)
            if b"--enable-libopenh264" not in data or b"libopenh264" not in data:
                raise ValueError(f"Range Time requires OpenH264, but {name} does not include its build flag")
            evidence.append({"abi": name.split("/")[1], "library": name, "configuredEncoder": "libopenh264"})
    return {"apk": apk.name, "verification": "packaged-configuration-only", "deviceEncodingTested": False, "libraries": evidence}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = json.dumps(verify(args.apk), indent=2) + "\n"
    if args.output:
        args.output.write_text(result)
    print(result, end="")


if __name__ == "__main__":
    main()
