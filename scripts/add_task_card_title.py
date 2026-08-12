# -*- coding: utf-8 -*-
"""Adds task_card_title to every locale except values/ (English).
Line-based and EOL-preserving; idempotent."""

import io
import os

BASE = "feature/automation-builder/src/main/res"

translations = {
    "ar": "المهمة",
    "de": "Aufgabe",
    "es": "Tarea",
    "fr": "Tâche",
    "hi": "कार्य",
    "ja": "タスク",
    "pt": "Tarefa",
    "ru": "Задача",
    "tr": "Görev",
    "zh-rCN": "任务",
}

ANCHOR = '<string name="builder_title">'


def main():
    for locale, val in translations.items():
        path = os.path.join(BASE, "values-" + locale, "strings.xml")
        if not os.path.isfile(path):
            print(f"SKIP {locale}")
            continue
        with io.open(path, "r", encoding="utf-8", newline="") as f:
            content = f.read()
        eol = "\r\n" if "\r\n" in content else "\n"
        lines = content.splitlines()
        if any("name=\"task_card_title\"" in ln for ln in lines):
            print(f"SKIP {locale}: exists")
            continue
        new_lines = []
        inserted = False
        for ln in lines:
            new_lines.append(ln)
            if not inserted and 'name="builder_title"' in ln:
                new_lines.append(f'    <string name="task_card_title">{val}</string>')
                inserted = True
        if not inserted:
            print(f"WARN {locale}: anchor not found")
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(eol.join(new_lines) + eol)
        print(f"OK {locale}")


if __name__ == "__main__":
    main()
