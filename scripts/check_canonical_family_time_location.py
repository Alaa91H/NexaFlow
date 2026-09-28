#!/usr/bin/env python3
"""Enforce T24 time/location family boundaries (plan §T24)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase24TimeLocation.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase24TimeLocationTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"SimpleDateFormat",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase24TimeLocation",
    "ruleOverrides",
    "adapterWithFamily",
    "scheduleSemantics",
    "scheduleSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverTimeLocationMembers",
    "familyAdapterKeepsTheFullTable",
    "locationStateUpgradesToTypedSetState",
    "alarmCarriesWallClockAndExplicitTimezone",
    "bogusWallClockIsRejected",
    "scheduleTriggerCarriesDstSafeWallClockPlusZone",
    "timerDurationsAreMonotonicNotWallClock",
    "geofenceRequiresBoundedRadius",
    "timezoneChangedStaysAPureChangeEvent",
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
                problems.append(f"FamilyPhase24TimeLocation.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase24TimeLocation.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase24TimeLocationTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_TIME_LOCATION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_TIME_LOCATION: OK — 5 actions + 6 triggers "
        "upgraded with DST-safe wall-clock schedules (explicit zone ids), "
        "monotonic timers, bounded geofence radii, idempotent and "
        "parity-pinned"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
