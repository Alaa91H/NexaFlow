"""Tests for the Compose stability budget gate (NFA-13)."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "check_compose_stability", ROOT / "scripts" / "check_compose_stability.py"
)
assert SPEC and SPEC.loader
stability = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = stability
SPEC.loader.exec_module(stability)


def write_metrics(root: Path, module: str, variant: str, **counts: int) -> Path:
    directory = root / module / "build" / "compose-metrics" / variant
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{module.replace('/', '-')}-module.json"
    path.write_text(json.dumps(counts), encoding="utf-8")
    return path


class ReadMetricsTest(unittest.TestCase):
    def test_module_path_and_worst_variant_are_used(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            debug = write_metrics(
                root,
                "feature/ai",
                "debug",
                totalComposables=10,
                skippableComposables=4,
                inferredUnstableClasses=2,
                inferredUncertainClasses=1,
            )
            write_metrics(
                root,
                "feature/ai",
                "release",
                totalComposables=12,
                skippableComposables=10,
                inferredUnstableClasses=1,
                inferredUncertainClasses=0,
            )
            summary = stability.read_metrics([debug], root)
        self.assertEqual(set(summary), {"feature/ai"})
        self.assertEqual(summary["feature/ai"]["maxUnstableClasses"], 3)
        self.assertEqual(summary["feature/ai"]["maxNonSkippableComposables"], 6)


class EvaluateTest(unittest.TestCase):
    summary = {
        "app": {"maxUnstableClasses": 3, "maxNonSkippableComposables": 2, "total": 9}
    }

    def test_within_budget_passes(self):
        failures = stability.evaluate(
            self.summary,
            {"modules": {"app": {"maxUnstableClasses": 3, "maxNonSkippableComposables": 2}}},
            allow_missing=False,
        )
        self.assertEqual(failures, [])

    def test_growth_fails(self):
        failures = stability.evaluate(
            self.summary,
            {"modules": {"app": {"maxUnstableClasses": 2, "maxNonSkippableComposables": 6}}},
            allow_missing=False,
        )
        self.assertEqual(len(failures), 1)
        self.assertIn("unstable classes", failures[0])

    def test_missing_budget_fails_unless_allowed(self):
        self.assertEqual(len(stability.evaluate(self.summary, {"modules": {}}, False)), 1)
        self.assertEqual(stability.evaluate(self.summary, {"modules": {}}, True), [])


class BudgetFileTest(unittest.TestCase):
    def test_repository_budget_covers_measured_modules(self):
        budget = json.loads(
            (ROOT / "config" / "compose-stability-budget.json").read_text(encoding="utf-8")
        )
        self.assertIn("app", budget["modules"])
        for limit in budget["modules"].values():
            self.assertGreaterEqual(limit["maxUnstableClasses"], 0)
            self.assertGreaterEqual(limit["maxNonSkippableComposables"], 0)


if __name__ == "__main__":
    unittest.main()
