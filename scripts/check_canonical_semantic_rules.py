#!/usr/bin/env python3
"""Enforce T06 semantic-rules architecture boundaries (plan §10, §46, Gate D)."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOMAIN_CANONICAL = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical"

RULES_FILE = DOMAIN_CANONICAL / "NodeSemanticRules.kt"
TEST_FILE = ROOT / (
    "domain/src/test/java/com/nexaflow/domain/canonical/"
    "NodeSemanticRulesTest.kt"
)

# plan §46 hard rules that T06 enforces in code:
#  - 46.6: no new semantic types without registry declarations
#  - 46.12: ALL is forbidden for groups that cannot coincide
#  - 46.13: contradictory batches are forbidden
#  - "no hidden runtime defaults": undeclared semantics fail closed
FORBIDDEN_PATTERNS = (
    # Name-based heuristics would reintroduce guesswork into the rules engine.
    r"parse.*legacy.*(?:action|trigger)type",
    r"\.name\.lowercase\(\)",
    r"\.name\.contains\(",
)

REQUIRED_CONSTRUCTS = (
    "NodeSemanticRules",
    "EventPredicateRule",
    "WriteOperationRule",
    "evaluateStateConditions",
    "evaluateEventGroup",
    "evaluateWriteConflicts",
    "validateWriteValueRequirements",
    "evaluateSemanticRules",
    "ContradictoryStateConditions",
    "EventAllOnMutuallyExclusivePredicates",
    "EventAllRequiresProof",
    "DuplicateConflictingWrites",
    "UnprovableWriteConflict",
    "UnregisteredEventPredicate",
    "UnregisteredWriteOperation",
    "WriteValueRequired",
)

REQUIRED_TEST_CASES = (
    "contradictoryStateAssertionsAreDetected",
    "eventAllWithProvenMutuallyExclusivePredicatesFails",
    "eventAllWithoutProvenExclusivityFailsClosed",
    "duplicateConflictingWritesInOneBatchAreDetected",
    "writesAcrossAWaitNeverConflict",
    "writesOnOppositeBranchSidesNeverConflict",
    "expressionWriteInsideConflictingScopeFailsClosed",
    "ruleEvaluationIsDeterministic",
)


def main() -> int:
    problems: list[str] = []

    if not RULES_FILE.is_file():
        problems.append(f"missing {RULES_FILE.relative_to(ROOT)}")
    if not TEST_FILE.is_file():
        problems.append(f"missing {TEST_FILE.relative_to(ROOT)}")

    if RULES_FILE.is_file():
        source = RULES_FILE.read_text(encoding="utf-8")
        for token in REQUIRED_CONSTRUCTS:
            if token not in source:
                problems.append(
                    f"NodeSemanticRules.kt missing required T06 construct {token!r}"
                )
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, source, re.IGNORECASE):
                problems.append(
                    f"NodeSemanticRules.kt matches forbidden pattern {pattern!r}"
                )

    if TEST_FILE.is_file():
        test_source = TEST_FILE.read_text(encoding="utf-8")
        for case in REQUIRED_TEST_CASES:
            if f"fun {case}" not in test_source:
                problems.append(
                    f"NodeSemanticRulesTest.kt missing required test {case!r}"
                )

    if problems:
        print("CANONICAL_SEMANTIC_RULES: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SEMANTIC_RULES: OK — "
        "contradictory states, mutually exclusive event ALL, duplicate "
        "conflicting writes and fail-closed unknown declarations enforced "
        "with mandatory T06 unit coverage"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
