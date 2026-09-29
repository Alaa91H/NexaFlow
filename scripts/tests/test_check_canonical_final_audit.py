from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_final_audit as gate
from scripts.check_canonical_final_audit import canonical_gates


class CanonicalFinalAuditGateTest(unittest.TestCase):
    def test_audit_discovers_gates_and_excludes_itself(self) -> None:
        gates = canonical_gates()
        self.assertIn("check_canonical_runtime_cutover", gates)
        self.assertIn("check_canonical_legacy_retirement", gates)
        self.assertIn("check_canonical_release_readiness", gates)
        self.assertNotIn(gate.SELF, gates)

    def test_audit_fails_on_an_unknown_inventory_phase(self) -> None:
        # T01/T02 have no closure statuses in the inventory (the record
        # starts at T03), so lowering the first phase must fail the audit.
        original = gate.FIRST_PHASE
        gate.FIRST_PHASE = 1
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.FIRST_PHASE = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
