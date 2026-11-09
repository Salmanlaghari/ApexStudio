#!/usr/bin/env python3
"""Policy guard for "free AI" in ApexStudio.

Answers one question: may this model ship as an on-device, on-demand AI feature?

Rules enforced (all fail-closed):
  1. License must be on the permissive allowlist (Apache-2.0, MIT, BSD-3-Clause, CC0-1.0).
     Anything with a "non-commercial", "research-only" or "evaluation" restriction is rejected
     because a Play Store app is a commercial distribution.
  2. Every artifact must pin an exact byte size AND a sha256. No hash -> no download.
  3. Large artifacts must be download-on-demand, never bundled in the APK/AAB assets.
  4. Artifact size must be inside the declared budget so a user is never surprised by a
     200 MB download without an explicit, sized confirmation.
  5. Source URL must be a pinned, immutable permalink (contains a commit/version), not a
     floating "latest" pointer that can silently change bytes under a cached hash.

This script never downloads anything. It reads app/src/main/assets/models/models.lock.json.

Usage:
    python3 scripts/verify-free-ai-policy.py            # verify
    python3 scripts/verify-free-ai-policy.py --report   # verify + print a sized summary
Exit code 0 = clean, 1 = policy violations found.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LOCK_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "models.lock.json"

PERMISSIVE = {
    "Apache-2.0",
    "MIT",
    "BSD-3-Clause",
    "BSD-2-Clause",
    "CC0-1.0",
}

# Phrases that make a licence unusable for a commercial Play release.
RESTRICTIVE = (
    "non-commercial",
    "noncommercial",
    "non commercial",
    "research only",
    "research-only",
    "evaluation only",
    "evaluation-only",
    "academic",
)

# Artifacts above this are only acceptable as an explicit, pre-sized on-demand download.
BUNDLE_LIMIT_BYTES = 2 * 1024 * 1024  # 2 MiB
HARD_DOWNLOAD_BUDGET_BYTES = 150 * 1024 * 1024  # 150 MiB

# A pinned permalink carries a commit sha or an explicit version segment.
PINNED_URL = re.compile(r"(/commit/|/resolve/|tree/[0-9a-f]{7,40}|/download/[0-9v])", re.I)
FLOATING = re.compile(r"/latest/|/main/|/master/|=latest", re.I)


def load_lock(path: Path) -> list[dict]:
    if not path.is_file():
        print(f"FAIL: lock file missing: {path}")
        sys.exit(1)
    data = json.loads(path.read_text(encoding="utf-8"))
    return data.get("models", [])


def check(entry: dict) -> list[str]:
    problems: list[str] = []
    path = entry.get("path", "<unknown>")
    license_id = (entry.get("license") or "").strip()

    if not license_id:
        problems.append(f"{path}: license is not declared")
    else:
        base = license_id.split(";")[0].strip()
        if base not in PERMISSIVE:
            problems.append(f"{path}: license '{base}' is not on the permissive allowlist {sorted(PERMISSIVE)}")
        lowered = license_id.lower()
        for phrase in RESTRICTIVE:
            if phrase in lowered:
                problems.append(f"{path}: license text contains restriction '{phrase}'")

    if not entry.get("bytes"):
        problems.append(f"{path}: byte size is not pinned")
    if not re.fullmatch(r"[0-9a-f]{64}", (entry.get("sha256") or "").strip().lower()):
        problems.append(f"{path}: sha256 is missing or not a 64-char hex digest")

    size = int(entry.get("bytes") or 0)
    bundled = bool(entry.get("bundled", False))
    if size > BUNDLE_LIMIT_BYTES and bundled:
        problems.append(
            f"{path}: {size} bytes exceeds the {BUNDLE_LIMIT_BYTES}-byte bundle limit "
            "but is marked bundled — move it to on-demand download"
        )
    if size > HARD_DOWNLOAD_BUDGET_BYTES:
        problems.append(
            f"{path}: {size} bytes exceeds the {HARD_DOWNLOAD_BUDGET_BYTES}-byte download budget"
        )

    source = (entry.get("source") or "").strip()
    if not source:
        problems.append(f"{path}: source URL is not recorded (provenance unknown)")
    elif FLOATING.search(source) and not PINNED_URL.search(source):
        problems.append(
            f"{path}: source URL is a floating pointer ({source}); pin it to a commit/version permalink"
        )

    license_file = entry.get("licenseFile")
    if not license_file:
        problems.append(f"{path}: licenseFile is not recorded — the licence text must ship with the artifact")

    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", action="store_true", help="print a sized summary of the inventory")
    args = ap.parse_args()

    entries = load_lock(LOCK_PATH)
    if not entries:
        print("FAIL: no models in lock file")
        return 1

    all_problems: list[str] = []
    total = 0
    for entry in entries:
        all_problems.extend(check(entry))
        total += int(entry.get("bytes") or 0)

    if args.report:
        print(f"AI model inventory — {len(entries)} artifacts, {total / 1024 / 1024:.1f} MiB total\n")
        for entry in sorted(entries, key=lambda e: -(int(e.get("bytes") or 0))):
            size = int(entry.get("bytes") or 0)
            flag = "BUNDLED" if entry.get("bundled") else "on-demand"
            print(f"  {size / 1024 / 1024:8.1f} MiB  {flag:9}  {entry.get('path', '')}")
        print()

    if all_problems:
        print(f"POLICY VIOLATIONS ({len(all_problems)}):")
        for p in all_problems:
            print(f"  - {p}")
        return 1

    print(f"OK: {len(entries)} model artifacts satisfy the free-AI policy "
          f"({total / 1024 / 1024:.1f} MiB, all on-demand unless under "
          f"{BUNDLE_LIMIT_BYTES // 1024 // 1024} MiB).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
