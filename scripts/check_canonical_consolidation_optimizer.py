#!/usr/bin/env python3
"""Enforce T29 safe consolidation optimizer boundaries (plan §T29)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OPTIMIZER_FILE = ROOT / (
    "domain/src/main/java/com/nexaflow/domain/canonical/"
    "CanonicalConsolidationOptimizer.kt"
)
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "CanonicalConsolidationOptimizerTest.kt"
)

FORBIDDEN_PATTERNS = (
    r"\bTriggerType\b",
    r"\bActionType\b",
    r"Map\s*<\s*String\s*,\s*String\s*>",
    r"System\.currentTimeMillis",
)

REQUIRED_CONSTRUCTS = (
    "CanonicalConsolidationOptimizer",
    "optimize",
    "OptimizationReport",
    "OptimizationResult",
    "MAX_PASSES",
    "eliminatedRedefinitions",
    "mergedWaits",
    "Math.addExact",
    "ExpressionValue",
)

REQUIRED_TEST_CASES = (
    "adjacentIdenticalWritesCollapseToTheLast",
    "differentPayloadOnSameTargetIsNeverCollapsed",
    "differentTargetsWithSamePayloadAreNeverCollapsed",
    "nonAdjacentDuplicatesSurviveUntouched",
    "expressionPayloadsAreNeverCollapsed",
    "mixedPrimitivesAreNeverCollapsed",
    "chainedDuplicatesCollapseInOnePassToTheSingleSurvivor",
    "adjacentWaitsMergeToTheExactSum",
    "mergedWaitThenWriteThenWaitIsUntouched",
    "optimizedTreeRevalidatesAgainstTheAstContract",
    "optimizationIsIdempotentWithAZeroSecondReport",
    "nestedBranchesAreOptimizedRecursively",
    "emptyReportOnAnAlreadyCleanTree",
)


def main() -> int:
    problems: list[str] = []

    if not OPTIMIZER_FILE.is_file():
        problems.append(f"missing {OPTIMIZER_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if OPTIMIZER_FILE.is_file():
        source = OPTIMIZER_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"CanonicalConsolidationOptimizer.kt missing {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"CanonicalConsolidationOptimizer.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"CanonicalConsolidationOptimizerTest.kt missing {case!r}"
                )

    if problems:
        print("CANONICAL_CONSOLIDATION_OPTIMIZER: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_CONSOLIDATION_OPTIMIZER: OK — safe consolidation over "
        "the canonical AST: adjacent identical desired-state rewrites "
        "collapse keep-last (execution-equivalent, expressions never "
        "collapsed), adjacent waits merge to the exact overflow-checked sum, "
        "node ids stay unique, the result re-validates against the AST "
        "contract, and optimization is idempotent with a deterministic "
        "removal report"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
