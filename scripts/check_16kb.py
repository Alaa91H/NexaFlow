"""Verify every native library inside an APK is 16 KB page-size aligned.

Google Play requires apps targeting Android 15+ to support 16 KB memory page
sizes: every ELF PT_LOAD segment in a packaged .so must have p_align >= 16384.
This gate runs in CI after assembleRelease so a future dependency shipping an
unaligned native library (e.g. re-adding sentry-android-ndk, or a vendor SDK)
fails the build instead of breaking on 16 KB devices.

Usage:
    python3 scripts/check_16kb.py path/to/app-release.apk
Exits 0 when all native libraries are aligned, 1 otherwise.
"""
import struct
import sys
import zipfile

PAGE_16K = 16384
PT_LOAD = 1


def elf_alignments(data: bytes):
    """Yield (segment_index, p_offset, p_filesz, p_align) for PT_LOAD segments."""
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    ei_class = data[4]
    endian = "<" if data[5] == 1 else ">"
    if ei_class == 2:  # ELF64
        e_phoff = struct.unpack_from(endian + "Q", data, 32)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 54)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 56)[0]
        p_align_off = 48
    elif ei_class == 1:  # ELF32
        e_phoff = struct.unpack_from(endian + "I", data, 28)[0]
        e_phentsize = struct.unpack_from(endian + "H", data, 42)[0]
        e_phnum = struct.unpack_from(endian + "H", data, 44)[0]
        p_align_off = 28
    else:
        raise ValueError(f"unsupported ELF class {ei_class}")

    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        p_offset = struct.unpack_from(endian + "Q" if ei_class == 2 else endian + "I", data, off + 8)[0]
        p_filesz = struct.unpack_from(endian + "Q" if ei_class == 2 else endian + "I", data, off + 32)[0]
        p_align = struct.unpack_from(endian + "Q" if ei_class == 2 else endian + "I", data, off + p_align_off)[0]
        yield i, p_offset, p_filesz, p_align


def check_apk(apk_path: str) -> int:
    failures = 0
    with zipfile.ZipFile(apk_path) as z:
        for name in sorted(n for n in z.namelist() if n.endswith(".so")):
            try:
                segments = list(elf_alignments(z.read(name)))
            except ValueError as exc:
                print(f"FAIL {name}: {exc}")
                failures += 1
                continue
            bad = [s for s in segments if s[2] > 0 and s[3] < PAGE_16K]
            if bad:
                failures += 1
                for idx, p_off, p_sz, p_align in bad:
                    print(
                        f"FAIL {name}: PT_LOAD[{idx}] offset={p_off} "
                        f"filesz={p_sz} p_align={p_align} (< {PAGE_16K})"
                    )
            else:
                aligns = sorted({s[3] for s in segments})
                print(f"OK   {name}: aligns={aligns}")
    return failures


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    failures = check_apk(sys.argv[1])
    if failures:
        print(f"\n{len(failures)} native library(-ies) are not 16 KB aligned.")
        return 1
    print("\nAll native libraries are 16 KB page-size aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
