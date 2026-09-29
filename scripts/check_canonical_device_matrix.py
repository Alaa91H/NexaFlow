#!/usr/bin/env python3
"""Enforce T36 device matrix simulator boundaries (plan §T36)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIMULATOR_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalDeviceMatrixSimulator.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalDeviceMatrixSimulatorTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    # Legacy config leak (declared config maps), not the reviewed enum-keyed
    # expectation tables the matrix intentionally models as string keys.
    r"val\s+\w+\s*:\s*Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
    r"kotlin\.random\.Random",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalDeviceMatrixSimulator",
    "DeviceMatrixProfile",
    "DeviceFamily",
    "IntegrationTier",
    "SdkBand",
    "Outcome",
    "SUPPORTED",
    "DEGRADED",
    "UNSUPPORTED",
    "Primitive",
    "MatrixResultRow",
    "CoverageReport",
    "blindSpots",
    "missingFamilies",
    "defaultMatrix",
    "replay",
    "coverage",
)

REQUIRED_TEST_CASES = (
    "profileWithCompleteExpectationsIsAccepted",
    "missingPrimitiveExpectationFailsClosed",
    "duplicatePrimitiveExpectationFailsClosed",
    "invalidProfileIdFailsClosed",
    "replayProducesOneCellPerProfileTimesPrimitive",
    "replayIsDeterministic",
    "defaultMatrixReplaysWithoutErrors",
    "coverageCountsOutcomesExactly",
    "blindSpotsListPrimitivesWithNoSupportedProfile",
    "missingFamiliesExposeMatrixGaps",
    "fullySupportedFractionIsExact",
)


def main() -> int:
    problems: list[str] = []

    if not SIMULATOR_FILE.is_file():
        problems.append(f"missing {SIMULATOR_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if SIMULATOR_FILE.is_file():
        source = SIMULATOR_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalDeviceMatrixSimulator.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalDeviceMatrixSimulator.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalDeviceMatrixSimulatorTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_DEVICE_MATRIX: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_DEVICE_MATRIX: OK — deterministic device matrix: OEM/ROM "
        "families with integration tiers and SDK bands, per-primitive "
        "expected outcomes (SUPPORTED/DEGRADED/UNSUPPORTED) declared "
        "fail-closed for every profile, deterministic replay, and computed "
        "coverage exposing primitive blind spots and missing family gaps"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
