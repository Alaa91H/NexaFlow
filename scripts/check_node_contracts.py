#!/usr/bin/env python3
"""Fail CI when runtime config keys drift outside the canonical node schemas.

This is deliberately runtime -> schema only. Builder catalog coverage is already
enforced by CatalogParityTest/audit_catalog_and_releases.py, while delegated
Compose editors can read compatibility keys conditionally and are therefore a
poor source for a strict per-action key diff.

The invariant enforced here is stronger and stable:
  every persisted config key read by an action handler or trigger matcher must
  be declared by ActionNodeSchemas/TriggerNodeSchemas.
"""
from __future__ import annotations

import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "scripts"))

import diff_action_keys  # noqa: E402

ACTION_SCHEMA = os.path.join(
    ROOT, "domain/src/main/java/com/nexaflow/domain/catalog/ActionNodeSchemas.kt"
)
TRIGGER_SCHEMA = os.path.join(
    ROOT, "domain/src/main/java/com/nexaflow/domain/catalog/TriggerNodeSchemas.kt"
)
AUTOMATION_MODEL = os.path.join(
    ROOT, "domain/src/main/java/com/nexaflow/domain/models/Automation.kt"
)
ACTION_HANDLER_DIR = os.path.join(
    ROOT, "core/execution/src/main/java/com/nexaflow/core/execution/handler"
)

STR_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])'")


def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def clean(line: str) -> str:
    line = STR_RE.sub('""', line)
    line = CHAR_RE.sub("''", line)
    return re.sub(r"//.*", "", line)


def enum_names(source: str, enum_name: str) -> list[str]:
    match = re.search(rf"enum class {enum_name}\s*\{{(.*?)\n\}}", source, re.S)
    if not match:
        raise RuntimeError(f"Could not parse enum {enum_name}")
    out = []
    for line in match.group(1).splitlines():
        line = re.sub(r"//.*", "", line).strip().rstrip(",")
        if re.fullmatch(r"[A-Z][A-Z0-9_]+", line):
            out.append(line)
    return out


def brace_block(lines: list[str], start: int) -> str:
    depth = 0
    opened = False
    body: list[str] = []
    for line in lines[start:]:
        structural = clean(line)
        depth += structural.count("{") - structural.count("}")
        body.append(line)
        if depth > 0:
            opened = True
        if opened and depth <= 0:
            break
    return "\n".join(body)


def when_block(source: str, needle: str) -> str:
    lines = source.splitlines()
    for idx, line in enumerate(lines):
        structural = clean(line)
        if re.search(r"\bwhen\s*\(", structural) and needle in structural:
            return brace_block(lines, idx)
    return ""


def split_enum_arms(body: str, prefix: str) -> dict[str, str]:
    """Split Prefix.X[, Prefix.Y]* -> bodies at depth one."""
    lines = body.splitlines()
    depth = 0
    current: list[str] = []
    buffer: list[str] = []
    pending_header: list[str] = []
    arms: dict[str, str] = {}

    def flush() -> None:
        if not current:
            return
        text = "\n".join(buffer)
        for name in current:
            arms[name] = arms.get(name, "") + "\n" + text

    token = re.escape(prefix)
    for line in lines:
        structural = clean(line)
        if depth == 1:
            direct = re.match(
                rf"^\s*({token}\.[A-Z_]+(?:\s*,\s*{token}\.[A-Z_]+)*)\s*->",
                structural,
            )
            continuation = re.match(rf"^\s*{token}\.[A-Z_]+\s*,\s*$", structural)
            if direct and not pending_header:
                flush()
                current = re.findall(rf"{token}\.([A-Z_]+)", direct.group(1))
                buffer = [line]
            elif continuation:
                pending_header.append(line)
                continue
            elif pending_header and "->" in structural:
                pending_header.append(line)
                joined = " ".join(clean(x).strip() for x in pending_header)
                flush()
                current = re.findall(rf"{token}\.([A-Z_]+)", joined)
                buffer = [line]
                pending_header = []
            elif current:
                buffer.append(line)
        elif current:
            buffer.append(line)

        depth += structural.count("{") - structural.count("}")

    flush()
    return arms


def schema_field_keys(body: str) -> set[str]:
    keys = set(
        re.findall(
            r"\b(?:stringField|packageField|urlField|secretField|jsonField|"
            r"coordinateField|dateField|timeField|durationField|integerField|"
            r"decimalField|booleanField|enumField)\(\s*\"([A-Za-z_]+)\"",
            body,
        )
    )
    keys.update(re.findall(r"\bkey\s*=\s*\"([A-Za-z_]+)\"", body))
    return keys


def action_schema_keys(action_names: list[str]) -> dict[str, set[str]]:
    source = read(ACTION_SCHEMA)
    arms = split_enum_arms(when_block(source, "type"), "ActionType")
    out = {name: schema_field_keys(arms.get(name, "")) for name in action_names}

    toggle_match = re.search(
        r"private val toggleActions.*?setOf\((.*?)\)\s*\}", source, re.S
    )
    if toggle_match:
        for name in re.findall(r"ActionType\.([A-Z_]+)", toggle_match.group(1)):
            out.setdefault(name, set()).add("enabled")

    helper_match = re.search(
        r"private fun dataActionSchema\(.*?\): NodeConfigurationSchema\s*\{(.*?)\n\s*\}",
        source,
        re.S,
    )
    helper_keys = schema_field_keys(helper_match.group(1)) if helper_match else set()
    for name in action_names:
        if name.startswith("DATA_"):
            out.setdefault(name, set()).update(helper_keys)
    return out


def action_runtime_keys(action_names: list[str]) -> dict[str, set[str]]:
    out = {name: set() for name in action_names}
    for path in glob.glob(os.path.join(ACTION_HANDLER_DIR, "*.kt")):
        source = read(path)
        for name, keys in diff_action_keys.engine_arm_keys(source).items():
            out.setdefault(name, set()).update(keys)

        # DataActionsHandler intentionally declares its supported types through
        # DataTransforms.operations.keys instead of a literal setOf(...).
        if "supportedTypes = DataTransforms.operations.keys" in source:
            keys = set(re.findall(r'(?:action\.)?config\["([A-Za-z_]+)"\]', source))
            for name in action_names:
                if name.startswith("DATA_"):
                    out[name].update(keys)
    return out


def trigger_schema_keys(trigger_names: list[str]) -> dict[str, set[str]]:
    source = read(TRIGGER_SCHEMA)
    arms = split_enum_arms(when_block(source, "type"), "TriggerType")
    out = {name: schema_field_keys(arms.get(name, "")) for name in trigger_names}

    # Threshold helper is intentionally shared by several trigger arms.
    for name, body in arms.items():
        if "thresholdSchema(" in body:
            out.setdefault(name, set()).update({"threshold", "direction"})
    return out


def keys_from_config_reads(source: str) -> set[str]:
    keys = set(re.findall(r'\bconfig\["([A-Za-z_]+)"\]', source))
    keys.update(re.findall(r'\bc\["([A-Za-z_]+)"\]', source))
    keys.update(re.findall(r'\bc\.get\("([A-Za-z_]+)"\)', source))
    return keys


def trigger_runtime_keys(trigger_names: list[str]) -> dict[str, set[str]]:
    out = {name: set() for name in trigger_names}

    evaluator_path = os.path.join(
        ROOT, "core/execution/src/main/java/com/nexaflow/core/execution/TriggerStateEvaluator.kt"
    )
    evaluator = read(evaluator_path)
    for name, body in split_enum_arms(when_block(evaluator, "trigger.type"), "TriggerType").items():
        out.setdefault(name, set()).update(keys_from_config_reads(body))

    one_shot_path = os.path.join(
        ROOT, "core/automation-engine/src/main/java/com/nexaflow/core/engine/DeviceOneShotTriggerMatcher.kt"
    )
    one_shot = read(one_shot_path)
    for name, body in split_enum_arms(when_block(one_shot, "type"), "TriggerType").items():
        out.setdefault(name, set()).update(keys_from_config_reads(body))

    dedicated = {
        "SMS": "core/automation-engine/src/main/java/com/nexaflow/core/engine/SmsTriggerMatcher.kt",
        "WEBHOOK": "core/automation-engine/src/main/java/com/nexaflow/core/engine/WebhookTriggerMatcher.kt",
        "SENSOR": "core/automation-engine/src/main/java/com/nexaflow/core/engine/SensorTriggerMatcher.kt",
        "BATTERY": "domain/src/main/java/com/nexaflow/domain/schedule/BatteryTriggerMatcher.kt",
        "TIME": "domain/src/main/java/com/nexaflow/domain/schedule/TimeTriggerCalculator.kt",
    }
    for name, rel in dedicated.items():
        path = os.path.join(ROOT, rel)
        if os.path.exists(path):
            out.setdefault(name, set()).update(keys_from_config_reads(read(path)))

    return out


def report_missing(
    label: str,
    runtime: dict[str, set[str]],
    schema: dict[str, set[str]],
) -> int:
    failures = 0
    for name in sorted(runtime):
        missing = sorted(runtime.get(name, set()) - schema.get(name, set()))
        if missing:
            failures += len(missing)
            print(f"{label} {name}: runtime keys missing from schema: {', '.join(missing)}")
    return failures


def main() -> int:
    model = read(AUTOMATION_MODEL)
    actions = enum_names(model, "ActionType")
    triggers = enum_names(model, "TriggerType")

    action_missing = report_missing(
        "ACTION",
        action_runtime_keys(actions),
        action_schema_keys(actions),
    )
    trigger_missing = report_missing(
        "TRIGGER",
        trigger_runtime_keys(triggers),
        trigger_schema_keys(triggers),
    )
    total = action_missing + trigger_missing
    if total:
        print(f"node contract audit FAILED: {total} runtime key(s) are undocumented")
        return 1

    print(
        f"node contract audit OK: {len(actions)} actions, {len(triggers)} triggers; "
        "all discovered runtime config keys are declared by their schemas"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
