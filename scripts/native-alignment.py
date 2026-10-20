#!/usr/bin/env python3
"""Static ELF/ZIP checks relevant to Android 16 KB page-size compatibility.

This tool checks archive contents, ELF PT_LOAD metadata, and (for APKs only)
ZIP data offsets. It does not prove runtime compatibility or full 16 KB support.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, BinaryIO

PAGE_SIZE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
ABI_64 = {"arm64-v8a", "x86_64"}
MAX_NESTED_DEPTH = 8


class ELFError(ValueError):
    """Raised when a shared library is not a valid, parseable ELF object."""


def parse_elf(data: bytes) -> dict[str, Any]:
    """Parse ELF identification and program headers without third-party code."""
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise ELFError("missing or truncated ELF identification")
    elf_class, encoding = data[4], data[5]
    if elf_class not in (1, 2):
        raise ELFError(f"unsupported ELF class {elf_class}")
    if encoding not in (1, 2):
        raise ELFError(f"unsupported ELF byte order {encoding}")
    endian = "<" if encoding == 1 else ">"
    if elf_class == 1:
        header_fmt, ph_fmt = endian + "16sHHIIIIIHHHHHH", endian + "IIIIIIII"
        expected_ehsize, minimum_phentsize = 52, 32
    else:
        header_fmt, ph_fmt = endian + "16sHHIQQQIHHHHHH", endian + "IIQQQQQQ"
        expected_ehsize, minimum_phentsize = 64, 56
    if len(data) < expected_ehsize:
        raise ELFError("truncated ELF header")
    values = struct.unpack_from(header_fmt, data)
    if elf_class == 1:
        phoff, ehsize, phentsize, phnum = values[5], values[8], values[9], values[10]
    else:
        phoff, ehsize, phentsize, phnum = values[5], values[8], values[9], values[10]
    if ehsize < expected_ehsize:
        raise ELFError(f"ELF header size {ehsize} is smaller than {expected_ehsize}")
    if phnum == 0xFFFF:
        raise ELFError("extended program-header numbering is unsupported")
    if phnum and phentsize < minimum_phentsize:
        raise ELFError(f"program-header entry size {phentsize} is too small")
    if phoff > len(data) or phnum * phentsize > len(data) - phoff:
        raise ELFError("program-header table extends beyond the ELF file")

    headers: list[dict[str, Any]] = []
    for index in range(phnum):
        start = phoff + index * phentsize
        fields = struct.unpack_from(ph_fmt, data, start)
        if elf_class == 1:
            p_type, p_offset, p_vaddr, _paddr, p_filesz, p_memsz, p_flags, p_align = fields
        else:
            p_type, p_flags, p_offset, p_vaddr, _paddr, p_filesz, p_memsz, p_align = fields
        if p_type not in (PT_LOAD, PT_GNU_RELRO):
            continue
        headers.append({
            "index": index,
            "type": "PT_LOAD" if p_type == PT_LOAD else "PT_GNU_RELRO",
            "offset": p_offset,
            "vaddr": p_vaddr,
            "filesz": p_filesz,
            "memsz": p_memsz,
            "flags": p_flags,
            "p_align": p_align,
            "p_align_hex": f"0x{p_align:x}",
            "offset_vaddr_congruent_16k": (p_offset % PAGE_SIZE) == (p_vaddr % PAGE_SIZE),
        })
    loads = [h for h in headers if h["type"] == "PT_LOAD"]
    if not loads:
        raise ELFError("ELF has no PT_LOAD program headers")
    relro_details = []
    for relro in (h for h in headers if h["type"] == "PT_GNU_RELRO"):
        end = relro["vaddr"] + relro["memsz"]
        containing_loads = [
            load for load in loads
            if load["vaddr"] <= relro["vaddr"] and load["vaddr"] + load["memsz"] >= end
        ]
        writable_tail = any(
            (load["flags"] & 2) and load["vaddr"] + load["memsz"] > end
            for load in containing_loads
        )
        end_remainder = end % PAGE_SIZE
        relro_details.append({
            "index": relro["index"],
            "vaddr": relro["vaddr"],
            "memsz": relro["memsz"],
            "end_vaddr": end,
            "end_vaddr_remainder_16k": end_remainder,
            "containing_load_indices": [load["index"] for load in containing_loads],
            "writable_data_follows_in_containing_load": writable_tail,
            "diagnostic": (
                "review_relro_boundary_with_following_writable_data"
                if end_remainder and writable_tail else
                "reported_only; not a hard alignment failure"
            ),
        })
    return {
        "class_bits": 32 if elf_class == 1 else 64,
        "endianness": "little" if encoding == 1 else "big",
        "machine": values[2],
        "program_headers": headers,
        "load_count": len(loads),
        "gnu_relro_count": sum(h["type"] == "PT_GNU_RELRO" for h in headers),
        "gnu_relro_diagnostics": relro_details,
    }


def _abi_for_path(name: str) -> str:
    parts = name.replace("\\", "/").split("/")
    for index, part in enumerate(parts[:-1]):
        if part == "lib" and index + 1 < len(parts):
            return parts[index + 1]
    return "unknown"


def _local_data_offset(stream: BinaryIO, info: zipfile.ZipInfo) -> int:
    """Return the absolute local-file data offset for a ZIP entry."""
    stream.seek(info.header_offset)
    header = stream.read(30)
    if len(header) != 30:
        raise zipfile.BadZipFile("truncated local ZIP header")
    signature, _version, _flags, _method, _time, _date, _crc, _csize, _usize, nlen, xlen = struct.unpack(
        "<IHHHHHIIIHH", header
    )
    if signature != 0x04034B50:
        raise zipfile.BadZipFile("invalid local ZIP header signature")
    return info.header_offset + 30 + nlen + xlen


def _archive_scope(kind: str, depth: int) -> str:
    return "nested_apk" if depth else ("apk" if kind == "apk" else kind)


def inspect_archive(path: str | os.PathLike[str]) -> dict[str, Any]:
    """Inspect an APK, AAB, or APKS and any contained APK archives."""
    archive_path = Path(path)
    digest = hashlib.sha256()
    with archive_path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    suffix = archive_path.suffix.lower().lstrip(".")
    if suffix not in {"apk", "aab", "apks"}:
        raise ValueError("input must have .apk, .aab, or .apks extension")
    result: dict[str, Any] = {
        "archive": str(archive_path.resolve()),
        "sha256": digest.hexdigest(),
        "archive_type": suffix,
        "scope": {
            "alignment_page_size_bytes": PAGE_SIZE,
            "zip_data_offsets_checked": suffix in {"apk", "apks"},
            "zip_offset_note": (
                "Stored native-library data offsets are checked in APK files; compressed entries are not applicable."
                if suffix in {"apk", "apks"} else
                "AAB ZIP offsets are not installation offsets; separately run bundletool dump config and inspect generated split APK ZIPs."
            ),
            "limitations": "Static checks only; p_align alone does not certify full 16 KB compatibility or runtime behavior.",
        },
        "libraries": [],
        "totals": {"libraries": 0, "by_abi": {}, "failures": 0, "invalid_elf": 0, "gnu_relro_libraries": 0},
    }
    libraries: list[dict[str, Any]] = []

    def visit(blob: bytes | None, filename: str, kind: str, depth: int, prefix: str = "") -> None:
        if depth > MAX_NESTED_DEPTH:
            libraries.append({"entry": prefix + filename, "abi": "unknown", "status": "failure", "error": "nested archive depth limit exceeded"})
            return
        try:
            stream: BinaryIO = io.BytesIO(blob) if blob is not None else open(filename, "rb")
            with stream:
                with zipfile.ZipFile(stream) as zf:
                    for info in zf.infolist():
                        if info.is_dir():
                            continue
                        entry = f"{prefix}{info.filename}"
                        if info.filename.lower().endswith(".so"):
                            abi = _abi_for_path(info.filename)
                            item: dict[str, Any] = {
                                "entry": entry,
                                "abi": abi,
                                "archive_scope": _archive_scope(kind, depth),
                                "compressed": info.compress_type != zipfile.ZIP_STORED,
                                "zip_data_offset": None,
                                "zip_offset_status": "not_applicable",
                            }
                            if kind == "apk" and info.compress_type == zipfile.ZIP_STORED:
                                try:
                                    offset = _local_data_offset(stream, info)
                                    item["zip_data_offset"] = offset
                                    item["zip_offset_status"] = "pass" if offset % PAGE_SIZE == 0 else "failure"
                                    item["zip_offset_remainder_16k"] = offset % PAGE_SIZE
                                except (OSError, zipfile.BadZipFile, struct.error) as exc:
                                    item["zip_offset_status"] = "failure"
                                    item["zip_offset_error"] = str(exc)
                            elif kind == "apk" and info.compress_type != zipfile.ZIP_STORED:
                                item["zip_offset_status"] = "compressed_not_applicable"
                            try:
                                elf = parse_elf(zf.read(info))
                                item["elf"] = elf
                                relevant_64 = abi in ABI_64 or elf["class_bits"] == 64
                                load_issues = [
                                    h for h in elf["program_headers"] if h["type"] == "PT_LOAD" and
                                    (h["p_align"] < PAGE_SIZE or not h["offset_vaddr_congruent_16k"])
                                ]
                                item["alignment_scope"] = "required_64bit_abi" if relevant_64 else "informational_only"
                                item["alignment_status"] = "failure" if relevant_64 and load_issues else "pass" if relevant_64 else "informational"
                                item["alignment_issues"] = [h["index"] for h in load_issues]
                                item["gnu_relro_diagnostic"] = {
                                    "present": elf["gnu_relro_count"] > 0,
                                    "count": elf["gnu_relro_count"],
                                    "segments": elf["gnu_relro_diagnostics"],
                                    "status": "diagnostic only; consult Android's RELRO guidance; not a hard alignment failure",
                                }
                            except (ELFError, OSError, RuntimeError, zipfile.BadZipFile, NotImplementedError) as exc:
                                item["error"] = str(exc)
                                item["alignment_scope"] = "invalid_elf"
                                item["alignment_status"] = "failure"
                                item["gnu_relro_diagnostic"] = {"present": None, "status": "unavailable_invalid_elf"}
                            hard_failure = item["alignment_status"] == "failure" or item["zip_offset_status"] == "failure"
                            item["status"] = "failure" if hard_failure else ("pass" if item["alignment_scope"] == "required_64bit_abi" else "informational")
                            libraries.append(item)
                        elif info.filename.lower().endswith(".apk"):
                            try:
                                nested = zf.read(info)
                                nested_prefix = f"{entry}!/"
                                visit(nested, info.filename, "apk", depth + 1, nested_prefix)
                            except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
                                libraries.append({"entry": entry, "abi": "unknown", "status": "failure", "error": f"cannot inspect nested APK: {exc}"})
        except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
            if blob is None:
                raise ValueError(f"cannot read archive {filename}: {exc}") from exc
            libraries.append({"entry": prefix + filename, "abi": "unknown", "status": "failure", "error": f"invalid nested APK: {exc}"})

    visit(None, str(archive_path), suffix, 0)
    result["libraries"] = libraries
    totals = result["totals"]
    totals["libraries"] = len(libraries)
    totals["failures"] = sum(lib.get("status") == "failure" for lib in libraries)
    totals["invalid_elf"] = sum(lib.get("alignment_scope") == "invalid_elf" for lib in libraries)
    totals["gnu_relro_libraries"] = sum(bool(lib.get("gnu_relro_diagnostic", {}).get("present")) for lib in libraries)
    for item in libraries:
        abi = item.get("abi", "unknown")
        totals["by_abi"].setdefault(abi, {"libraries": 0, "failures": 0, "scope": "required_64bit_alignment" if item.get("alignment_scope") == "required_64bit_abi" else "informational_32bit_or_unknown"})
        totals["by_abi"][abi]["libraries"] += 1
        totals["by_abi"][abi]["failures"] += int(item.get("status") == "failure")
    result["status"] = "failure" if totals["failures"] else "pass" if libraries else "no_native_libraries"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", help="APK, AAB, or APKS archive to inspect")
    parser.add_argument("--output", help="write JSON report to this path (default: stdout)")
    args = parser.parse_args(argv)
    try:
        report = inspect_archive(args.archive)
    except (OSError, ValueError) as exc:
        print(f"native-alignment: {exc}", file=sys.stderr)
        return 2
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        try:
            Path(args.output).write_text(rendered, encoding="utf-8")
        except OSError as exc:
            print(f"native-alignment: cannot write report: {exc}", file=sys.stderr)
            return 2
    else:
        sys.stdout.write(rendered)
    return 1 if report["status"] == "failure" else 0


if __name__ == "__main__":
    raise SystemExit(main())
