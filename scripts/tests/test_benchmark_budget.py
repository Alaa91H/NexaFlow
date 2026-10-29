"""Tests for the macrobenchmark budget gate (NFA-12)."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "check_benchmark_budget", ROOT / "scripts" / "check_benchmark_budget.py"
)
assert SPEC and SPEC.loader
budget_gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = budget_gate
SPEC.loader.exec_module(budget_gate)


def benchmark(name: str, startup_ms: float) -> dict:
    return {
        "name": name,
        "metrics": {
            "startupMs": {"minimum": startup_ms - 5, "median": startup_ms, "maximum": startup_ms + 5}
        },
    }


class EvaluateTest(unittest.TestCase):
    def test_under_ceiling_passes(self):
        failures = budget_gate.evaluate(
            [benchmark("startup", 900.0)], {"metrics": {"startupMs": 2000.0}}
        )
        self.assertEqual(failures, [])

    def test_over_ceiling_fails(self):
        failures = budget_gate.evaluate(
            [benchmark("startup", 2500.0)], {"metrics": {"startupMs": 2000.0}}
        )
        self.assertEqual(len(failures), 1)
        self.assertIn("startup.startupMs", failures[0])

    def test_unbudgeted_metric_is_ignored(self):
        entry = benchmark("startup", 5000.0)
        entry["metrics"]["frameDurationCpuMs"] = {"median": 99.0}
        self.assertEqual(budget_gate.evaluate([entry], {"metrics": {"startupMs": 6000.0}}), [])


class CliTest(unittest.TestCase):
    def test_missing_report_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            code = budget_gate.main(["--report", directory])
        self.assertEqual(code, 1)

    def test_report_file_is_loaded(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "x-benchmarkData.json"
            report.write_text(json.dumps({"benchmarks": [benchmark("startup", 2100.0)]}))
            code = budget_gate.main(["--report", str(report)])
        self.assertEqual(code, 1)


if __name__ == "__main__":
    unittest.main()
