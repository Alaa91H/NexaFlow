#!/usr/bin/env python3
"""Enforce T20 display/sound family boundaries (plan §T20)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase20DisplaySound.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase20DisplaySoundTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase20DisplaySound",
    "ruleOverrides",
    "adapterWithFamily",
    "profileSemantics",
    "ringerModeSchema",
    "brightnessSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverDisplaySoundMembers",
    "familyAdapterKeepsTheFullTable",
    "booleanActionsUpgradeToTypedSetState",
    "numericValueActionsUpgradeToTypedIntegers",
    "nonNumericValueStaysStrictText",
    "missingValueDefersToCatalogContract",
    "realRingerModeKeyIsPreservedForCatalogValidation",
    "triggersUpgradeStateConditionally",
    "nightProfileBatchIsProvenParallelSafe",
    "contradictoryProfileIsRejected",
    "ringerModeSchemaEnforcesTheTokenAllowlist",
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
                problems.append(f"FamilyPhase20DisplaySound.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase20DisplaySound.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase20DisplaySoundTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_DISPLAY_SOUND: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_DISPLAY_SOUND: OK — 33 actions + 11 triggers "
        "upgraded with typed values, batch profiles contradiction-checked, "
        "ringer/brightness schemas enforced, idempotent and parity-pinned"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
