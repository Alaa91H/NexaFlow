"""Insert the Quick Settings Tiles strings into all 10 locale files of feature/widgets."""
import io
import os
import re
import tempfile

BASE = r"feature\widgets\src\main\res"
ROOT = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.normpath(os.path.join(ROOT, "..", BASE))

STRINGS = {
    "": {
        "section_tiles": "QUICK SETTINGS TILES",
        "tile_1_label": "Task 1",
        "tile_2_label": "Task 2",
        "tile_3_label": "Task 3",
        "tile_4_label": "Task 4",
        "tile_1_desc": "Toggle the first task straight from the Quick Settings panel.",
        "tile_2_desc": "Toggle the second task straight from the Quick Settings panel.",
        "tile_3_desc": "Toggle the third task straight from the Quick Settings panel.",
        "tile_4_desc": "Toggle the fourth task straight from the Quick Settings panel.",
        "tile_add": "Add to Quick Settings",
        "tile_requires_android_13": "Requires Android 13+",
        "tile_controls": "Controls",
        "tile_automatic": "Automatic (first enabled task)",
        "tile_choose_task": "Choose task",
        "tile_cancel": "Cancel",
        "tile_unbound": "No task",
    },
    "ar": {
        "section_tiles": "بلاطات الإعدادات السريعة",
        "tile_1_label": "المهمة 1",
        "tile_2_label": "المهمة 2",
        "tile_3_label": "المهمة 3",
        "tile_4_label": "المهمة 4",
        "tile_1_desc": "بدّل المهمة الأولى مباشرة من لوحة الإعدادات السريعة.",
        "tile_2_desc": "بدّل المهمة الثانية مباشرة من لوحة الإعدادات السريعة.",
        "tile_3_desc": "بدّل المهمة الثالثة مباشرة من لوحة الإعدادات السريعة.",
        "tile_4_desc": "بدّل المهمة الرابعة مباشرة من لوحة الإعدادات السريعة.",
        "tile_add": "إضافة إلى الإعدادات السريعة",
        "tile_requires_android_13": "يتطلب أندرويد 13 أو أحدث",
        "tile_controls": "تتحكم في",
        "tile_automatic": "تلقائي (أول مهمة مفعّلة)",
        "tile_choose_task": "اختر المهمة",
        "tile_cancel": "إلغاء",
        "tile_unbound": "لا توجد مهمة",
    },
    "de": {
        "section_tiles": "SCHNELLEINSTELLUNGEN-KACHELN",
        "tile_1_label": "Aufgabe 1",
        "tile_2_label": "Aufgabe 2",
        "tile_3_label": "Aufgabe 3",
        "tile_4_label": "Aufgabe 4",
        "tile_1_desc": "Schalte die erste Aufgabe direkt über die Schnelleinstellungen.",
        "tile_2_desc": "Schalte die zweite Aufgabe direkt über die Schnelleinstellungen.",
        "tile_3_desc": "Schalte die dritte Aufgabe direkt über die Schnelleinstellungen.",
        "tile_4_desc": "Schalte die vierte Aufgabe direkt über die Schnelleinstellungen.",
        "tile_add": "Zu Schnelleinstellungen hinzufügen",
        "tile_requires_android_13": "Erfordert Android 13+",
        "tile_controls": "Steuert",
        "tile_automatic": "Automatisch (erste aktive Aufgabe)",
        "tile_choose_task": "Aufgabe auswählen",
        "tile_cancel": "Abbrechen",
        "tile_unbound": "Keine Aufgabe",
    },
    "es": {
        "section_tiles": "MOSAICOS DE AJUSTES RÁPIDOS",
        "tile_1_label": "Tarea 1",
        "tile_2_label": "Tarea 2",
        "tile_3_label": "Tarea 3",
        "tile_4_label": "Tarea 4",
        "tile_1_desc": "Alterna la primera tarea directamente desde los ajustes rápidos.",
        "tile_2_desc": "Alterna la segunda tarea directamente desde los ajustes rápidos.",
        "tile_3_desc": "Alterna la tercera tarea directamente desde los ajustes rápidos.",
        "tile_4_desc": "Alterna la cuarta tarea directamente desde los ajustes rápidos.",
        "tile_add": "Añadir a ajustes rápidos",
        "tile_requires_android_13": "Requiere Android 13+",
        "tile_controls": "Controla",
        "tile_automatic": "Automático (primera tarea activa)",
        "tile_choose_task": "Elegir tarea",
        "tile_cancel": "Cancelar",
        "tile_unbound": "Sin tarea",
    },
    "fr": {
        "section_tiles": "TUILES DE RÉGLAGES RAPIDES",
        "tile_1_label": "Tâche 1",
        "tile_2_label": "Tâche 2",
        "tile_3_label": "Tâche 3",
        "tile_4_label": "Tâche 4",
        "tile_1_desc": "Basculez la première tâche directement depuis les réglages rapides.",
        "tile_2_desc": "Basculez la deuxième tâche directement depuis les réglages rapides.",
        "tile_3_desc": "Basculez la troisième tâche directement depuis les réglages rapides.",
        "tile_4_desc": "Basculez la quatrième tâche directement depuis les réglages rapides.",
        "tile_add": "Ajouter aux réglages rapides",
        "tile_requires_android_13": "Nécessite Android 13+",
        "tile_controls": "Contrôle",
        "tile_automatic": "Automatique (première tâche active)",
        "tile_choose_task": "Choisir une tâche",
        "tile_cancel": "Annuler",
        "tile_unbound": "Aucune tâche",
    },
    "hi": {
        "section_tiles": "त्वरित सेटिंग्स टाइलें",
        "tile_1_label": "कार्य 1",
        "tile_2_label": "कार्य 2",
        "tile_3_label": "कार्य 3",
        "tile_4_label": "कार्य 4",
        "tile_1_desc": "पहले कार्य को त्वरित सेटिंग्स पैनल से सीधे टॉगल करें।",
        "tile_2_desc": "दूसरे कार्य को त्वरित सेटिंग्स पैनल से सीधे टॉगल करें।",
        "tile_3_desc": "तीसरे कार्य को त्वरित सेटिंग्स पैनल से सीधे टॉगल करें।",
        "tile_4_desc": "चौथे कार्य को त्वरित सेटिंग्स पैनल से सीधे टॉगल करें।",
        "tile_add": "त्वरित सेटिंग्स में जोड़ें",
        "tile_requires_android_13": "Android 13+ आवश्यक",
        "tile_controls": "नियंत्रित करता है",
        "tile_automatic": "स्वचालित (पहला सक्षम कार्य)",
        "tile_choose_task": "कार्य चुनें",
        "tile_cancel": "रद्द करें",
        "tile_unbound": "कोई कार्य नहीं",
    },
    "ja": {
        "section_tiles": "クイック設定タイル",
        "tile_1_label": "タスク 1",
        "tile_2_label": "タスク 2",
        "tile_3_label": "タスク 3",
        "tile_4_label": "タスク 4",
        "tile_1_desc": "クイック設定パネルから最初のタスクを直接切り替えます。",
        "tile_2_desc": "クイック設定パネルから2番目のタスクを直接切り替えます。",
        "tile_3_desc": "クイック設定パネルから3番目のタスクを直接切り替えます。",
        "tile_4_desc": "クイック設定パネルから4番目のタスクを直接切り替えます。",
        "tile_add": "クイック設定に追加",
        "tile_requires_android_13": "Android 13+が必要です",
        "tile_controls": "操作対象",
        "tile_automatic": "自動（最初の有効なタスク）",
        "tile_choose_task": "タスクを選択",
        "tile_cancel": "キャンセル",
        "tile_unbound": "タスクなし",
    },
    "pt": {
        "section_tiles": "BLOCO DE AJUSTES RÁPIDOS",
        "tile_1_label": "Tarefa 1",
        "tile_2_label": "Tarefa 2",
        "tile_3_label": "Tarefa 3",
        "tile_4_label": "Tarefa 4",
        "tile_1_desc": "Alterne a primeira tarefa diretamente no painel de ajustes rápidos.",
        "tile_2_desc": "Alterne a segunda tarefa diretamente no painel de ajustes rápidos.",
        "tile_3_desc": "Alterne a terceira tarefa diretamente no painel de ajustes rápidos.",
        "tile_4_desc": "Alterne a quarta tarefa diretamente no painel de ajustes rápidos.",
        "tile_add": "Adicionar aos ajustes rápidos",
        "tile_requires_android_13": "Requer Android 13+",
        "tile_controls": "Controla",
        "tile_automatic": "Automático (primeira tarefa ativa)",
        "tile_choose_task": "Escolher tarefa",
        "tile_cancel": "Cancelar",
        "tile_unbound": "Sem tarefa",
    },
    "ru": {
        "section_tiles": "ПЛИТКИ БЫСТРЫХ НАСТРОЕК",
        "tile_1_label": "Задача 1",
        "tile_2_label": "Задача 2",
        "tile_3_label": "Задача 3",
        "tile_4_label": "Задача 4",
        "tile_1_desc": "Переключайте первую задачу прямо с панели быстрых настроек.",
        "tile_2_desc": "Переключайте вторую задачу прямо с панели быстрых настроек.",
        "tile_3_desc": "Переключайте третью задачу прямо с панели быстрых настроек.",
        "tile_4_desc": "Переключайте четвёртую задачу прямо с панели быстрых настроек.",
        "tile_add": "Добавить в быстрые настройки",
        "tile_requires_android_13": "Требуется Android 13+",
        "tile_controls": "Управляет",
        "tile_automatic": "Автоматически (первая активная задача)",
        "tile_choose_task": "Выбрать задачу",
        "tile_cancel": "Отмена",
        "tile_unbound": "Нет задачи",
    },
    "tr": {
        "section_tiles": "HIZLI AYARLAR DÖŞEMELERİ",
        "tile_1_label": "Görev 1",
        "tile_2_label": "Görev 2",
        "tile_3_label": "Görev 3",
        "tile_4_label": "Görev 4",
        "tile_1_desc": "İlk görevi doğrudan hızlı ayarlar panelinden değiştirin.",
        "tile_2_desc": "İkinci görevi doğrudan hızlı ayarlar panelinden değiştirin.",
        "tile_3_desc": "Üçüncü görevi doğrudan hızlı ayarlar panelinden değiştirin.",
        "tile_4_desc": "Dördüncü görevi doğrudan hızlı ayarlar panelinden değiştirin.",
        "tile_add": "Hızlı ayarlara ekle",
        "tile_requires_android_13": "Android 13+ gerekir",
        "tile_controls": "Kontrol eder",
        "tile_automatic": "Otomatik (ilk etkin görev)",
        "tile_choose_task": "Görev seç",
        "tile_cancel": "İptal",
        "tile_unbound": "Görev yok",
    },
    "zh-rCN": {
        "section_tiles": "快捷设置磁贴",
        "tile_1_label": "任务 1",
        "tile_2_label": "任务 2",
        "tile_3_label": "任务 3",
        "tile_4_label": "任务 4",
        "tile_1_desc": "直接从快捷设置面板切换第一个任务。",
        "tile_2_desc": "直接从快捷设置面板切换第二个任务。",
        "tile_3_desc": "直接从快捷设置面板切换第三个任务。",
        "tile_4_desc": "直接从快捷设置面板切换第四个任务。",
        "tile_add": "添加到快捷设置",
        "tile_requires_android_13": "需要 Android 13+",
        "tile_controls": "控制",
        "tile_automatic": "自动（第一个已启用的任务）",
        "tile_choose_task": "选择任务",
        "tile_cancel": "取消",
        "tile_unbound": "无任务",
    },
}


def atomic_write(path, text):
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), suffix=".tmp")
    try:
        with io.open(fd, "w", encoding="utf-8", newline="") as f:
            f.write(text)
        os.replace(tmp, path)
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def main():
    for suffix, entries in STRINGS.items():
        folder = os.path.join(BASE, "values" + ("-" + suffix if suffix else ""))
        path = os.path.join(folder, "strings.xml")
        if not os.path.exists(path):
            print("MISSING:", path)
            continue
        with io.open(path, "r", encoding="utf-8", newline="") as f:
            text = f.read()
        newline = "\r\n" if "\r\n" in text else "\n"
        added = []
        for key, value in entries.items():
            if re.search(r'name="' + key + r'"', text):
                continue
            escaped = value.replace("\\", "\\\\").replace('"', '\\"')
            line = newline + '    <string name="' + key + '">' + escaped + "</string>"
            text = text.replace("</resources>", line + newline + "</resources>")
            added.append(key)
        if added:
            atomic_write(path, text)
            print("ADDED", suffix or "default", len(added), "keys ->", os.path.basename(path))
        else:
            print("SKIP ", suffix or "default")


if __name__ == "__main__":
    main()
