from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_selection_semantics import (
    FORBIDDEN_PATTERNS,
    REQUIRED_CONSTRUCTS,
    REQUIRED_TEST_CASES,
)


class CanonicalSelectionSemanticsGateTest(unittest.TestCase):
    def test_forbidden_patterns_detect_semantic_overloads(self) -> None:
        samples_by_pattern = {
            r"enum\s+class\s+\w*(?:AnyOrAll|AnyAll)\w*": (
                "enum class AnyOrAllSemantics { ANY, ALL }",
                "enum class SelectionAnyAll { X }",
            ),
            r"\?\s*:\s*(?:ExecutionMode|FailurePolicy|ConditionLogic|EventLogic)\.\w+": (
                "executionMode ?: ExecutionMode.SINGLE",
                "failurePolicy ?: FailurePolicy.FAIL_FAST",
                "conditionLogic ?: ConditionLogic.ALL",
                "eventLogic ?: EventLogic.ANY_OF",
            ),
        }
        self.assertEqual(set(samples_by_pattern), set(FORBIDDEN_PATTERNS))
        for pattern, samples in samples_by_pattern.items():
            for sample in samples:
                self.assertRegex(
                    sample, pattern, f"pattern {pattern!r} must match {sample!r}"
                )

    def test_gate_script_detects_missing_required_constructs(self) -> None:
        import scripts.check_canonical_selection_semantics as gate

        original = gate.REQUIRED_CONSTRUCTS
        gate.REQUIRED_CONSTRUCTS = ("ConstructThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_CONSTRUCTS = original

    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_selection_semantics as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_selection_semantics as gate

        self.assertEqual(gate.main(), 0)

    def test_required_test_cases_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_TEST_CASES,
            (
                "multiSelectWithoutExecutionModeFailsClosed",
                "batchContradictoryWritesFailClosed",
                "multiSelectWithoutFailurePolicyFailsClosed",
                "validMultiSelectOrderedPasses",
            ),
        )

    def test_required_constructs_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_CONSTRUCTS,
            (
                "TargetSelectionMode",
                "EventLogic",
                "ConditionLogic",
                "ExecutionMode",
                "FailurePolicy",
                "NodeSelectionSemantics",
                "OperationCardinality",
                "validateSelectionSemantics",
                "requireValidSelectionSemantics",
            ),
        )


if __name__ == "__main__":
    unittest.main()
