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

    if problems:
        print("CANONICAL_MIGRATION_ROLLOUT: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_MIGRATION_ROLLOUT: OK — controlled migration rollout: "
        "deterministic bounded batches over the T27 V3-write policy, a "
        "durable idempotent journal with typed per-id outcomes, a per-run "
        "failure threshold that aborts systematic conversion bugs, crash "
        "recovery by re-planning from the journal, and a fail-closed "
        "completion gate that only unlocks T27 V3_ONLY writes when every "
        "legacy id has settled"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
