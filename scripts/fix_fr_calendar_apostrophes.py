"""Escape apostrophes in the French CALENDAR-trigger strings.

Android resource flattening rejects a bare apostrophe (') inside a string
unless it is escaped as \\' . Only the newly added calendar keys are touched.
"""
import glob
import io
import os
import re

KEYS = [
    # feature/automation-builder
    "trigger_type_calendar", "trigger_calendar", "any_calendar", "choose_calendar",
    "no_calendars_found", "calendar_contains", "calendar_contains_hint",
    "calendar_event_start", "calendar_event_end", "calendar_event_created",
    "calendar_before_none", "calendar_before_minutes", "calendar_permission_hint",
    # feature/automations
    "trigger_calendar_sub",
    # feature/settings
    "calendar_permission", "calendar_permission_sub",
]

MODULES = [
    "feature/automation-builder/src/main/res",
    "feature/automations/src/main/res",
    "feature/dashboard/src/main/res",
    "feature/settings/src/main/res",
]


def fix(path):
    native = os.path.normpath(path)
    with io.open(native, "r", encoding="utf-8-sig") as f:
        content = f.read()
    changed = False
    for key in KEYS:
        pattern = re.compile(
            r'(<string name="%s">)(.*?)(</string>)' % re.escape(key), re.DOTALL
        )

        def repl(m):
            body = m.group(2)
            # Escape every bare apostrophe. Newly added strings have none
            # pre-escaped, so a plain replace is safe.
            fixed = body.replace("'", "\\'")
            nonlocal_changed = fixed != body
            return (m.group(1) + fixed + m.group(3), nonlocal_changed)

        # Apply and track change via the returned tuple.
        def apply(m):
            new_text, did_change = repl(m)
            nonlocal changed
            if did_change:
                changed = True
            return new_text

        content = pattern.sub(apply, content)
    if changed:
        tmp = native + ".tmp"
        with io.open(tmp, "w", encoding="utf-8", newline="") as f:
            f.write(content)
        os.replace(tmp, native)
        print("FIXED:", path.replace("\\", "/"))


for mod in MODULES:
    for path in glob.glob(mod + "/values-fr/strings.xml"):
        fix(path)

print("done")
