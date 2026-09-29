#!/usr/bin/env python3
"""Enforce T27 dual-read/V3-write persistence policy boundaries (plan §T27)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/workflow/"
    "WorkflowPersistencePolicy.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/workflow/"
    "WorkflowPersistencePolicyTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
)

REQUIRED_CONSTRUCTS = (
    "WorkflowPersistencePolicy",
    "POLICY_VERSION",
    "planWrite",
    "planRead",
    "WriteDecision",
    "PersistedWorkflowRowV3",
    "LegacyAutomationSnapshot",
    "LEGACY_ONLY",
    "DUAL_WRITE_V3_PRIMARY",
    "V3_ONLY",
    "legacyMigrationComplete",
    "StorageReadResult",
    "FallbackNeeded",
    "ReadFallbackReason",
)

REQUIRED_TEST_CASES = (
    "dualWriteIsTheDefaultAndCarriesBothPayloads",
    "legacyOnlyModeWritesNoV3Row",
    "v3OnlyWithoutCompletedMigrationFailsClosed",
    "v3OnlyAfterCompletedMigrationDropsTheLegacyRow",
    "writePlanningIsDeterministic",
    "v3WritePreparationDegradesToLegacyOnlyWithATypedWarning",
    "readWithoutAV3RowRequestsTheLegacyPath",
    "readServesTheAuthoritativeDocumentWithUnmodeledFieldsMerged",
    "writeReadRoundTripIsLossless",
    "corruptDocumentFallsBackToTheLegacySnapshot",
    "futureDocumentVersionIsATypedFallbackNotACrash",
    "corruptLegacySnapshotIsUnrepairableAndFailClosed",
    "deepLinkTokenNeverRidesTheDocumentJson",
)


def main() -> int:
    problems: list[str] = []

    if not POLICY_FILE.is_file():
        problems.append(f"missing {POLICY_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if POLICY_FILE.is_file():
        source = POLICY_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"WorkflowPersistencePolicy.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"WorkflowPersistencePolicy.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"WorkflowPersistencePolicyTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_PERSISTENCE_POLICY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERSISTENCE_POLICY: OK — dual-read / V3-write storage "
        "policy: every save persists the versioned document row plus the "
        "lossless legacy snapshot (rollback path), V3_ONLY is refused until "
        "the legacy migration is declared complete, failed V3 preparation "
        "degrades to the legacy row with a typed warning, and reads serve "
        "the document as authoritative with typed fallbacks — never "
        "fabricated defaults"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
