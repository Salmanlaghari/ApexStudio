#!/usr/bin/env python3
"""APK/AAB size report for ApexStudio CI.

Usage: python3 scripts/size-report.py <apk-or-aab> [label]

Prints:
  - total file size (bytes + MiB)
  - category totals (lib/<abi>, assets, res, dex, META-INF, other)
  - top 20 individual entries by uncompressed size
  - top 20 native libraries by ABI

Output is plain text so it lands in the CI log and can be pasted
verbatim into a PR description. Exit code is always 0 (measurement
must never fail a build).
"""
import os
import sys
import zipfile
from collections import defaultdict


def human(n):
    for unit in ("B", "KiB", "MiB", "GiB"):
        if n < 1024 or unit == "GiB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024.0
    return f"{n:.1f} GiB"


def category(name):
    if name.startswith("lib/"):
        parts = name.split("/")
        return f"lib/{parts[1]}" if len(parts) > 1 else "lib"
    if name.startswith("assets/"):
        return "assets"
    if name.startswith("res/"):
        return "res"
    if name.endswith(".dex"):
        return "dex"
    if name.startswith("META-INF/"):
        return "META-INF"
    if name.endswith((".so", ".a")):
        return "native-other"
    return "other"


def main():
    if len(sys.argv) < 2:
        print("usage: size-report.py <apk-or-aab> [label]")
        return 0
    path = sys.argv[1]
    label = sys.argv[2] if len(sys.argv) > 2 else os.path.basename(path)
    if not os.path.exists(path):
        print(f"[size-report] {label}: file not found: {path}")
        return 0
    total = os.path.getsize(path)
    print(f"=== SIZE REPORT: {label} ===")
    print(f"file: {path}")
    print(f"compressed (on disk): {total} bytes ({human(total)})")
    try:
        zf = zipfile.ZipFile(path)
    except Exception as e:
        print(f"[size-report] not a zip container: {e}")
        return 0
    entries = []
    cats = defaultdict(int)
    for info in zf.infolist():
        if info.is_dir():
            continue
        entries.append((info.file_size, info.filename))
        cats[category(info.filename)] += info.file_size
    uncompressed = sum(s for s, _ in entries)
    print(f"uncompressed (installed) total: {uncompressed} bytes ({human(uncompressed)})")
    print()
    print("--- by category (uncompressed) ---")
    for cat, size in sorted(cats.items(), key=lambda kv: -kv[1]):
        pct = 100.0 * size / uncompressed if uncompressed else 0
        print(f"{size:>12}  {pct:5.1f}%  {cat}")
    print()
    print("--- top 20 entries (uncompressed) ---")
    for size, name in sorted(entries, reverse=True)[:20]:
        print(f"{size:>12}  {name}")
    print()
    print("--- top 20 native libs (.so) ---")
    sos = [(s, n) for s, n in entries if n.endswith(".so")]
    for size, name in sorted(sos, reverse=True)[:20]:
        print(f"{size:>12}  {name}")
    print(f"--- .so total: {sum(s for s, _ in sos)} bytes ({human(sum(s for s, _ in sos))}) "
          f"across {len(sos)} libs ---")
    return 0


if __name__ == "__main__":
    sys.exit(main())
