#!/usr/bin/env python3
"""Build/check the source-derived T01 legacy automation inventory.

The inventory is intentionally generated from source contracts instead of a
hand-maintained 233-row table. Review-only canonical fields remain UNREVIEWED
until a human/source audit assigns semantics, canonical target/operation,
capability and side-effect metadata.

Usage:
    python3 scripts/generate_canonical_inventory.py
    python3 scripts/generate_canonical_inventory.py --check
    python3 scripts/generate_canonical_inventory.py --json
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import check_node_contracts as contracts  # noqa: E402
from canonical_inventory_review import ACTION_REVIEWS, TRIGGER_REVIEWS  # noqa: E402

MODEL = ROOT / "domain/src/main/java/com/nexaflow/domain/models/Automation.kt"
CATALOG = ROOT / "domain/src/main/java/com/nexaflow/domain/catalog/AutomationNodeCatalog.kt"
HANDLERS = ROOT / "core/execution/src/main/java/com/nexaflow/core/execution/handler"

TRIGGER_RUNTIME_ROOTS = (
    ROOT / "core/automation-engine/src/main/java",
    ROOT / "core/execution/src/main/java",
    ROOT / "domain/src/main/java",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def extract_family_block(source: str, variable: str) -> str:
    marker = f"private val {variable}:"
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"{variable} not found in AutomationNodeCatalog")
    start = source.find("strictFamilyMap", start)
    if start < 0:
        raise RuntimeError(f"{variable} strictFamilyMap not found")
    end = source.find("\n    ) }", start)
    if end < 0:
        raise RuntimeError(f"{variable} strictFamilyMap end not found")
    return source[start:end]


def family_map(source: str, variable: str, enum_prefix: str) -> dict[str, str]:
    block = extract_family_block(source, variable)
    out: dict[str, str] = {}
    groups = re.finditer(
        r"AutomationNodeFamily\.([A-Z_]+)\s+to\s+listOf\((.*?)\n\s*\)",
        block,
        re.S,
    )
    for match in groups:
        family = match.group(1)
        for name in re.findall(rf"{enum_prefix}\.([A-Z0-9_]+)", match.group(2)):
            if name in out:
                raise RuntimeError(f"{enum_prefix}.{name} assigned twice")
            out[name] = family
    return out


def action_runtime_owners(action_names: list[str]) -> dict[str, list[str]]:
    owners = {name: [] for name in action_names}
    for raw_path in glob.glob(str(HANDLERS / "*.kt")):
        path = Path(raw_path)
        source = read(path)
        # Handlers may declare constructor parameters before ': ActionHandler'.
        # This directory keeps one concrete handler class per handler source file.
        if ": ActionHandler" not in source:
            continue
        class_match = re.search(r"class\s+([A-Za-z0-9_]+)", source)
        if not class_match:
            continue
        owner = class_match.group(1)
        for name in action_names:
            if f"ActionType.{name}" in source and owner not in owners[name]:
                owners[name].append(owner)
        if "supportedTypes = DataTransforms.operations.keys" in source:
            for name in action_names:
                if name.startswith("DATA_") and owner not in owners[name]:
                    owners[name].append(owner)
    return owners


def trigger_runtime_owners(trigger_names: list[str]) -> dict[str, list[str]]:
    owners = {name: [] for name in trigger_names}
    kotlin_files: list[Path] = []
    for root in TRIGGER_RUNTIME_ROOTS:
        if root.exists():
            kotlin_files.extend(root.rglob("*.kt"))
    for path in kotlin_files:
        # Catalog/editor/model declarations are not runtime owners.
        p = str(path).replace("\\", "/")
        if "/catalog/" in p or p.endswith("/models/Automation.kt"):
            continue
        source = read(path)
        for name in trigger_names:
            if f"TriggerType.{name}" in source:
                label = str(path.relative_to(ROOT))
                if label not in owners[name]:
                    owners[name].append(label)
    return owners


def semantic_hint(kind: str, name: str) -> str:
    if kind == "TRIGGER":
        if name == "TIME" or "CALENDAR" in name:
            return "SCHEDULE"
        if name.endswith("_CHANGED") or name in {"SMS", "WEBHOOK", "BOOT_COMPLETED", "APP_INSTALLED", "PLUGIN_EVENT"}:
            return "EVENT_OR_TRANSITION"
        if any(token in name for token in ("LEVEL", "STRENGTH", "TEMPERATURE", "STORAGE_LOW")):
            return "THRESHOLD"
        return "STATE_OR_EVENT_REVIEW_REQUIRED"
    if name.startswith(("SYSTEM_OPEN_", "APPLICATION_LAUNCH")):
        return "OPEN_OR_INVOKE"
    if name.startswith("DATA_"):
        return "TRANSFORM"
    if name in {"SYSTEM_WAIT"}:
        return "WAIT"
    if name.startswith("SYSTEM_SEND_") or name == "SYSTEM_HTTP_REQUEST":
        return "SEND"
    return "STATE_VALUE_OR_INVOKE_REVIEW_REQUIRED"


def build_inventory() -> list[dict[str, object]]:
    model = read(MODEL)
    catalog_source = read(CATALOG)
    triggers = contracts.enum_names(model, "TriggerType")
    actions = contracts.enum_names(model, "ActionType")

    trigger_families = family_map(catalog_source, "triggerFamilies", "TriggerType")
    action_families = family_map(catalog_source, "actionFamilies", "ActionType")

    trigger_schema = contracts.trigger_schema_keys(triggers)
    action_schema = contracts.action_schema_keys(actions)
    trigger_runtime = contracts.trigger_runtime_keys(triggers)
    action_runtime = contracts.action_runtime_keys(actions)
    action_owners = action_runtime_owners(actions)
    trigger_owners = trigger_runtime_owners(triggers)

    rows: list[dict[str, object]] = []
    for kind, names, families, schemas, runtime, owners, reviews in (
        ("TRIGGER", triggers, trigger_families, trigger_schema, trigger_runtime, trigger_owners, TRIGGER_REVIEWS),
        ("ACTION", actions, action_families, action_schema, action_runtime, action_owners, ACTION_REVIEWS),
    ):
        expected = set(names)
        reviewed_names = set(reviews)
        missing_reviews = sorted(expected - reviewed_names)
        unknown_reviews = sorted(reviewed_names - expected)
        if missing_reviews or unknown_reviews:
            raise RuntimeError(
                f"{kind} semantic-review coverage mismatch: "
                f"missing={missing_reviews}, unknown={unknown_reviews}"
            )
        for name in names:
            review = reviews[name].as_dict()
            rows.append(
                {
                    "legacyType": name,
                    "kind": kind,
                    "currentFamily": families.get(name),
                    "schemaKeys": sorted(schemas.get(name, set())),
                    "runtimeKeys": sorted(runtime.get(name, set())),
                    "runtimeOwners": sorted(owners.get(name, [])),
                    "semanticHint": semantic_hint(kind, name),
                    **review,
                    "goldenTestId": f"legacy_{kind.lower()}_{name.lower()}",
                }
            )
    return rows


def validate(rows: list[dict[str, object]]) -> list[str]:
    problems: list[str] = []
    triggers = [row for row in rows if row["kind"] == "TRIGGER"]
    actions = [row for row in rows if row["kind"] == "ACTION"]
    if len(triggers) != 57:
        problems.append(f"expected 57 trigger rows, found {len(triggers)}")
    if len(actions) != 176:
        problems.append(f"expected 176 action rows, found {len(actions)}")
    if len(rows) != 233:
        problems.append(f"expected 233 total rows, found {len(rows)}")

    identities = [(row["kind"], row["legacyType"]) for row in rows]
    if len(set(identities)) != len(identities):
        problems.append("duplicate legacy inventory identities")

    for row in rows:
        label = f'{row["kind"]}:{row["legacyType"]}'
        if not row["currentFamily"]:
            problems.append(f"{label} has no current family")
        runtime = set(row["runtimeKeys"])
        schema = set(row["schemaKeys"])
        if not runtime.issubset(schema):
            problems.append(
                f"{label} runtime keys outside schema: {sorted(runtime - schema)}"
            )
        if row["kind"] == "ACTION" and not row["runtimeOwners"]:
            problems.append(f"{label} has no discovered ActionHandler owner")
        if row["reviewStatus"] != "REVIEWED":
            problems.append(f"{label} semantic review is not closed")
        for required in ("canonicalTarget", "canonicalOperation", "selectionMode",
                         "combinationMode", "sideEffect", "idempotency",
                         "retrySafety", "goldenTestId"):
            value = row.get(required)
            if value is None or (isinstance(value, str) and not value.strip()):
                problems.append(f"{label} missing reviewed field {required}")

    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    rows = build_inventory()
    problems = validate(rows)

    if args.json:
        print(json.dumps({"rows": rows}, indent=2, sort_keys=False))

    if problems:
        print("CANONICAL_INVENTORY: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    reviewed = sum(row["reviewStatus"] == "REVIEWED" for row in rows)
    print(
        "CANONICAL_INVENTORY: REVIEW COVERAGE OK — "
        f"{len(rows)} rows (57 triggers, 176 actions), "
        f"{reviewed}/233 semantically reviewed"
    )
    if args.check:
        return 0
    if not args.json:
        for row in rows:
            print(
                f'{row["kind"]:7} {row["legacyType"]:42} '
                f'family={row["currentFamily"]:14} '
                f'owners={len(row["runtimeOwners"])}'
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
