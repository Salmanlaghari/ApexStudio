import importlib.util
import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "native-alignment.py"
spec = importlib.util.spec_from_file_location("native_alignment", SCRIPT)
native_alignment = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(native_alignment)


def make_elf(bits=64, endian="<", p_align=0x4000, p_offset=0x4000, p_vaddr=0x8000, relro=False):
    encoding = 1 if endian == "<" else 2
    ident = b"\x7fELF" + bytes([2 if bits == 64 else 1, encoding, 1, 0, 0]) + bytes(7)
    machine = 183 if bits == 64 else 40
    if bits == 64:
        ehdr_fmt, phdr_fmt = endian + "16sHHIQQQIHHHHHH", endian + "IIQQQQQQ"
        ehsize, phentsize = 64, 56
        phoff = ehsize
        phdr = struct.pack(phdr_fmt, 1, 5, p_offset, p_vaddr, p_vaddr, 64, 64, p_align)
        phdrs = phdr
        phnum = 1
        if relro:
            phdrs += struct.pack(phdr_fmt, 0x6474E552, 4, 0x5000, 0x9000, 0x9000, 16, 16, 1)
            phnum += 1
        header = struct.pack(ehdr_fmt, ident, 3, machine, 1, 0, phoff, 0, 0, ehsize, phentsize, phnum, 0, 0, 0)
    else:
        ehdr_fmt, phdr_fmt = endian + "16sHHIIIIIHHHHHH", endian + "IIIIIIII"
        ehsize, phentsize = 52, 32
        phoff = ehsize
        phdr = struct.pack(phdr_fmt, 1, p_offset, p_vaddr, p_vaddr, 64, 64, 5, p_align)
        phdrs = phdr
        phnum = 1
        if relro:
            phdrs += struct.pack(phdr_fmt, 0x6474E552, 0x5000, 0x9000, 0x9000, 16, 16, 4, 1)
            phnum += 1
        header = struct.pack(ehdr_fmt, ident, 3, machine, 1, 0, phoff, 0, 0, ehsize, phentsize, phnum, 0, 0, 0)
    return header + phdrs + bytes(64)


def aligned_zip_extra(name):
    """Extra field padding which places the local entry's data on a 16 KB boundary."""
    base = 30 + len(name.encode("utf-8"))
    pad = (-base) % native_alignment.PAGE_SIZE
    if pad == 0:
        return b""
    if pad < 4:
        pad += native_alignment.PAGE_SIZE
    return struct.pack("<HH", 0xFFFF, pad - 4) + bytes(pad - 4)


def write_zip(path, entries, *, aligned_so=False):
    with zipfile.ZipFile(path, "w") as zf:
        for name, content in entries:
            info = zipfile.ZipInfo(name)
            info.compress_type = zipfile.ZIP_STORED
            if aligned_so and name.endswith(".so"):
                info.extra = aligned_zip_extra(name)
            zf.writestr(info, content)


class NativeAlignmentTests(unittest.TestCase):
    def test_parse_elf64_little_endian_and_relro_separately(self):
        parsed = native_alignment.parse_elf(make_elf(relro=True))
        self.assertEqual(parsed["class_bits"], 64)
        self.assertEqual(parsed["endianness"], "little")
        self.assertEqual(parsed["program_headers"][0]["p_align"], 16384)
        self.assertTrue(parsed["program_headers"][0]["offset_vaddr_congruent_16k"])
        self.assertEqual(parsed["gnu_relro_count"], 1)

    def test_parse_elf32_big_endian(self):
        parsed = native_alignment.parse_elf(make_elf(32, ">", p_align=0x1000))
        self.assertEqual(parsed["class_bits"], 32)
        self.assertEqual(parsed["endianness"], "big")
        self.assertEqual(parsed["program_headers"][0]["p_align"], 4096)

    def test_empty_archive_does_not_claim_native_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "empty.apk"
            write_zip(apk, [("assets/test", b"test")])
            report = native_alignment.inspect_archive(apk)
        self.assertEqual(report["status"], "no_native_libraries")

    def test_unknown_abi_elf64_is_still_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "unknown.aab"
            write_zip(aab, [("base/lib/new64/libbad.so", make_elf(p_align=4096))])
            report = native_alignment.inspect_archive(aab)
        self.assertEqual(report["totals"]["failures"], 1)

    def test_arm64_aligned_library_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "good.apk"
            write_zip(apk, [("lib/arm64-v8a/libgood.so", make_elf())], aligned_so=True)
            report = native_alignment.inspect_archive(apk)
        library = report["libraries"][0]
        self.assertEqual(library["alignment_status"], "pass")
        self.assertEqual(library["zip_offset_status"], "pass")
        self.assertEqual(library["status"], "pass")
        self.assertEqual(len(report["sha256"]), 64)

    def test_4k_load_and_noncongruent_load_fail_64bit(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "bad.apk"
            elf = make_elf(p_align=0x1000, p_offset=0x4000, p_vaddr=0x8001)
            write_zip(apk, [("lib/x86_64/libbad.so", elf)], aligned_so=True)
            report = native_alignment.inspect_archive(apk)
        self.assertEqual(report["status"], "failure")
        header = report["libraries"][0]["elf"]["program_headers"][0]
        self.assertEqual(header["p_align"], 4096)
        self.assertFalse(header["offset_vaddr_congruent_16k"])
        self.assertEqual(report["libraries"][0]["alignment_status"], "failure")

    def test_zip_data_offset_checked_and_compressed_is_not_applicable(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "zip-offset.apk"
            write_zip(apk, [("lib/arm64-v8a/libx.so", make_elf())])
            report = native_alignment.inspect_archive(apk)
        item = report["libraries"][0]
        self.assertEqual(item["zip_offset_status"], "failure")
        self.assertNotEqual(item["zip_data_offset"] % 16384, 0)

        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "compressed.apk"
            with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("lib/arm64-v8a/libx.so", make_elf())
            compressed = native_alignment.inspect_archive(apk)["libraries"][0]
        self.assertEqual(compressed["zip_offset_status"], "compressed_not_applicable")
        self.assertEqual(compressed["status"], "pass")

    def test_aab_scans_all_abis_but_does_not_check_container_zip_offsets(self):
        with tempfile.TemporaryDirectory() as tmp:
            aab = Path(tmp) / "app.aab"
            write_zip(aab, [
                ("base/lib/arm64-v8a/lib64.so", make_elf()),
                ("base/lib/armeabi-v7a/lib32.so", make_elf(32, p_align=0x1000)),
            ])
            report = native_alignment.inspect_archive(aab)
        self.assertEqual(report["status"], "pass")
        self.assertFalse(report["scope"]["zip_data_offsets_checked"])
        self.assertIn("bundletool dump config", report["scope"]["zip_offset_note"])
        self.assertEqual(set(report["totals"]["by_abi"]), {"arm64-v8a", "armeabi-v7a"})
        self.assertEqual(report["totals"]["by_abi"]["armeabi-v7a"]["scope"], "informational_32bit_or_unknown")
        self.assertEqual(report["libraries"][1]["alignment_status"], "informational")

    def test_nested_apks_in_apks_are_scanned(self):
        with tempfile.TemporaryDirectory() as tmp:
            inner = io.BytesIO()
            with zipfile.ZipFile(inner, "w") as zf:
                zf.writestr("lib/arm64-v8a/libnested.so", make_elf())
            apks = Path(tmp) / "bundle.apks"
            write_zip(apks, [("splits/base-master.apk", inner.getvalue())])
            report = native_alignment.inspect_archive(apks)
        self.assertEqual(len(report["libraries"]), 1)
        self.assertIn("splits/base-master.apk!/lib/arm64-v8a/libnested.so", report["libraries"][0]["entry"])
        self.assertEqual(report["libraries"][0]["archive_scope"], "nested_apk")

    def test_invalid_elf_is_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "invalid.apk"
            write_zip(apk, [("lib/arm64-v8a/not-elf.so", b"not elf")], aligned_so=True)
            report = native_alignment.inspect_archive(apk)
        self.assertEqual(report["status"], "failure")
        self.assertEqual(report["totals"]["invalid_elf"], 1)


if __name__ == "__main__":
    unittest.main()
