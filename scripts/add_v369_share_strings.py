#!/usr/bin/env python3
"""Adds the v3.69.0 single-task share strings to the feature:dashboard module.

Keys: share_task (menu label), share_task_title (share sheet), share_task_failed (toast).
English authoritative; the other 10 locales get real translations.
All values are apostrophe-free so no XML escaping beyond & -> &amp; applies.
"""
CATALOG = {
    "": {
        "share_task": "Share task",
        "share_task_title": "Share task (.nexaflow)",
        "share_task_failed": "Could not share this task",
    },
    "values-ar": {
        "share_task": "مشاركة المهمة",
        "share_task_title": "مشاركة المهمة (.nexaflow)",
        "share_task_failed": "تعذرت مشاركة هذه المهمة",
    },
    "values-de": {
        "share_task": "Aufgabe teilen",
        "share_task_title": "Aufgabe teilen (.nexaflow)",
        "share_task_failed": "Aufgabe konnte nicht geteilt werden",
    },
    "values-es": {
        "share_task": "Compartir tarea",
        "share_task_title": "Compartir tarea (.nexaflow)",
        "share_task_failed": "No se pudo compartir esta tarea",
    },
    "values-fr": {
        "share_task": "Partager la tâche",
        "share_task_title": "Partager la tâche (.nexaflow)",
        "share_task_failed": "Impossible de partager cette tâche",
    },
    "values-hi": {
        "share_task": "कार्य साझा करें",
        "share_task_title": "कार्य साझा करें (.nexaflow)",
        "share_task_failed": "इस कार्य को साझा नहीं किया जा सका",
    },
    "values-ja": {
        "share_task": "タスクを共有",
        "share_task_title": "タスクを共有（.nexaflow）",
        "share_task_failed": "このタスクを共有できませんでした",
    },
    "values-pt": {
        "share_task": "Compartilhar tarefa",
        "share_task_title": "Compartilhar tarefa (.nexaflow)",
        "share_task_failed": "Não foi possível compartilhar esta tarefa",
    },
    "values-ru": {
        "share_task": "Поделиться задачей",
        "share_task_title": "Поделиться задачей (.nexaflow)",
        "share_task_failed": "Не удалось поделиться этой задачей",
    },
    "values-tr": {
        "share_task": "Görevi paylaş",
        "share_task_title": "Görevi paylaş (.nexaflow)",
        "share_task_failed": "Bu görev paylaşılamadı",
    },
    "values-zh-rCN": {
        "share_task": "分享任务",
        "share_task_title": "分享任务（.nexaflow）",
        "share_task_failed": "无法分享此任务",
    },
}

MODULE_RES = "feature/dashboard/src/main/res"


def xml_escape(value: str) -> str:
    return value.replace("&", "&amp;")


def locale_file(locale: str) -> str:
    return (
        f"{MODULE_RES}/values/strings.xml"
        if locale == ""
        else f"{MODULE_RES}/{locale}/strings.xml"
    )


def insert_strings(locale: str, strings: dict) -> None:
    path = locale_file(locale)
    with open(path, "r", encoding="utf-8") as handle:
        content = handle.read()
    lines = content.splitlines()
    changed = False
    for name, raw in strings.items():
        value = xml_escape(raw)
        if f'name="{name}"' in content:
            continue
        line = f'    <string name="{name}">{value}</string>'
        for index in range(len(lines) - 1, -1, -1):
            if lines[index].strip() == "</resources>":
                lines.insert(index, line)
                changed = True
                break
    if changed:
        with open(path, "w", encoding="utf-8", newline="\r\n") as handle:
            handle.write("\r\n".join(lines) + "\r\n")


for locale, strings in CATALOG.items():
    insert_strings(locale, strings)
print("dashboard share strings inserted")
