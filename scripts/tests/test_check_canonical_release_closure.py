from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

import scripts.check_canonical_release_closure as gate
from scripts.check_canonical_release_closure import (
    REQUIRED_CI_MARKERS,
    REQUIRED_RELEASING_PHRASES,
)


class CanonicalReleaseClosureGateTest(unittest.TestCase):
    def test_tagging_rules_are_pinned(self) -> None:
        joined = "\n".join(REQUIRED_RELEASING_PHRASES)
        self.assertIn("created explicitly", joined)
        self.assertIn("Never move", joined)

    def test_tag_hygiene_stays_scoped_to_version_tags(self) -> None:
        joined = "\n".join(REQUIRED_CI_MARKERS)
        self.assertIn("refs/tags/", joined)
        self.assertIn("tag-changelog", joined)

    def test_gate_script_passes_on_current_tree(self) -> None:
        self.assertEqual(gate.main(), 0)


if __name__ == "__main__":
    unittest.main()
