"""Unify remaining old terminology (Triggers/Actions) with the new naming
Conditions/Execution across automations, dashboard and automation-builder
string catalogs (all 11 locales)."""
import io, glob, re, os, json

# Locale dir names
LOCALES = ["", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-pt", "-ru", "-tr", "-zh-rCN"]

# New values per key per locale (indexed by LOCALES order).
# Terminology borrowed from the already-renamed step_triggers/step_actions.
V = {
    # ── section headers (automations details) ──
    "section_triggers": [
        "Conditions", "الشروط", "Bedingungen", "Condiciones", "Conditions",
        "शर्तें", "条件", "Condições", "Условия", "Koşullar", "条件",
    ],
    "section_actions": [
        "Execution", "التنفيذ", "Ausführung", "Ejecución", "Exécution",
        "निष्पादन", "実行", "Execução", "Выполнение", "Yürütme", "执行",
    ],
    # ── empty states ──
    "no_triggers": [
        "No conditions configured.", "لا توجد شروط مكوّنة.",
        "Keine Bedingungen konfiguriert.", "No hay condiciones configuradas.",
        "Aucune condition configurée.", "कोई शर्त कॉन्फ़िगर नहीं।",
        "条件が設定されていません。", "Nenhuma condição configurada.",
        "Условия не настроены.", "Yapılandırılmış koşul yok.", "未配置条件。",
    ],
    "no_actions": [
        "No execution configured.", "لا يوجد تنفيذ مكوّن.",
        "Keine Ausführung konfiguriert.", "No hay ejecución configurada.",
        "Aucune exécution configurée.", "कोई निष्पादन कॉन्फ़िगर नहीं।",
        "実行が設定されていません。", "Nenhuma execução configurada.",
        "Выполнение не настроено.", "Yapılandırılmış yürütme yok.", "未配置执行。",
    ],
    # ── dashboard summary ──
    "summary_any_trigger": [
        "any condition", "أي شرط", "beliebige Bedingung", "cualquier condición",
        "n\\'importe quelle condition", "कोई भी शर्त", "任意の条件",
        "qualquer condição", "любое условие", "herhangi bir koşul", "任意条件",
    ],
    "summary_no_actions": [
        "no execution", "بدون تنفيذ", "keine Ausführung", "sin ejecución",
        "aucune exécution", "कोई निष्पादन नहीं", "実行なし",
        "sem execução", "без выполнения", "yürütme yok", "无执行",
    ],
    "summary_actions_count": [
        "%1$d execution step(s)", "%1$d خطوة تنفيذ", "%1$d Ausführungsschritt(e)",
        "%1$d paso(s) de ejecución", "%1$d étape(s) d\\'exécution",
        "%1$d निष्पादन चरण", "%1$d 実行ステップ", "%1$d etapa(s) de execução",
        "%1$d шаг(ов) выполнения", "%1$d yürütme adımı", "%1$d 个执行步骤",
    ],
    # ── builder editor ──
    "trigger_n": [
        "Condition %1$d", "الشرط %1$d", "Bedingung %1$d", "Condición %1$d",
        "Condition %1$d", "शर्त %1$d", "条件 %1$d", "Condição %1$d",
        "Условие %1$d", "Koşul %1$d", "条件 %1$d",
    ],
    "remove_trigger": [
        "Remove condition", "إزالة الشرط", "Bedingung entfernen", "Quitar condición",
        "Retirer la condition", "शर्त हटाएँ", "条件を削除", "Remover condição",
        "Удалить условие", "Koşulu kaldır", "移除条件",
    ],
    "remove_action": [
        "Remove execution step", "إزالة خطوة التنفيذ", "Ausführungsschritt entfernen",
        "Quitar paso de ejecución", "Retirer l\\'étape d\\'exécution",
        "निष्पादन चरण हटाएँ", "実行ステップを削除", "Remover etapa de execução",
        "Удалить шаг выполнения", "Yürütme adımını kaldır", "移除执行步骤",
    ],
    "next_needs_trigger": [
        "Add at least one condition first", "أضف شرطاً واحداً على الأقل أولاً",
        "Füge zuerst mindestens eine Bedingung hinzu",
        "Añade al menos una condición primero",
        "Ajoutez d\\'abord au moins une condition",
        "पहले कम से कम एक शर्त जोड़ें", "先に条件を1つ以上追加してください",
        "Adicione pelo menos uma condição primeiro",
        "Сначала добавьте хотя бы одно условие", "Önce en az bir koşul ekleyin",
        "请先添加至少一个条件",
    ],
    "next_needs_action": [
        "Add at least one execution step first",
        "أضف خطوة تنفيذ واحدة على الأقل أولاً",
        "Füge zuerst mindestens einen Ausführungsschritt hinzu",
        "Añade al menos un paso de ejecución primero",
        "Ajoutez d\\'abord au moins une étape d\\'exécution",
        "पहले कम से कम एक निष्पादन चरण जोड़ें",
        "先に実行ステップを1つ以上追加してください",
        "Adicione pelo menos uma etapa de execução primeiro",
        "Сначала добавьте хотя бы один шаг выполнения",
        "Önce en az bir yürütme adımı ekleyin", "请先添加至少一个执行步骤",
    ],
    # ── exit behavior (task ends) ──
    "add_exit_action": [
        "Add exit step", "إضافة خطوة خروج", "Ausstiegsschritt hinzufügen",
        "Añadir paso de salida", "Ajouter une étape de sortie",
        "निकास चरण जोड़ें", "終了ステップを追加", "Adicionar etapa de saída",
        "Добавить шаг выхода", "Çıkış adımı ekle", "添加退出步骤",
    ],
    "pick_exit_action": [
        "Pick an exit step", "اختر خطوة الخروج", "Ausstiegsschritt auswählen",
        "Elegir paso de salida", "Choisir une étape de sortie",
        "निकास चरण चुनें", "終了ステップを選択", "Escolher etapa de saída",
        "Выберите шаг выхода", "Çıkış adımı seç", "选择退出步骤",
    ],
    "exit_extra_actions_label": [
        "Extra steps when the task ends", "خطوات إضافية عند انتهاء المهمة",
        "Zusätzliche Schritte am Ende der Aufgabe",
        "Pasos adicionales al finalizar la tarea",
        "Étapes supplémentaires à la fin de la tâche",
        "कार्य समाप्त होने पर अतिरिक्त चरण", "タスク終了時の追加ステップ",
        "Etapas adicionais ao término da tarefa",
        "Дополнительные шаги при завершении задачи",
        "Görev sonunda ek adımlar", "任务结束时执行额外步骤",
    ],
}

MODULES = {
    "automations": "feature/automations",
    "dashboard": "feature/dashboard",
    "automation-builder": "feature/automation-builder",
}

changed_files = 0
changed_entries = 0
for mod, base in MODULES.items():
    # Which keys apply to this module
    keys = {
        "section_triggers", "section_actions", "no_triggers", "no_actions",
    } if mod == "automations" else (
        {"summary_any_trigger", "summary_no_actions", "summary_actions_count"}
        if mod == "dashboard" else {
            "trigger_n", "remove_trigger", "remove_action",
            "next_needs_trigger", "next_needs_action",
            "add_exit_action", "pick_exit_action", "exit_extra_actions_label",
        }
    )
    for i, loc in enumerate(LOCALES):
        path = os.path.join(base, "src/main/res", f"values{loc}", "strings.xml")
        if not os.path.exists(path):
            continue
        with io.open(path, encoding="utf-8") as f:
            text = f.read()
        new_text = text
        for key in keys:
            values = V[key]
            if i >= len(values):
                continue
            new_val = values[i]
            # Replace the full <string name="key">...</string> element.
            pattern = re.compile(
                r'<string name="' + re.escape(key) + r'"[^>]*>.*?</string>',
                re.S,
            )
            replacement = '<string name="' + key + '">' + new_val + "</string>"
            match = pattern.search(new_text)
            if not match:
                continue
            new_text = pattern.sub(replacement, new_text, count=1)
            changed_entries += 1
        if new_text != text:
            with io.open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(new_text)
            changed_files += 1

print(f"changed files: {changed_files}, changed entries: {changed_entries}")

# ── i18n JSON catalogs ──
def clean_json(path, keys):
    if not os.path.exists(path):
        return
    with io.open(path, encoding="utf-8") as f:
        data = json.load(f)

    def walk(o, keys):
        if isinstance(o, dict):
            for k in list(o.keys()):
                if k in keys:
                    del o[k]
                else:
                    walk(o[k], keys)
        elif isinstance(o, list):
            for item in o:
                walk(item, keys)

    walk(data, keys)
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"cleaned {path}")

for mod in MODULES:
    clean_json(
        os.path.join("scripts/i18n", f"{mod}_strings.json"),
        set(V.keys()),
    )
