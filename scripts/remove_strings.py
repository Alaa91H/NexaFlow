#!/usr/bin/env python3
"""Removes now-unused strings from a module's locale files.

The global revert-on-exit toggle was unified into per-action end behavior, so
the builder module no longer references these strings.
"""
import glob
import re
import sys

KEYS = [
    "exit_revert_label",
    "exit_revert_sub",
    "revert_when_ends",
    "exit_revert_actions_label",
]


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: remove_strings.py <module-res-dir>")
        sys.exit(1)
    res_dir = sys.argv[1].rstrip("/").replace("\\", "/")
    for path in glob.glob(res_dir + "/values*/strings.xml"):
        with open(path, encoding="utf-8") as f:
            content = f.read()
        original = content
        for key in KEYS:
            # <string name="exit_revert_label">...</string> (possibly multiline)
            content = re.sub(
                r"\s*<string name=\"%s\">.*?</string>" % re.escape(key),
                "",
                content,
                flags=re.DOTALL,
            )
        if content != original:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(content)
            print("cleaned", path)


if __name__ == "__main__":
    main()
