#!/usr/bin/env python3
"""Verify MediaPipe's narrow R8 protections survive a minified release build."""

from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parent.parent
RULES = ROOT / "app/proguard-rules.pro"
CONFIG = ROOT / "app/build/outputs/mapping/release/configuration.txt"
MAPPING = ROOT / "app/build/outputs/mapping/release/mapping.txt"
APK = ROOT / "app/build/outputs/apk/release/app-release.apk"
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
DEXDUMP = SDK / "build-tools/36.0.0/dexdump"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(pattern: str, text: str, description: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE):
        print(f"PASS: {description}")
    else:
        fail(f"{description} is missing")


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text)


def dex_contains_message_field() -> bool:
    descriptor = (
        "Lcom/google/mediapipe/calculator/proto/"
        "FlowLimiterCalculatorProto$FlowLimiterCalculatorOptions;"
    )
    with zipfile.ZipFile(APK) as apk, tempfile.TemporaryDirectory() as temp_dir:
        dex_names = sorted(
            name
            for name in apk.namelist()
            if re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)
        )
        if not dex_names:
            fail("release APK contains no DEX files")

        for index, name in enumerate(dex_names):
            dex_path = Path(temp_dir) / f"classes-{index}.dex"
            with apk.open(name) as source, dex_path.open("wb") as target:
                shutil.copyfileobj(source, target)

            process = subprocess.Popen(
                [str(DEXDUMP), str(dex_path)],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            in_target_class = False
            found_field = False
            for line in process.stdout:
                if "Class descriptor" in line:
                    in_target_class = descriptor in line
                elif in_target_class and re.search(
                    r"\bname\s*:\s*'maxInFlight_'", line
                ):
                    found_field = True

            if process.wait() != 0:
                fail(f"dexdump could not inspect {name}")
            if found_field:
                return True

    return False


for path in (RULES, CONFIG, MAPPING, APK):
    if not path.is_file():
        fail(f"required release artifact is missing: {path.relative_to(ROOT)}")
if not DEXDUMP.is_file():
    fail(f"Android dexDump tool is missing: {DEXDUMP}")

rules = RULES.read_text(encoding="utf-8")
config = normalize(CONFIG.read_text(encoding="utf-8"))
mapping = MAPPING.read_text(encoding="utf-8")

flogger_rule = "-keep class com.google.common.flogger.FluentLogger { *; }"
proto_fields_rule = (
    "-keepclassmembers class * extends "
    "com.google.protobuf.GeneratedMessageLite { <fields>; }"
)

for label, text in (("source rules", normalize(rules)), ("merged R8 configuration", config)):
    if flogger_rule not in text:
        fail(f"Flogger caller-frame keep rule is missing from {label}")
    if proto_fields_rule not in text:
        fail(f"protobuf-lite generated-field keep rule is missing from {label}")
    print(f"PASS: Flogger and generated-protobuf rules are present in {label}")

if "-keep class com.google.protobuf.** { *; }" in normalize(rules):
    fail("protobuf runtime blanket keep found; keep only generated-message fields")
print("PASS: protobuf keep scope is limited to generated-message fields")

require(
    r"^com\.google\.common\.flogger\.FluentLogger -> "
    r"com\.google\.common\.flogger\.FluentLogger:$",
    mapping,
    "FluentLogger remains unrenamed in the release mapping",
)
require(
    r"^com\.google\.mediapipe\.calculator\.proto\."
    r"FlowLimiterCalculatorProto\$FlowLimiterCalculatorOptions -> "
    r"com\.google\.mediapipe\.calculator\.proto\."
    r"FlowLimiterCalculatorProto\$FlowLimiterCalculatorOptions:$",
    mapping,
    "a generated MediaPipe protobuf message remains available by its original name",
)
if not dex_contains_message_field():
    fail("maxInFlight_ is missing from the generated protobuf message in release DEX")
print("PASS: generated protobuf field maxInFlight_ is present in release DEX")
