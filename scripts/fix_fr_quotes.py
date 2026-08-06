"""Wraps French permission-explanation strings with apostrophes in double quotes."""
import os
import tempfile

PATH = "feature/automation-builder/src/main/res/values-fr/strings.xml"

# Keys whose values contain an apostrophe and therefore must be wrapped in
# double quotes for aapt2 to accept them.
KEYS = [
    "permission_location_body",
    "permission_sms_body",
    "permission_send_sms_body",
    "permission_camera_body",
    "permission_notifications_body",
    "permission_bluetooth_body",
    "permission_calendar_body",
    "permission_generic_body",
]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write_atomic(path, content):
    d = os.path.dirname(path)
    fd, tmp = tempfile.mkstemp(dir=d, suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


content = read(PATH)
changed = 0
for key in KEYS:
    start = content.find('<string name="%s">' % key)
    if start < 0:
        print("SKIP (missing): %s" % key)
        continue
    value_start = content.find(">", start) + 1
    value_end = content.find("</string>", value_start)
    value = content[value_start:value_end]
    if "'" in value and not value.startswith('"'):
        content = content[:value_start] + '"' + value + '"' + content[value_end:]
        changed += 1
        print("WRAPPED: %s" % key)
write_atomic(PATH, content)
print("Done (%d wrapped)." % changed)
