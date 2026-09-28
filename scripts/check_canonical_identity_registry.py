#!/usr/bin/env python3
"""Cross-check T01 semantic review identities against the T03 Kotlin registry."""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from canonical_inventory_review import ACTION_REVIEWS, TRIGGER_REVIEWS  # noqa: E402

REGISTRY = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/CanonicalIdentityRegistry.kt"


def block_values(source: str, property_name: str, id_prefix: str) -> set[str]:
    start = source.find(f"val {property_name}:")
    if start < 0:
        raise RuntimeError(f"{property_name} registry block missing")
    end = source.find("\n    ).map", start)
    if end < 0:
        raise RuntimeError(f"{property_name} registry block end missing")
    block = source[start:end]
    values = re.findall(r'"([^"]+)"', block)
    filtered = {value for value in values if value.startswith(id_prefix)}
    if len(filtered) != len(values):
        raise RuntimeError(f"{property_name} contains malformed/non-{id_prefix} ids")
    if len(filtered) != len(values):
        raise RuntimeError(f"duplicate {property_name} identifiers")
    return filtered


def main() -> int:
    source = REGISTRY.read_text(encoding="utf-8")
    actual_targets = block_values(source, "targets", "")
    actual_operations = block_values(source, "operations", "core.operation.")
    actual_predicates = block_values(source, "predicates", "core.predicate.")

    expected_targets = {
        review.canonicalTarget
        for review in (*TRIGGER_REVIEWS.values(), *ACTION_REVIEWS.values())
    }
    expected_operations = {
        f"core.operation.{review.canonicalOperation.lower()}"
        for review in ACTION_REVIEWS.values()
    }
    # Existing semantic reads are part of the compatibility operation registry
    # even though T01 inventories persisted user-facing actions.
    expected_operations.update(
        {
            "core.operation.get_state",
            "core.operation.get_value",
            "core.operation.get_enabled",
        }
    )
    expected_predicates = {
        f"core.predicate.{review.canonicalOperation.lower()}"
        for review in TRIGGER_REVIEWS.values()
    }

    problems: list[str] = []
    for label, expected, actual in (
        ("targets", expected_targets, actual_targets),
        ("operations", expected_operations, actual_operations),
        ("predicates", expected_predicates, actual_predicates),
    ):
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        if missing or extra:
            problems.append(f"{label}: missing={missing}, extra={extra}")

    if problems:
        print("CANONICAL_IDENTITY_REGISTRY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_IDENTITY_REGISTRY: OK — "
        f"{len(actual_targets)} targets, "
        f"{len(actual_operations)} operations, "
        f"{len(actual_predicates)} predicates"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
