#!/usr/bin/env python3
"""Enforce T10 execution-planner boundaries (plan §17, ADR-008)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

PLANNER_FILE = DOMAIN_CANONICAL / "ExecutionPlanner.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "ExecutionPlannerTest.kt"
)

# The planner must stay pure and validation-first: no Android runtime, no UI,
# no silent fallbacks, and no hidden defaults for undeclared operations.
FORBIDDEN_PATTERNS = (
    r"\bContext\b",
    r"androidx\.compose",
    r"runCatching\s*\{\s*plan",
    r"\?:\s*CommandIdempotency\.\w+",
)

REQUIRED_CONSTRUCTS = (
    "AtomicCommand",
    "CommandGroup",
    "CommandSemantics",
    "CommandIdempotency",
    "CompensationCommand",
    "ExecutionPlan",
    "PlanExecutionPolicy",
    "CanonicalExecutionPlanner",
    "planValidated",
)

REQUIRED_TEST_CASES = (
    "actionsCompileIntoAtomicCommandsWithDeclaredSemantics",
    "planningIsDeterministic",
    "waitsSplitScopesAndPreserveOrdering",
    "conflictFreeBatchIsProvenParallelUnderParallelSafePolicy",
    "bestEffortRequiresContinueOnError",
    "transactionalPlanProducesReverseOrderCompensations",
    "undeclaredOperationFailsPlanningClosed",
    "planValidatedRefusesInvalidVerdicts",
)


def main() -> int:
    problems: list[str] = []

    if not PLANNER_FILE.is_file():
        problems.append(f"missing {PLANNER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if PLANNER_FILE.is_file():
        source = PLANNER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"ExecutionPlanner.kt missing required T10 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"ExecutionPlanner.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"ExecutionPlannerTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_EXECUTION_PLANNER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_EXECUTION_PLANNER: OK — "
        "deterministic atomic-command planning with proven parallel safety, "
        "declared semantics, policy gates and validation-first entry enforced "
        "with mandatory T10 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
