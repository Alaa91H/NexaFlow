#!/usr/bin/env python3
"""Generate reproducible, evidence-labelled trigger/action source inventories.

The output reports static source declarations and config-key parity. It does not
claim that a catalog entry works on a particular Android device or OEM.
"""
from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from functools import lru_cache
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import check_node_contracts as contracts  # noqa: E402

MODEL = Path("domain/src/main/java/com/nexaflow/domain/models/Automation.kt")
TRIGGER_SCHEMA = Path("domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt")
ACTION_SCHEMA = Path("domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt")
CATALOG = Path("domain/src/main/java/com/nexaflow/domain/catalog/AutomationNodeCatalog.kt")
TRANSFORMS = Path("domain/src/main/java/com/nexaflow/domain/workflow/DataTransforms.kt")
TRIGGER_RUNTIME = Path("core/execution/src/main/java/com/nexaflow/core/execution/TriggerStateEvaluator.kt")
ONE_SHOT = Path("core/automation-engine/src/main/java/com/nexaflow/core/engine/DeviceOneShotTriggerMatcher.kt")
TRIGGER_ROOT = Path("core/automation-engine/src/main/java/com/nexaflow/core/engine")
ACTION_ROOT = Path("core/execution/src/main/java/com/nexaflow/core/execution/handler")
PICKER_TRIGGER = Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerCatalogPresentation.kt")
PICKER_ACTION = Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder/BuilderActionCatalog.kt")
CHURN_ANCHOR = "4199f4a1108b60cab05b3df017a10b2d24a0e2d8"

FIELD_CALL = re.compile(
    r"\b(stringField|packageField|urlField|secretField|jsonField|coordinateField|"
    r"dateField|timeField|durationField|integerField|decimalField|booleanField|enumField)"
    r"\(\s*\"([A-Za-z_][A-Za-z0-9_]*)\"([^)]*)\)", re.S
)


@lru_cache(maxsize=None)
def read(root: Path, relative: Path) -> str:
    return (root / relative).read_text(encoding="utf-8")


def stable_paths(paths: Iterable[Path]) -> list[Path]:
    """Sort paths with identical POSIX semantics on Windows and Linux."""
    return sorted(paths, key=lambda path: (path.as_posix().casefold(), path.as_posix()))


def enum_values(source: str, name: str) -> list[str]:
    match = re.search(rf"enum class {re.escape(name)}\s*\{{(.*?)\n\}}", source, re.S)
    if not match:
        raise ValueError(f"enum class {name} was not found")
    values = []
    for line in match.group(1).splitlines():
        line = re.sub(r"//.*", "", line).strip().rstrip(",")
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", line):
            values.append(line)
    if not values or len(values) != len(set(values)):
        raise ValueError(f"enum class {name} is empty or contains duplicates")
    return values


def schema_fields(root: Path, relative: Path, enum_name: str, values: list[str]) -> dict[str, list[str]]:
    return schema_fields_from_source(read(root, relative), enum_name, values)


def schema_fields_from_source(source: str, enum_name: str, values: list[str]) -> dict[str, list[str]]:
    arms = contracts.split_enum_arms(contracts.when_block(source, "type"), enum_name)
    result = {}
    for value in values:
        body = arms.get(value, "")
        result[value] = list(dict.fromkeys(match.group(2) for match in FIELD_CALL.finditer(body)))
    return result


def schema_field_details(root: Path, relative: Path, enum_name: str, values: list[str]) -> dict[str, dict[str, str]]:
    source = read(root, relative)
    arms = contracts.split_enum_arms(contracts.when_block(source, "type"), enum_name)
    out: dict[str, dict[str, str]] = {}
    for value in values:
        fields: dict[str, str] = {}
        for match in FIELD_CALL.finditer(arms.get(value, "")):
            builder, key, args = match.groups()
            required = "required=true" if re.search(r"\brequired\s*=\s*true", args) else "required=false-or-derived"
            sensitive = "sensitive=true" if builder == "secretField" or re.search(r"\bsensitive\s*=\s*true", args) else "sensitive=false-or-derived"
            default = re.search(r'\bdefault\s*=\s*"([^"\\]*(?:\\.[^"\\]*)*)"', args)
            default_text = f"default={default.group(1)}" if default else "default=helper-or-none"
            fields[key] = f"{builder};{required};{sensitive};{default_text}"
        out[value] = fields
    return out


def csv_write(path: Path, fields: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


@lru_cache(maxsize=None)
def referenced_files(root: Path, base: Path, token: str) -> list[str]:
    found = []
    if not base.is_dir():
        return found
    for path in stable_paths(base.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        if re.search(rf"\b{re.escape(token)}\b", source):
            found.append(path.relative_to(root).as_posix())
    return found


@lru_cache(maxsize=None)
def field_node_index(root: Path, base: Path, kind: str) -> dict[str, list[Path]]:
    index: dict[str, list[Path]] = {}
    if not base.is_dir():
        return index
    for path in stable_paths(base.rglob("*.kt")):
        source = read(root, path.relative_to(root))
        for node in set(re.findall(rf"\b{kind.title()}Type\.([A-Z_]+)", source)):
            index.setdefault(node, []).append(path)
    return index


@lru_cache(maxsize=None)
def field_source_candidates(root: Path, base: Path, kind: str, node: str, key: str) -> list[str]:
    return [
        path.relative_to(root).as_posix()
        for path in field_node_index(root, base, kind).get(node, [])
        if re.search(rf"[\"']{re.escape(key)}[\"']", read(root, path.relative_to(root)))
    ]


@lru_cache(maxsize=None)
def test_node_index(root: Path, kind: str) -> dict[str, list[Path]]:
    index: dict[str, list[Path]] = {}
    for base in stable_paths(root.rglob("src/test")):
        if not base.is_dir():
            continue
        for path in stable_paths(base.rglob("*.kt")):
            source = read(root, path.relative_to(root))
            for node in set(re.findall(rf"\b{kind.title()}Type\.([A-Z_]+)", source)):
                index.setdefault(node, []).append(path)
    return index


@lru_cache(maxsize=None)
def test_source_candidates(root: Path, kind: str, node: str, key: str) -> list[str]:
    return [
        path.relative_to(root).as_posix()
        for path in test_node_index(root, kind).get(node, [])
        if re.search(rf"[\"']{re.escape(key)}[\"']", read(root, path.relative_to(root)))
    ]


def operation_map(root: Path) -> dict[str, list[str]]:
    return operation_map_from_source(read(root, TRANSFORMS))


def operation_map_from_source(source: str) -> dict[str, list[str]]:
    return {
        name: re.findall(r'"([^"\n]+)"', operations)
        for name, operations in re.findall(
            r"ActionType\.([A-Z_]+)\s*to\s*listOf\(([^)]*)\)", source, re.S
        )
    }


def ranked_hotspots(root: Path) -> list[dict[str, str]]:
    source_roots = [
        Path("domain/src/main/java/com/nexaflow/domain"),
        Path("feature/automation-builder/src/main/java/com/nexaflow/feature/builder"),
        Path("core/automation-engine/src/main/java/com/nexaflow/core/engine"),
        Path("core/execution/src/main/java/com/nexaflow/core/execution"),
        Path("data/src/main/java/com/nexaflow/data"),
        Path("core/database/src/main/java/com/nexaflow/core/database"),
    ]
    files = stable_paths({path for base in source_roots for path in (root / base).rglob("*.kt")})
    churn_output = subprocess.run(
        ["git", "log", CHURN_ANCHOR, "-n", "50", "--name-only", "--format="],
        cwd=root, check=True, capture_output=True, text=True, encoding="utf-8",
    ).stdout
    churn: dict[str, int] = {}
    for line in churn_output.splitlines():
        path = line.strip().replace("\\", "/")
        if path:
            churn[path] = churn.get(path, 0) + 1

    metrics = []
    for path in files:
        relative = path.relative_to(root).as_posix()
        source = read(root, path.relative_to(root))
        branch_count = len(re.findall(r"\b(?:if|when|for|while|catch)\b", source))
        cross_module_imports = len(set(re.findall(r"^import com\.nexaflow\.(?:core|data|domain|feature)\.[^\n]+", source, re.M)))
        type_references = len(set(re.findall(r"\b(?:TriggerType|ActionType)\.([A-Z_]+)", source)))
        coupling = cross_module_imports + type_references
        file_churn = churn.get(relative, 0)
        evidence = f"branch_tokens={branch_count};cross_module_imports={cross_module_imports};enum_refs={type_references};commits_in_last_50_before_{CHURN_ANCHOR[:7]}={file_churn}"
        metrics.append({
            "path": relative, "complexity": branch_count, "coupling": coupling,
            "churn": file_churn, "evidence": evidence,
        })
    maxima = {
        key: max((int(row[key]) for row in metrics), default=1) or 1
        for key in ("complexity", "coupling", "churn")
    }
    for row in metrics:
        row["score"] = f"{sum(int(row[key]) / maxima[key] for key in maxima):.6f}"
    ranked = sorted(metrics, key=lambda row: (-float(row["score"]), row["path"]))[:50]
    for rank, row in enumerate(ranked, 1):
        row["rank"] = str(rank)
    return ranked


def architecture_artifacts(root: Path, trigger_rows: list[dict[str, str]], action_rows: list[dict[str, str]]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    owners = {
        "TriggerIndex": "core/automation-engine/src/main/java/com/nexaflow/core/engine/TriggerIndex.kt",
        "ExecutionEngine": "core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt",
        "TaskManager": "core/execution/src/main/java/com/nexaflow/core/execution/task/TaskManager.kt",
        "WorkflowInterpreter": "core/execution/src/main/java/com/nexaflow/core/execution/workflow/WorkflowInterpreter.kt",
        "ActionRegistry": "core/execution/src/main/java/com/nexaflow/core/execution/handler/ActionRegistry.kt",
        "CapabilityRouter": "core/execution/src/main/java/com/nexaflow/core/execution/capability/semantic/CapabilityRouter.kt",
    }
    edges = [
        ("Trigger ingress and monitors", "TriggerIndex", "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventIngress.kt"),
        ("TriggerIndex", "ExecutionEngine", owners["TriggerIndex"]),
        ("ExecutionEngine", "TaskManager", owners["ExecutionEngine"]),
        ("TaskManager", "WorkflowInterpreter", owners["TaskManager"]),
        ("WorkflowInterpreter", "ActionRegistry", "core/execution/src/main/java/com/nexaflow/core/execution/workflow/ActionRegistryExecutor.kt"),
        ("ActionRegistry", "family ActionHandlers", owners["ActionRegistry"]),
        ("family ActionHandlers", "CapabilityRouter", "core/execution/src/main/java/com/nexaflow/core/execution/capability/semantic/SemanticActionRouter.kt"),
        ("CapabilityRouter", "typed capability strategies", owners["CapabilityRouter"]),
        ("typed capability strategies", "Android/root/Shizuku backends", "core/execution/src/main/java/com/nexaflow/core/execution/capability/PrivilegedCapabilityBackends.kt"),
        ("ExecutionEngine", "history and recovery", owners["ExecutionEngine"]),
    ]
    graph = [
        {"from": source, "to": target, "source": path,
         "evidence": "STATIC_ARCHITECTURE_EDGE_CANDIDATE_NOT_DYNAMIC_CALL_TRACE"}
        for source, target, path in edges
    ]
    findings: list[dict[str, str]] = []
    kotlin_roots = [root / Path("core/automation-engine/src/main"), root / Path("core/execution/src/main")]
    for name, relative in owners.items():
        count = sum(
            len(re.findall(rf"\b(?:class|object)\s+{re.escape(name)}\b", read(root, path.relative_to(root))))
            for base in kotlin_roots for path in stable_paths(base.rglob("*.kt"))
        )
        findings.append({
            "finding": "SHARED_RUNTIME_OWNER_DECLARATION", "subject": name,
            "evidence_source": relative, "observation": f"production_declaration_count={count}",
            "status": "SINGLE_OWNER_CANDIDATE" if count == 1 else "REVIEW_DUPLICATE_OR_MISSING_OWNER",
        })
    for row in trigger_rows:
        if row["legacy_type"] == "CONNECTIVITY":
            findings.append({
                "finding": "LEGACY_TRIGGER_PATH", "subject": "TriggerType.CONNECTIVITY",
                "evidence_source": row["lifecycle_owner_candidates"],
                "observation": "legacy hidden enum remains in source/runtime references",
                "status": "REVIEW_LEGACY_COMPATIBILITY_PATH",
            })
    for row in action_rows:
        if row["picker"] != "PRESENT" or not row["runtime_handler_candidates"]:
            findings.append({
                "finding": "ACTION_SURFACE_GAP_CANDIDATE", "subject": row["legacy_type"],
                "evidence_source": row["runtime_handler_candidates"],
                "observation": f"picker={row['picker']};runtime_handler_candidates={bool(row['runtime_handler_candidates'])}",
                "status": "REVIEW_UNREACHABLE_OR_SHARED_DISPATCH",
            })
    placeholder_count = 0
    for base in kotlin_roots:
        for path in stable_paths(base.rglob("*.kt")):
            for number, line in enumerate(read(root, path.relative_to(root)).splitlines(), 1):
                if re.search(r"\b(TODO|FIXME|NotImplementedError)\b", line):
                    placeholder_count += 1
                    findings.append({
                        "finding": "PLACEHOLDER_TOKEN_CANDIDATE", "subject": f"{path.relative_to(root).as_posix()}:{number}",
                        "evidence_source": path.relative_to(root).as_posix(),
                        "observation": line.strip()[:240], "status": "MANUAL_REVIEW_REQUIRED",
                    })
    if placeholder_count == 0:
        findings.append({
            "finding": "PLACEHOLDER_TOKEN_SCAN", "subject": "automation/execution production Kotlin",
            "evidence_source": "core/automation-engine/src/main; core/execution/src/main",
            "observation": "no TODO/FIXME/NotImplementedError tokens found by static scan",
            "status": "STATIC_SCAN_ONLY",
        })
    return graph, findings


def generate(root: Path, destination: Path) -> None:
    model = read(root, MODEL)
    triggers = contracts.enum_names(model, "TriggerType")
    actions = contracts.enum_names(model, "ActionType")
    if len(triggers) != 57 or len(actions) != 180:
        raise ValueError(f"Expected baseline 57/180; found {len(triggers)}/{len(actions)}. Review inventory update.")

    trigger_fields = schema_fields(root, TRIGGER_SCHEMA, "TriggerType", triggers)
    action_fields = schema_fields(root, ACTION_SCHEMA, "ActionType", actions)
    trigger_details = schema_field_details(root, TRIGGER_SCHEMA, "TriggerType", triggers)
    action_details = schema_field_details(root, ACTION_SCHEMA, "ActionType", actions)
    trigger_runtime = contracts.trigger_runtime_keys(triggers)
    action_runtime = contracts.action_runtime_keys(actions)
    action_consumers = action_runtime_owners(root, actions)
    for action in actions:
        if action.startswith("DATA_"):
            action_runtime[action] = set(action_consumers.get(action, {}))
    trigger_schema_keys = contracts.trigger_schema_keys(triggers)
    action_schema_keys = contracts.action_schema_keys(actions)
    trigger_consumers = trigger_runtime_owners(root)
    action_schema_source = read(root, ACTION_SCHEMA)
    toggle_match = re.search(r"toggleActions.*?setOf\((.*?)\)", action_schema_source, re.S)
    toggle_actions = set(re.findall(r"ActionType\.([A-Z_]+)", toggle_match.group(1))) if toggle_match else set()
    operations = operation_map(root)
    catalog = read(root, CATALOG)
    trigger_picker = read(root, PICKER_TRIGGER)
    action_picker = read(root, PICKER_ACTION)

    destination.mkdir(parents=True, exist_ok=True)
    trigger_rows = []
    for name in triggers:
        visibility = (
            "LEGACY_HIDDEN" if name == "CONNECTIVITY" else
            "CONFIGURATION_ONLY" if name == "PLUGIN_EVENT" else "DISCOVERABLE"
        )
        trigger_rows.append({
            "stable_id": f"trigger.{name.lower()}", "legacy_type": name,
            "picker": "NO_RESTRICTED" if name in {"CONNECTIVITY", "PLUGIN_EVENT"} else
                ("PRESENT" if f"TriggerType.{name}" in trigger_picker else "NOT_FOUND_BY_STATIC_SEARCH"),
            "visibility": visibility, "schema_fields": "|".join(trigger_fields[name]),
            "runtime_config_reads": "|".join(sorted(trigger_runtime.get(name, set()))),
            "schema_status": "SCHEMA_ARM_PRESENT" if name in contracts.split_enum_arms(
                contracts.when_block(read(root, TRIGGER_SCHEMA), "type"), "TriggerType") else "NO_MATCHED_SCHEMA_ARM",
            "lifecycle_evidence": "STATIC_REFERENCES_ONLY",
            "lifecycle_owner_candidates": "|".join(referenced_files(root, root / TRIGGER_ROOT, name)),
            "runtime_owner": "TriggerIndex / existing monitor ingress; per-source mapping requires review",
            "dispatch_evidence": "TriggerStateEvaluator and DeviceOneShotTriggerMatcher (see lifecycle matrix)",
            "android_oem_support": "NOT_TESTED",
        })

    action_rows = []
    for name in actions:
        subs = operations.get(name, []) or ["DEFAULT_ACTION"]
        handler_files = referenced_files(root, root / ACTION_ROOT, name)
        for operation in subs:
            action_rows.append({
                "stable_id": f"action.{name.lower()}", "legacy_type": name,
                "sub_operation": operation, "picker": "PRESENT" if f"ActionType.{name}" in action_picker else "NOT_FOUND_BY_STATIC_SEARCH",
                "schema_fields": "|".join(action_fields[name]),
                "runtime_config_reads": "|".join(sorted(action_runtime.get(name, set()))),
                "schema_status": (
                    "SCHEMA_ARM_PRESENT" if name in contracts.split_enum_arms(
                        contracts.when_block(action_schema_source, "type"), "ActionType"
                    ) else "SHARED_TOGGLE_SCHEMA" if name in toggle_actions else "EMPTY_SCHEMA_FALLBACK"
                ),
                "runtime_handler_candidates": "|".join(handler_files),
                "runtime_owner": "ExecutionEngine -> ActionRegistry -> family handler / capability router",
                "readback_exit_history_recovery": "NOT_ASSERTED_BY_STATIC_INVENTORY",
                "android_oem_support": "NOT_TESTED",
            })

    field_rows = []
    for kind, names, schema_keys, runtime_keys, details in (
        ("TRIGGER", triggers, trigger_schema_keys, trigger_runtime, trigger_details),
        ("ACTION", actions, action_schema_keys, action_runtime, action_details),
    ):
        for name in names:
            declared = set(schema_keys.get(name, set()))
            read_keys = set(runtime_keys.get(name, set()))
            for key in sorted(declared | read_keys):
                consumer_files = trigger_consumers.get(name, {}).get(key, []) if kind == "TRIGGER" else action_consumers.get(name, {}).get(key, [])
                if key in declared and key in read_keys and consumer_files:
                    status = "DECLARED_AND_RUNTIME_READ"
                elif key in declared and key in read_keys:
                    status = "DECLARED_RUNTIME_READ_OWNER_UNRESOLVED"
                elif key in declared:
                    status = "DECLARED_NO_STATIC_RUNTIME_READ"
                else:
                    status = "RUNTIME_READ_UNDECLARED"
                field_rows.append({
                    "kind": kind, "legacy_type": name, "field": key,
                    "schema_producer": f"{relative_schema(kind)} ({details.get(name, {}).get(key, 'helper-or-derived')})" if key in declared else "UNMAPPED",
                    "runtime_consumer": "|".join(consumer_files) if consumer_files else "NO_STATIC_MATCH",
                    "runtime_owner_candidates": "|".join(
                        path.as_posix() for path in runtime_candidate_index(kind).get(name, [])
                    ),
                    "ui_producer_candidates": "|".join(field_source_candidates(root, root / Path("feature/automation-builder/src/main"), kind, name, key)),
                    "test_candidates": "|".join(test_source_candidates(root, kind, name, key)),
                    "serialization_candidates": "core/database Converters + data/mapper/AutomationMapper (shared map serializer)",
                    "migration_candidates": "data/repository/CanonicalWorkflowMigrationRunner + database migrations (shared migration path; per-field migration not implied)",
                    "permission_api_backend_candidates": "core/execution compat/WorkflowRequirementCatalog + CommandCatalog + CommandRequirementCatalog; per-node resolution not statically evaluated",
                    "parity_status": status,
                    "default_handling": "See schema source; static literal extraction is incomplete",
                    "identity_version": f"AutomationNodeCatalog stable id + persisted {kind.title()}Type name; workflowVersion in Automation model",
                    "save_reload_round_trip": "NOT_TRACED_BY_THIS_GATE",
                    "import_export": "data/backup/BackupManager (generic workflow encoding; per-field import/export behavior not implied)",
                    "device_api_oem": "NOT_TESTED",
                })

    lifecycle_rows = []
    generic_stages = [
        ("picker", "feature/automation-builder/TriggerCatalogPresentation.kt or BuilderActionCatalog.kt"),
        ("draft", "feature/automation-builder/AutomationBuilderScreen.kt; TriggerEditorCard.kt; ActionConfigEditor.kt"),
        ("ui_validation", "domain/catalog/NodeConfigurationValidator.kt; editor-specific validation"),
        ("save", "feature/automation-builder/AutomationBuilderViewModel.kt; data/repository/AutomationRepositoryImpl.kt"),
        ("room_serialization", "core/database/Converters.kt; data/mapper/AutomationMapper.kt"),
        ("migration", "data/repository/CanonicalWorkflowMigrationRunner.kt; canonical workflow migrations"),
        ("reload", "data/mapper/AutomationMapper.kt; data/repository/AutomationRepositoryImpl.kt"),
        ("admission", "core/automation-engine/TriggerIndex.kt; core/execution/ExecutionEngine.kt"),
        ("dispatch", "per-trigger monitor or core/execution/handler/ActionRegistry.kt"),
        ("read_back", "core/execution/DeviceStateSnapshot.kt; ActionExecutionResult (not equivalent to observing success)"),
        ("exit", "core/execution/ExecutionEngine.kt; exit-action/reconcile path"),
        ("history", "data/repository/HistoryRepositoryImpl.kt; core/execution/ExecutionEngine.kt"),
        ("recovery", "core/datastore/ActiveExecutionStore.kt; core/execution/recovery"),
        ("import_export", "data/backup/BackupManager.kt"),
        ("identity_version", "domain/catalog/AutomationNodeCatalog.kt; domain/models/Automation.kt"),
    ]
    node_lifecycle_rows = []
    lifecycle_sources = {
        "picker": ["feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerCatalogPresentation.kt", "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/BuilderActionCatalog.kt"],
        "draft": ["feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt", "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt", "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt"],
        "ui_validation": ["domain/src/main/java/com/nexaflow/domain/catalog/NodeConfigurationValidator.kt", "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderViewModel.kt"],
        "save": ["feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderViewModel.kt", "data/src/main/java/com/nexaflow/data/repository/AutomationRepositoryImpl.kt"],
        "room_serialization": ["core/database/src/main/java/com/nexaflow/core/database/Converters.kt", "data/src/main/java/com/nexaflow/data/mapper/AutomationMapper.kt"],
        "migration": ["data/src/main/java/com/nexaflow/data/repository/CanonicalWorkflowMigrationRunner.kt", "domain/src/main/java/com/nexaflow/domain/canonical/CanonicalWorkflowV3Codec.kt"],
        "reload": ["data/src/main/java/com/nexaflow/data/mapper/AutomationMapper.kt", "data/src/main/java/com/nexaflow/data/repository/AutomationRepositoryImpl.kt"],
        "admission": ["core/automation-engine/src/main/java/com/nexaflow/core/engine/TriggerIndex.kt", "core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt"],
        "dispatch": ["core/automation-engine/src/main/java/com/nexaflow/core/engine/TriggerIndex.kt", "core/execution/src/main/java/com/nexaflow/core/execution/handler/ActionRegistry.kt", "core/execution/src/main/java/com/nexaflow/core/execution/workflow/WorkflowInterpreter.kt"],
        "read_back": ["core/execution/src/main/java/com/nexaflow/core/execution/DeviceStateSnapshot.kt", "domain/src/main/java/com/nexaflow/domain/models/ExecutionRecord.kt"],
        "exit": ["core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt"],
        "history": ["data/src/main/java/com/nexaflow/data/repository/HistoryRepositoryImpl.kt", "core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt"],
        "recovery": ["core/datastore/src/main/java/com/nexaflow/core/datastore/ActiveExecutionStore.kt", "core/execution/src/main/java/com/nexaflow/core/execution/recovery/ExecutionRecoveryCoordinator.kt"],
        "import_export": ["data/src/main/java/com/nexaflow/data/backup/BackupManager.kt"],
        "identity_version": [CATALOG.as_posix(), MODEL.as_posix()],
    }

    def add_lifecycle(kind: str, name: str, operation: str, stages: list[tuple[str, str]]) -> None:
        for stage, description in stages:
            sources = lifecycle_sources.get(stage, [])
            existing = [path for path in sources if (root / Path(path)).is_file()]
            row = {
                "kind": kind, "legacy_type": name, "sub_operation": operation,
                "stage": stage, "candidate_source": "|".join(existing),
                "owner_description": description,
                "evidence_status": "SHARED_OWNER_CANDIDATE_REQUIRES_NODE_REVIEW" if existing else "NOT_MAPPED",
                "device_verification": "NOT_TESTED",
            }
            node_lifecycle_rows.append(row)
            if kind == "TRIGGER":
                lifecycle_rows.append({
                    "trigger": name, "stage": stage,
                    "candidate_source": row["candidate_source"] or description,
                    "evidence_status": row["evidence_status"],
                    "trigger_source_files": "|".join(referenced_files(root, root / TRIGGER_ROOT, name)),
                    "device_verification": "NOT_TESTED",
                })

    for name in triggers:
        add_lifecycle("TRIGGER", name, "", generic_stages)
    for row in action_rows:
        add_lifecycle("ACTION", row["legacy_type"], row["sub_operation"], generic_stages)

    hotspots = ranked_hotspots(root)
    dependency_graph, architecture_findings = architecture_artifacts(root, trigger_rows, action_rows)
    plugin_flows = [
        {"flow": "event configuration", "node": "TriggerType.PLUGIN_EVENT", "source": "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt", "platform_gate": "configuration-only surface; permission/approval fields are source-defined", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "event source lifecycle", "node": "TriggerSource.PLUGIN", "source": "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventSource.kt", "platform_gate": "Android API 34+; source disabled below API 34", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "external event receive", "node": "LocaleContract.ACTION_REQUEST_QUERY", "source": "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventReceiver.kt", "platform_gate": "exported receiver registered only while source is active", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "payload validation", "node": "plugin event payload", "source": "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventPayloadAdapter.kt", "platform_gate": "bounded typed Bundle-to-JSON conversion", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "event matching and admission", "node": "TriggerType.PLUGIN_EVENT", "source": "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventIngress.kt", "platform_gate": "PluginCanonicalContract event match then TriggerIndex admission", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "event route and execution", "node": "TriggerType.PLUGIN_EVENT", "source": "core/automation-engine/src/main/java/com/nexaflow/core/engine/PluginEventRouter.kt", "platform_gate": "shared ExecutionEngine path", "verification": "STATIC_SOURCE_LINKS_ONLY"},
        {"flow": "plugin action dispatch", "node": "ActionType.PLUGIN_FIRE", "source": "core/execution/src/main/java/com/nexaflow/core/execution/capability/PluginCapabilityBackend.kt", "platform_gate": "capability backend; no device support claim", "verification": "STATIC_SOURCE_LINKS_ONLY"},
    ]

    csv_write(destination / "triggers-matrix.csv", list(trigger_rows[0]), trigger_rows)
    csv_write(destination / "actions-operations-matrix.csv", list(action_rows[0]), action_rows)
    csv_write(destination / "field-runtime-parity.csv", list(field_rows[0]), field_rows)
    csv_write(destination / "trigger-source-lifecycle.csv", list(lifecycle_rows[0]), lifecycle_rows)
    csv_write(destination / "node-lifecycle.csv", list(node_lifecycle_rows[0]), node_lifecycle_rows)
    csv_write(destination / "hotspots.csv", ["rank", "score", "path", "complexity", "coupling", "churn", "evidence"], hotspots)
    csv_write(destination / "special-plugin-flows.csv", list(plugin_flows[0]), plugin_flows)
    csv_write(destination / "dependency-graph.csv", list(dependency_graph[0]), dependency_graph)
    csv_write(destination / "architecture-findings.csv", list(architecture_findings[0]), architecture_findings)
    summary = [
        "# Atomic trigger/action source inventory",
        "",
        "Generated by `python scripts/audit_atomic_inventory.py --output docs/audit/atomic-inventory`.",
        "This is static source evidence. Catalog membership and static references do not establish runtime correctness or device/OEM support.",
        "",
        f"- Trigger enum entries: {len(trigger_rows)}; discoverable per current visibility metadata: {sum(row['visibility'] == 'DISCOVERABLE' for row in trigger_rows)}.",
        f"- Action enum entries: {len(actions)}; operation rows: {len(action_rows)} (data transform operations expanded individually).",
        f"- Field parity rows: {len(field_rows)}; runtime/schema mismatches: {sum(row['parity_status'] == 'RUNTIME_READ_UNDECLARED' for row in field_rows)}; declared fields without static reads: {sum(row['parity_status'] == 'DECLARED_NO_STATIC_RUNTIME_READ' for row in field_rows)}.",
        f"- Trigger lifecycle rows: {len(lifecycle_rows)} across {len(generic_stages)} shared stages per trigger; these are owner candidates, not proof of every per-trigger path.",
        f"- Combined trigger/action-operation lifecycle rows: {len(node_lifecycle_rows)} across {len(generic_stages)} distinct stages. Shared-owner candidates require per-node call-path review.",
        f"- Dependency graph edges: {len(dependency_graph)} static architecture candidates; duplicate owners, legacy references, action gaps, and placeholder tokens are inventoried in architecture-findings.csv.",
        "- Top 50 review hotspots rank normalized branch-token count, cross-module import/enum-reference coupling, and touches in the 50 commits ending at the frozen T00 baseline. This is a triage heuristic, not a defect score.",
        "- Android/OEM support, live providers, and hardware behavior: NOT TESTED by this generator.",
        "- Schema field defaults are source-linked; helper/derived defaults and producer/consumer lifecycles still require manual source review.",
        "",
        "## Runtime owner graph (static architectural entry points)",
        "",
        "See dependency-graph.csv. Edges are architecture/source candidates, not a dynamic call trace.",
        "",
        "## Explicit unresolved scope",
        "",
        "The matrices preserve unknown/unverified states. The static generator does not prove editor round-trip, Room migration, read-back, compensation, recovery ownership, per-operation intent semantics, or physical device support. These require the bounded follow-up reviews and regressions recorded in T02 onward.",
        "",
    ]
    (destination / "atomic-inventory.md").write_text("\n".join(summary), encoding="utf-8", newline="\n")


def relative_schema(kind: str) -> str:
    return (TRIGGER_SCHEMA if kind == "TRIGGER" else ACTION_SCHEMA).as_posix()


@lru_cache(maxsize=None)
def runtime_candidate_index(kind: str) -> dict[str, list[Path]]:
    if kind == "TRIGGER":
        candidates = [
            path.relative_to(ROOT)
            for base in (TRIGGER_ROOT, Path("core/execution/src/main/java/com/nexaflow/core/execution"))
            for path in stable_paths((ROOT / base).rglob("*.kt"))
        ]
    else:
        candidates = [path.relative_to(ROOT) for path in stable_paths((ROOT / ACTION_ROOT).rglob("*.kt"))]
        candidates.append(TRANSFORMS)
    index: dict[str, list[Path]] = {}
    for relative in candidates:
        source = read(ROOT, relative)
        types = set(re.findall(rf"\b{kind.title()}Type\.([A-Z_]+)", source))
        for type_name in types:
            index.setdefault(type_name, []).append(relative)
    return index


def action_runtime_owners(root: Path, actions: list[str]) -> dict[str, dict[str, list[str]]]:
    owners: dict[str, dict[str, list[str]]] = {name: {} for name in actions}

    def add(name: str, keys: set[str], source: Path) -> None:
        for key in keys:
            owners.setdefault(name, {}).setdefault(key, []).append(source.as_posix())

    for path in stable_paths((root / ACTION_ROOT).rglob("*.kt")):
        relative = path.relative_to(root)
        source = read(root, relative)
        for name, keys in contracts.diff_action_keys.engine_arm_keys(source).items():
            add(name, keys, relative)
        if "supportedTypes = DataTransforms.operations.keys" in source:
            common_keys = contracts.keys_from_config_reads(source)
            for name in actions:
                if name.startswith("DATA_"):
                    add(name, common_keys, relative)

    transform_source = read(root, TRANSFORMS)
    apply_source = transform_source[transform_source.index("fun apply("):]
    apply_arms = contracts.split_enum_arms(
        contracts.when_block(apply_source, "type"), "ActionType"
    )
    for name, body in apply_arms.items():
        add(name, contracts.keys_from_config_reads(body), TRANSFORMS)
    return owners


def trigger_runtime_owners(root: Path) -> dict[str, dict[str, list[str]]]:
    owners: dict[str, dict[str, list[str]]] = {}

    def add(name: str, keys: set[str], source: Path) -> None:
        for key in keys:
            owners.setdefault(name, {}).setdefault(key, []).append(source.as_posix())

    for relative, selector in ((TRIGGER_RUNTIME, "trigger.type"), (ONE_SHOT, "type")):
        source = read(root, relative)
        for name, body in contracts.split_enum_arms(
            contracts.when_block(source, selector), "TriggerType"
        ).items():
            add(name, contracts.keys_from_config_reads(body), relative)

    dedicated = {
        "SMS": Path("core/automation-engine/src/main/java/com/nexaflow/core/engine/SmsTriggerMatcher.kt"),
        "WEBHOOK": Path("core/automation-engine/src/main/java/com/nexaflow/core/engine/WebhookTriggerMatcher.kt"),
        "SENSOR": Path("core/automation-engine/src/main/java/com/nexaflow/core/engine/SensorTriggerMatcher.kt"),
        "BATTERY": Path("domain/src/main/java/com/nexaflow/domain/schedule/BatteryTriggerMatcher.kt"),
        "TIME": Path("domain/src/main/java/com/nexaflow/domain/schedule/TimeTriggerCalculator.kt"),
    }
    for name, relative in dedicated.items():
        source = read(root, relative)
        add(name, contracts.keys_from_config_reads(source), relative)
    return owners


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("docs/audit/atomic-inventory"))
    parser.add_argument("--check", action="store_true", help="fail if committed inventory differs from regenerated output")
    args = parser.parse_args()
    if not args.check:
        generate(ROOT, args.output)
        print(f"ATOMIC_INVENTORY: generated {args.output}")
        return 0
    import tempfile

    with tempfile.TemporaryDirectory() as temp:
        generated = Path(temp) / "inventory"
        generate(ROOT, generated)
        expected = args.output
        names = sorted(path.name for path in generated.iterdir())
        if not expected.is_dir() or sorted(path.name for path in expected.iterdir()) != names:
            print(f"ATOMIC_INVENTORY: committed output set differs at {expected}")
            return 1
        mismatches = [name for name in names if (generated / name).read_bytes() != (expected / name).read_bytes()]
        if mismatches:
            print("ATOMIC_INVENTORY: stale generated files: " + ", ".join(mismatches))
            print("Run python scripts/audit_atomic_inventory.py to refresh them.")
            return 1
    print("ATOMIC_INVENTORY: OK — deterministic matrices match current source")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
