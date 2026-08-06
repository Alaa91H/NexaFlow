"""Add localized network/state labels for the simplified connectivity editor
across all 10 locales using safe atomic writes."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Mobile data",
        "state_connected": "Connected",
        "state_disconnected": "Disconnected",
    },
    "values-ar": {
        "network_wifi": "واي فاي",
        "network_mobile": "بيانات الجوال",
        "state_connected": "متصل",
        "state_disconnected": "غير متصل",
    },
    "values-de": {
        "network_wifi": "WLAN",
        "network_mobile": "Mobile Daten",
        "state_connected": "Verbunden",
        "state_disconnected": "Getrennt",
    },
    "values-es": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Datos móviles",
        "state_connected": "Conectado",
        "state_disconnected": "Desconectado",
    },
    "values-fr": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Données mobiles",
        "state_connected": "Connecté",
        "state_disconnected": "Déconnecté",
    },
    "values-hi": {
        "network_wifi": "वाई-फ़ाई",
        "network_mobile": "मोबाइल डेटा",
        "state_connected": "कनेक्टेड",
        "state_disconnected": "डिस्कनेक्टेड",
    },
    "values-ja": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "モバイルデータ",
        "state_connected": "接続済み",
        "state_disconnected": "切断済み",
    },
    "values-pt": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Dados móveis",
        "state_connected": "Conectado",
        "state_disconnected": "Desconectado",
    },
    "values-ru": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Мобильные данные",
        "state_connected": "Подключено",
        "state_disconnected": "Отключено",
    },
    "values-tr": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "Mobil veri",
        "state_connected": "Bağlı",
        "state_disconnected": "Bağlı değil",
    },
    "values-zh-rCN": {
        "network_wifi": "Wi-Fi",
        "network_mobile": "移动数据",
        "state_connected": "已连接",
        "state_disconnected": "未连接",
    },
}


def update_file(path, new_map):
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
        if update_file(path, translations):
            total += len(translations)
            print("UPDATED: %s" % path)
    print("TOTAL keys inserted: %d" % total)


if __name__ == "__main__":
    main()
