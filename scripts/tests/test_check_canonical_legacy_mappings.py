from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_legacy_mappings import REQUIRED_TEST_CASES


class CanonicalLegacyMappingsGateTest(unittest.TestCase):
    def test_gate_script_detects_missing_required_tests(self) -> None:
        import scripts.check_canonical_legacy_mappings as gate

        original = gate.REQUIRED_TEST_CASES
        gate.REQUIRED_TEST_CASES = ("testThatDoesNotExist",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_TEST_CASES = original

    def test_gate_script_detects_missing_generated_table(self) -> None:
        import scripts.check_canonical_legacy_mappings as gate

        original = gate.TABLE_FILE
        gate.TABLE_FILE = Path("nonexistent/LegacyMappingTable.kt")
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.TABLE_FILE = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_legacy_mappings as gate

        self.assertEqual(gate.main(), 0)

    def test_required_test_cases_are_declared(self) -> None:
        self.assertEqual(
            REQUIRED_TEST_CASES,
            (
                "tableCoversAll237AutomationTypes",
                "everyMappingReferencesRegisteredIdentities",
                "canonicalizationIsDeterministicAndIdempotent",
                "unknownInputStaysRejected",
                "legacyConfigRidesAlongUnconsumed",
                "pilotMappingsArePinned",
                "allMappingsAreFailClosedConsistent",
            ),
        )


if __name__ == "__main__":
    unittest.main()
