from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_capability_resolver import (
    FORBIDDEN_PATTERNS,
    REQUIRED_CONSTRUCTS,
    REQUIRED_TEST_CASES,
)


class CanonicalCapabilityResolverGateTest(unittest.TestCase):
    def test_forbidden_patterns_detect_nondeterminism_and_silent_fallback(self) -> None:
        samples_by_pattern = {
            r"fallbackTo\w*\s*\(": (
                "spec.fallbackToDefault()",
                "resolver.fallbackTo(intent)",
            ),
            r"GlobalScope": ("GlobalScope.launch { }",),
            r"System\.currentTimeMillis\(\)": ("val now = System.currentTimeMillis()",),
            r" kotlin\.random\.Random\b": ("Random.nextInt(4)",),
        }
        self.assertEqual(set(samples_by_pattern), set(FORBIDDEN_PATTERNS))
        for pattern, samples in samples_by_pattern.items():
            for sample in samples:
                self.assertRegex(
                    sample, pattern, f"pattern {pattern!r} must match {sample!r}"
                )

    def test_gate_script_detects_missing_required_constructs(self) -> None:
        import scripts.check_canonical_capability_resolver as gate

        original = gate.REQUIRED_CONSTRUCTS
        gate.REQUIRED_CONSTRUCTS = ("ConstructThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_CONSTRUCTS = original

    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_capability_resolver as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_capability_resolver as gate

        self.assertEqual(gate.main(), 0)

    def test_required_test_cases_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_TEST_CASES,
            (
                "providerSelectionIsDeterministicAcrossRepeatedCalls",
                "privilegedProviderRequiresExplicitOptIn",
                "partialAvailabilityNeverUpgradesToExecutable",
                "unobservedBackendFailsClosed",
                "unsupportedProviderIsNeverSelectedAsSilentFallback",
                "unknownOperationIsUnsupportedNotGuessed",
                "pinnedBackendWinsOverRanking",
                "fallbackOrderFollowsRankingAfterSelection",
            ),
        )


if __name__ == "__main__":
    unittest.main()
