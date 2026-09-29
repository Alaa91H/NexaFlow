from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_identity_registry import block_values


class CanonicalIdentityRegistryGateTest(unittest.TestCase):
    def test_block_values_extracts_prefixed_ids(self) -> None:
        source = (
            "val operations: Set<String> = setOf(\n"
            '    "core.operation.wifi",\n'
            '    "core.operation.bluetooth",\n'
            "    ).map { it }\n"
        )
        self.assertEqual(
            block_values(source, "operations", "core.operation."),
            {"core.operation.wifi", "core.operation.bluetooth"},
        )

    def test_block_values_rejects_non_prefixed_ids(self) -> None:
        source = (
            "val operations: Set<String> = setOf(\n"
            '    "core.operation.wifi",\n'
            '    "rogue-id",\n'
            "    ).map { it }\n"
        )
        with self.assertRaises(RuntimeError):
            block_values(source, "operations", "core.operation.")

    def test_block_values_fails_when_block_missing(self) -> None:
        with self.assertRaises(RuntimeError):
            block_values("val other = 1", "operations", "core.operation.")

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_identity_registry as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
