#!/usr/bin/env python3
"""Writes the new call/sms/schedule strings directly into the dashboard's
strings.xml files (the dashboard module has no dedicated i18n catalog)."""
import json

with open("scripts/i18n/automation-builder_strings.json", encoding="utf-8") as f:
    builder = json.load(f)

prefixes = (
    "sms_match",
    "trigger_type_incoming",
    "call_",
    "schedule_",
    "starter_template_scheduled",
    "starter_template_nightly",
)
new_keys = [k for k in builder[""] if k.startswith(prefixes)]

LOCALES = ["", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-pt", "-ru", "-tr", "-zh-rCN"]
RES = "feature/dashboard/src/main/res"

for locale in LOCALES:
    values_dir = RES + ("/values" + locale if locale else "/values")
    path = values_dir + "/strings.xml"
    try:
        with open(path, encoding="utf-8") as f:
            content = f.read()
    except FileNotFoundError:
        continue
    existing = set()
    for line in content.splitlines():
        if "<string name=" in line:
            existing.add(line.split('name="', 1)[1].split('"', 1)[0])
    lines = []
    for key in new_keys:
        if key in existing:
            continue
        value = builder.get(locale, {}).get(key) or builder[""][key]
        lines.append(f'    <string name="{key}">{value}</string>')
    if not lines:
        continue
    block = "\n".join(lines) + "\n"
    content = content.replace("</resources>", block + "</resources>", 1)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print(f"updated {path} ({len(lines)} keys)")
