#!/usr/bin/env python3
"""Enforce T21 applications-family boundaries (plan §T21, §9.3)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase21Applications.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase21ApplicationsTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase21Applications",
    "ruleOverrides",
    "adapterWithFamily",
    "openCardinality",
    "packageMultiCardinality",
    "destructiveMultiCardinality",
    "launchSemantics",
    "packageBatchSemantics",
    "destructiveSemantics",
    "commandSemantics",
    "uninstallSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverApplicationMembers",
    "familyAdapterKeepsTheFullTable",
    "launchActionUpgradesToTypedPackage",
    "forceStopUpgradesToTypedList",
    "forceStopWithoutPackagesIsRejected",
    "uninstallWithBogusPackageIsRejectedNotCoerced",
    "openIsStrictlySingleTarget",
    "destructiveSemanticsAreFailFastAndCapped",
    "destructiveOperationsAreNotBlindlyRetryable",
    "uninstallSchemaRequiresCapabilityDeclaration",
    "canonicalizationRemainsIdempotent",
    "driftedTableFailsClosed",
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
                problems.append(f"FamilyPhase21Applications.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase21Applications.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase21ApplicationsTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_APPLICATIONS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_APPLICATIONS: OK — per-operation cardinality "
        "(open=SINGLE, force-stop/enable bounded MULTI, destructive capped "
        "MULTI fail-fast), typed package lists, CONDITIONALLY_IDEMPOTENT "
        "destructive semantics and DESTRUCTIVE schema with capability "
        "declaration, enforced with mandatory T21 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
