#!/usr/bin/env python3
"""Compose stability budget gate (NFA-13).

The Compose compiler writes one JSON report per variant under
``<module>/build/compose-metrics/<variant>/<module>-module.json``. Until now
nothing read it, so a change could turn skippable composables into restarted
ones (or stable classes into unstable ones) without anyone noticing.

This gate compares each module's report with the reviewed budget in
``config/compose-stability-budget.json``:

* ``maxUnstableClasses`` — inferred unstable classes may not grow.
* ``maxNonSkippableComposables`` — total minus skippable may not grow.

Budgets only ratchet down. A module without a budget entry fails unless
``--allow-missing`` is passed, which keeps new UI modules from silently
skipping the gate.

Usage:
    python3 scripts/check_compose_stability.py
    python3 scripts/check_compose_stability.py --metrics app/build/compose-metrics
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUDGET_FILE = ROOT / "config" / "compose-stability-budget.json"
METRICS_GLOB = "*/build/compose-metrics/*/*-module.json"


def module_of(metrics_file: Path, root: Path) -> str:
    """"app/build/compose-metrics/debug/x.json" -> "app" (keeps nesting)."""
    relative = metrics_file.relative_to(root) if metrics_file.is_absolute() else metrics_file
    parts = relative.parts
    build_index = parts.index("build")
    return "/".join(parts[:build_index])


def read_metrics(metrics_files: list[Path], root: Path) -> dict[str, dict[str, int]]:
    """Keeps the worst variant per module so debug/release cannot hide debt."""
    summary: dict[str, dict[str, int]] = {}
    for file in metrics_files:
        module = module_of(file, root)
        data = json.loads(file.read_text(encoding="utf-8"))
        total = int(data.get("totalComposables", 0))
        skippable = int(data.get("skippableComposables", 0))
        unstable = int(data.get("inferredUnstableClasses", 0)) + int(
            data.get("inferredUncertainClasses", 0)
        )
        entry = summary.setdefault(
            module,
            {"maxUnstableClasses": 0, "maxNonSkippableComposables": 0, "total": 0},
        )
        entry["maxUnstableClasses"] = max(entry["maxUnstableClasses"], unstable)
        entry["maxNonSkippableComposables"] = max(
            entry["maxNonSkippableComposables"], total - skippable
        )
        entry["total"] = max(entry["total"], total)
    return summary


def evaluate(
    summary: dict[str, dict[str, int]],
    budget: dict,
    allow_missing: bool,
) -> list[str]:
    failures: list[str] = []
    modules = budget.get("modules", {})
    for module, measured in sorted(summary.items()):
        limit = modules.get(module)
        if limit is None:
            message = f"{module}: no stability budget (unstable={measured['maxUnstableClasses']}, non-skippable={measured['maxNonSkippableComposables']})"
            if allow_missing:
                print(f"COMPOSE_STABILITY: {message} — report-only")
            else:
                failures.append(message)
            continue
        unstable_limit = int(limit["maxUnstableClasses"])
        non_skippable_limit = int(limit["maxNonSkippableComposables"])
        print(
            f"COMPOSE_STABILITY: {module} unstable {measured['maxUnstableClasses']}/{unstable_limit}, "
            f"non-skippable {measured['maxNonSkippableComposables']}/{non_skippable_limit} "
            f"(composables={measured['total']})"
        )
        if measured["maxUnstableClasses"] > unstable_limit:
            failures.append(
                f"{module}: unstable classes {measured['maxUnstableClasses']} > {unstable_limit}"
            )
        if measured["maxNonSkippableComposables"] > non_skippable_limit:
            failures.append(
                f"{module}: non-skippable composables "
                f"{measured['maxNonSkippableComposables']} > {non_skippable_limit}"
            )
    return failures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(ROOT))
    parser.add_argument("--metrics", action="append", help="Explicit metrics directory")
    parser.add_argument("--budget", default=str(BUDGET_FILE))
    parser.add_argument("--allow-missing", action="store_true")
    args = parser.parse_args(argv)
    root = Path(args.root)

    if args.metrics:
        files: list[Path] = []
        for directory in args.metrics:
            files.extend(sorted(Path(directory).glob("*/*-module.json")))
    else:
        files = sorted(root.glob(METRICS_GLOB))
    if not files:
        print("COMPOSE_STABILITY: no metrics found — run a Compose compile first")
        return 0

    budget = json.loads(Path(args.budget).read_text(encoding="utf-8"))
    summary = read_metrics(files, root)
    failures = evaluate(summary, budget, args.allow_missing)
    for failure in failures:
        print(f"COMPOSE_STABILITY: FAIL — {failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
