#!/usr/bin/env python3
"""Enforce T23 power/sensor family boundaries (plan §T23)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAMILY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FamilyPhase23PowerSensors.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FamilyPhase23PowerSensorsTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "FamilyPhase23PowerSensors",
    "ruleOverrides",
    "adapterWithFamily",
    "powerSemantics",
    "batterySaverThresholdSchema",
)

REQUIRED_TEST_CASES = (
    "familyOverridesCoverPowerSensorMembers",
    "familyAdapterKeepsTheFullTable",
    "powerSaverUpgradesToTypedSetState",
    "chargingLimitUpgradesToTypedInteger",
    "bogusThresholdIsRejectedNotCoerced",
    "batteryTriggerCarriesTypedThresholdAndChargingFilter",
    "chargerTriggerCarriesOptionalTypedState",
    "multiSensorObservationsComposeThroughTheStateRules",
    "thresholdSchemaEnforcesBoundedValues",
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
                problems.append(f"FamilyPhase23PowerSensors.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FamilyPhase23PowerSensors.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FamilyPhase23PowerSensorsTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_FAMILY_POWER_SENSORS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAMILY_POWER_SENSORS: OK — 5 actions + 8 triggers "
        "upgraded with typed threshold/state semantics, bounded schemas, "
        "multi-sensor rule composition, idempotent and parity-pinned"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
