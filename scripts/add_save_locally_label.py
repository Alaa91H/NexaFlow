# -*- coding: utf-8 -*-
"""Add save_locally_label to all app locales (line-based, CRLF-safe)."""
import io, os, re

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

TRANSLATIONS = {
    "values": "Save locally",
    "values-ar": "\u0627\u0644\u062d\u0641\u0638 \u0645\u062d\u0644\u064a\u0627\u064b",
    "values-de": "Lokal speichern",
    "values-es": "Guardar localmente",
    "values-fr": "Enregistrer localement",
    "values-hi": "\u0938\u094d\u0925\u093e\u0928\u0940\u092f \u0930\u0942\u092a \u0938\u0947 \u0938\u0939\u0947\u091c\u0947\u0902",
    "values-ja": "\u30ed\u30fc\u30ab\u30eb\u306b\u4fdd\u5b58",
    "values-pt": "Salvar localmente",
    "values-ru": "\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u043b\u043e\u043a\u0430\u043b\u044c\u043d\u043e",
    "values-tr": "Yerel olarak kaydet",
    "values-zh-rCN": "\u672c\u5730\u4fdd\u5b58",
}

KEY = "save_locally_label"

def add_to(locale, text):
    path = os.path.join(ROOT, locale, "strings.xml")
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    if 'name="%s"' % KEY in content:
        print(locale, "already has", KEY)
        return
    # Insert after app_name line, preserving its line ending.
    m = re.search(r'(<string name="app_name">[^<]*</string>)(\r?\n)', content)
    if not m:
        print(locale, "WARN: no app_name found, skipping")
        return
    nl = m.group(2)
    insertion = m.group(1) + nl + '    <string name="%s">%s</string>' % (KEY, text)
    content = content[:m.start()] + insertion + content[m.end():]
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(content)
    print(locale, "added", KEY)

for loc, txt in TRANSLATIONS.items():
    add_to(loc, txt)
