"""Adds exit_auto_reply_label to the 10 automation-builder locales atomically."""
import os
import tempfile

BASE = "feature/automation-builder/src/main/res"
TRANSLATIONS = {
    "values": "Auto-reply (SMS)",
    "values-ar": "رد تلقائي (رسالة)",
    "values-de": "Automatische Antwort (SMS)",
    "values-es": "Respuesta automática (SMS)",
    "values-fr": "Réponse automatique (SMS)",
    "values-hi": "स्वचालित उत्तर (SMS)",
    "values-ja": "自動返信（SMS）",
    "values-pt": "Resposta automática (SMS)",
    "values-ru": "Автоответ (SMS)",
    "values-tr": "Otomatik yanıt (SMS)",
    "values-zh-rCN": "自动回复（短信）",
}

def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

def write_atomic(path, content):
    d = os.path.dirname(path)
    fd, tmp = tempfile.mkstemp(dir=d, suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)

KEY = '    <string name="exit_auto_reply_label">%s</string>\n'
INSERT_AFTER = '<string name="exit_behavior_hint">'

for locale, text in TRANSLATIONS.items():
    path = os.path.join(BASE, locale, "strings.xml")
    content = read(path)
    if "exit_auto_reply_label" in content:
        print(f"SKIP (exists): {locale}")
        continue
    line = KEY % text
    idx = content.find(INSERT_AFTER)
    if idx < 0:
        print(f"ERROR anchor not found: {locale}")
        continue
    end = content.find("</string>", idx) + len("</string>")
    content = content[:end] + "\n" + line + content[end:]
    write_atomic(path, content)
    print(f"OK: {locale}")

print("Done.")
