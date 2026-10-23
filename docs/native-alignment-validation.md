# Native-library alignment validation

`scripts/native-alignment.py` is a dependency-free **static inspection aid** for APK, AAB, and APKS artifacts. It recursively inspects APKs nested in APKS (and other ZIP containers), scans every `.so` entry it encounters, parses ELF32/ELF64 program headers in either byte order, and emits a JSON report.

## Run it

```sh
python3 scripts/native-alignment.py path/to/app.apk
python3 scripts/native-alignment.py path/to/app.aab --output build/native-alignment.json
python3 scripts/native-alignment.py path/to/app.apks --output build/native-alignment.json
python3 -m unittest discover -s scripts/tests -p 'test_native_alignment.py' -v
```

The command exits `0` when its static checks pass (or reports `no_native_libraries` for an archive without native code), `1` when it finds a library failure, and `2` for input/report errors. The JSON includes the input archive path and SHA-256, per-library results, totals grouped by ABI, alignment scope, and GNU_RELRO diagnostics. `arm64-v8a`, `x86_64` and any ELF64 found under an unknown path receive required PT_LOAD checks; all ABIs are listed, while 32-bit alignment is informational. A malformed ELF is a failure regardless of ABI.

For each PT_LOAD, the report includes `p_align`, `p_offset`, `p_vaddr`, and whether the two addresses are congruent modulo 16,384. A relevant 64-bit PT_LOAD with `p_align < 16,384` or non-congruent addresses is flagged. In APK files, uncompressed (`stored`) native-library ZIP data offsets are checked for 16 KB alignment; compressed entries are explicitly `compressed_not_applicable`, not a ZIP failure. The checker applies offsets in each actual APK it examines, not to offsets of `.so` entries in an AAB container.

## Required companion checks

An AAB's ZIP offsets are not installation offsets. For an AAB, also inspect the bundle's requested ZIP alignment and the actual APKs generated for delivery:

```sh
bundletool dump config --bundle=path/to/app.aab | grep alignment
bundletool build-apks --bundle=path/to/app.aab --output=build/app.apks
python3 scripts/native-alignment.py build/app.apks --output=build/generated-apks-alignment.json
```

Confirm the bundle reports `PAGE_ALIGNMENT_16K`, and validate the APK splits generated using the same delivery configuration you intend to ship. The script scans APKs nested in an APKS archive. Use Android's Build Tools `zipalign` check as an independent packaging check as well, for example:

```sh
$ANDROID_HOME/build-tools/<version>/zipalign -c -P 16 -v 4 path/to/app.apk
```

## Interpretation and limitations

This report is **not a runtime compatibility certificate**. Passing it does not establish full 16 KB support: native code, prebuilt dependencies, app behavior, memory assumptions/allocators, packaging and runtime behavior must also be reviewed and tested on an appropriate 16 KB environment. In particular, do not infer full support from `p_align` alone.

GNU_RELRO is surfaced separately from hard alignment status. The JSON includes RELRO ranges, `(VirtAddr + MemSize) mod 16 KB`, and whether writable data follows within a containing writable PT_LOAD. A non-zero RELRO end remainder is not automatically reported as a failure: Android's current guidance distinguishes RELRO sharing a LOAD with subsequent writable data from RELRO in its own LOAD segment. Review the emitted diagnostic in that context rather than treating RELRO presence or the remainder alone as a verdict.

The script's ZIP-offset check is a lightweight static check, not a replacement for `zipalign`. To validate the actual device behavior, use Android's 16 KB compatibility testing guidance and a 16 KB device or emulator. No Android device or emulator is available in the current validation environment (`adb` is unavailable), so runtime testing was not performed.

## References

- [Android: Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) — ELF load-segment checks, ZIP alignment, bundletool config, and nuanced GNU_RELRO guidance.
