#!/usr/bin/env python3
"""Adds new <string> entries to every locale file of a module.

Usage:
    python3 scripts/add_strings.py <module-res-dir>

Keys + translations come from `scripts/i18n/<module>_strings.json`, where the
module name is the path segment before `src` (e.g. `feature/icons/src/main/res`
→ module `icons`). Each locale key in the JSON maps a string key to its
translated value; the `""` entry holds the default-language values.
"""
import json
import os
import sys

LOCALES = ["", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-pt", "-ru", "-tr", "-zh-rCN"]


def module_of(res_dir: str) -> str:
    parts = res_dir.rstrip("/").replace("\\", "/").split("/")
    try:
        idx = parts.index("src")
        return parts[idx - 1]
    except ValueError:
        return parts[-3] if len(parts) >= 3 else parts[-1]


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: add_strings.py <module-res-dir>")
        sys.exit(1)
    res_dir = sys.argv[1].rstrip("/")
    module = module_of(res_dir)
    json_path = os.path.join("scripts", "i18n", f"{module}_strings.json")
    with open(json_path, encoding="utf-8") as f:
        translations = json.load(f)
    defaults = translations.get("", {})

    for locale in LOCALES:
        values_dir = res_dir + ("/values" + locale if locale else "/values")
        path = os.path.join(values_dir, "strings.xml")
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            content = f.read()
        # Skip keys already present (idempotent re-runs).
        existing = set()
        for line in content.splitlines():
            if "<string name=" in line:
                name = line.split('name="', 1)[1].split('"', 1)[0]
                existing.add(name)
        lines = []
        for key, default in defaults.items():
            if key in existing:
                continue
            value = translations.get(locale, {}).get(key, default)
            lines.append(f'    <string name="{key}">{value}</string>')
        if not lines:
            continue
        block = "\n".join(lines) + "\n"
        if "</resources>" in content:
            content = content.replace("</resources>", block + "</resources>", 1)
        else:
            content = content.rstrip() + "\n" + block
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
        print(f"updated {path}")


if __name__ == "__main__":
    main()
