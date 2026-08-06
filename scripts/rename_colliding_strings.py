"""Rename string keys that collide with androidx.compose.ui private resources.

- feature/automation-builder: range_start -> time_range_start, range_end -> time_range_end
- feature/settings: state_on -> state_enabled, state_off -> state_disabled
"""
import glob
import io
import os
import re

RENAMES = {
    "range_start": "time_range_start",
    "range_end": "time_range_end",
    "state_on": "state_enabled",
    "state_off": "state_disabled",
}

MODULES = [
    "feature/automation-builder/src/main/res",
    "feature/settings/src/main/res",
]


def normalize(path):
    return os.path.normpath(path).replace("\\", "/")


def fix_strings_xml(path, renames):
    with io.open(path, "r", encoding="utf-8-sig") as f:
        content = f.read()
    newline = "\r\n" if "\r\n" in content else "\n"
    changed = False
    for old, new in renames.items():
        # Only rename exact <string name="old"> definitions and usages @string/old
        pat_def = re.compile(r'(<string name="%s">)' % re.escape(old))
        pat_ref = re.compile(r'(@string/)%s\b' % re.escape(old))
        if pat_def.search(content) or pat_ref.search(content):
            content = pat_def.sub(lambda m: m.group(1).replace('"%s"' % old, '"%s"' % new), content)
            content = pat_ref.sub(lambda m: m.group(1) + new, content)
            changed = True
    if changed:
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(content)
    return changed


def fix_kotlin(path, renames):
    with io.open(path, "r", encoding="utf-8-sig") as f:
        content = f.read()
    newline = "\r\n" if "\r\n" in content else "\n"
    changed = False
    for old, new in renames.items():
        pat = re.compile(r'(R\.string\.)%s\b' % re.escape(old))
        if pat.search(content):
            content = pat.sub(lambda m: m.group(1) + new, content)
            changed = True
    if changed:
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(content)
    return changed


changed_files = []

for mod in MODULES:
    for path in glob.glob(normalize(mod) + "/values*/strings.xml"):
        if fix_strings_xml(normalize(path), RENAMES):
            changed_files.append(normalize(path))

kotlin_targets = [
    "feature/automation-builder/src/main/java/com/nexaflow/feature/builder/TriggerEditorCard.kt",
    "feature/settings/src/main/java/com/nexaflow/feature/settings/SettingsScreen.kt",
]
for path in kotlin_targets:
    if fix_kotlin(normalize(path), RENAMES):
        changed_files.append(normalize(path))

for f in sorted(changed_files):
    print("RENAMED:", f)
print("Total changed files:", len(changed_files))
