#!/usr/bin/env python3
"""Verify gradle/verification-metadata.xml is up to date.

Usage:
    python3 scripts/check_verification_metadata.py <expected.xml> <actual.xml>

Compares the *semantic content* of two Gradle dependency-verification metadata
files: the set of (component, artifact, sha256) entries and the trusted-artifact
rules. Ordering differences (Gradle writes platform artifacts like aapt2 in an
OS-dependent order) are ignored, so this works when the expected file was
generated on a different OS than the one running the check (e.g. committed on
Windows, checked in CI on Linux).

Exits non-zero with a readable diff if the sets differ.
"""

import re
import sys
from pathlib import Path


def parse(path: Path):
    """Return (trusted_rules, entries) where entries is a dict:
    (group, name, version) -> {artifact_name: sha256}."""
    text = path.read_text(encoding="utf-8")
    trusted = sorted(
        re.findall(r'<trust file="([^"]+)" regex="true"\s*/>', text)
    )
    entries = {}
    for comp in re.finditer(
        r'<component group="([^"]+)" name="([^"]+)" version="([^"]+)">(.*?)</component>',
        text,
        re.S,
    ):
        group, name, version = comp.group(1), comp.group(2), comp.group(3)
        artifacts = {}
        for art in re.finditer(
            r'<artifact name="([^"]+)">\s*'
            r'<sha256 value="([0-9a-fA-F]{64})"[^>]*/>\s*</artifact>',
            comp.group(4),
            re.S,
        ):
            artifacts[art.group(1)] = art.group(2).lower()
        # A component with no sha256 artifacts (e.g. only a module entry) is
        # still significant: keep it with an empty artifact map marker.
        entries[(group, name, version)] = artifacts
    return trusted, entries


def diff(expected: Path, actual: Path) -> list:
    """Return a list of human-readable difference lines (empty = identical)."""
    lines = []
    exp_trusted, exp_entries = parse(expected)
    act_trusted, act_entries = parse(actual)

    if exp_trusted != act_trusted:
        lines.append("trusted-artifacts rules differ:")
        for t in sorted(set(exp_trusted) - set(act_trusted)):
            lines.append(f"  missing in actual: {t}")
        for t in sorted(set(act_trusted) - set(exp_trusted)):
            lines.append(f"  unexpected in actual: {t}")

    for key in sorted(set(exp_entries) - set(act_entries)):
        lines.append(f"missing component {key[0]}:{key[1]}:{key[2]}")
    for key in sorted(set(act_entries) - set(exp_entries)):
        lines.append(f"unexpected component {key[0]}:{key[1]}:{key[2]}")

    for key in sorted(set(exp_entries) & set(act_entries)):
        exp_arts, act_arts = exp_entries[key], act_entries[key]
        for a in sorted(set(exp_arts) - set(act_arts)):
            lines.append(
                f"missing artifact {key[0]}:{key[1]}:{key[2]} -> {a} "
                f"(sha256 {exp_arts[a]})"
            )
        for a in sorted(set(act_arts) - set(exp_arts)):
            lines.append(
                f"unexpected artifact {key[0]}:{key[1]}:{key[2]} -> {a} "
                f"(sha256 {act_arts[a]})"
            )
        for a in sorted(set(exp_arts) & set(act_arts)):
            if exp_arts[a] != act_arts[a]:
                lines.append(
                    f"sha256 changed for {key[0]}:{key[1]}:{key[2]} -> {a}: "
                    f"{exp_arts[a]} != {act_arts[a]}"
                )
    return lines


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    expected = Path(sys.argv[1])
    actual = Path(sys.argv[2])
    for p in (expected, actual):
        if not p.is_file():
            print(f"error: {p} does not exist")
            return 2
    lines = diff(expected, actual)
    if not lines:
        print("verification-metadata.xml is up to date (semantic match).")
        return 0
    print(
        f"verification-metadata.xml is OUT OF DATE: "
        f"{len(lines)} semantic difference(s). "
        f"Regenerate locally with:\n"
        f"  ./gradlew --write-verification-metadata sha256 :app:assembleDebug"
    )
    for line in lines[:50]:
        print(f"  {line}")
    if len(lines) > 50:
        print(f"  ... and {len(lines) - 50} more")
    return 1


if __name__ == "__main__":
    sys.exit(main())
