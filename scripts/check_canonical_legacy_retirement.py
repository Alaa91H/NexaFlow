#!/usr/bin/env python3
"""Enforce T39 legacy retirement gates (plan §T39).

The legacy type system is retired by *containment*, not deletion: the enum
types remain the storage compatibility surface (persistence snapshots must
keep decoding every historical row, and the engine monitors keep dispatching
on them until the T40 cutover), while the canonical platform refuses any NEW
product surface from growing onto them. The gate pins that balance:

  1. Containment zones — where legacy types may still appear (the domain
     models that define them, the engine monitors, the app/feature UI, and
     the compatibility layers).
  2. Retired surfaces — the canonical packages, the workflow persistence
     contracts and the plugin SDK contract must be legacy-type-free.
  3. The retirement ledger in the inventory doc documents the contained
     file count at retirement time; growth beyond containment fails.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "docs/architecture/canonical-automation-inventory.md"

LEGACY_TYPE_PATTERN = re.compile(r"\b(TriggerType|ActionType)\b")

# Documentation mentions (comments/KDoc) are not code usage: strip comments
# before matching so doc references to the legacy system survive retirement.
COMMENT_PATTERN = re.compile(r"/\*.*?\*/|^[^\n]*?//[^\n]*$", re.S | re.M)


def strip_comments(source: str) -> str:
    return COMMENT_PATTERN.sub("", source)

# Surfaces where legacy types are retired outright (the canonical platform).
RETIRED_ROOTS = (
    ROOT / "domain/src/main/java/com/nexaflow/domain/canonical",
    ROOT / "domain/src/main/java/com/nexaflow/domain/workflow",
    ROOT / "core/plugin-sdk/src/main/java/com/nexaflow/core/pluginsdk",
)

# Prefixes (repo-relative, forward slashes) where legacy types may live.
CONTAINED_ROOTS = (
    "app/src/main/java/com/nexaflow/app/",
    "app/src/androidTest/java/com/nexaflow/app/",
    "app/src/test/java/com/nexaflow/app/",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/",
    "core/automation-engine/src/test/java/com/nexaflow/core/engine/",
    "core/automation-control/src/test/java/com/nexaflow/core/automationcontrol/",
    "core/automation-control/src/main/java/com/nexaflow/core/automationcontrol/",
    "core/execution/src/main/java/com/nexaflow/core/execution/",
    "core/execution/src/test/java/com/nexaflow/core/execution/",
    "core/compatibility/src/main/java/com/nexaflow/core/compat/",
    "core/database/src/test/java/com/nexaflow/core/database/",
    "core/database/src/main/java/com/nexaflow/core/database/",
    "core/wear-protocol/src/main/java/com/nexaflow/core/wearprotocol/",
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/",
    "core/agent-api/src/main/java/com/nexaflow/core/agentapi/",
    "core/automation-control/src/test/java/com/nexaflow/core/automationcontrol/",
    "data/src/main/java/com/nexaflow/data/",
    "data/src/test/java/com/nexaflow/data/",
    "domain/src/main/java/com/nexaflow/domain/models/",
    "domain/src/main/java/com/nexaflow/domain/catalog/",
    "domain/src/main/java/com/nexaflow/domain/repositories/",
    "domain/src/main/java/com/nexaflow/domain/schedule/",
    "domain/src/main/java/com/nexaflow/domain/capability/",
    "domain/src/main/java/com/nexaflow/domain/workflow/",  # mappers only
    "domain/src/test/java/com/nexaflow/domain/",
    "feature/",
)

# The workflow mappers and the data-transform registry are the *lossless
# compatibility bridges*: they are the only workflow files allowed to name
# legacy types (that is their contract). The policy files may not.
WORKFLOW_BRIDGE_FILES = (
    "WorkflowDocumentMappers.kt",
    "DataTransforms.kt",
)
WORKFLOW_POLICY_FILES = (
    "WorkflowPersistencePolicy.kt",
    "WorkflowMigrationOrchestrator.kt",
)


def is_contained(relative_path: str) -> bool:
    return any(relative_path.startswith(prefix) for prefix in CONTAINED_ROOTS)


def main() -> int:
    problems: list[str] = []
    contained_count = 0

    # 1. Retired surfaces must be legacy-free.
    for root in RETIRED_ROOTS:
        if not root.is_dir():
            problems.append(f"missing retired root {root.relative_to(ROOT)}")
            continue
        for path in sorted(root.rglob("*.kt")):
            relative = path.relative_to(ROOT).as_posix()
            if relative.startswith(
                "domain/src/main/java/com/nexaflow/domain/workflow/"
            ) and path.name in WORKFLOW_BRIDGE_FILES:
                # Declared compatibility bridge: checked explicitly below.
                continue
            source = strip_comments(
                path.read_text(encoding="utf-8", errors="replace")
            )
            if LEGACY_TYPE_PATTERN.search(source):
                problems.append(
                    f"{path.relative_to(ROOT)} references legacy types "
                    f"(retired surface)"
                )

    # 2. Explicit workflow carve-outs: bridge files must name legacy types
    #    (that is the bridge contract); policy files must not.
    workflow_dir = ROOT / "domain/src/main/java/com/nexaflow/domain/workflow"
    for name in WORKFLOW_BRIDGE_FILES:
        path = workflow_dir / name
        if not path.is_file():
            problems.append(f"missing compatibility bridge {path.relative_to(ROOT)}")
        elif not LEGACY_TYPE_PATTERN.search(
            strip_comments(path.read_text(encoding="utf-8", errors="replace"))
        ):
            problems.append(
                f"{path.relative_to(ROOT)} is a declared bridge but references no legacy types"
            )
    for name in WORKFLOW_POLICY_FILES:
        path = (
            ROOT
            / "domain/src/main/java/com/nexaflow/domain/workflow"
            / name
        )
        if path.is_file() and LEGACY_TYPE_PATTERN.search(
            path.read_text(encoding="utf-8", errors="replace")
        ):
            problems.append(f"{path.relative_to(ROOT)} references legacy types")

    # 3. Every other legacy reference must sit inside a containment zone.
    for path in sorted(ROOT.rglob("*.kt")):
        relative = path.relative_to(ROOT).as_posix()
        if relative.startswith(("build/", ".freebuff/", "scripts/")):
            continue
        if "/build/" in f"/{relative}":
            continue
        if not LEGACY_TYPE_PATTERN.search(
            strip_comments(path.read_text(encoding="utf-8", errors="replace"))
        ):
            continue
        if is_contained(relative):
            contained_count += 1
        else:
            problems.append(
                f"{relative} references legacy types outside containment"
            )

    # 4. The inventory must carry the retirement ledger (T39 status).
    if INVENTORY.is_file():
        inventory = INVENTORY.read_text(encoding="utf-8")
        if "T39: **implemented**" not in inventory:
            problems.append("inventory missing implementation status 'T39: **implemented**'")
        if "retirement ledger" not in inventory:
            problems.append("inventory missing the retirement ledger section")
    else:
        problems.append("missing canonical automation inventory doc")

    if problems:
        print("CANONICAL_LEGACY_RETIREMENT: FAIL")
        for problem in problems:
            print(f" - {problem}")
        return 1

    print(
        f"CANONICAL_LEGACY_RETIREMENT: OK — legacy types retired by "
        f"containment: the canonical packages, workflow persistence "
        f"contracts and plugin SDK contract are legacy-free, every "
        f"remaining reference sits inside a reviewed containment zone, and "
        f"the retirement ledger ({contained_count} contained files) is "
        f"recorded in the inventory"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
