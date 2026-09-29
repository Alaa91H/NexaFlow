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

V3_DOCUMENT_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalWorkflowDocumentV3.kt"
)
ENTITY_FILE = ROOT / "core/database/src/main/java/com/nexaflow/core/database/AutomationEntity.kt"
DATABASE_FILE = ROOT / "core/database/src/main/java/com/nexaflow/core/database/AppDatabase.kt"
MIGRATIONS_FILE = ROOT / "core/database/src/main/java/com/nexaflow/core/database/Migrations.kt"
MAPPER_FILE = ROOT / "data/src/main/java/com/nexaflow/data/mapper/AutomationMapper.kt"

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

    # Product closure: the policy must be wired to Room and the production
    # mapper, not merely modeled in a domain-only policy object.
    for path in (V3_DOCUMENT_FILE, ENTITY_FILE, DATABASE_FILE, MIGRATIONS_FILE, MAPPER_FILE):
        if not path.is_file():
            problems.append(f"missing production V3 wiring {path.relative_to(ROOT)}")

    if V3_DOCUMENT_FILE.is_file():
        v3 = V3_DOCUMENT_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowDocumentV3",
            "CanonicalWorkflowV3Codec",
            "CanonicalRuntimePipeline.defaultAdapter",
            "schemaVersion",
        ):
            if token not in v3:
                problems.append(f"CanonicalWorkflowDocumentV3.kt missing {token!r}")

    if ENTITY_FILE.is_file() and "canonicalWorkflowJson" not in ENTITY_FILE.read_text(encoding="utf-8"):
        problems.append("AutomationEntity does not persist canonicalWorkflowJson")
    if DATABASE_FILE.is_file() and "version = 22" not in DATABASE_FILE.read_text(encoding="utf-8"):
        problems.append("AppDatabase is not bumped to canonical V3 schema version 22")
    if MIGRATIONS_FILE.is_file():
        migrations = MIGRATIONS_FILE.read_text(encoding="utf-8")
        if "MIGRATION_21_22" not in migrations or "canonicalWorkflowJson" not in migrations:
            problems.append("Room migration 21->22 does not add canonicalWorkflowJson")
    if MAPPER_FILE.is_file():
        mapper = MAPPER_FILE.read_text(encoding="utf-8")
        if "CanonicalWorkflowV3Codec.encodeOrNull(this)" not in mapper:
            problems.append("production AutomationMapper does not write canonical V3")

    if problems:
        print("CANONICAL_PERSISTENCE_POLICY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERSISTENCE_POLICY: OK — policy plus production Room wiring: "
        "typed Canonical V3 payloads are emitted by AutomationMapper, persisted "
        "by schema 22 with a lossless 21->22 migration, while legacy columns "
        "remain the controlled rollback/read fallback"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
