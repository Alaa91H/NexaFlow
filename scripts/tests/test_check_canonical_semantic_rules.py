from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_semantic_rules import (
    FORBIDDEN_PATTERNS,
    REQUIRED_CONSTRUCTS,
    REQUIRED_TEST_CASES,
)


class CanonicalSemanticRulesGateTest(unittest.TestCase):
    def test_forbidden_patterns_detect_name_heuristics(self) -> None:
        samples_by_pattern = {
            r"parse.*legacy.*(?:action|trigger)type": (
                "parseLegacyActionType(raw)",
                "parse legacy triggerType payload",
            ),
            r"\.name\.lowercase\(\)": (
                "val key = node.name.lowercase()",
            ),
            r"\.name\.contains\(": (
                "check(x.name.contains(\"WIFI\"))",
            ),
        }
        self.assertEqual(set(samples_by_pattern), set(FORBIDDEN_PATTERNS))
        for pattern, samples in samples_by_pattern.items():
            for sample in samples:
                self.assertRegex(
                    sample, pattern, f"pattern {pattern!r} must match {sample!r}"
                )

    def test_gate_script_detects_missing_required_constructs(self) -> None:
        import scripts.check_canonical_semantic_rules as gate

        original = gate.REQUIRED_CONSTRUCTS
        gate.REQUIRED_CONSTRUCTS = ("ConstructThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_CONSTRUCTS = original

    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_semantic_rules as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_semantic_rules as gate

        self.assertEqual(gate.main(), 0)

    def test_violations_carry_stable_rule_names(self) -> None:
        import scripts.check_canonical_semantic_rules as gate

        rules_source = Path(gate.RULES_FILE).read_text(encoding="utf-8")
        # The violation contract exposes a stable machine-readable rule name
        # (for diagnostics UI and golden test pinning) next to the message.
        self.assertIn("sealed interface SemanticRuleViolation", rules_source)
        self.assertLessEqual(
            rules_source.count("val rule: String"), 1,
            msg="rule-name property must be declared once on the contract",
        )
        self.assertIn("val rule: String", rules_source)

    def test_required_test_cases_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_TEST_CASES,
            (
                "contradictoryStateAssertionsAreDetected",
                "eventAllWithProvenMutuallyExclusivePredicatesFails",
                "eventAllWithoutProvenExclusivityFailsClosed",
                "duplicateConflictingWritesInOneBatchAreDetected",
                "writesAcrossAWaitNeverConflict",
                "writesOnOppositeBranchSidesNeverConflict",
                "expressionWriteInsideConflictingScopeFailsClosed",
                "ruleEvaluationIsDeterministic",
            ),
        )


if __name__ == "__main__":
    unittest.main()
