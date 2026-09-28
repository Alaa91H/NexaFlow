#!/usr/bin/env python3
"""Enforce T05 selection-semantics architecture boundaries (ADR-003)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

SEMANTICS_FILE = DOMAIN_CANONICAL / "SelectionSemantics.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "SelectionSemanticsTest.kt"
)

# ADR-003 decision: the five semantic dimensions stay separate. A combined
# ANY/ALL flag spanning targets/events/execution would reintroduce the
# ambiguity T05 removes.
FORBIDDEN_PATTERNS = (
    # Single enum carrying several semantic dimensions at once.
    r"enum\s+class\s+\w*(?:AnyOrAll|AnyAll)\w*",
    # Hidden runtime defaults: undeclared semantics silently falling back to a
    # concrete mode/policy (the "no hidden runtime defaults" invariant).
    r"\?\s*:\s*(?:ExecutionMode|FailurePolicy|ConditionLogic|EventLogic)\.\w+",
)

REQUIRED_CONSTRUCTS = (
    "TargetSelectionMode",
    "EventLogic",
    "ConditionLogic",
    "ExecutionMode",
    "FailurePolicy",
    "NodeSelectionSemantics",
    "OperationCardinality",
    "validateSelectionSemantics",
    "requireValidSelectionSemantics",
)

REQUIRED_TEST_CASES = (
    "multiSelectWithoutExecutionModeFailsClosed",
    "batchContradictoryWritesFailClosed",
    "multiSelectWithoutFailurePolicyFailsClosed",
    "validMultiSelectOrderedPasses",
)


def main() -> int:
    problems: list[str] = []

    if not SEMANTICS_FILE.is_file():
        problems.append(f"missing {SEMANTICS_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if SEMANTICS_FILE.is_file():
        source = SEMANTICS_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"SelectionSemantics.kt missing required T05 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source):
                problems.append(
                    f"SelectionSemantics.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"SelectionSemanticsTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_SELECTION_SEMANTICS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SELECTION_SEMANTICS: OK — "
        "explicit SINGLE/MULTI, ANY/ALL, ANY_OF, BATCH/ORDERED and failure "
        "policies enforced fail-closed with ADR-003 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
