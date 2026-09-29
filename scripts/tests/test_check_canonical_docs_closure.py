from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_docs_closure as gate
from scripts.check_canonical_docs_closure import (
    REQUIRED_CONTRACT_ROWS,
    REQUIRED_GUIDE_SECTIONS,
)


class CanonicalDocsClosureGateTest(unittest.TestCase):
    def test_required_sections_cover_the_guide_skeleton(self) -> None:
        joined = "\n".join(REQUIRED_GUIDE_SECTIONS)
        self.assertIn("Standing guarantees", joined)
        self.assertIn("Verification (the gates)", joined)
        self.assertIn("## Status", joined)

    def test_contract_rows_span_the_platform(self) -> None:
        self.assertIn("T26", REQUIRED_CONTRACT_ROWS)
        self.assertIn("T41", REQUIRED_CONTRACT_ROWS)

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
