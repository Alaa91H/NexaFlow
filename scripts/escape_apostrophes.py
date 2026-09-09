#!/usr/bin/env python3
"""Idempotently escapes bare apostrophes inside <string> values so aapt
accepts them (Android XML requires \\' inside quoted string resources)."""
import glob
import re

pat = re.compile(r'(<string name="[^"]+">)(.*?)(</string>)')
apos = re.compile(r"(?<!\\)'")
ESC = "\\'"

total = 0
for path in glob.glob("feature/automation-builder/src/main/res/values*/strings.xml"):
    with open(path, encoding="utf-8") as f:
        content = f.read()

    def repl(m):
        global total
        head, text, tail = m.group(1), m.group(2), m.group(3)
        new_text = apos.sub(ESC, text)
        if new_text != text:
            total += 1
        return head + new_text + tail

    new_content = pat.sub(repl, content)
    if new_content != content:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(new_content)

print("escaped values across locales:", total)
