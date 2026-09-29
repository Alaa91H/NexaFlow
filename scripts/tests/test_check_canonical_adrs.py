from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_adrs import MUST_CONTAIN, REQUIRED


class CanonicalAdrsGateTest(unittest.TestCase):
    def test_fifteen_adrs_are_required(self) -> None:
        self.assertEqual(len(REQUIRED), 15)
        self.assertEqual(sorted(REQUIRED), list(range(1, 16)))
        for number, filename in REQUIRED.items():
            self.assertTrue(filename.startswith(f"ADR-{number:03d}-"))

    def test_every_adr_declares_binding_phrases(self) -> None:
        for number in REQUIRED:
            self.assertTrue(MUST_CONTAIN.get(number), f"ADR-{number:03d} has no binding phrases")

    def test_enum_values_parses_simple_enums(self) -> None:
        source = "enum class Sample {\n    TIME,\n    BATTERY,\n    ONLINE,\n}\n"
        self.assertEqual(enum_values(source, "Sample"), ["TIME", "BATTERY", "ONLINE"])

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_adrs as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
