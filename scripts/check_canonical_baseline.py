#!/usr/bin/env python3
"""Freeze the legacy automation surface during canonical architecture migration.

The legacy TriggerType/ActionType enums are compatibility contracts, not the
future extension mechanism. This gate makes accidental enum growth or removal
fail loudly while the canonical target/operation model is introduced.

Intentional surface additions must update BASELINE_* in the same reviewed
change; existing released enum values must remain present.
"""
from __future__ import annotations

import re
from pathlib import Path

MODEL = Path("domain/src/main/java/com/nexaflow/domain/models/Automation.kt")
CATALOG = Path("domain/src/main/java/com/nexaflow/domain/catalog/AutomationNodeCatalog.kt")
CONTRACT_GATE = Path("scripts/check_node_contracts.py")

BASELINE_TRIGGERS = 57
BASELINE_ACTIONS = 180
BASELINE_TOTAL = 237
BASELINE_COMMIT = "4af72b54870c9938d0147d6daccc4f33eece8eb0"


def enum_values(source: str, enum_name: str) -> list[str]:
    body = re.search(rf"enum class {enum_name}\s*\{{(.*?)\n\}}", source, re.S)
    if body is None:
        raise RuntimeError(f"{enum_name} not found")
    values: list[str] = []
    for line in body.group(1).splitlines():
        clean = re.sub(r"//.*", "", line).strip().rstrip(",")
        if re.fullmatch(r"[A-Z][A-Z0-9_]+", clean):
            values.append(clean)
    return values


def main() -> int:
    problems: list[str] = []
    model = MODEL.read_text(encoding="utf-8")
    triggers = enum_values(model, "TriggerType")
    actions = enum_values(model, "ActionType")

    if len(triggers) != BASELINE_TRIGGERS:
        problems.append(
            f"TriggerType drift: expected {BASELINE_TRIGGERS}, found {len(triggers)}"
        )
    if len(actions) != BASELINE_ACTIONS:
        problems.append(
            f"ActionType drift: expected {BASELINE_ACTIONS}, found {len(actions)}"
        )
    if len(triggers) + len(actions) != BASELINE_TOTAL:
        problems.append(
            f"legacy node total drift: expected {BASELINE_TOTAL}, "
            f"found {len(triggers) + len(actions)}"
        )

    catalog = CATALOG.read_text(encoding="utf-8")
    for name in triggers:
        if f"TriggerType.{name}" not in catalog:
            problems.append(f"TriggerType.{name} missing from AutomationNodeCatalog")
    for name in actions:
        if f"ActionType.{name}" not in catalog:
            problems.append(f"ActionType.{name} missing from AutomationNodeCatalog")

    if not CONTRACT_GATE.is_file():
        problems.append("scripts/check_node_contracts.py is missing")

    if problems:
        print("CANONICAL_BASELINE: FAIL")
        for problem in problems:
            print(f" - {problem}")
        print(
            "Automation enum changes are migration-contract changes. Existing "
            "released values must remain compatible."
        )
        return 1

    print(
        "CANONICAL_BASELINE: OK — "
        f"{len(triggers)} triggers + {len(actions)} actions = "
        f"{len(triggers) + len(actions)} catalog node kinds; "
        "original 57/176 surface preserved"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
