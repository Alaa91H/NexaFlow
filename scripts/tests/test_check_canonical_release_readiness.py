from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_release_readiness import (
    READINESS_GATES,
    REQUIRED_INVENTORY_STATUSES,
)


class CanonicalReleaseReadinessGateTest(unittest.TestCase):
    def test_readiness_covers_every_t26_plus_gate(self) -> None:
        for gate in (
            "check_canonical_runtime_cutover",
            "check_canonical_persistence_policy",
            "check_canonical_migration_rollout",
            "check_canonical_consolidation_optimizer",
            "check_canonical_diagnostics_model",
            "check_canonical_plugin_sdk",
            "check_canonical_fault_injection",
            "check_canonical_performance_budget",
            "check_canonical_security_auditor",
            "check_canonical_accessibility_model",
            "check_canonical_device_matrix",
            "check_canonical_architecture_fitness",
        ):
            self.assertIn(gate, READINESS_GATES)
        self.assertGreaterEqual(len(READINESS_GATES), 24)

    def test_inventory_statuses_cover_every_t26_plus_phase(self) -> None:
        phases = {status.split(":")[0] for status in REQUIRED_INVENTORY_STATUSES}
        for phase in ("T26", "T27", "T28", "T29", "T30", "T31", "T32", "T33", "T34", "T35", "T36", "T37"):
            self.assertIn(phase, phases)

    def test_gate_script_detects_a_missing_inventory_status(self) -> None:
        import scripts.check_canonical_release_readiness as gate

        original = gate.REQUIRED_INVENTORY_STATUSES
        gate.REQUIRED_INVENTORY_STATUSES = original + ("T99: **implemented**",)
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.REQUIRED_INVENTORY_STATUSES = original

    def test_gate_script_detects_a_missing_changelog_section(self) -> None:
        import scripts.check_canonical_release_readiness as gate

        original = gate.CHANGELOG
        gate.CHANGELOG = Path("nonexistent/CHANGELOG.md")
        try:
            self.assertEqual(gate.main(), 1)
        finally:
            gate.CHANGELOG = original

    def test_gate_script_passes_on_current_tree(self) -> None:
        # The real aggregate: every canonical gate runs against this tree.
        import scripts.check_canonical_release_readiness as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
