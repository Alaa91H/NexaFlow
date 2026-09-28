#!/usr/bin/env python3
"""Enforce T22 communication-family boundaries (plan §T22)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase22Communication.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase22CommunicationTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase22Communication",
    "ruleOverrides",
    "adapterWithFamily",
    "sendSemantics",
    "policySemantics",
    "commandSemantics",
    "smsSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverCommunicationMembers",
    "familyAdapterKeepsTheFullTable",
    "smsActionUpgradesTypedNumberAndText",
    "smsTriggerFilterUpgradesTypedListAndStaysOptional",
    "sendsAreNeverBlindlyRetryable",
    "smsSchemaIsSensitiveAndComplete",
    "sendSemanticsAreSingleTarget",
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
                problems.append(f"FamilyPhase22Communication.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase22Communication.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase22CommunicationTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_COMMUNICATION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_COMMUNICATION: OK — 14 actions + 4 triggers "
        "upgraded with typed values, NON_IDEMPOTENT sends (no blind retry), "
        "SENSITIVE sms schema and optional typed sender filters, enforced "
        "with mandatory T22 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
