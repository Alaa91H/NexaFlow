"""Verify that the release APK can request READ_PHONE_STATE on current Android.

A transitive manifest previously contributed android:maxSdkVersion="32" to
READ_PHONE_STATE. Android therefore discarded the permission on Android 13+,
leaving the runtime request unavailable even though the source manifest declared
it. This gate audits the packaged APK rather than trusting source manifests.

Usage:
    python3 scripts/check_apk_phone_permission.py path/to/app-release.apk

Exits 0 only when READ_PHONE_STATE is present without maxSdkVersion.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

PERMISSION = "android.permission.READ_PHONE_STATE"


def find_aapt() -> str | None:
    direct = shutil.which("aapt")
    if direct:
        return direct

    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_root:
        return None

    candidates = sorted(
        Path(sdk_root).glob("build-tools/*/aapt"),
        key=lambda path: tuple(int(part) if part.isdigit() else part for part in path.parent.name.replace("-", ".").split(".")),
    )
    return str(candidates[-1]) if candidates else None


def audit(apk_path: str) -> int:
    aapt = find_aapt()
    if not aapt:
        print("FAIL: Android aapt was not found; cannot audit the packaged manifest.")
        return 2

    try:
        result = subprocess.run(
            [aapt, "dump", "permissions", apk_path],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as error:
        print("FAIL: could not read permissions from the release APK.")
        print(error.stderr.strip())
        return 2

    phone_lines = [line.strip() for line in result.stdout.splitlines() if PERMISSION in line]
    if not phone_lines:
        print(f"FAIL: {PERMISSION} is absent from the packaged APK manifest.")
        return 1

    if any("maxSdkVersion" in line for line in phone_lines):
        print(f"FAIL: {PERMISSION} is capped in the packaged APK:")
        for line in phone_lines:
            print(f"  {line}")
        return 1

    print(f"APK permission gate passed: {PERMISSION} is present and unbounded.")
    return 0


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    return audit(sys.argv[1])


if __name__ == "__main__":
    sys.exit(main())
