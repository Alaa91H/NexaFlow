from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_architecture_fitness import (
    CANONICAL_FORBIDDEN_IMPORTS,
    PLUGINSDK_CONTRACT_FORBIDDEN,
    REQUIRED_GATES,
    WORKFLOW_FORBIDDEN,
)


class CanonicalArchitectureFitnessGateTest(unittest.TestCase):
    def test_required_gates_cover_the_platform_family(self) -> None:
        self.assertIn("check_canonical_persistence_policy", REQUIRED_GATES)
        self.assertIn("check_canonical_runtime_cutover", REQUIRED_GATES)
        self.assertIn("check_canonical_device_matrix", REQUIRED_GATES)
        # The T26+ family: eleven gates plus this fitness gate.
        self.assertGreaterEqual(len(REQUIRED_GATES), 10)

    def test_forbidden_import_patterns_are_declared(self) -> None:
        joined = "\\n".join(CANONICAL_FORBIDDEN_IMPORTS)
        for expected in ("android", "androidx", "nexaflow"):
            self.assertIn(expected, joined)

    def test_pluginsdk_contract_forbids_android_and_domain(self) -> None:
        joined = "\n".join(PLUGINSDK_CONTRACT_FORBIDDEN)
        self.assertIn("import\\s+android\\.", joined)
        self.assertIn("import\\s+com\\.nexaflow\\.domain\\.", joined)

    def test_workflow_forbidden_patterns_detect_nondeterminism(self) -> None:
        samples = {
            r"System\.currentTimeMillis": "val now = System.currentTimeMillis()",
            r"kotlin\.random\.Random": "import kotlin.random.Random",
            r"java\.time\.Clock": "import java.time.Clock",
        }
        for pattern, sample in samples.items():
            self.assertIn(pattern, WORKFLOW_FORBIDDEN)
            self.assertRegex(sample, pattern)

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_architecture_fitness as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
