"""Fail when production Kotlin suppression annotations exceed the reviewed budget."""

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUDGET_PATH = ROOT / "config" / "detekt" / "suppression-budget.json"
SOURCE_ROOTS = ("app", "core", "data", "domain", "feature", "rom", "wear")
ANNOTATIONS = ("Suppress", "SuppressLint")


def count_annotations(source: str) -> dict[str, int]:
    return {
        name: len(re.findall(rf"@{name}\s*\(", source))
        for name in ANNOTATIONS
    }


def budget_violations(actual: dict[str, int], budget: dict[str, int]) -> list[str]:
    return [
        f"{name} count {actual.get(name, 0)} exceeds budget {limit}"
        for name, limit in budget.items()
        if actual.get(name, 0) > limit
    ]


def production_sources() -> list[Path]:
    return sorted(
        path
        for root_name in SOURCE_ROOTS
        for path in (ROOT / root_name).rglob("*.kt")
        if "src" in path.parts and "main" in path.parts
    )


def scan() -> dict[str, int]:
    totals = dict.fromkeys(ANNOTATIONS, 0)
    for path in production_sources():
        for name, count in count_annotations(path.read_text(encoding="utf-8")).items():
            totals[name] += count
    return totals


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--print-counts", action="store_true", help="print measured counts without enforcing the budget")
    args = parser.parse_args()
    actual = scan()
    if args.print_counts:
        print(json.dumps(actual, sort_keys=True))
        return 0
    budget = json.loads(BUDGET_PATH.read_text(encoding="utf-8"))
    violations = budget_violations(actual, budget)
    if violations:
        print("SUPPRESSION_BUDGET: FAIL")
        for violation in violations:
            print(f"- {violation}")
        return 1
    print(f"SUPPRESSION_BUDGET: OK {actual}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
