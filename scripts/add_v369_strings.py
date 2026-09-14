#!/usr/bin/env python3
"""Adds the v3.69.0 run-progress Live Update strings to the core:execution module.

Keys: run_progress_channel_name, run_progress_channel_desc, run_progress_starting,
run_progress_step.

English authoritative; the other 10 locales get real translations.
All values are apostrophe-free so no XML escaping beyond & -> &amp; applies.
"""
import json
import re

CATALOG = {
    "": {
        "run_progress_channel_name": "Task run progress",
        "run_progress_channel_desc": "Shows which action a running task is executing",
        "run_progress_starting": "Starting…",
        "run_progress_step": "Step %1$d of %2$d",
    },
    "values-ar": {
        "run_progress_channel_name": "تقدم تنفيذ المهمة",
        "run_progress_desc": "يعرض الإجراء الذي تنفذه المهمة الجارية",
        "run_progress_starting": "جارٍ البدء…",
        "run_progress_step": "الخطوة %1$d من %2$d",
    },
    "values-de": {
        "run_progress_channel_name": "Aufgabenausführungsfortschritt",
        "run_progress_desc": "Zeigt, welche Aktion eine laufende Aufgabe ausführt",
        "run_progress_starting": "Wird gestartet…",
        "run_progress_step": "Schritt %1$d von %2$d",
    },
    "values-es": {
        "run_progress_channel_name": "Progreso de ejecución de tareas",
        "run_progress_desc": "Muestra qué acción está ejecutando una tarea en curso",
        "run_progress_starting": "Iniciando…",
        "run_progress_step": "Paso %1$d de %2$d",
    },
    "values-fr": {
        "run_progress_channel_name": "Progression d'exécution des tâches",
        "run_progress_desc": "Affiche l'action qu'exécute une tâche en cours",
        "run_progress_starting": "Démarrage…",
        "run_progress_step": "Étape %1$d sur %2$d",
    },
    "values-hi": {
        "run_progress_channel_name": "कार्य रन प्रगति",
        "run_progress_desc": "दिखाता है कि चल रहा कार्य कौन सी क्रिया निष्पादित कर रहा है",
        "run_progress_starting": "शुरू हो रहा है…",
        "run_progress_step": "चरण %1$d / %2$d",
    },
    "values-ja": {
        "run_progress_channel_name": "タスク実行の進行状況",
        "run_progress_desc": "実行中のタスクがどのアクションを実行しているかを表示します",
        "run_progress_starting": "開始中…",
        "run_progress_step": "ステップ %1$d / %2$d",
    },
    "values-pt": {
        "run_progress_channel_name": "Progresso de execução de tarefas",
        "run_progress_desc": "Mostra qual ação uma tarefa em execução está realizando",
        "run_progress_starting": "Iniciando…",
        "run_progress_step": "Etapa %1$d de %2$d",
    },
    "values-ru": {
        "run_progress_channel_name": "Прогресс выполнения задач",
        "run_progress_desc": "Показывает, какое действие выполняет запущенная задача",
        "run_progress_starting": "Запуск…",
        "run_progress_step": "Шаг %1$d из %2$d",
    },
    "values-tr": {
        "run_progress_channel_name": "Görev çalıştırma ilerlemesi",
        "run_progress_desc": "Çalışan görevin hangi eylemi yürüttüğünü gösterir",
        "run_progress_starting": "Başlatılıyor…",
        "run_progress_step": "Adım %1$d / %2$d",
    },
    "values-zh-rCN": {
        "run_progress_channel_name": "任务运行进度",
        "run_progress_desc": "显示正在运行的任务正在执行哪个操作",
        "run_progress_starting": "正在启动…",
        "run_progress_step": "第 %1$d 步，共 %2$d 步",
    },
}

# The descriptor key above is the channel-description key; normalize it.
for locale in CATALOG.values():
    if "run_progress_desc" in locale and "run_progress_channel_desc" not in locale:
        locale["run_progress_channel_desc"] = locale.pop("run_progress_desc")

MODULE_RES = "core/execution/src/main/res"
CATALOG_DIR = "scripts/i18n"
MODULE_NAME = "execution"
JSON_PATH = f"{CATALOG_DIR}/{MODULE_NAME}_strings.json"


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
        # Insert before the closing </resources> line.
        for index in range(len(lines) - 1, -1, -1):
            if lines[index].strip() == "</resources>":
                lines.insert(index, line)
                changed = True
                break
    if changed:
        with open(path, "w", encoding="utf-8", newline="\r\n") as handle:
            handle.write("\r\n".join(lines) + "\r\n")


def main() -> None:
    for locale, strings in CATALOG.items():
        insert_strings(locale, strings)

    # Keep the i18n JSON catalog in sync when it exists.
    try:
        with open(JSON_PATH, "r", encoding="utf-8") as handle:
            catalog = json.load(handle)
    except FileNotFoundError:
        print(f"no catalog at {JSON_PATH}; XML-only update")
        return

    def normalize(value: str) -> str:
        return value

    updated = 0
    for locale, strings in CATALOG.items():
        json_locale = "" if locale == "" else locale.replace("values-", "")
        bucket = catalog.setdefault(json_locale, {})
        for name, raw in strings.items():
            existing = bucket.get(name)
            if existing is None or existing == "":
                bucket[name] = normalize(raw)
                updated += 1
            else:
                bucket[name] = existing
    with open(JSON_PATH, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(catalog, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"catalog updated: {updated} entries added")


if __name__ == "__main__":
    main()
