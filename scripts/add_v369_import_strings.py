#!/usr/bin/env python3
"""Adds the v3.69.0 single-task import strings to the app module (11 locales).

Keys: task_import_success, task_import_invalid_workflow, task_import_not_single,
task_import_invalid_file.
English authoritative; the other 10 locales get real translations.
All values are apostrophe-free so no XML escaping beyond & -> &amp; applies.
"""
CATALOG = {
    "": {
        "task_import_success": "Task imported: %1$s (review and enable it)",
        "task_import_invalid_workflow": "Task not imported: it contains unsupported steps for this version",
        "task_import_not_single": "File not imported: choose a single-task (.nexaflow) file, not a full backup",
        "task_import_invalid_file": "File not imported: it is not a valid NexaFlow task file",
    },
    "values-ar": {
        "task_import_success": "تم استيراد المهمة: %1$s (راجعها وفعّلها)",
        "task_import_invalid_workflow": "لم تُستورد المهمة: تحتوي على خطوات غير مدعومة في هذا الإصدار",
        "task_import_not_single": "لم يُستورد الملف: اختر ملف مهمة واحدة (.nexaflow) وليس نسخة احتياطية كاملة",
        "task_import_invalid_file": "لم يُستورد الملف: إنه ليس ملف مهمة NexaFlow صالحًا",
    },
    "values-de": {
        "task_import_success": "Aufgabe importiert: %1$s (prüfen und aktivieren)",
        "task_import_invalid_workflow": "Aufgabe nicht importiert: Sie enthält nicht unterstützte Schritte für diese Version",
        "task_import_not_single": "Datei nicht importiert: Wählen Sie eine Einzelaufgaben-Datei (.nexaflow), kein vollständiges Backup",
        "task_import_invalid_file": "Datei nicht importiert: Sie ist keine gültige NexaFlow-Aufgabendatei",
    },
    "values-es": {
        "task_import_success": "Tarea importada: %1$s (revísala y actívala)",
        "task_import_invalid_workflow": "Tarea no importada: contiene pasos no compatibles con esta versión",
        "task_import_not_single": "Archivo no importado: elige un archivo de tarea única (.nexaflow), no una copia completa",
        "task_import_invalid_file": "Archivo no importado: no es un archivo de tarea de NexaFlow válido",
    },
    "values-fr": {
        "task_import_success": "Tâche importée : %1$s (vérifiez-la et activez-la)",
        "task_import_invalid_workflow": "Tâche non importée : elle contient des étapes non prises en charge par cette version",
        "task_import_not_single": "Fichier non importé : choisissez un fichier de tâche unique (.nexaflow), pas une sauvegarde complète",
        "task_import_invalid_file": "Fichier non importé : ce n'est pas un fichier de tâche NexaFlow valide",
    },
    "values-hi": {
        "task_import_success": "कार्य आयातित: %1$s (समीक्षा करें और सक्षम करें)",
        "task_import_invalid_workflow": "कार्य आयातित नहीं हुआ: इसमें इस संस्करण के लिए असमर्थित चरण हैं",
        "task_import_not_single": "फ़ाइल आयातित नहीं हुई: पूर्ण बैकअप नहीं, एकल-कार्य (.nexaflow) फ़ाइल चुनें",
        "task_import_invalid_file": "फ़ाइल आयातित नहीं हुई: यह मान्य NexaFlow कार्य फ़ाइल नहीं है",
    },
    "values-ja": {
        "task_import_success": "タスクをインポートしました：%1$s（確認して有効にしてください）",
        "task_import_invalid_workflow": "タスクをインポートしませんでした：このバージョンで未対応のステップが含まれています",
        "task_import_not_single": "ファイルをインポートしませんでした：完全バックアップではなく、単一タスク（.nexaflow）ファイルを選択してください",
        "task_import_invalid_file": "ファイルをインポートしませんでした：有効なNexaFlowタスクファイルではありません",
    },
    "values-pt": {
        "task_import_success": "Tarefa importada: %1$s (revise e ative)",
        "task_import_invalid_workflow": "Tarefa não importada: contém etapas não compatíveis com esta versão",
        "task_import_not_single": "Arquivo não importado: escolha um arquivo de tarefa única (.nexaflow), não um backup completo",
        "task_import_invalid_file": "Arquivo não importado: não é um arquivo de tarefa NexaFlow válido",
    },
    "values-ru": {
        "task_import_success": "Задача импортирована: %1$s (проверьте и включите её)",
        "task_import_invalid_workflow": "Задача не импортирована: она содержит шаги, не поддерживаемые этой версией",
        "task_import_not_single": "Файл не импортирован: выберите файл одной задачи (.nexaflow), а не полную резервную копию",
        "task_import_invalid_file": "Файл не импортирован: это не допустимый файл задачи NexaFlow",
    },
    "values-tr": {
        "task_import_success": "Görev içe aktarıldı: %1$s (gözden geçirin ve etkinleştirin)",
        "task_import_invalid_workflow": "Görev içe aktarılmadı: bu sürümde desteklenmeyen adımlar içeriyor",
        "task_import_not_single": "Dosya içe aktarılmadı: tam yedek değil, tek görev (.nexaflow) dosyası seçin",
        "task_import_invalid_file": "Dosya içe aktarılmadı: geçerli bir NexaFlow görev dosyası değil",
    },
    "values-zh-rCN": {
        "task_import_success": "任务已导入：%1$s（请检查并启用）",
        "task_import_invalid_workflow": "任务未导入：包含此版本不支持的步骤",
        "task_import_not_single": "文件未导入：请选择单个任务（.nexaflow）文件，而不是完整备份",
        "task_import_invalid_file": "文件未导入：这不是有效的 NexaFlow 任务文件",
    },
}

MODULE_RES = "app/src/main/res"


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
print("app import strings inserted")
