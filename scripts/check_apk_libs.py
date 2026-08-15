"""Audit the native-library payload of a release APK.

Runs in CI after assembleRelease to catch two silent regressions:

1. An unsupported ABI sneaking in (e.g. `lib/mips/` from an old vendor SDK).
   Unknown ABI directories FAIL the build.
2. Emulator-only ABIs (x86, x86_64) shipping into a phone-oriented release.
   They are legitimate for emulators, so they are reported with their exact
   size as WARNINGS instead of failing — the numbers feed the bundletool
   split-size comparison in the workflow.

Usage:
    python3 scripts/check_apk_libs.py path/to/app-release.apk
Exits 0 when no unknown ABI is present, 1 otherwise.
"""
import sys
import zipfile

# ABIs Android devices/emulators actually run. Anything else (mips, mips64,
# riscv64 pre-release stubs, ...) is a packaging bug.
SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"}
# Emulator-only: useless on a phone, but harmless to keep for local testing.
EMULATOR_ONLY_ABIS = {"x86_64", "x86"}


def audit(apk_path: str) -> int:
    failures = 0
    per_abi: dict[str, int] = {}
    unknown: list[str] = []
    with zipfile.ZipFile(apk_path) as z:
        for name in z.namelist():
            parts = name.split("/")
            if len(parts) < 3 or parts[0] != "lib" or not name.endswith(".so"):
                continue
            abi = parts[1]
            size = z.getinfo(name).file_size
            per_abi[abi] = per_abi.get(abi, 0) + size
            if abi not in SUPPORTED_ABIS:
                unknown.append(name)

    if not per_abi:
        print("No native libraries found in APK.")
    else:
        total = sum(per_abi.values())
        print(f"Native libraries by ABI (uncompressed):")
        for abi in sorted(per_abi):
            flag = ""
            if abi in EMULATOR_ONLY_ABIS:
                flag = "  [WARN emulator-only - unnecessary on phones]"
            print(f"  {abi:12s} {per_abi[abi]:>9,} bytes{flag}")
        print(f"  {'TOTAL':12s} {total:>9,} bytes")

    if unknown:
        failures += 1
        print("\nFAIL: unsupported ABI directories present:")
        for name in unknown:
            print(f"  {name}")

    if failures:
        print("\nNative-library audit FAILED.")
        return 1
    print("\nNative-library audit passed (no unsupported ABIs).")
    return 0


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    return audit(sys.argv[1])


if __name__ == "__main__":
    sys.exit(main())
