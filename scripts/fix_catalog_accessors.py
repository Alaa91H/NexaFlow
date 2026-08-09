"""Gradle version-catalog accessors use dots for both `-` and `.` in aliases,
so `libs.androidx-core-core-ktx` must be written `libs.androidx.core.core.ktx`.
This pass rewrites any dashed accessor produced by migrate_version_catalog.py."""
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

files = [
    p.replace("\\", "/")
    for p in glob.glob("**/build.gradle.kts", recursive=True)
    if "/build/" not in p
]

count = 0
for p in files:
    with open(p, encoding="utf-8") as f:
        text = f.read()
    new = re.sub(
        r"\blibs\.plugins\.[a-zA-Z0-9-]+",
        lambda m: "libs.plugins." + m.group(0).split(".plugins.")[1].replace("-", "."),
        text,
    )
    new = re.sub(
        r"\blibs\.[a-zA-Z0-9][a-zA-Z0-9.-]*",
        lambda m: "libs." + m.group(0)[len("libs."):].replace("-", "."),
        new,
    )
    if new != text:
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(new)
        count += 1
print("fixed", count, "files")
