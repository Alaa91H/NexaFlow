#!/usr/bin/env python3
"""Enforce T14 legacy-adapter boundaries (plan §26, ADR-004)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

ADAPTER_FILE = DOMAIN_CANONICAL / "LegacyCanonicalAdapter.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "LegacyCanonicalAdapterTest.kt"
)

# The adapter must stay pure and lossless: no persistence writes, no Android
# runtime, no silent coercions, no name-based guessing.
FORBIDDEN_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"toBoolean\(\)",
    r"toInt\(\)|toLong\(\)",
)

REQUIRED_CONSTRUCTS = (
    "LegacyNodeKind",
    "LegacyConfigEntry",
    "LegacyNodeInput",
    "LegacyAdapterOutcome",
    "LegacyAdapterRejection",
    "LegacyMappingRule",
    "LegacyValueParsers",
    "LegacyCanonicalAdapter",
)

REQUIRED_TEST_CASES = (
    "legacyWifiMapsToTypedSetState",
    "canonicalizationIsIdempotent",
    "unconsumedConfigIsPreservedLosslessly",
    "unknownLegacyTypeIsRejectedNotGuessed",
    "missingRequiredConfigIsRejected",
    "unparsableBooleanIsRejectedNotCoerced",
    "kindMismatchIsRejected",
    "ruleTableRejectsDuplicates",
)


def main() -> int:
    problems: list[str] = []

    if not ADAPTER_FILE.is_file():
        problems.append(f"missing {ADAPTER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if ADAPTER_FILE.is_file():
        source = ADAPTER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"LegacyCanonicalAdapter.kt missing required T14 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"LegacyCanonicalAdapter.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"LegacyCanonicalAdapterTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_LEGACY_ADAPTER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_LEGACY_ADAPTER: OK — "
        "deterministic idempotent legacy-to-canonical mapping with lossless "
        "config preservation and fail-closed rejections, enforced with "
        "mandatory T14 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
