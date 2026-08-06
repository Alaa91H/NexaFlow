"""Add/change/remove builder UX strings across all 10 locales using safe
atomic writes (temp file + os.replace)."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

NEW = {
    "revert_when_ends": "Revert when the task ends",
    "device_screen_on": "Screen on",
    "device_screen_off": "Screen off",
    "device_power_connected": "Charging started",
    "device_power_disconnected": "Charging stopped",
    "device_headset_connected": "Headset connected",
    "device_headset_disconnected": "Headset disconnected",
    "device_bluetooth": "Bluetooth device",
}
CHANGE = {
    "builder_title": "New Task",
}
REMOVE = [
    "summary_if",
    "summary_then",
    "summary_no_triggers",
    "summary_no_actions",
    "repeat_specific_date",
    "specific_date",
]

LOCALES = {
    "values": {
        "revert_when_ends": "Revert when the task ends",
        "device_screen_on": "Screen on",
        "device_screen_off": "Screen off",
        "device_power_connected": "Charging started",
        "device_power_disconnected": "Charging stopped",
        "device_headset_connected": "Headset connected",
        "device_headset_disconnected": "Headset disconnected",
        "device_bluetooth": "Bluetooth device",
        "builder_title": "New Task",
    },
    "values-ar": {
        "revert_when_ends": "التراجع عند انتهاء المهمة",
        "device_screen_on": "تشغيل الشاشة",
        "device_screen_off": "إيقاف الشاشة",
        "device_power_connected": "بدأ الشحن",
        "device_power_disconnected": "توقف الشحن",
        "device_headset_connected": "توصيل السماعة",
        "device_headset_disconnected": "فصل السماعة",
        "device_bluetooth": "جهاز بلوتوث",
        "builder_title": "مهمة جديدة",
    },
    "values-de": {
        "revert_when_ends": "Am Ende der Aufgabe zurücksetzen",
        "device_screen_on": "Bildschirm an",
        "device_screen_off": "Bildschirm aus",
        "device_power_connected": "Laden gestartet",
        "device_power_disconnected": "Laden gestoppt",
        "device_headset_connected": "Kopfhörer verbunden",
        "device_headset_disconnected": "Kopfhörer getrennt",
        "device_bluetooth": "Bluetooth-Gerät",
        "builder_title": "Neue Aufgabe",
    },
    "values-es": {
        "revert_when_ends": "Revertir al finalizar la tarea",
        "device_screen_on": "Pantalla encendida",
        "device_screen_off": "Pantalla apagada",
        "device_power_connected": "Carga iniciada",
        "device_power_disconnected": "Carga detenida",
        "device_headset_connected": "Auriculares conectados",
        "device_headset_disconnected": "Auriculares desconectados",
        "device_bluetooth": "Dispositivo Bluetooth",
        "builder_title": "Nueva tarea",
    },
    "values-fr": {
        "revert_when_ends": "Rétablir à la fin de la tâche",
        "device_screen_on": "Écran allumé",
        "device_screen_off": "Écran éteint",
        "device_power_connected": "Charge démarrée",
        "device_power_disconnected": "Charge arrêtée",
        "device_headset_connected": "Casque connecté",
        "device_headset_disconnected": "Casque déconnecté",
        "device_bluetooth": "Appareil Bluetooth",
        "builder_title": "Nouvelle tâche",
    },
    "values-hi": {
        "revert_when_ends": "कार्य समाप्त होने पर पुनर्स्थापित करें",
        "device_screen_on": "स्क्रीन चालू",
        "device_screen_off": "स्क्रीन बंद",
        "device_power_connected": "चार्जिंग शुरू",
        "device_power_disconnected": "चार्जिंग रुकी",
        "device_headset_connected": "हेडसेट कनेक्टेड",
        "device_headset_disconnected": "हेडसेट डिस्कनेक्टेड",
        "device_bluetooth": "ब्लूटूथ डिवाइस",
        "builder_title": "नया कार्य",
    },
    "values-ja": {
        "revert_when_ends": "タスク終了時に元に戻す",
        "device_screen_on": "画面オン",
        "device_screen_off": "画面オフ",
        "device_power_connected": "充電開始",
        "device_power_disconnected": "充電停止",
        "device_headset_connected": "ヘッドセット接続",
        "device_headset_disconnected": "ヘッドセット切断",
        "device_bluetooth": "Bluetoothデバイス",
        "builder_title": "新しいタスク",
    },
    "values-pt": {
        "revert_when_ends": "Reverter ao terminar a tarefa",
        "device_screen_on": "Tela ligada",
        "device_screen_off": "Tela desligada",
        "device_power_connected": "Carregamento iniciado",
        "device_power_disconnected": "Carregamento parado",
        "device_headset_connected": "Fone conectado",
        "device_headset_disconnected": "Fone desconectado",
        "device_bluetooth": "Dispositivo Bluetooth",
        "builder_title": "Nova tarefa",
    },
    "values-ru": {
        "revert_when_ends": "Вернуть при завершении задачи",
        "device_screen_on": "Экран включён",
        "device_screen_off": "Экран выключен",
        "device_power_connected": "Зарядка началась",
        "device_power_disconnected": "Зарядка остановлена",
        "device_headset_connected": "Наушники подключены",
        "device_headset_disconnected": "Наушники отключены",
        "device_bluetooth": "Устройство Bluetooth",
        "builder_title": "Новая задача",
    },
    "values-tr": {
        "revert_when_ends": "Görev bitince geri al",
        "device_screen_on": "Ekran açık",
        "device_screen_off": "Ekran kapalı",
        "device_power_connected": "Şarj başladı",
        "device_power_disconnected": "Şarj durdu",
        "device_headset_connected": "Kulaklık bağlandı",
        "device_headset_disconnected": "Kulaklık çıkarıldı",
        "device_bluetooth": "Bluetooth cihazı",
        "builder_title": "Yeni görev",
    },
    "values-zh-rCN": {
        "revert_when_ends": "任务结束时还原",
        "device_screen_on": "屏幕开启",
        "device_screen_off": "屏幕关闭",
        "device_power_connected": "开始充电",
        "device_power_disconnected": "停止充电",
        "device_headset_connected": "耳机已连接",
        "device_headset_disconnected": "耳机已断开",
        "device_bluetooth": "蓝牙设备",
        "builder_title": "新任务",
    },
}


def update_file(path, new_map, change_map, remove_keys):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False

    for key, value in new_map.items():
        safe = value.replace("'", "\\'")
        if re.search(r'name="%s"' % re.escape(key), content):
            continue
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

    for key in remove_keys:
        pattern = r'\s*<string name="%s">.*?</string>\n?' % re.escape(key)
        new_content, n = re.subn(pattern, "\n", content)
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
        new_map = {k: translations[k] for k in NEW if k in translations}
        change_map = {k: translations[k] for k in CHANGE if k in translations}
        if update_file(path, new_map, change_map, REMOVE):
            total += len(new_map) + len(change_map)
            print("UPDATED: %s" % path)
    print("TOTAL keys touched: %d" % total)


if __name__ == "__main__":
    main()
