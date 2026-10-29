"""Tests for the module-boundary architecture gate (NFA-07)."""
from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "check_module_boundaries", ROOT / "scripts" / "check_module_boundaries.py"
)
assert SPEC and SPEC.loader
boundaries = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = boundaries
SPEC.loader.exec_module(boundaries)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class EdgeRuleTest(unittest.TestCase):
    def test_feature_cannot_depend_on_data_or_database(self):
        self.assertEqual(
            boundaries.edge_is_forbidden(":feature:settings", ":data"),
            "feature-to-persistence",
        )
        self.assertEqual(
            boundaries.edge_is_forbidden(":feature:history", ":core:database"),
            "feature-to-persistence",
        )

    def test_feature_may_depend_on_domain_and_core(self):
        self.assertIsNone(boundaries.edge_is_forbidden(":feature:history", ":domain"))
        self.assertIsNone(boundaries.edge_is_forbidden(":feature:history", ":core:logging"))

    def test_core_cannot_depend_on_feature_or_shells(self):
        self.assertEqual(
            boundaries.edge_is_forbidden(":core:execution", ":feature:dashboard"),
            "core-to-upper-layer",
        )
        self.assertIsNone(boundaries.edge_is_forbidden(":core:execution", ":core:common"))

    def test_datastore_cannot_depend_on_ai_runtime(self):
        self.assertEqual(
            boundaries.edge_is_forbidden(":core:datastore", ":core:ai-runtime"),
            "datastore-to-ai-runtime",
        )
        self.assertIsNone(boundaries.edge_is_forbidden(":core:ai-runtime", ":core:datastore"))


class AuditTest(unittest.TestCase):
    def test_new_violation_fails_and_allowlisted_edge_is_debt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write(
                root / "feature/dashboard/build.gradle.kts",
                'dependencies {\n    implementation(project(":data"))\n'
                '    implementation(project(":domain"))\n}\n',
            )
            write(
                root / "feature/settings/build.gradle.kts",
                'dependencies {\n    implementation(project(":data"))\n}\n',
            )
            violations, debt = boundaries.audit(
                root, {(":feature:settings", ":data")}
            )
        self.assertEqual(violations, [":feature:dashboard -> :data breaks feature-to-persistence"])
        self.assertEqual(debt, [":feature:settings -> :data (feature-to-persistence)"])

    def test_repository_tree_has_no_unallowlisted_violations(self):
        violations, _ = boundaries.audit(
            ROOT, boundaries.load_allowlist(ROOT / "config" / "module-boundaries.json")
        )
        self.assertEqual(violations, [])

    def test_allowlist_requires_reason(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "allow.json"
            path.write_text('{"allowed": [{"from": ":a", "to": ":b"}]}', encoding="utf-8")
            with self.assertRaises(SystemExit):
                boundaries.load_allowlist(path)


if __name__ == "__main__":
    unittest.main()
