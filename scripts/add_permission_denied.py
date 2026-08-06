"""Adds permission_denied_hint to the 10 automation-builder locales atomically."""
import os
import tempfile

BASE = "feature/automation-builder/src/main/res"
TRANSLATIONS = {
    "values": "Permission required to enable this feature",
    "values-ar": "مطلوب إذن لتفعيل هذه الميزة",
    "values-de": "Berechtigung erforderlich, um diese Funktion zu aktivieren",
    "values-es": "Se requiere permiso para habilitar esta función",
    "values-fr": "Autorisation requise pour activer cette fonctionnalité",
    "values-hi": "इस सुविधा को सक्षम करने के लिए अनुमति आवश्यक है",
    "values-ja": "この機能を有効にするには権限が必要です",
    "values-pt": "Permissão necessária para ativar este recurso",
    "values-ru": "Требуется разрешение для включения этой функции",
    "values-tr": "Bu özelliği etkinleştirmek için izin gerekli",
    "values-zh-rCN": "需要权限才能启用此功能",
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

KEY = '    <string name="permission_denied_hint">%s</string>\n'
INSERT_AFTER = '<string name="sms_reply_hint">'

for locale, text in TRANSLATIONS.items():
    path = os.path.join(BASE, locale, "strings.xml")
    content = read(path)
    if "permission_denied_hint" in content:
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
