#!/usr/bin/env python3
"""Adds the app-picker selection-preview strings to the builder module.

Keys: selected_count ("Selected: %1$d"), remove ("Remove").
English is authoritative; ar/de/es/fr/hi/ja/pt/ru/tr/zh-rCN get real
translations. Apostrophe-free values so no XML escaping is needed.
"""
import glob
import json
import re

TRANSLATIONS = {
    "": {"selected_count": "Selected: %1$d", "remove": "Remove"},
    "values-ar": {
        "selected_count": "المحدد: %1$d",
        "remove": "إزالة",
    },
    "values-de": {
        "selected_count": "Ausgewählt: %1$d",
        "remove": "Entfernen",
    },
    "values-es": {
        "selected_count": "Seleccionadas: %1$d",
        "remove": "Quitar",
    },
    "values-fr": {
        "selected_count": "Sélection : %1$d",
        "remove": "Retirer",
    },
    "values-hi": {
        "selected_count": "चयनित: %1$d",
        "remove": "हटाएँ",
    },
    "values-ja": {
        "selected_count": "選択済み: %1$d",
        "remove": "削除",
    },
    "values-pt": {
        "selected_count": "Selecionados: %1$d",
        "remove": "Remover",
    },
    "values-ru": {
        "selected_count": "Выбрано: %1$d",
        "remove": "Убрать",
    },
    "values-tr": {
        "selected_count": "Seçilen: %1$d",
        "remove": "Kaldır",
    },
    "values-zh-rCN": {
        "selected_count": "已选择：%1$d",
        "remove": "移除",
    },
}

with open("scripts/i18n/automation-builder_strings.json", encoding="utf-8") as f:
    catalog = json.load(f)

# catalog shape: { module: { key: { locale: value } } } or { key: {locale: value} }
def upsert(target):
    for key, values in TRANSLATIONS.items():
        for locale, text in values.items():
            entry = target.setdefault(key, {})
            entry[locale] = text

if isinstance(catalog, dict) and "automation-builder" in catalog:
    upsert(catalog["automation-builder"])
else:
    upsert(catalog)

with open("scripts/i18n/automation-builder_strings.json", "w", encoding="utf-8", newline="") as f:
    json.dump(catalog, f, ensure_ascii=False, indent=2)
    f.write("\n")

for locale, values in TRANSLATIONS.items():
    path = (
        "feature/automation-builder/src/main/res/values/strings.xml"
        if locale == ""
        else f"feature/automation-builder/src/main/res/{locale}/strings.xml"
    )
    with open(path, encoding="utf-8") as f:
        content = f.read()
    changed = False
    for key, text in values.items():
        xml_text = text.replace("&", "&amp;")
        pattern = rf'[ \t]*<string name="{key}">.*?</string>\n'
        entry = f'    <string name="{key}">{xml_text}</string>\n'
        if re.search(pattern, content):
            content = re.sub(pattern, entry, content, count=1)
        else:
            content = content.replace("</resources>", entry + "</resources>")
        changed = True
    if changed:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(content)

print("done: selected_count + remove in all locales and catalog")
