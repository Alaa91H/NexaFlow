#!/usr/bin/env python3
"""Enforce T17 pilot-family boundaries (plan §T17)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/PilotOpenFamily.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "PilotOpenFamilyTest.kt"
)

# The pilot may only refine reviewed mappings: no legacy enum references, no
# raw config maps, no name-based guessing inside the family rules.
FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"\.lowercase\(\)\s*==",
)

REQUIRED_CONSTRUCTS = (
    "PilotOpenFamily",
    "ruleOverrides",
    "adapterWithPilot",
    "openSettingsSchema",
    "cardinality",
    "semantics",
)

REQUIRED_TEST_CASES = (
    "pilotOverridesCoverExactlyTheGeneratedOpenFamily",
    "pilotAdapterKeepsEveryOtherRuleIntact",
    "pageOpenActionsPreserveParityWithGeneratedTable",
    "urlActionUpgradesToTypedUri",
    "urlActionWithoutUrlIsRejected",
    "appActionWithBogusPackageIsRejectedNotCoerced",
    "unconsumedKeysStillRideAlong",
    "schemaValidatesTypedPageTokens",
    "familySemanticsAreSingleTarget",
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
                problems.append(f"PilotOpenFamily.kt missing required T17 construct {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(f"PilotOpenFamily.kt matches forbidden pattern {pattern!r}")

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"PilotOpenFamilyTest.kt missing required test {case!r}")

    if problems:
        print("CANONICAL_PILOT_OPEN_FAMILY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PILOT_OPEN_FAMILY: OK — 41 SYSTEM_OPEN_* actions upgraded "
        "with typed values over reviewed mappings, parity-pinned, lossless "
        "and fail-closed, with mandatory T17 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
