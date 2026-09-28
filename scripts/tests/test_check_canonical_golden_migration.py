from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_golden_migration import (
    FORBIDDEN_PATTERNS,
    REQUIRED_TEST_CASES,
)


class CanonicalGoldenMigrationGateTest(unittest.TestCase):
    def test_forbidden_patterns_detect_skipped_goldens(self) -> None:
        samples_by_pattern = {
            r"assumeTrue": ("assumeTrue(device.isEmulator)",),
            r"Assert\.assertThrows\(\s*Exception\.class": (
                "Assert.assertThrows(Exception.class, () -> run())",
            ),
        }
        self.assertEqual(set(samples_by_pattern), set(FORBIDDEN_PATTERNS))
        for pattern, samples in samples_by_pattern.items():
            for sample in samples:
                self.assertRegex(
                    sample, pattern, f"pattern {pattern!r} must match {sample!r}"
                )

    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_golden_migration as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_detects_missing_suite_file(self) -> None:
        import scripts.check_canonical_golden_migration as gate

        original = gate.TEST_FILE
        gate.TEST_FILE = Path("nonexistent/GoldenMigrationSuiteTest.kt")
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.TEST_FILE = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_golden_migration as gate

        self.assertEqual(gate.main(), 0)

    def test_required_test_cases_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_TEST_CASES,
            (
                "goldenContractExistsAndIsValidForEachOfThe233Mappings",
                "goldenOutputIsPinnedToTheReviewedIdentity",
                "migrateIsIdempotentAcrossTheWholeTable",
                "triggerAndActionGoldenSplitsMatchTheBaseline",
                "canonicalizedNodesRoundTripThroughSerialization",
                "noGoldenUsesAnUnregisteredIdentity",
            ),
        )


if __name__ == "__main__":
    unittest.main()
