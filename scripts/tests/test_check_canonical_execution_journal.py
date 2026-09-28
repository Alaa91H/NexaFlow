from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_execution_journal import (
    FORBIDDEN_PATTERNS,
    REQUIRED_CONSTRUCTS,
    REQUIRED_ERROR_CODES,
    REQUIRED_TEST_CASES,
)


class CanonicalExecutionJournalGateTest(unittest.TestCase):
    def test_forbidden_patterns_detect_runtime_and_leak_sources(self) -> None:
        samples_by_pattern = {
            r"\bContext\b": ("fun sweep(context: Context)",),
            r"androidx\.compose": ("import androidx.compose.runtime.Immutable",),
            r"Throwable": ("val cause: Throwable? = null",),
            r"printStackTrace": ("e.printStackTrace()",),
        }
        self.assertEqual(set(samples_by_pattern), set(FORBIDDEN_PATTERNS))
        for pattern, samples in samples_by_pattern.items():
            for sample in samples:
                self.assertRegex(
                    sample, pattern, f"pattern {pattern!r} must match {sample!r}"
                )

    def test_error_code_list_matches_the_plan_contract(self) -> None:
        self.assertEqual(
            REQUIRED_ERROR_CODES,
            (
                "INVALID_CONFIGURATION",
                "UNSUPPORTED",
                "PERMISSION_MISSING",
                "CAPABILITY_MISSING",
                "SECURITY_REJECTED",
                "TIMEOUT",
                "TRANSIENT_FAILURE",
                "PROVIDER_UNAVAILABLE",
                "CANCELLED",
                "CONFLICT",
                "MIGRATION_FAILED",
            ),
        )

    def test_gate_script_detects_missing_required_constructs(self) -> None:
        import scripts.check_canonical_execution_journal as gate

        original = gate.REQUIRED_CONSTRUCTS
        gate.REQUIRED_CONSTRUCTS = ("ConstructThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_CONSTRUCTS = original

    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_execution_journal as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_execution_journal as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
