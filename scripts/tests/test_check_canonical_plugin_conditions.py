from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_plugin_conditions as gate
from scripts.check_canonical_plugin_conditions import (
    FORBIDDEN_PATTERNS,
    REQUIRED_CONSTRUCTS,
    REQUIRED_TEST_CASES,
)


class CanonicalPluginConditionsGateTest(unittest.TestCase):
    def test_required_constructs_cover_the_contract_surface(self) -> None:
        joined = "\n".join(REQUIRED_CONSTRUCTS)
        self.assertIn("PluginConditionContract", joined)
        self.assertIn("core.capability.plugin_condition_read", joined)
        self.assertIn("booleanVerdict", joined)
        self.assertIn("fromLocaleResultCode", joined)

    def test_forbidden_patterns_keep_the_contract_pure(self) -> None:
        joined = "|".join(FORBIDDEN_PATTERNS)
        self.assertIn("android", joined)
        self.assertIn("System", joined)

    def test_required_test_cases_pin_the_tri_state_rule(self) -> None:
        self.assertIn("onlyRealVerdictsProduceABoolean", REQUIRED_TEST_CASES)
        self.assertIn("unmappedResultCodesAreTypedErrorsNeverBooleans", REQUIRED_TEST_CASES)

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
