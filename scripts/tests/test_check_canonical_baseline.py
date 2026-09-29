from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_baseline as gate
from scripts.check_canonical_baseline import BASELINE_TOTAL, enum_values


class CanonicalBaselineGateTest(unittest.TestCase):
    FROZEN_MODEL = (
        "enum class TriggerType {\n"
        "    TIME,\n"
        "    BATTERY,\n"
        "    ONLINE,\n"
        "}\n"
        "enum class ActionType {\n"
        "    SYSTEM_WIFI,\n"
        "    POWER_TOGGLE,\n"
        "}\n"
    )

    def test_frozen_surface_totals(self) -> None:
        self.assertEqual(BASELINE_TOTAL, 233)

    def test_enum_values_ignores_comments_and_annotations(self) -> None:
        source = (
            "enum class Sample {\n"
            "    AAA, // trailing comment\n"
            "    /** doc */\n"
            "    BBB,\n"
            "    lowercase_not_an_enum_entry,\n"
            "}\n"
        )
        self.assertEqual(enum_values(source, "Sample"), ["AAA", "BBB"])

    def test_gate_detects_enum_drift(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            model = Path(tmp) / "Automation.kt"
            model.write_text(self.FROZEN_MODEL, encoding="utf-8")
            original = gate.MODEL
            gate.MODEL = model
            try:
                values = enum_values(
                    model.read_text(encoding="utf-8"), "TriggerType"
                )
            finally:
                gate.MODEL = original
        self.assertEqual(values, ["TIME", "BATTERY", "ONLINE"])

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
