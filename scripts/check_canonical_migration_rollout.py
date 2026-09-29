#!/usr/bin/env python3
"""Enforce T28 controlled migration rollout boundaries (plan §T28)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORCHESTRATOR_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/workflow/"
    "WorkflowMigrationOrchestrator.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/workflow/"
    "WorkflowMigrationOrchestratorTest.kt"
)
RUNNER_FILE = ROOT / (
    "data/src/main/java/com/nexaflow/data/repository/"
    "CanonicalWorkflowMigrationRunner.kt"
)
RUNNER_TEST_FILE = ROOT / (
    "data/src/test/java/com/nexaflow/data/repository/"
    "CanonicalWorkflowMigrationRunnerTest.kt"
)
APPLICATION_FILE = ROOT / (
    "app/src/main/java/com/nexaflow/app/NexaFlowApplication.kt"
)
MAINTENANCE_WORKER_FILE = ROOT / (
    "app/src/main/java/com/nexaflow/app/work/MaintenanceWorker.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
)

REQUIRED_CONSTRUCTS = (
    "WorkflowMigrationOrchestrator",
    "MigrationItem",
    "MigrationBatch",
    "MigrationJournal",
    "MigrationOutcome",
    "OutcomeStatus",
    "MIGRATED",
    "DEGRADED_LEGACY_ONLY",
    "FAILED",
    "planBatches",
    "attemptBatch",
    "declareMigrationComplete",
    "maxFailuresPerRun",
)

REQUIRED_TEST_CASES = (
    "batchesAreDeterministicSortedAndBounded",
    "alreadySettledIdsAreNotReplanned",
    "batchSizeIsBoundedAndDuplicatesRefused",
    "healthyBatchMigratesEveryItem",
    "failureThresholdAbortsTheBatchMidWay",
    "degradedPreparationLandsAsLegacyOnlyNotFailed",
    "journalApplyIsIdempotentAndRetriesFailures",
    "failedOutcomesMustCarryATypedReason",
    "progressCountsEveryTerminalState",
    "completionIsRefusedUntilEveryIdIsSettled",
    "degradedRowsAreRetriedAndBlockCompletion",
    "crashedRunResumesFromTheJournalWithoutDoubleWork",
)


def main() -> int:
    problems: list[str] = []

    if not ORCHESTRATOR_FILE.is_file():
        problems.append(f"missing {ORCHESTRATOR_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if ORCHESTRATOR_FILE.is_file():
        source = ORCHESTRATOR_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"WorkflowMigrationOrchestrator.kt missing {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"WorkflowMigrationOrchestrator.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"WorkflowMigrationOrchestratorTest.kt missing {case!r}"
                )

    for path in (
        RUNNER_FILE,
        RUNNER_TEST_FILE,
        APPLICATION_FILE,
        MAINTENANCE_WORKER_FILE,
    ):
        if not path.is_file():
            problems.append(f"missing production migration wiring {path.relative_to(ROOT)}")

    if RUNNER_FILE.is_file():
        runner = RUNNER_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowMigrationRunner",
            "getAllAutomationsSnapshot",
            "compareAndSetAutomation",
            "canonicalWriteState",
            "V3_WITH_LEGACY_FALLBACK",
            "LEGACY_ONLY_DEGRADED",
            "canonicalGraphMigrationComplete",
            "legacyRetirementReady",
            "WorkflowMigrationOrchestrator.MAX_BATCH_SIZE",
        ):
            if token not in runner:
                problems.append(f"production migration runner missing {token!r}")

    if RUNNER_TEST_FILE.is_file():
        runner_tests = RUNNER_TEST_FILE.read_text(encoding="utf-8")
        for case in (
            "migrationRunsInDeterministicBoundedBatches",
            "canonicalGraphCanMigrateWhileSecretFallbackStillBlocksLegacyRetirement",
            "degradedRowsRemainPendingAndCanAbortTheRollout",
            "concurrentEditWinsWithoutConsumingFailureBudget",
        ):
            if f"fun {case}" not in runner_tests:
                problems.append(f"migration runner tests missing {case!r}")

    if APPLICATION_FILE.is_file():
        application = APPLICATION_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalWorkflowMigrationRunner",
            "canonicalMigrationRunner.runNextBatch()",
        ):
            if token not in application:
                problems.append(f"startup is missing canonical migration invocation {token!r}")

    if MAINTENANCE_WORKER_FILE.is_file():
        maintenance = MAINTENANCE_WORKER_FILE.read_text(encoding="utf-8")
        for token in (
            "WorkerDependenciesEntryPoint",
            "dependencies.canonicalWorkflowMigrationRunner().runNextBatch()",
        ):
            if token not in maintenance:
                problems.append(
                    f"maintenance is missing canonical migration invocation {token!r}"
                )

    if problems:
        print("CANONICAL_MIGRATION_ROLLOUT: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_MIGRATION_ROLLOUT: OK — Room rows are migrated in bounded "
        "deterministic batches with canonicalWriteState as the durable journal, "
        "CAS protects concurrent edits, degraded rows remain retryable, and "
        "canonical graph completion is distinct from legacy-retirement readiness "
        "when secret fallback is still required; startup and periodic maintenance "
        "both invoke the same resumable runner"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
