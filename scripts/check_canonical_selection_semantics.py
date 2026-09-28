#!/usr/bin/env python3
"""Enforce T05 selection/combination semantic boundaries."""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from canonical_inventory_review import ACTION_REVIEWS, TRIGGER_REVIEWS  # noqa: E402

SELECTION_FILE = (
    ROOT
    / "domain/src/main/java/com/nexaflow/domain/canonical/CanonicalSelectionSemantics.kt"
)
AST_FILE = ROOT / "domain/src/main/java/com/nexaflow/domain/canonical/CanonicalAst.kt"

EXPECTED_ENUMS = {
    "TargetSelectionMode": {"SINGLE", "MULTI"},
    "EventLogic": {"ANY_OF"},
    "ConditionLogic": {"ANY", "ALL"},
    "ExecutionMode": {"SINGLE", "BATCH", "ORDERED"},
    "FailurePolicy": {
        "FAIL_FAST",
        "CONTINUE_ON_ERROR",
        "ROLLBACK_WHEN_SUPPORTED",
    },
}

TRIGGER_COMBINATIONS = {"N/A", "ANY", "ALL", "ANY_OF"}
ACTION_COMBINATIONS = {"N/A", "BATCH", "ORDERED"}


def enum_values(source: str, name: str) -> set[str]:
    match = re.search(rf"enum class {name}\s*\{{(.*?)\n\}}", source, re.S)
    if not match:
        raise RuntimeError(f"{name} enum missing")
    return {
        token
        for token in re.findall(r"\b[A-Z][A-Z0-9_]+\b", match.group(1))
    }


def main() -> int:
    problems: list[str] = []
    if not SELECTION_FILE.is_file():
        problems.append("CanonicalSelectionSemantics.kt is missing")
        source = ""
    else:
        source = SELECTION_FILE.read_text(encoding="utf-8")

    for enum_name, expected in EXPECTED_ENUMS.items():
        try:
            actual = enum_values(source, enum_name)
        except RuntimeError as exc:
            problems.append(str(exc))
            continue
        if actual != expected:
            problems.append(
                f"{enum_name} vocabulary drift: expected={sorted(expected)}, "
                f"actual={sorted(actual)}"
            )

    required_constructs = (
        "ActionSelectionSemantics",
        "EventSelectionNode",
        "ConditionGroupNode",
        "ActionSelectionNode",
        "MAX_SELECTION_ITEMS",
    )
    for token in required_constructs:
        if token not in source:
            problems.append(f"T05 construct missing: {token}")

    forbidden_coupling = (
        "androidx.compose",
        "com.nexaflow.core.execution",
        "com.nexaflow.feature.",
        "TriggerType",
        "ActionType",
    )
    code = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    code = re.sub(r"//.*", "", code)
    for token in forbidden_coupling:
        if token in code:
            problems.append(f"selection semantics couples to forbidden token {token!r}")

    for name, review in TRIGGER_REVIEWS.items():
        if review.selectionMode not in EXPECTED_ENUMS["TargetSelectionMode"]:
            problems.append(f"trigger {name}: unknown selectionMode={review.selectionMode}")
        if review.combinationMode not in TRIGGER_COMBINATIONS:
            problems.append(
                f"trigger {name}: action-only combinationMode={review.combinationMode}"
            )

    for name, review in ACTION_REVIEWS.items():
        if review.selectionMode not in EXPECTED_ENUMS["TargetSelectionMode"]:
            problems.append(f"action {name}: unknown selectionMode={review.selectionMode}")
        if review.combinationMode not in ACTION_COMBINATIONS:
            problems.append(
                f"action {name}: trigger-only combinationMode={review.combinationMode}"
            )

    if AST_FILE.is_file():
        ast = AST_FILE.read_text(encoding="utf-8")
        for primitive in (
            "ACTION_SELECTION",
            "EVENT_SELECTION",
            "CONDITION_GROUP",
        ):
            if primitive not in ast:
                problems.append(f"CanonicalPrimitive missing {primitive}")
        for branch in (
            "is ActionSelectionNode",
            "is EventSelectionNode",
            "is ConditionGroupNode",
        ):
            if branch not in ast:
                problems.append(f"AST validator does not traverse {branch}")
    else:
        problems.append("CanonicalAst.kt is missing")

    if problems:
        print("CANONICAL_SELECTION_SEMANTICS: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        "CANONICAL_SELECTION_SEMANTICS: OK — "
        "SINGLE/MULTI, ANY/ALL, ANY_OF, BATCH/ORDERED boundaries enforced "
        "and T01 review vocabulary is representable"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
