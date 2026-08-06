# -*- coding: utf-8 -*-
"""Escapes apostrophes in newly added French strings (values-fr/strings.xml)."""
import glob
import io

KEYS = [
    "trigger_type_notification", "trigger_notification", "trigger_notification_sub",
    "notification_contains", "notification_contains_hint", "any_app",
    "notification_access_hint", "action_block_notification",
    "action_block_notification_sub", "action_clear_app_notifications",
    "action_clear_app_notifications_sub", "block_label", "notification_access",
    "notification_access_sub", "notification_listener_label",
]

MODS = [
    "feature/automation-builder", "feature/automations", "feature/dashboard",
    "feature/settings", "core/automation-engine",
]

fixed = 0
for mod in MODS:
    for path in glob.glob(mod + "/src/main/res/values-fr/strings.xml"):
        with io.open(path, "r", encoding="utf-8", newline="") as fh:
            lines = fh.readlines()
        changed = False
        for i, line in enumerate(lines):
            for key in KEYS:
                if ('name="%s"' % key) in line and "\\'" not in line and "'" in line:
                    lines[i] = line.replace("'", "\\'")
                    changed = True
                    fixed += 1
                    break
        if changed:
            with io.open(path, "w", encoding="utf-8", newline="") as fh:
                fh.writelines(lines)
            print("FIXED:", path)
print("total fixed:", fixed)
