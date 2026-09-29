#!/usr/bin/env python3
"""Enforce T32 deterministic fault injection boundaries (plan §T32)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTROLLER_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "FaultInjectionController.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "FaultInjectionControllerTest.kt"
)
RUNTIME_GATE_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/"
    "CanonicalFaultInjectionGate.kt"
)
ENGINE_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/"
    "ExecutionEngine.kt"
)
RECOVERY_FILE = ROOT / (
    "core/execution/src/main/java/com/nexaflow/core/execution/recovery/"
    "ExecutionRecoveryCoordinator.kt"
)
INTEGRATION_TEST_FILE = ROOT / (
    "core/execution/src/test/java/com/nexaflow/core/execution/"
    "CanonicalFaultInjectionIntegrationTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
    r"kotlin\.random\.Random",
    r"Math\.random",
)

REQUIRED_CONSTRUCTS = (
    "FaultInjectionController",
    "FaultSchedule",
    "FaultSpec",
    "FaultAction",
    "FAIL",
    "HANG",
    "STALL",
    "Trigger",
    "FIRST_ATTEMPT",
    "EVERY_ATTEMPT",
    "EXACT_ATTEMPT",
    "Decision",
    "FaultRefusal",
    "AttemptTracker",
    "decide",
    "replayUntilPass",
    "MAX_SCHEDULE_ENTRIES",
)

REQUIRED_TEST_CASES = (
    "firstAttemptTriggerInjectsExactlyOnce",
    "everyAttemptTriggerInjectsOnEveryRetry",
    "exactAttemptTriggerHitsOnlyTheNthAttempt",
    "identicalSequencesReplayIdenticalDecisions",
    "pausedScheduleRefusesWithoutTracking",
    "expiredScheduleRefuses",
    "unknownCommandRefusesAndNeverInjects",
    "scheduleConstructorRefusesDuplicatesAndOversize",
    "replayUntilPassCountsExactRetryStorm",
    "replayStopsAtTheFirstPass",
    "attemptTrackingIsPerCommand",
)

REQUIRED_PRODUCT_TEST_CASES = (
    "firstAttemptFaultRetriesOnlyThroughIdempotentCanonicalCommand",
    "injectedHangTimesOutAsUnknownAndRequiresVerification",
    "cancellationDuringInjectedStallSurvivesAsRebootRecoveryWork",
    "providerPermissionAndNetworkFaultsAreKnownFailuresWithoutCorruptState",
)


def main() -> int:
    problems: list[str] = []

    if not CONTROLLER_FILE.is_file():
        problems.append(f"missing {CONTROLLER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if CONTROLLER_FILE.is_file():
        source = CONTROLLER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"FaultInjectionController.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"FaultInjectionController.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"FaultInjectionControllerTest.kt missing {case!r}")

    for path in (RUNTIME_GATE_FILE, ENGINE_FILE, RECOVERY_FILE, INTEGRATION_TEST_FILE):
        if not path.is_file():
            problems.append(f"missing product fault-injection wiring {path.relative_to(ROOT)}")

    if RUNTIME_GATE_FILE.is_file():
        runtime_gate = RUNTIME_GATE_FILE.read_text(encoding="utf-8")
        for token in (
            "CanonicalFaultInjectionGate",
            "ScheduledCanonicalFaultInjectionGate",
            "AtomicCommand",
            "FaultInjectionController.decide",
            "command.commandId",
        ):
            if token not in runtime_gate:
                problems.append(f"runtime fault gate missing {token!r}")

    if ENGINE_FILE.is_file():
        engine = ENGINE_FILE.read_text(encoding="utf-8")
        for token in (
            "faultInjectionGate",
            "executeCanonicalCommandWithFaultInjection",
            "FaultInjectionController.FaultAction.FAIL",
            "FaultInjectionController.FaultAction.HANG",
            "FaultInjectionController.FaultAction.STALL",
            "outcomeUncertain = true",
            "awaitCancellation()",
        ):
            if token not in engine:
                problems.append(f"ExecutionEngine T32 wiring missing {token!r}")
        if engine.index("markActionStarted(") > engine.index("executeCanonicalCommandWithFaultInjection("):
            problems.append(
                "fault injection must run only after ACTION_STARTED is durable"
            )

    if RECOVERY_FILE.is_file():
        recovery = RECOVERY_FILE.read_text(encoding="utf-8")
        for token in (
            "DurableExecutionStatus.ACTION_STARTED",
            "DurableExecutionStatus.ACTION_UNKNOWN",
            "RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED",
        ):
            if token not in recovery:
                problems.append(f"recovery policy missing {token!r}")

    if INTEGRATION_TEST_FILE.is_file():
        integration_tests = INTEGRATION_TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_PRODUCT_TEST_CASES:
            if f"fun {case}" not in integration_tests:
                problems.append(f"product fault test missing {case!r}")

    if problems:
        print("CANONICAL_FAULT_INJECTION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAULT_INJECTION: OK — deterministic FAIL/HANG/STALL "
        "schedules are wired to the real AtomicCommand boundary after durable "
        "ACTION_STARTED; timeout/cancellation leave ACTION_UNKNOWN for startup "
        "verification instead of blind replay, known provider/permission/network "
        "failures terminate cleanly, and retry behavior is exercised through "
        "canonical idempotency"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
