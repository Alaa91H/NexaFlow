from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_typed_model import strip_kotlin_comments


class CanonicalTypedModelGateTest(unittest.TestCase):
    def test_strip_kotlin_comments_removes_block_and_line_comments(self) -> None:
        source = (
            "/** KDoc mentioning TriggerType. */\n"
            "val a = 1 // line comment with ActionType\n"
            "val b = 2\n"
        )
        stripped = strip_kotlin_comments(source)
        self.assertNotIn("TriggerType", stripped)
        self.assertNotIn("ActionType", stripped)
        self.assertIn("val a = 1", stripped)
        self.assertIn("val b = 2", stripped)

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_typed_model as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
