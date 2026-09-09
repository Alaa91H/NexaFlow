#!/usr/bin/env python3
"""Generates docs/options-audit.md.

For every TriggerType and ActionType the report lists:
- the UI control shape used by the builder editor (parsed from the editor
  when() arms: chips / slider / bounded field / picker / delegated selector)
- the config keys the engine actually reads (parsed from the handler and
  evaluator when() arms with the same splitting used by the diff scripts)
- the localization status of every R.string.* key the editors reference

For end behaviors it lists the catalog classification per action
(toggle / value / revert-only / default) and which end modes are offered.

The report is deterministic: same sources, same output.
"""
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "options-audit.md")

BUILDER = os.path.join(ROOT, "feature/automation-builder/src/main/java/com/nexaflow/feature/builder")
DOMAIN_AUTOMATION = os.path.join(ROOT, "domain/src/main/java/com/nexaflow/domain/models/Automation.kt")
ENGINE = os.path.join(ROOT, "core/execution/src/main/java/com/nexaflow/core/execution")
TRIGGER_ENGINE_FILES = [
    os.path.join(ROOT, "core/execution/src/main/java/com/nexaflow/core/execution/TriggerStateEvaluator.kt"),
    os.path.join(ROOT, "core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationScheduler.kt"),
    os.path.join(ROOT, "core/automation-engine/src/main/java/com/nexaflow/core/engine/SmsTriggerMatcher.kt"),
]

LOCALES = ["ar", "de", "es", "fr", "hi", "ja", "pt", "ru", "tr", "zh-rCN"]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def enum_names(source, enum_name):
    body = re.search(rf"enum class {enum_name} \{{(.*?)\n\}}", source, re.S).group(1)
    names = []
    for line in body.splitlines():
        line = re.sub(r"//.*", "", line).strip().rstrip(",")
        m = re.match(r"^([A-Z][A-Z0-9_]+)$", line)
        if m:
            names.append(m.group(1))
    return names


def split_action_arms(source):
    """ActionType.X[, Y]* -> { body } into {name: body}, multi-name groups shared."""
    parts = re.split(r"(ActionType\.[A-Z_]+(?:\s*,\s*ActionType\.[A-Z_]+)*\s*->)", source)
    arms = {}
    cur = None
    for part in parts:
        head = re.match(r"^ActionType\.([A-Z_]+(?:\s*,\s*ActionType\.[A-Z_]+)*)\s*->$", part.strip())
        if head:
            names = [n.strip() for n in head.group(1).split(",")]
            for n in names:
                arms.setdefault(n, "")
            cur = names
        elif cur:
            for n in cur:
                arms[n] += part
            cur = None
    return arms


def split_trigger_arms(source, indent=12):
    """TriggerType.X -> ... arms at a given indentation (evaluator vs editor)."""
    pad = " " * indent
    pat = "\n" + pad + r"TriggerType\.([A-Z_]+) ->"
    parts = re.split(pat, source)
    arms = {}
    for i in range(1, len(parts) - 1, 2):
        name = parts[i]
        body = parts[i + 1]
        # Cut at the next arm of the same shape.
        nxt = re.search("\n" + pad + r"TriggerType\.", body)
        if nxt:
            body = body[: nxt.start()]
        arms.setdefault(name, "")
        arms[name] += body
    return arms


def classify_controls(body):
    controls = []
    if "SelectChip(" in body or "FilterChip(" in body or "OptionChips(" in body:
        controls.append("chips")
    if "SliderRow(" in body:
        controls.append("slider")
    if "BoundedNumberField(" in body:
        controls.append("bounded-number")
    if "PackagePickerField(" in body or "MultiPackagePicker" in body:
        controls.append("app picker")
    if "NetworkModeSelector(" in body:
        controls.append("network selector")
    if "ToggleConfigRow(" in body or "Switch(" in body:
        controls.append("toggle")
    if "VariableInsertChips(" in body or "ContextPathInsertChips(" in body:
        controls.append("variable chips")
    delegated = sorted(set(re.findall(r"\b([A-Z][A-Za-z0-9]*(?:Selector|Editor|Picker|Field|Chips))\(", body)))
    delegated = [d for d in delegated if d not in {
        "PackagePickerField", "NetworkModeSelector", "BoundedNumberField",
        "VariableInsertChips", "ContextPathInsertChips", "OutlinedTextField",
    }]
    if delegated:
        controls.append("delegated: " + ", ".join(delegated))
    if "OutlinedTextField(" in body:
        controls.append("text field(s)")
    return " + ".join(controls) if controls else "static"


def localization_status(editor_sources, res_dir):
    keys = set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", editor_sources))
    missing = {}
    for locale in [""] + LOCALES:
        path = (
            os.path.join(res_dir, "values/strings.xml")
            if locale == ""
            else os.path.join(res_dir, f"values-{locale}/strings.xml")
        )
        have = set(re.findall(r'<string name="([^"]+)"', read(path)))
        miss = keys - have
        if miss:
            missing[locale or "en"] = sorted(miss)
    return len(keys), missing


def fmt_keys(keys):
    return ", ".join(f"`{k}`" for k in sorted(keys)) if keys else "—"


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    automation = read(DOMAIN_AUTOMATION)
    triggers = enum_names(automation, "TriggerType")
    actions = enum_names(automation, "ActionType")

    action_editor = read(os.path.join(BUILDER, "ActionConfigEditor.kt"))
    trigger_editor = read(os.path.join(BUILDER, "TriggerEditorCard.kt"))
    end_editor = read(os.path.join(BUILDER, "EndBehaviorEditor.kt"))
    end_catalog = read(os.path.join(ROOT, "domain/src/main/java/com/nexaflow/domain/models/EndBehavior.kt"))

    # Engine-read keys per action: split every handler's when() arms. Handlers
    # without a when() (single-purpose, e.g. HttpRequestHandler) declare their
    # supported types via `supportedTypes = setOf(...)`; those get the file's
    # full key set.
    action_engine_keys = {}
    for path in glob.glob(os.path.join(ENGINE, "handler/*.kt")):
        source = read(path)
        for name, body in split_action_arms(source).items():
            keys = set(re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', body))
            action_engine_keys.setdefault(name, set()).update(keys)
        for m in re.finditer(
            r"supportedTypes[^=]*=\s*setOf\((.*?)\)", source, re.S
        ):
            declared = re.findall(r"ActionType\.([A-Z_]+)", m.group(1))
            file_keys = set(re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', source))
            for name in declared:
                if not action_engine_keys.get(name):
                    action_engine_keys.setdefault(name, set()).update(file_keys)

    # Engine-read keys per trigger: evaluator/scheduler/matcher arms read `c["x"]`.
    trigger_engine_keys = {}
    for path in TRIGGER_ENGINE_FILES:
        if os.path.exists(path):
            for name, body in split_trigger_arms(read(path), indent=12).items():
                keys = set(re.findall(r'\bc(?:onfig)?\["([a-zA-Z_]+)"\]', body))
                keys |= set(re.findall(r'\bc\.get\("([a-zA-Z_]+)"\)', body))
                trigger_engine_keys.setdefault(name, set()).update(keys)

    editor_arm_bodies = split_action_arms(action_editor)

    # The trigger editor is defaults-driven: TriggerType.X -> mapOf("k" to ...)
    # plus a generic renderer, so per-trigger control shapes are not expressed
    # as when() arms. Report the editor's default config keys instead.
    trigger_editor_keys = {}
    for m in re.finditer(r"TriggerType\.([A-Z_]+) -> mapOf\((.*?)\)", trigger_editor):
        trigger_editor_keys.setdefault(m.group(1), set()).update(
            re.findall(r'"([a-zA-Z_]+)"\s*to', m.group(2))
        )

    res_dir = os.path.join(ROOT, "feature/automation-builder/src/main/res")
    total_keys, missing = localization_status(
        action_editor + trigger_editor + end_editor, res_dir
    )

    toggle = set(re.findall(r"ActionType\.([A-Z_]+)", re.search(
        r"val toggleActions[^=]*=\s*setOf\((.*?)\)", end_catalog, re.S).group(1)))
    value = set(re.findall(r"ActionType\.([A-Z_]+)", re.search(
        r"val valueActions[^=]*=\s*setOf\((.*?)\)", end_catalog, re.S).group(1)))
    revert_only = set(re.findall(r"ActionType\.([A-Z_]+)", re.search(
        r"val revertOnlyActions[^=]*=\s*setOf\((.*?)\)", end_catalog, re.S).group(1)))

    lines = []
    ap = lines.append
    ap("# Options Audit")
    ap("")
    ap(
        "Generated by `scripts/generate_options_audit.py` — do not edit by hand. "
        "Covers every trigger, action, and end-behavior option: the UI control "
        "shape, the config keys the engine actually reads, and the "
        "localization status of the editor strings."
    )
    ap("")
    if missing:
        ap(f"Editor localization: **{total_keys} keys, MISSING in {sorted(missing)}** "
           f"({sorted({k for v in missing.values() for k in v})})")
    else:
        ap(f"Editor localization: **{total_keys} keys, complete in all 11 locales**.")
    ap("")

    ap("## Triggers")
    ap("")
    ap(
        "The trigger editor is defaults-driven (a default-config map per trigger "
        "plus a generic renderer), so the table lists the editor's default "
        "config keys rather than per-trigger control shapes."
    )
    ap("")
    ap("| Trigger | Editor default config keys | Engine config keys |")
    ap("|---|---|---|")
    for t in triggers:
        ap(
            f"| `{t}` | {fmt_keys(trigger_editor_keys.get(t, set()))} | "
            f"{fmt_keys(trigger_engine_keys.get(t, set()))} |"
        )
    ap("")

    ap("## Actions")
    ap("")
    ap("| Action | Control shape | Engine config keys |")
    ap("|---|---|---|")
    for a in actions:
        body = editor_arm_bodies.get(a, "")
        ap(f"| `{a}` | {classify_controls(body)} | {fmt_keys(action_engine_keys.get(a, set()))} |")
    ap("")

    ap('## End behaviors ("when the task ends")')
    ap("")
    ap(
        "Modes: LEAVE (keep as-is), REVERT (restore pre-run state), "
        "SET_VALUE (apply a specific end value), RERUN (execute again). "
        "Toggle actions offer on/off/revert; value actions offer a full "
        "end-value editor (guarded by `EndBehaviorCoverageTest`); "
        "revert-only actions offer restore; everything else offers LEAVE/RERUN."
    )
    ap("")
    ap("| Action | End classification | End modes offered |")
    ap("|---|---|---|")
    for a in actions:
        if a in toggle:
            cls, modes = "toggle", "LEAVE, RERUN, SET_VALUE (on/off), REVERT"
        elif a in value:
            cls, modes = "value", "LEAVE, RERUN, SET_VALUE (value editor), REVERT"
        elif a in revert_only:
            cls, modes = "revert-only", "LEAVE, RERUN, REVERT"
        else:
            cls, modes = "default", "LEAVE, RERUN"
        ap(f"| `{a}` | {cls} | {modes} |")
    ap("")

    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    print(
        f"docs/options-audit.md written: {len(triggers)} triggers, {len(actions)} actions; "
        f"localization: {total_keys} keys, "
        f"{'complete' if not missing else 'MISSING ' + str(sorted(missing))}"
    )


if __name__ == "__main__":
    main()
