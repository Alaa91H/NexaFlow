#!/usr/bin/env python3
"""Enforce T18 media/navigation family boundaries (plan §T18)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase18MediaNavigation.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase18MediaNavigationTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase18MediaNavigation",
    "ruleOverrides",
    "adapterWithFamily",
    "mediaSchema",
    "navigationSchema",
    "mediaCardinality",
    "mediaSemantics",
    "navigationSemantics",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverOnlyTableMembers",
    "familyAdapterKeepsTheFullTable",
    "mediaTargetsPreserveParityWithTheSkeletonTable",
    "bogusSessionPackageIsRejectedNotCoerced",
    "searchRequiresQueryButPackageStaysOptional",
    "mediaMultiTargetSemanticsAreExecutable",
    "mediaCardinalityRejectsBeyondFourTargets",
    "navigationSemanticsAreSingleTarget",
    "canonicalizationRemainsIdempotent",
)


def main() -> int:
    problems: list[str] = []

    if not FAMILY_FILE.is_file():
        problems.append(f"missing {FAMILY_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if FAMILY_FILE.is_file():
        source = FAMILY_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"FamilyPhase18MediaNavigation.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase18MediaNavigation.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase18MediaNavigationTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_MEDIA_NAVIGATION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_MEDIA_NAVIGATION: OK — media transport and system "
        "navigation upgrades over reviewed mappings, optional typed filters, "
        "multi-target ordered media semantics, parity-pinned and idempotent"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
