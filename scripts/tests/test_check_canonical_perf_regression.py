from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_perf_regression as gate
from scripts.check_canonical_perf_regression import (
    FORBIDDEN_PATTERNS,
    REQUIRED_PROBES,
    REQUIRED_TEST_CASES,
)


class CanonicalPerfRegressionGateTest(unittest.TestCase):
    def test_forbidden_patterns_pin_determinism(self) -> None:
        joined = "|".join(FORBIDDEN_PATTERNS)
        self.assertIn("currentTimeMillis", joined)
        self.assertIn("Random", joined)
        self.assertIn("Clock", joined)

    def test_probes_cover_the_worst_case_families(self) -> None:
        # Depth limit, fan-out limit, and optimizer churn.
        self.assertEqual(
            set(REQUIRED_PROBES),
            {"maxDepthSpine", "wideFanOut", "optimizerChurn"},
        )

    def test_required_tests_pin_a_typed_regression(self) -> None:
        self.assertIn("anActualRegressionIsTypedWithBaselineAndObservation", REQUIRED_TEST_CASES)

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
