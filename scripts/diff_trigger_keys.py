#!/usr/bin/env python3
"""Diff engine-read trigger config keys vs editor-exposed keys per TriggerType.

Engine side: TriggerStateEvaluator.triggerSatisfied + monitor reads.
Editor side: TriggerEditorCard.kt when(trigger.type) branches.
Prints MISSING (engine reads, editor never exposes) and STALE (editor writes,
engine ignores) per trigger type.
"""
import re
import sys

ENGINE_FILES = [
    "core/execution/src/main/java/com/nexaflow/core/execution/TriggerStateEvaluator.kt",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationScheduler.kt",
    "core/automation-engine/src/main/java/com/nexaflow/core/engine/SmsTriggerMatcher.kt",
]
EDITOR_FILE = "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt"

editor_src = open(EDITOR_FILE, encoding="utf-8").read()

# 1) Editor-exposed keys per TriggerType: split when(trigger.type) blocks
editor_keys = {}
for m in re.finditer(r"TriggerType\.([A-Z_]+) -> \{(.*?)\n    \}", editor_src, re.S):
    ttype, body = m.group(1), m.group(2)
    editor_keys[ttype] = set(re.findall(r'"([a-zA-Z]+)"\s*(?:to|:)', body))
# Also single-line arms: TriggerType.X -> mapOf("k" to "v", ...)
for m in re.finditer(r"TriggerType\.([A-Z_]+) -> mapOf\((.*?)\)", editor_src):
    ttype, body = m.group(1), m.group(2)
    keys = set(re.findall(r'"([a-zA-Z]+)"\s*to', body))
    editor_keys.setdefault(ttype, set()).update(keys)
# FilterChips / SelectChip arms inside TriggerEditorCard's edit section
for m in re.finditer(r"TriggerType\.([A-Z_]+)[ ,]*->(.*?\n        \))", editor_src, re.S):
    ttype, body = m.group(1), m.group(2)
    for lit in re.findall(r'(?:config\["|\bkey = )"([a-zA-Z]+)"', body):
        editor_keys.setdefault(ttype, set()).add(lit)

# 2) Engine-read keys per TriggerType from triggerSatisfied's when block
engine_src = "".join(open(f, encoding="utf-8").read() for f in ENGINE_FILES if __import__("os").path.exists(f))
# Split TriggerStateEvaluator's when(trigger.type) arms
arms = re.split(r"\n            TriggerType\.([A-Z_]+) ->", engine_src)
engine_keys = {}
for i in range(1, len(arms) - 1, 2):
    ttype, body = arms[i], arms[i + 1]
    body = body[: body.find("\n            TriggerType.")] if "\n            TriggerType." in body else body
    keys = set(re.findall(r'c\["([a-zA-Z]+)"\]', body))
    keys |= set(re.findall(r'c\.get\("([a-zA-Z]+)"\)', body))
    keys |= set(re.findall(r'config\["([a-zA-Z]+)"\]', body))
    engine_keys.setdefault(ttype, set()).update(keys)

print(f"{'TriggerType':28} {'missing (engine reads, editor hides)':45} stale")
total_missing = 0
for ttype in sorted(set(engine_keys) | set(editor_keys)):
    ek = engine_keys.get(ttype, set())
    dk = editor_keys.get(ttype, set())
    missing = sorted(ek - dk)
    stale = sorted(dk - ek)
    total_missing += len(missing)
    if missing or stale:
        print(f"{ttype:28} {','.join(missing) or '-':45} {','.join(stale) or '-'}")
print(f"\ntotal hidden engine keys: {total_missing}")
