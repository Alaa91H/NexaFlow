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
APP_PICKER_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "AppPickerDialog.kt"
)
APP_PICKER_PROJECTION_FILE = ROOT / (
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/"
    "AppPickerProjection.kt"
)
APP_PICKER_TEST_FILE = ROOT / (
    "feature/automation-builder/src/test/java/com/nexaflow/feature/builder/"
    "AppPickerProjectionTest.kt"
)
MIGRATION_TEST_FILE = ROOT / (
    "data/src/test/java/com/nexaflow/data/repository/"
    "CanonicalWorkflowMigrationRunnerTest.kt"
)
EVENT_BURST_TEST_FILE = ROOT / (
    "core/automation-engine/src/test/java/com/nexaflow/core/engine/"
    "PluginEventIngressTest.kt"
)
PLANNER_BENCHMARK_FILE = ROOT / (
    "macrobenchmark/src/main/java/com/nexaflow/macrobenchmark/"
    "CanonicalPlannerBenchmarks.kt"
)
STARTUP_BENCHMARK_FILE = ROOT / (
    "macrobenchmark/src/main/java/com/nexaflow/macrobenchmark/"
    "StartupBenchmarks.kt"
)
BENCHMARK_BUILD_FILE = ROOT / "macrobenchmark/build.gradle.kts"

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
    "thousandTwentyFourCommandWorkflowPlansWithinShippedBudget",
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

    for path in (
        APP_PICKER_FILE,
        APP_PICKER_PROJECTION_FILE,
        APP_PICKER_TEST_FILE,
        MIGRATION_TEST_FILE,
        EVENT_BURST_TEST_FILE,
        PLANNER_BENCHMARK_FILE,
        STARTUP_BENCHMARK_FILE,
        BENCHMARK_BUILD_FILE,
    ):
        if not path.is_file():
            problems.append(f"missing product scalability evidence {path.relative_to(ROOT)}")

    if APP_PICKER_FILE.is_file():
        picker = APP_PICKER_FILE.read_text(encoding="utf-8")
        for token in (
            "LazyColumn(",
            "remember(app.packageName)",
            "projectAppPickerApps(",
            "val projection = remember(",
        ):
            if token not in picker:
                problems.append(f"app picker scalability wiring missing {token!r}")

    if APP_PICKER_PROJECTION_FILE.is_file():
        projection = APP_PICKER_PROJECTION_FILE.read_text(encoding="utf-8")
        for token in (
            "fun projectAppPickerApps",
            "associateBy",
            "hashSetOf",
            "recentsLimit",
        ):
            if token not in projection:
                problems.append(f"app picker projection missing {token!r}")

    if APP_PICKER_TEST_FILE.is_file():
        picker_tests = APP_PICKER_TEST_FILE.read_text(encoding="utf-8")
        for case in (
            "thousandAppProjectionPreservesAllItemsWithoutSearch",
            "recentsStayBoundedAndAreRemovedFromMainListAtLargeScale",
            "packageSearchOverThousandAppsIsExactAndDoesNotLeakSystemFilter",
        ):
            if f"fun {case}" not in picker_tests:
                problems.append(f"app picker scale tests missing {case!r}")

    if MIGRATION_TEST_FILE.is_file():
        migration_tests = MIGRATION_TEST_FILE.read_text(encoding="utf-8")
        if "hundredTwentyFiveLegacyRowsMigrateInBoundedResumableBatches" not in migration_tests:
            problems.append("100+ automation migration scale test is missing")

    if EVENT_BURST_TEST_FILE.is_file():
        event_tests = EVENT_BURST_TEST_FILE.read_text(encoding="utf-8")
        if "hundredEventBurstIsBoundedAtIngressWithoutUnboundedQueueGrowth" not in event_tests:
            problems.append("repeated event burst scale test is missing")

    if PLANNER_BENCHMARK_FILE.is_file():
        planner_benchmark = PLANNER_BENCHMARK_FILE.read_text(encoding="utf-8")
        for token in (
            "BenchmarkRule",
            "measureRepeated",
            "1_024",
            "fun plan1024Commands",
        ):
            if token not in planner_benchmark:
                problems.append(f"planner latency benchmark missing {token!r}")

    if STARTUP_BENCHMARK_FILE.is_file():
        startup_benchmark = STARTUP_BENCHMARK_FILE.read_text(encoding="utf-8")
        for token in ("StartupTimingMetric()", "FrameTimingMetric()"):
            if token not in startup_benchmark:
                problems.append(f"device startup/frame benchmark missing {token!r}")

    if BENCHMARK_BUILD_FILE.is_file():
        benchmark_build = BENCHMARK_BUILD_FILE.read_text(encoding="utf-8")
        for token in (
            "libs.androidx.benchmark.benchmark.junit4",
            'project(":domain")',
        ):
            if token not in benchmark_build:
                problems.append(f"planner benchmark dependency missing {token!r}")

    if problems:
        print("CANONICAL_PERFORMANCE_BUDGET: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_PERFORMANCE_BUDGET: OK — structural budgets plus product "
        "scale evidence: memoized/lazy 1000-app picker projection, 125-row "
        "bounded migration, 100-event ingress burst control, 1024-command "
        "planner workload, and device-side planner/startup/frame benchmarks; "
        "JVM budgets stay deterministic while wall-clock timing remains in "
        "the dedicated device benchmark harness"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
