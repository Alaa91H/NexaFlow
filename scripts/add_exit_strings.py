"""Add the per-action revert header and rename save-related strings (New
Automation -> New Task wording) across all 10 locales atomically."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": {
        "exit_revert_actions_label": "Revert selected actions",
        "save_automation": "Save Task",
        "saved_successfully": "Task saved",
    },
    "values-ar": {
        "exit_revert_actions_label": "التراجع عن الإجراءات المحددة",
        "save_automation": "حفظ المهمة",
        "saved_successfully": "تم حفظ المهمة",
    },
    "values-de": {
        "exit_revert_actions_label": "Ausgewählte Aktionen zurücksetzen",
        "save_automation": "Aufgabe speichern",
        "saved_successfully": "Aufgabe gespeichert",
    },
    "values-es": {
        "exit_revert_actions_label": "Revertir acciones seleccionadas",
        "save_automation": "Guardar tarea",
        "saved_successfully": "Tarea guardada",
    },
    "values-fr": {
        "exit_revert_actions_label": "Rétablir les actions sélectionnées",
        "save_automation": "Enregistrer la tâche",
        "saved_successfully": "Tâche enregistrée",
    },
    "values-hi": {
        "exit_revert_actions_label": "चयनित क्रियाएँ पूर्ववत करें",
        "save_automation": "कार्य सहेजें",
        "saved_successfully": "कार्य सहेजा गया",
    },
    "values-ja": {
        "exit_revert_actions_label": "選択した操作を元に戻す",
        "save_automation": "タスクを保存",
        "saved_successfully": "タスクを保存しました",
    },
    "values-pt": {
        "exit_revert_actions_label": "Reverter ações selecionadas",
        "save_automation": "Salvar tarefa",
        "saved_successfully": "Tarefa salva",
    },
    "values-ru": {
        "exit_revert_actions_label": "Вернуть выбранные действия",
        "save_automation": "Сохранить задачу",
        "saved_successfully": "Задача сохранена",
    },
    "values-tr": {
        "exit_revert_actions_label": "Seçili işlemleri geri al",
        "save_automation": "Görevi kaydet",
        "saved_successfully": "Görev kaydedildi",
    },
    "values-zh-rCN": {
        "exit_revert_actions_label": "还原所选操作",
        "save_automation": "保存任务",
        "saved_successfully": "任务已保存",
    },
}


def update_file(path, new_map, change_map):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for key, value in new_map.items():
        if re.search(r'name="%s"' % re.escape(key), content):
            continue
        safe = value.replace("'", "\\'")
        snippet = '    <string name="%s">%s</string>\n' % (key, safe)
        content = content.replace("</resources>", snippet + "</resources>", 1)
        changed = True
    for key, value in change_map.items():
        safe = value.replace("'", "\\'")
        pattern = r'(<string name="%s">).*?(</string>)' % re.escape(key)
        new_content, n = re.subn(pattern, lambda m: m.group(1) + safe + m.group(2), content)
        if n:
            content = new_content
            changed = True
    if not changed:
        return False
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    except Exception:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise
    return True


def main():
    total = 0
    base = os.path.join(ROOT, "feature/automation-builder/src/main/res")
    for locale, translations in LOCALES.items():
        path = os.path.join(base, locale, "strings.xml")
        if not os.path.exists(path):
            print("SKIP missing: %s" % path)
            continue
        new_map = {k: v for k, v in translations.items() if k == "exit_revert_actions_label"}
        change_map = {k: v for k, v in translations.items() if k != "exit_revert_actions_label"}
        if update_file(path, new_map, change_map):
            total += 1
            print("UPDATED: %s" % path)
    print("TOTAL files touched: %d" % total)


if __name__ == "__main__":
    main()
