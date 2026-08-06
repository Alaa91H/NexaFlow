"""Add the 'Selected: ...' repeat summary string to all 10 locales atomically."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": "Selected: %1$s",
    "values-ar": "المحدد: %1$s",
    "values-de": "Ausgewählt: %1$s",
    "values-es": "Seleccionado: %1$s",
    "values-fr": "Sélection : %1$s",
    "values-hi": "चयनित: %1$s",
    "values-ja": "選択: %1$s",
    "values-pt": "Selecionado: %1$s",
    "values-ru": "Выбрано: %1$s",
    "values-tr": "Seçilen: %1$s",
    "values-zh-rCN": "已选择: %1$s",
}


def update_file(path, value):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if re.search(r'name="repeat_selected"', content):
        return False
    safe = value.replace("'", "\\'")
    snippet = '    <string name="repeat_selected">%s</string>\n' % safe
    content = content.replace("</resources>", snippet + "</resources>", 1)
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
    for locale, value in LOCALES.items():
        path = os.path.join(base, locale, "strings.xml")
        if not os.path.exists(path):
            print("SKIP missing: %s" % path)
            continue
        if update_file(path, value):
            total += 1
            print("UPDATED: %s" % path)
    print("TOTAL files touched: %d" % total)


if __name__ == "__main__":
    main()
