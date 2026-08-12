"""Add the location-check + use-current-location strings to every locale.

Idempotent: skips keys that already exist. Mirrors scripts/add_sensor_strings.py.
"""
import glob
import os
import re

SETTINGS = {
    "section_location": {
        "values": "Location",
        "ar": "الموقع",
        "de": "Standort",
        "es": "Ubicación",
        "fr": "Localisation",
        "hi": "स्थान",
        "ja": "位置情報",
        "pt": "Localização",
        "ru": "Местоположение",
        "tr": "Konum",
        "zh-rCN": "位置信息",
    },
    "location_check_interval": {
        "values": "Verify location when switched off",
        "ar": "التحقق من الموقع عند تعطيله",
        "de": "Standort prüfen, wenn ausgeschaltet",
        "es": "Verificar ubicación cuando está desactivada",
        "fr": "Vérifier la position quand elle est désactivée",
        "hi": "बंद होने पर स्थान सत्यापित करें",
        "ja": "オフ時の位置情報チェック",
        "pt": "Verificar localização quando desativada",
        "ru": "Проверять местоположение, когда оно выключено",
        "tr": "Kapalıyken konumu doğrula",
        "zh-rCN": "关闭时验证位置信息",
    },
    "location_check_interval_manual_sub": {
        "values": "Check only when you tap \"Use my location\" inside a task",
        "ar": "التحقق فقط عند الضغط على «استخدام موقعي» داخل مهمة",
        "de": "Nur prüfen, wenn du in einer Aufgabe auf „Meinen Standort verwenden“ tippst",
        "es": "Comprobar solo al pulsar «Usar mi ubicación» en una tarea",
        "fr": "Vérifier uniquement quand vous touchez « Utiliser ma position » dans une tâche",
        "hi": "किसी कार्य में «मेरा स्थान उपयोग करें» दबाने पर ही जाँचें",
        "ja": "タスク内で「現在地を使用」をタップした時のみチェック",
        "pt": "Verificar apenas ao tocar em «Usar minha localização» em uma tarefa",
        "ru": "Проверять только при нажатии «Использовать моё местоположение» в задаче",
        "tr": "Yalnızca bir görevde «Konumumu kullan»a dokunduğunda denetle",
        "zh-rCN": "仅在任务中点击“使用我的位置”时检查",
    },
    "location_check_interval_auto_sub": {
        "values": "Auto-check in the background at a fixed interval",
        "ar": "تحقق تلقائي في الخلفية بفاصل زمني ثابت",
        "de": "Automatische Hintergrundprüfung in festen Abständen",
        "es": "Verificación automática en segundo plano a intervalos fijos",
        "fr": "Vérification automatique en arrière-plan à intervalle fixe",
        "hi": "निश्चित अंतराल पर पृष्ठभूमि में स्वतः जाँचें",
        "ja": "一定間隔でバックグラウンド自動チェック",
        "pt": "Verificação automática em segundo plano em intervalos fixos",
        "ru": "Автоматическая проверка в фоне через заданный интервал",
        "tr": "Sabit aralıklarla arka planda otomatik denetle",
        "zh-rCN": "按固定间隔在后台自动检查",
    },
    "location_check_title": {
        "values": "Location check interval",
        "ar": "مدة التحقق من الموقع",
        "de": "Standort-Prüfintervall",
        "es": "Intervalo de verificación de ubicación",
        "fr": "Intervalle de vérification de la position",
        "hi": "स्थान जाँच अंतराल",
        "ja": "位置情報チェック間隔",
        "pt": "Intervalo de verificação de localização",
        "ru": "Интервал проверки местоположения",
        "tr": "Konum denetim aralığı",
        "zh-rCN": "位置信息检查间隔",
    },
    "location_check_manual": {
        "values": "Manual",
        "ar": "يدوي",
        "de": "Manuell",
        "es": "Manual",
        "fr": "Manuel",
        "hi": "मैन्युअल",
        "ja": "手動",
        "pt": "Manual",
        "ru": "Вручную",
        "tr": "Manuel",
        "zh-rCN": "手动",
    },
    "location_check_15": {
        "values": "Every 15 minutes",
        "ar": "كل 15 دقيقة",
        "de": "Alle 15 Minuten",
        "es": "Cada 15 minutos",
        "fr": "Toutes les 15 minutes",
        "hi": "हर 15 मिनट",
        "ja": "15分ごと",
        "pt": "A cada 15 minutos",
        "ru": "Каждые 15 минут",
        "tr": "Her 15 dakikada bir",
        "zh-rCN": "每15分钟",
    },
    "location_check_30": {
        "values": "Every 30 minutes",
        "ar": "كل 30 دقيقة",
        "de": "Alle 30 Minuten",
        "es": "Cada 30 minutos",
        "fr": "Toutes les 30 minutes",
        "hi": "हर 30 मिनट",
        "ja": "30分ごと",
        "pt": "A cada 30 minutos",
        "ru": "Каждые 30 минут",
        "tr": "Her 30 dakikada bir",
        "zh-rCN": "每30分钟",
    },
    "location_check_60": {
        "values": "Every hour",
        "ar": "كل ساعة",
        "de": "Jede Stunde",
        "es": "Cada hora",
        "fr": "Toutes les heures",
        "hi": "हर घंटे",
        "ja": "1時間ごと",
        "pt": "A cada hora",
        "ru": "Каждый час",
        "tr": "Her saat",
        "zh-rCN": "每小时",
    },
    "location_check_180": {
        "values": "Every 3 hours",
        "ar": "كل 3 ساعات",
        "de": "Alle 3 Stunden",
        "es": "Cada 3 horas",
        "fr": "Toutes les 3 heures",
        "hi": "हर 3 घंटे",
        "ja": "3時間ごと",
        "pt": "A cada 3 horas",
        "ru": "Каждые 3 часа",
        "tr": "Her 3 saatte bir",
        "zh-rCN": "每3小时",
    },
    "location_check_360": {
        "values": "Every 6 hours",
        "ar": "كل 6 ساعات",
        "de": "Alle 6 Stunden",
        "es": "Cada 6 horas",
        "fr": "Toutes les 6 heures",
        "hi": "हर 6 घंटे",
        "ja": "6時間ごと",
        "pt": "A cada 6 horas",
        "ru": "Каждые 6 часов",
        "tr": "Her 6 saatte bir",
        "zh-rCN": "每6小时",
    },
}

BUILDER = {
    "use_current_location": {
        "values": "Use my current location",
        "ar": "استخدام موقعي الحالي",
        "de": "Meinen aktuellen Standort verwenden",
        "es": "Usar mi ubicación actual",
        "fr": "Utiliser ma position actuelle",
        "hi": "मेरा वर्तमान स्थान उपयोग करें",
        "ja": "現在地を使用",
        "pt": "Usar minha localização atual",
        "ru": "Использовать моё текущее местоположение",
        "tr": "Mevcut konumumu kullan",
        "zh-rCN": "使用我的当前位置",
    },
    "location_fix_failed": {
        "values": "\"Couldn't get your location. Enable location and try again.\"",
        "ar": "تعذّر تحديد موقعك الحالي. فعّل الموقع ثم أعد المحاولة.",
        "de": "Standort konnte nicht ermittelt werden. Aktiviere den Standort und versuche es erneut.",
        "es": "No se pudo obtener tu ubicación. Activa la ubicación e inténtalo de nuevo.",
        "fr": "\"Impossible d'obtenir votre position. Activez la position et réessayez.\"",
        "hi": "आपका स्थान प्राप्त नहीं हो सका। स्थान सक्षम करके पुनः प्रयास करें।",
        "ja": "現在地を取得できませんでした。位置情報を有効にして再試行してください。",
        "pt": "Não foi possível obter sua localização. Ative a localização e tente novamente.",
        "ru": "Не удалось получить местоположение. Включите его и попробуйте снова.",
        "tr": "Konumunuz alınamadı. Konumu açıp tekrar deneyin.",
        "zh-rCN": "无法获取您的位置。请开启位置信息后重试。",
    },
    "map_unavailable": {
        "values": "No map app found",
        "ar": "لا يوجد تطبيق خرائط",
        "de": "Keine Karten-App gefunden",
        "es": "No se encontró ninguna app de mapas",
        "fr": "Aucune application de cartes trouvée",
        "hi": "कोई मानचित्र ऐप नहीं मिला",
        "ja": "地図アプリが見つかりません",
        "pt": "Nenhum app de mapas encontrado",
        "ru": "Приложение карт не найдено",
        "tr": "Harita uygulaması bulunamadı",
        "zh-rCN": "未找到地图应用",
    },
}


def norm(p: str) -> str:
    return p.replace("\\", "/")


def locale_of(p: str) -> str:
    base = norm(p)
    if "/values/" in base:
        return "values"
    m = re.search(r"/values-([^/]+)/", base)
    return m.group(1) if m else "values"


def insert(res: dict, module_root: str) -> None:
    for pattern in ("**/src/main/res/values/strings.xml", "**/src/main/res/values-*/strings.xml"):
        for p in glob.glob(os.path.join(module_root, pattern), recursive=True):
            p = norm(p)
            if "/build/" in p:
                continue
            loc = locale_of(p)
            text = open(p, encoding="utf-8").read()
            original = text
            for key, by_locale in res.items():
                if f'name="{key}"' in text:
                    continue
                value = by_locale.get(loc, by_locale["values"])
                entry = f'    <string name="{key}">{value}</string>\r\n'
                if text.rstrip().endswith("</resources>"):
                    text = text.rstrip()[:-len("</resources>")] + entry + "</resources>\r\n"
                else:
                    text = text.rstrip() + "\r\n" + entry
            if text != original:
                open(p, "w", encoding="utf-8", newline="").write(text)
                print(f"updated {p} [{loc}]")


insert(SETTINGS, "feature/settings")
insert(BUILDER, "feature/automation-builder")
print("DONE")
