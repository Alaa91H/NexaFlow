from __future__ import annotations

import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.check_canonical_legacy_retirement import (
    CONTAINED_ROOTS,
    LEGACY_TYPE_PATTERN,
    RETIRED_ROOTS,
    WORKFLOW_POLICY_FILES,
    is_contained,
)


class CanonicalLegacyRetirementGateTest(unittest.TestCase):
    def test_retired_surfaces_exclude_containment_zones(self) -> None:
        # Retired surfaces are exactly the canonical platform packages; none
        # of them may double as containment zones.
        retired_prefixes = {root.name for root in RETIRED_ROOTS}
        self.assertIn("canonical", retired_prefixes)
        self.assertIn("workflow", retired_prefixes)
        self.assertIn("pluginsdk", retired_prefixes)

    def test_containment_zones_cover_engine_and_app(self) -> None:
        joined = "\n".join(CONTAINED_ROOTS)
        self.assertIn("core/automation-engine", joined)
        self.assertIn("app/src/main", joined)
        self.assertIn("domain/models", joined)

    def test_legacy_pattern_matches_both_type_names(self) -> None:
        self.assertTrue(LEGACY_TYPE_PATTERN.search("when (type) is TriggerType"))
        self.assertTrue(LEGACY_TYPE_PATTERN.search("ActionType.SYSTEM_WIFI"))
        self.assertFalse(LEGACY_TYPE_PATTERN.search("CanonicalActionType"))

    def test_policy_files_are_retired_not_contained(self) -> None:
        for name in WORKFLOW_POLICY_FILES:
            self.assertIn(name, ("WorkflowPersistencePolicy.kt", "WorkflowMigrationOrchestrator.kt"))
        # The workflow folder itself is contained (mappers), but the policy
        # files are explicitly re-checked as retired.
        self.assertTrue(is_contained("domain/src/main/java/com/nexaflow/domain/workflow/WorkflowDocumentMappers.kt"))

    def test_is_contained_rejects_canonical_paths(self) -> None:
        self.assertFalse(
            is_contained("domain/src/main/java/com/nexaflow/domain/canonical/OpenNode.kt")
        )
        self.assertFalse(
            is_contained("core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk/X.kt")
        )

    def test_gate_script_passes_on_current_tree(self) -> None:
        import scripts.check_canonical_legacy_retirement as gate

        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
