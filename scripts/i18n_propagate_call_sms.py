#!/usr/bin/env python3
"""Propagates the new call/sms/schedule strings into the automations catalog
and reports which catalog feeds feature/dashboard."""
import json
import os

with open("scripts/i18n/automation-builder_strings.json", encoding="utf-8") as f:
    builder = json.load(f)
new_keys = [
    k
    for k, v in builder[""].items()
    if k.startswith(
        (
            "sms_match",
            "trigger_type_incoming",
            "call_",
            "schedule_",
            "starter_template_scheduled",
            "starter_template_nightly",
        )
    )
]
print("new keys:", len(new_keys))

with open("scripts/i18n/automations_strings.json", encoding="utf-8") as f:
    auto = json.load(f)
added = 0
for locale, values in builder.items():
    bucket = auto.setdefault(locale, {})
    for key in new_keys:
        if key in values and key not in bucket:
            bucket[key] = values[key]
            added += 1
with open("scripts/i18n/automations_strings.json", "w", encoding="utf-8") as f:
    json.dump(auto, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("automations added:", added)

for root, dirs, files in os.walk("feature/dashboard/src/main/res"):
    for name in files:
        if name == "strings.xml":
            p = os.path.join(root, name).replace(os.sep, "/")
            with open(p, encoding="utf-8") as f:
                content = f.read()
            print("dashboard res:", p, "| has trigger_call_state:", "trigger_call_state" in content)
