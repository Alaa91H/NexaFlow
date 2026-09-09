#!/usr/bin/env python3
"""Diff handler-read config keys vs editor-exposed keys per action branch."""
import glob
import os
import re

editor = open(
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt",
    encoding="utf-8",
).read()
branches = re.split(r"(ActionType\.[A-Z_]+(?:\s*,\s*ActionType\.[A-Z_]+)*\s*->)", editor)
cur = None
exposed = {}
for part in branches:
    mm = re.match(r"ActionType\.([A-Z_]+)", part.strip())
    if mm:
        cur = mm.group(1)
        exposed.setdefault(cur, set())
    elif cur:
        exposed[cur] |= set(re.findall(r'config\["([a-zA-Z_]+)"\]', part))

handler_keys = {}
for f in glob.glob("core/execution/src/main/java/com/nexaflow/core/execution/handler/*.kt"):
    txt = open(f, encoding="utf-8").read()
    keys = set(re.findall(r'(?:action\.)?config\["([a-zA-Z_]+)"\]', txt))
    handler_keys[os.path.basename(f)] = keys

any_exposed = set()
for v in exposed.values():
    any_exposed |= v

print("HTTP handler keys NOT exposed in SYSTEM_HTTP_REQUEST editor:")
print(sorted(handler_keys.get("HttpRequestHandler.kt", set()) - exposed.get("SYSTEM_HTTP_REQUEST", set())))
print()
print("EvoActionHandler keys NOT exposed in any EVO_* editor branch:")
evo_exposed = set()
for k, v in exposed.items():
    if k.startswith("EVO_"):
        evo_exposed |= v
print(sorted(handler_keys.get("EvoActionHandler.kt", set()) - evo_exposed))
print()
print("SystemActionsHandler keys NOT exposed in ANY editor branch:")
print(sorted(handler_keys.get("SystemActionsHandler.kt", set()) - any_exposed))
print()
print("NotificationActionsHandler keys NOT exposed in any editor branch:")
print(sorted(handler_keys.get("NotificationActionsHandler.kt", set()) - any_exposed))
print()
print("ConnectivityActionsHandler keys NOT exposed in any editor branch:")
print(sorted(handler_keys.get("ConnectivityActionsHandler.kt", set()) - any_exposed))
print()
print("SoundActionsHandler keys NOT exposed in any editor branch:")
print(sorted(handler_keys.get("SoundActionsHandler.kt", set()) - any_exposed))
