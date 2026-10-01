#!/usr/bin/env python3
"""Ratchet architectural hotspots while the atomic overhaul decomposes them."""
from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAX_NEW_KOTLIN_BYTES = 45_000

DIRECT_SCOPE_ALLOWLIST = {
    "app/src/main/java/com/nexaflow/app/NexaFlowWidgetProviders.kt",
    "wear/src/main/java/com/nexaflow/wear/data/WearDataListenerService.kt",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/di/CoroutinesModule.kt",
    "wear/src/main/java/com/nexaflow/wear/data/WearCapabilityPublisher.kt",
    "app/src/main/java/com/nexaflow/app/wear/WearSyncManager.kt",
    "app/src/main/java/com/nexaflow/app/wear/WearCommandListenerService.kt",
    "app/src/main/java/com/nexaflow/app/agent/NexaFlowAgentService.kt",
    "feature/widgets/src/main/java/com/nexaflow/feature/widgets/TaskTileService.kt",
    "core/execution/src/main/java/com/nexaflow/core/execution/task/TaskManager.kt",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/SensorMonitor.kt",
}

# Existing debt is frozen at the v3.91.3 baseline. These files may shrink,
# but they may never grow. Once a file falls below the generic ceiling it can
# be removed from this map.
BASELINE_HOTSPOTS = {
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt": 187_609,
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt": 161_621,
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt": 128_671,
    "core/rom-integration/src/main/java/com/nexaflow/core/rom/SystemController.kt": 99_683,
    "core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt": 83_641,
    "feature/automations/src/main/java/com/nexaflow/feature/automations/AutomationDetailsScreen.kt": 71_326,
    "domain/src/main/java/com/nexaflow/domain/canonical/LegacyMappingTable.kt": 63_734,
    "core/ui-components/src/main/java/com/nexaflow/core/ui/NexaFlowIcons.kt": 58_833,
    "feature/settings/src/main/java/com/nexaflow/feature/settings/AgentSettingsScreen.kt": 50_706,
    "feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsScreen.kt": 50_434,
    "feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/DashboardScreen.kt": 46_733,
    "core/execution/src/main/java/com/nexaflow/core/execution/TriggerStateEvaluator.kt": 46_187,
    "feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/RoutineCardDetails.kt": 45_904,
}

def is_production_kotlin(path: Path) -> bool:
    value = path.as_posix()
    return value.endswith(".kt") and "/src/main/" in f"/{value}" and "/build/" not in f"/{value}"

def collect_sizes(root: Path) -> dict[str, int]:
    return {
        path.relative_to(root).as_posix(): path.stat().st_size
        for path in root.rglob("*.kt")
        if is_production_kotlin(path.relative_to(root))
    }

def violations(sizes: dict[str, int]) -> list[str]:
    errors: list[str] = []
    for path, size in sizes.items():
        baseline = BASELINE_HOTSPOTS.get(path)
        if baseline is not None:
            if size > baseline:
                errors.append(f"hotspot grew: {path} {size}>{baseline}")
        elif size > MAX_NEW_KOTLIN_BYTES:
            errors.append(f"new oversized Kotlin file: {path} {size}>{MAX_NEW_KOTLIN_BYTES}")
    return sorted(errors)

def direct_scope_violations(root: Path) -> list[str]:
    errors: list[str] = []
    for path in root.rglob("*.kt"):
        relative = path.relative_to(root)
        if not is_production_kotlin(relative):
            continue
        value = relative.as_posix()
        text = path.read_text(encoding="utf-8")
        if "CoroutineScope(" in text and "rememberCoroutineScope(" not in text and value not in DIRECT_SCOPE_ALLOWLIST:
            errors.append(f"new unmanaged CoroutineScope: {value}")
    return sorted(errors)

def self_test() -> None:
    tiny = {"feature/x/src/main/Foo.kt": 1_000}
    assert violations(tiny) == []
    oversized = {"feature/x/src/main/Foo.kt": MAX_NEW_KOTLIN_BYTES + 1}
    assert "new oversized Kotlin file" in violations(oversized)[0]
    hotspot = next(iter(BASELINE_HOTSPOTS))
    assert violations({hotspot: BASELINE_HOTSPOTS[hotspot]}) == []
    assert "hotspot grew" in violations({hotspot: BASELINE_HOTSPOTS[hotspot] + 1})[0]

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Atomic architecture fitness self-test OK")
        return 0
    errors = violations(collect_sizes(ROOT)) + direct_scope_violations(ROOT)
    if errors:
        print("\n".join(f"ERROR: {item}" for item in errors))
        return 1
    print("Atomic architecture fitness OK")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
