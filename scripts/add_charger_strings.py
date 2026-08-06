"""Add charger-type trigger strings to the automation-builder and automations
modules for all 10 locales using safe atomic writes (temp file + os.replace)."""
import glob
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (builder value, automations value)
BUILDER_STRINGS = {
    "charger_type_label": "Charger type",
    "charger_any": "Any",
    "charger_ac": "AC / Wall",
    "charger_usb": "USB",
    "charger_wireless": "Wireless",
}
DETAILS_STRINGS = {
    "charger_any": "Any",
    "charger_ac": "AC / Wall",
    "charger_usb": "USB",
    "charger_wireless": "Wireless",
}

LOCALES = {
    "values": {
        "charger_type_label": "Charger type",
        "charger_any": "Any",
        "charger_ac": "AC / Wall",
        "charger_usb": "USB",
        "charger_wireless": "Wireless",
    },
    "values-ar": {
        "charger_type_label": "نوع الشاحن",
        "charger_any": "أي",
        "charger_ac": "حائط",
        "charger_usb": "USB",
        "charger_wireless": "لاسلكي",
    },
    "values-de": {
        "charger_type_label": "Ladegerät-Typ",
        "charger_any": "Beliebig",
        "charger_ac": "AC / Wand",
        "charger_usb": "USB",
        "charger_wireless": "Kabellos",
    },
    "values-es": {
        "charger_type_label": "Tipo de cargador",
        "charger_any": "Cualquiera",
        "charger_ac": "CA / Pared",
        "charger_usb": "USB",
        "charger_wireless": "Inalámbrico",
    },
    "values-fr": {
        "charger_type_label": "Type de chargeur",
        "charger_any": "Peu importe",
        "charger_ac": "Secteur / Mur",
        "charger_usb": "USB",
        "charger_wireless": "Sans fil",
    },
    "values-hi": {
        "charger_type_label": "चार्जर प्रकार",
        "charger_any": "कोई भी",
        "charger_ac": "AC / दीवार",
        "charger_usb": "USB",
        "charger_wireless": "वायरलेस",
    },
    "values-ja": {
        "charger_type_label": "充電器の種類",
        "charger_any": "すべて",
        "charger_ac": "AC / 壁",
        "charger_usb": "USB",
        "charger_wireless": "ワイヤレス",
    },
    "values-pt": {
        "charger_type_label": "Tipo de carregador",
        "charger_any": "Qualquer",
        "charger_ac": "CA / Parede",
        "charger_usb": "USB",
        "charger_wireless": "Sem fio",
    },
    "values-ru": {
        "charger_type_label": "Тип зарядного устройства",
        "charger_any": "Любой",
        "charger_ac": "Сеть / Розетка",
        "charger_usb": "USB",
        "charger_wireless": "Беспроводная",
    },
    "values-tr": {
        "charger_type_label": "Şarj cihazı türü",
        "charger_any": "Herhangi biri",
        "charger_ac": "AC / Duvar",
        "charger_usb": "USB",
        "charger_wireless": "Kablosuz",
    },
    "values-zh-rCN": {
        "charger_type_label": "充电器类型",
        "charger_any": "任意",
        "charger_ac": "交流电 / 墙插",
        "charger_usb": "USB",
        "charger_wireless": "无线",
    },
}

# Modules: (relative path, keys to add)
MODULES = [
    ("feature/automation-builder/src/main/res", BUILDER_STRINGS.keys()),
    ("feature/automations/src/main/res", DETAILS_STRINGS.keys()),
]


def insert_strings(path, new_strings):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    added = []
    for key, value in new_strings.items():
        if re.search(r'name="%s"' % re.escape(key), content):
            continue  # already present
        # Escape apostrophes for Android XML.
        safe = value.replace("'", "\\'")
        snippet = '    <string name="%s">%s</string>\n' % (key, safe)
        content = content.replace("</resources>", snippet + "</resources>", 1)
        added.append(key)

    if not added:
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
    for module, keys in MODULES:
        base = os.path.join(ROOT, module)
        for locale, translations in LOCALES.items():
            dirpath = os.path.join(base, locale)
            path = os.path.join(dirpath, "strings.xml")
            if not os.path.exists(path):
                print("SKIP missing: %s" % path)
                continue
            wanted = {k: translations[k] for k in keys if k in translations}
            if insert_strings(path, wanted):
                total += len(wanted)
                print("UPDATED: %s (%d keys)" % (path, len(wanted)))
    print("TOTAL keys inserted: %d" % total)


if __name__ == "__main__":
    main()
