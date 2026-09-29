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

    if problems:
        print("CANONICAL_FAULT_INJECTION: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_FAULT_INJECTION: OK — deterministic fault injection for "
        "the canonical runtime: scripted FAIL/HANG/STALL actions with "
        "first/every/exact-attempt triggers, per-command attempt tracking, "
        "bounded auditable schedules, typed refusals for paused/expired/"
        "unknown scripts (never silent passes), no clocks and no randomness "
        "so every harness replays identical decisions"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
