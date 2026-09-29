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
READ_MAPPER_FILE = ROOT / (
    "data/src/main/java/com/nexaflow/data/mapper/"
    "CanonicalWorkflowV3ReadMapper.kt"
)
DATA_TEST_FILE = ROOT / (
    "data/src/test/java/com/nexaflow/data/repository/RepositoryImplTest.kt"
)
DATA_MAPPER_TEST_FILE = ROOT / (
    "data/src/test/java/com/nexaflow/data/mapper/AutomationMapperTest.kt"
)
NORMALIZER_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "LegacyCatalogCanonicalContract.kt"
)
V3_TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalWorkflowDocumentV3Test.kt"
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

    # Product closure: the policy must be wired to Room and the production
    # mapper, not merely modeled in a domain-only policy object.
    for path in (
        V3_DOCUMENT_FILE,
        NORMALIZER_FILE,
        V3_TEST_FILE,
        ENTITY_FILE,
        DATABASE_FILE,
        MIGRATIONS_FILE,
        MAPPER_FILE,
        READ_MAPPER_FILE,
        DATA_TEST_FILE,
        DATA_MAPPER_TEST_FILE,
    ):
        if not path.is_file():
            problems.append(f"missing production V3 wiring {path.relative_to(ROOT)}")

    if V3_DOCUMENT_FILE.is_file():
        v3 = V3_DOCUMENT_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowDocumentV3",
            "CanonicalWorkflowV3Codec",
            "CanonicalRuntimePipeline",
            "LegacyCatalogCanonicalContractNormalizer",
            "planLegacy",
            "requiresLegacyFallback",
            "CanonicalV3WriteState",
            "prepareWrite",
            "schemaVersion",
            "suppliedConfigKeys",
        ):
            if token not in v3:
                problems.append(f"CanonicalWorkflowDocumentV3.kt missing {token!r}")

    if ENTITY_FILE.is_file():
        entity = ENTITY_FILE.read_text(encoding="utf-8")
        for token in (
            "canonicalWorkflowJson",
            "canonicalWriteState",
            "canonicalWriteErrorCode",
        ):
            if token not in entity:
                problems.append(f"AutomationEntity missing production V3 column {token!r}")
    if DATABASE_FILE.is_file() and "version = 22" not in DATABASE_FILE.read_text(encoding="utf-8"):
        problems.append("AppDatabase is not bumped to canonical V3 schema version 22")
    if MIGRATIONS_FILE.is_file():
        migrations = MIGRATIONS_FILE.read_text(encoding="utf-8")
        for token in (
            "MIGRATION_21_22",
            "canonicalWorkflowJson",
            "canonicalWriteState",
            "canonicalWriteErrorCode",
        ):
            if token not in migrations:
                problems.append(f"Room migration 21->22 missing {token!r}")
    if MAPPER_FILE.is_file():
        mapper = MAPPER_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowV3Codec.prepareWrite(this)",
            "canonicalWrite.payload",
            "canonicalWrite.state.name",
            "canonicalWrite.errorCode",
        ):
            if token not in mapper:
                problems.append(f"production AutomationMapper missing V3 write wiring {token!r}")

        for token in (
            "CanonicalWorkflowV3ReadMapper.toAutomation",
            "CanonicalWorkflowV3Codec.decode(payload)",
            "CanonicalV3WriteState.LEGACY_ONLY_DEGRADED",
        ):
            if token not in mapper:
                problems.append(f"production AutomationMapper missing V3 read wiring {token!r}")

    if READ_MAPPER_FILE.is_file():
        read_mapper = READ_MAPPER_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowV3ReadMapper",
            "persisted.sourceType",
            "legacyFallbackRequired",
            "SecretReferenceValue",
            "triggerMatch",
            "suppliedConfigKeys",
            "NUMERIC_ENUM_PREFIX",
        ):
            if token not in read_mapper:
                problems.append(f"V3 read compatibility mapper missing {token!r}")

    if DATA_TEST_FILE.is_file():
        data_tests = DATA_TEST_FILE.read_text(encoding="utf-8")
        if "automation repository reads canonical V3 before stale legacy workflow columns" not in data_tests:
            problems.append("repository tests do not prove canonical V3 read precedence")

    if DATA_MAPPER_TEST_FILE.is_file():
        mapper_tests = DATA_MAPPER_TEST_FILE.read_text(encoding="utf-8")
        if "numericLegacyEnumAndSparseConfigRoundTripThroughCanonicalV3" not in mapper_tests:
            problems.append(
                "AutomationMapperTest does not prove sparse numeric enum V3 round-trip"
            )

    if NORMALIZER_FILE.is_file():
        normalizer = NORMALIZER_FILE.read_text(encoding="utf-8")
        for token in (
            "LegacyCatalogCanonicalContractNormalizer",
            "containsLegacySecretMaterial",
            "sanitizedPreservedConfig",
        ):
            if token not in normalizer:
                problems.append(f"shared canonical normalizer missing {token!r}")

    if V3_TEST_FILE.is_file():
        v3_tests = V3_TEST_FILE.read_text(encoding="utf-8")
        for case in (
            "v3UsesCatalogTypedDefaultsLikeTheRuntime",
            "rawWifiPasswordNeverEntersCanonicalJsonAndFallbackIsExplicit",
            "validatedBrightnessBoundsApplyToV3WritesToo",
        ):
            if f"fun {case}" not in v3_tests:
                problems.append(f"CanonicalWorkflowDocumentV3Test missing {case!r}")

    if problems:
        print("CANONICAL_PERSISTENCE_POLICY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERSISTENCE_POLICY: OK — production Room saves write the "
        "validated canonical V3 graph and production reads prefer that graph; "
        "typed state makes degradation observable, raw secrets stay outside V3 "
        "and resolve only through the explicit legacy fallback boundary"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
