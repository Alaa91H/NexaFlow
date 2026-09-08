#!/usr/bin/env python3
"""One-shot: escape raw apostrophes in the new call/sms/schedule string keys.

Android resource parsing rejects an unescaped ' inside an unquoted <string>
value ("Invalid unicode escape sequence" shows for the related aapt failure
mode; the actual aapt error for a raw apostrophe is "Apostrophe not preceded
by \\"). Only keys belonging to the v3.59.0 feature work are touched, and only
when the value is not already a quoted string literal.
"""
import glob
import re

KEYS = re.compile(
    r'name="(sms_match_\w+|action_call_block\w*|action_call_silence\w*'
    r'|call_block_hint|call_silence_hint|trigger_type_incoming\w*'
    r'|constraint_type_schedule\w*|schedule_\w+|starter_template_scheduled\w*'
    r'|starter_template_call_\w+|permission_call_screening\w*)"'
)

VALUE = re.compile(r'(\s*<string name="[^"]+">)(.*)(</string>\s*)$')

fixed = 0
for path in glob.glob("feature/*/src/main/res/values*/strings.xml"):
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    changed = False
    for i, line in enumerate(lines):
        if not KEYS.search(line):
            continue
        m = VALUE.match(line.rstrip("\n"))
        if not m:
            continue
        head, val, tail = m.groups()
        if val.startswith('"'):
            continue  # quoted string literal: apostrophes inside are legal
        newval = re.sub(r"(?<!\\)'", r"\\'", val)
        if newval != val:
            lines[i] = head + newval + tail + "\n"
            changed = True
            fixed += 1
    if changed:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.writelines(lines)
print("escaped lines:", fixed)
