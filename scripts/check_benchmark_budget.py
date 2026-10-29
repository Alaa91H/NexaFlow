#!/usr/bin/env python3
"""Startup budget gate over androidx.benchmark JSON output (NFA-12).

`connectedBenchmarkAndroidTest` writes `<test>-benchmarkData.json` next to the
test results. Its ``benchmarks[].metrics`` object carries
``{"minimum": …, "median": …, "maximum": …}`` per metric. This gate compares
the median of each budgeted metric with the ceiling in
``config/benchmark-budget.json`` so a startup regression fails the nightly run
instead of being discovered by users.

Budgets are provisional ceilings until ten nightly runs establish the p95;
they may only ratchet down. A run that produced no report is a configuration
error, not a pass, so the gate also fails when the report is missing.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BUDGET = ROOT / "config" / "benchmark-budget.json"


def load_reports(paths: list[Path]) -> list[dict]:
    reports = []
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        for benchmark in data.get("benchmarks", []):
            reports.append(benchmark)
    return reports


def evaluate(benchmarks: list[dict], budget: dict) -> list[str]:
    ceilings = budget.get("metrics", {})
    failures: list[str] = []
    for benchmark in benchmarks:
        name = benchmark.get("name", "<unnamed>")
        metrics = benchmark.get("metrics", {})
        for metric, ceiling in ceilings.items():
            value = metrics.get(metric)
            if value is None:
                continue
            median = value.get("median")
            if median is None:
                continue
            print(f"BENCHMARK: {name}.{metric} median={median:.1f} ceiling={ceiling}")
            if median > ceiling:
                failures.append(f"{name}.{metric} median {median:.1f} > {ceiling}")
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--budget", default=str(DEFAULT_BUDGET))
    parser.add_argument(
        "--report",
        action="append",
        help="Benchmark JSON file or directory (repeatable). Defaults to the macrobenchmark output tree.",
    )
    args = parser.parse_args(argv)

    targets = args.report or [
        str(ROOT / "macrobenchmark/build/outputs/connected_android_test_additional_output")
    ]
    paths: list[Path] = []
    for target in targets:
        path = Path(target)
        if path.is_dir():
            paths.extend(sorted(path.rglob("*-benchmarkData.json")))
        elif path.is_file():
            paths.append(path)
    if not paths:
        print("BENCHMARK: no benchmarkData.json found — refusing to pass")
        return 1

    budget = json.loads(Path(args.budget).read_text(encoding="utf-8"))
    failures = evaluate(load_reports(paths), budget)
    for failure in failures:
        print(f"BENCHMARK: FAIL — {failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
