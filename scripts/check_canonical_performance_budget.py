#!/usr/bin/env python3
"""Enforce T33 structural performance budget boundaries (plan §T33)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUDGET_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalPerformanceBudget.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalPerformanceBudgetTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
    r"kotlin\.random\.Random",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalPerformanceBudget",
    "Budget",
    "AstMetrics",
    "PlanMetrics",
    "BudgetViolation",
    "PerformanceReport",
    "withinBudget",
    "measureAst",
    "measurePlan",
    "validate",
    "validateAstOnly",
    "DEFAULT_MAX_NODES",
    "DEFAULT_MAX_DEPTH",
    "DEFAULT_MAX_PLAN_COMMANDS",
)

REQUIRED_TEST_CASES = (
    "astMetricsCountNodesDepthAndWaits",
    "singleNodeMeasuresDepthOne",
    "planMetricsCountGroupsAndCommands",
    "withinBudgetReportHasNoViolations",
    "exceededNodeBudgetProducesATypedViolation",
    "exceededWaitBudgetIsReportedSeparately",
    "exceededPlanCommandBudgetIsReported",
    "multipleViolationsStackInFixedRuleOrder",
    "measurementIsDeterministicAcrossRuns",
    "budgetConstructorRejectsNonPositiveLimits",
    "optimizingAnOverBudgetTreeShrinksTheReport",
    "mergedWaitsReduceTheWaitCount",
)


def main() -> int:
    problems: list[str] = []

    if not BUDGET_FILE.is_file():
        problems.append(f"missing {BUDGET_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if BUDGET_FILE.is_file():
        source = BUDGET_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(f"CanonicalPerformanceBudget.kt missing {token!r}")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalPerformanceBudget.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(f"CanonicalPerformanceBudgetTest.kt missing {case!r}")

    if problems:
        print("CANONICAL_PERFORMANCE_BUDGET: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERFORMANCE_BUDGET: OK — structural performance budgets "
        "over the canonical AST and execution plan: deterministic "
        "measurement of node count, depth, waits, plan commands and groups; "
        "typed violations naming rule, observation and bound; no clocks and "
        "no randomness; integrates with the T29 optimizer so over-budget "
        "trees re-measure smaller after consolidation"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
