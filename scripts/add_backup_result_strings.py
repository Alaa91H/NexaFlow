# -*- coding: utf-8 -*-
"""Add backup_saved / backup_save_failed to app module locales (CRLF-safe)."""
import io, os, re

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

# (key, text) pairs per locale, mirroring feature/settings translations.
TRANSLATIONS = {
    "values": [("backup_saved", "Backup saved"), ("backup_save_failed", "Could not save the backup file")],
    "values-ar": [("backup_saved", "\u062a\u0645 \u062d\u0641\u0638 \u0627\u0644\u0646\u0633\u062e\u0629 \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a\u0629"), ("backup_save_failed", "\u062a\u0639\u0630\u0631 \u062d\u0641\u0638 \u0645\u0644\u0641 \u0627\u0644\u0646\u0633\u062e\u0629 \u0627\u0644\u0627\u062d\u062a\u064a\u0627\u0637\u064a\u0629")],
    "values-de": [("backup_saved", "Backup gespeichert"), ("backup_save_failed", "Backup konnte nicht gespeichert werden")],
    "values-es": [("backup_saved", "Copia de seguridad guardada"), ("backup_save_failed", "No se pudo guardar la copia de seguridad")],
    "values-fr": [("backup_saved", "Sauvegarde enregistr\u00e9e"), ("backup_save_failed", "Impossible d\u2019enregistrer la sauvegarde")],
    "values-hi": [("backup_saved", "\u092c\u0948\u0915\u0905\u092a \u0938\u0939\u0947\u091c \u0917\u092f\u093e"), ("backup_save_failed", "\u092c\u0948\u0915\u0905\u092a \u092b\u093c\u093e\u0907\u0932 \u0938\u0939\u0947\u091c\u0940 \u0928\u0939\u0940\u0902 \u091c\u093e \u0938\u0915\u0940")],
    "values-ja": [("backup_saved", "\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u3092\u4fdd\u5b58\u3057\u307e\u3057\u305f"), ("backup_save_failed", "\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u30d5\u30a1\u30a4\u30eb\u3092\u4fdd\u5b58\u3067\u304d\u307e\u305b\u3093\u3067\u3057\u305f")],
    "values-pt": [("backup_saved", "C\u00f3pia de seguran\u00e7a guardada"), ("backup_save_failed", "N\u00e3o foi poss\u00edvel guardar a c\u00f3pia de seguran\u00e7a")],
    "values-ru": [("backup_saved", "\u0420\u0435\u0437\u0435\u0440\u0432\u043d\u0430\u044f \u043a\u043e\u043f\u0438\u044f \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0430"), ("backup_save_failed", "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u0444\u0430\u0439\u043b \u0440\u0435\u0437\u0435\u0440\u0432\u043d\u043e\u0439 \u043a\u043e\u043f\u0438\u0438")],
    "values-tr": [("backup_saved", "Yedek kaydedildi"), ("backup_save_failed", "Yedek dosyas\u0131 kaydedilemedi")],
    "values-zh-rCN": [("backup_saved", "\u5907\u4efd\u5df2\u4fdd\u5b58"), ("backup_save_failed", "\u65e0\u6cd5\u4fdd\u5b58\u5907\u4efd\u6587\u4ef6")],
}

def add_to(locale, pairs):
    path = os.path.join(ROOT, locale, "strings.xml")
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    missing = [(k, v) for k, v in pairs if 'name="%s"' % k not in content]
    if not missing:
        print(locale, "already complete")
        return
    m = re.search(r'(<string name="app_name">[^<]*</string>)(\r?\n)', content)
    if not m:
        print(locale, "WARN: no app_name found, skipping")
        return
    nl = m.group(2)
    lines = "".join('    <string name="%s">%s</string>%s' % (k, v, nl) for k, v in missing)
    insertion = m.group(1) + nl + lines
    content = content[:m.start()] + insertion + content[m.end():]
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(content)
    print(locale, "added", [k for k, _ in missing])

for loc, pairs in TRANSLATIONS.items():
    add_to(loc, pairs)
