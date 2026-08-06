"""Add CALENDAR-trigger strings to all 10 locales across affected modules."""
import glob
import io
import os
import re

# key -> {locale-suffix-or-empty: value}
STRINGS = {
    # --- feature/automation-builder ---
    "trigger_type_calendar": {
        "": "Calendar", "ar": "التقويم", "de": "Kalender", "es": "Calendario",
        "fr": "Calendrier", "hi": "कैलेंडर", "ja": "カレンダー", "pt": "Calendário",
        "ru": "Календарь", "tr": "Takvim", "zh-rCN": "日历"
    },
    "trigger_calendar": {
        "": "Calendar event", "ar": "حدث التقويم", "de": "Kalenderereignis", "es": "Evento del calendario",
        "fr": "Événement du calendrier", "hi": "कैलेंडर इवेंट", "ja": "カレンダーの予定", "pt": "Evento do calendário",
        "ru": "Событие календаря", "tr": "Takvim etkinliği", "zh-rCN": "日历事件"
    },
    "any_calendar": {
        "": "Any calendar", "ar": "أي تقويم", "de": "Beliebiger Kalender", "es": "Cualquier calendario",
        "fr": "N'importe quel calendrier", "hi": "कोई भी कैलेंडर", "ja": "すべてのカレンダー", "pt": "Qualquer calendário",
        "ru": "Любой календарь", "tr": "Herhangi bir takvim", "zh-rCN": "任意日历"
    },
    "choose_calendar": {
        "": "Choose a calendar", "ar": "اختر تقويماً", "de": "Kalender auswählen", "es": "Elige un calendario",
        "fr": "Choisir un calendrier", "hi": "कैलेंडर चुनें", "ja": "カレンダーを選択", "pt": "Escolher um calendário",
        "ru": "Выберите календарь", "tr": "Takvim seçin", "zh-rCN": "选择日历"
    },
    "no_calendars_found": {
        "": "No calendars found", "ar": "لا توجد تقاويم", "de": "Keine Kalender gefunden", "es": "No se encontraron calendarios",
        "fr": "Aucun calendrier trouvé", "hi": "कोई कैलेंडर नहीं मिला", "ja": "カレンダーが見つかりません", "pt": "Nenhum calendário encontrado",
        "ru": "Календари не найдены", "tr": "Takvim bulunamadı", "zh-rCN": "未找到日历"
    },
    "calendar_contains": {
        "": "Event contains", "ar": "الحدث يحتوي", "de": "Ereignis enthält", "es": "El evento contiene",
        "fr": "L'événement contient", "hi": "इवेंट में शामिल है", "ja": "予定に含まれる文字", "pt": "O evento contém",
        "ru": "Событие содержит", "tr": "Etkinlik şunları içeriyor", "zh-rCN": "事件包含"
    },
    "calendar_contains_hint": {
        "": "e.g. meeting, dentist (blank = any)", "ar": "مثال: اجتماع، طبيب (فارغ = أي شيء)",
        "de": "z. B. Meeting, Zahnarzt (leer = beliebig)", "es": "p. ej. reunión, dentista (vacío = cualquiera)",
        "fr": "ex. réunion, dentiste (vide = n'importe)", "hi": "जैसे मीटिंग, डेंटिस्ट (खाली = कोई भी)",
        "ja": "例: 会議、歯医者 (空欄 = すべて)", "pt": "ex. reunião, dentista (vazio = qualquer)",
        "ru": "напр. встреча, стоматолог (пусто = любое)", "tr": "örn. toplantı, dişçi (boş = herhangi biri)",
        "zh-rCN": "例如：会议、牙医（留空 = 任意）"
    },
    "calendar_event_start": {
        "": "Starts", "ar": "عند البدء", "de": "Beginnt", "es": "Empieza",
        "fr": "Commence", "hi": "शुरू होता है", "ja": "開始時", "pt": "Começa",
        "ru": "Начало", "tr": "Başlar", "zh-rCN": "开始时"
    },
    "calendar_event_end": {
        "": "Ends", "ar": "عند الانتهاء", "de": "Endet", "es": "Termina",
        "fr": "Se termine", "hi": "समाप्त होता है", "ja": "終了時", "pt": "Termina",
        "ru": "Окончание", "tr": "Biter", "zh-rCN": "结束时"
    },
    "calendar_event_created": {
        "": "Added to calendar", "ar": "عند الإضافة للتقويم", "de": "Zum Kalender hinzugefügt", "es": "Añadido al calendario",
        "fr": "Ajouté au calendrier", "hi": "कैलेंडर में जोड़ा गया", "ja": "予定が追加された時", "pt": "Adicionado ao calendário",
        "ru": "Добавлено в календарь", "tr": "Takvime eklendi", "zh-rCN": "添加到日历时"
    },
    "calendar_before_none": {
        "": "At event start", "ar": "عند بداية الحدث", "de": "Bei Ereignisbeginn", "es": "Al inicio del evento",
        "fr": "Au début de l'événement", "hi": "इवेंट शुरू होने पर", "ja": "予定開始時", "pt": "No início do evento",
        "ru": "В начале события", "tr": "Etkinlik başlangıcında", "zh-rCN": "事件开始时"
    },
    "calendar_before_minutes": {
        "": "Before event: %1$d min", "ar": "قبل الحدث: %1$d دقيقة", "de": "Vor dem Ereignis: %1$d Min", "es": "Antes del evento: %1$d min",
        "fr": "Avant l'événement : %1$d min", "hi": "इवेंट से पहले: %1$d मिनट", "ja": "予定の%1$d分前", "pt": "Antes do evento: %1$d min",
        "ru": "До события: %1$d мин", "tr": "Etkinlikten önce: %1$d dk", "zh-rCN": "事件前：%1$d 分钟"
    },
    "calendar_permission_hint": {
        "": "Needs calendar permission to read events", "ar": "يتطلب إذن التقويم لقراءة الأحداث",
        "de": "Benötigt Kalenderberechtigung zum Lesen von Ereignissen", "es": "Necesita permiso de calendario para leer eventos",
        "fr": "Nécessite l'autorisation du calendrier pour lire les événements", "hi": "इवेंट पढ़ने के लिए कैलेंडर अनुमति चाहिए",
        "ja": "予定を読むにはカレンダーの権限が必要です", "pt": "Requer permissão de calendário para ler eventos",
        "ru": "Требуется разрешение календаря для чтения событий", "tr": "Etkinlikleri okumak için takvim izni gerekir",
        "zh-rCN": "需要日历权限才能读取事件"
    },
    # --- feature/automations ---
    "trigger_calendar_sub": {
        "": "When a calendar event matches", "ar": "عندما يطابق حدث تقويم", "de": "Wenn ein Kalenderereignis passt", "es": "Cuando un evento del calendario coincide",
        "fr": "Lorsqu'un événement du calendrier correspond", "hi": "जब कैलेंडर इवेंट मेल खाता है", "ja": "カレンダーの予定が一致した時", "pt": "Quando um evento do calendário corresponde",
        "ru": "Когда событие календаря совпадает", "tr": "Bir takvim etkinliği eşleştiğinde", "zh-rCN": "当日历事件匹配时"
    },
    # --- feature/settings ---
    "calendar_permission": {
        "": "Calendar", "ar": "التقويم", "de": "Kalender", "es": "Calendario",
        "fr": "Calendrier", "hi": "कैलेंडर", "ja": "カレンダー", "pt": "Calendário",
        "ru": "Календарь", "tr": "Takvim", "zh-rCN": "日历"
    },
    "calendar_permission_sub": {
        "": "Read your events for calendar triggers", "ar": "قراءة أحداثك لمشغلات التقويم",
        "de": "Ereignisse für Kalender-Trigger lesen", "es": "Leer tus eventos para los disparadores de calendario",
        "fr": "Lire vos événements pour les déclencheurs de calendrier", "hi": "कैलेंडर ट्रिगर्स के लिए अपने इवेंट पढ़ें",
        "ja": "カレンダートリガー用に予定を読み取ります", "pt": "Ler seus eventos para gatilhos de calendário",
        "ru": "Чтение событий для календарных триггеров", "tr": "Takvim tetikleyicileri için etkinliklerinizi okuyun",
        "zh-rCN": "读取日历事件用于日历触发器"
    },
}

MODULES = [
    ("feature/automation-builder/src/main/res", [
        "trigger_type_calendar", "trigger_calendar", "any_calendar", "choose_calendar",
        "no_calendars_found", "calendar_contains", "calendar_contains_hint",
        "calendar_event_start", "calendar_event_end", "calendar_event_created",
        "calendar_before_none", "calendar_before_minutes", "calendar_permission_hint"
    ]),
    ("feature/automations/src/main/res", ["trigger_calendar", "trigger_calendar_sub"]),
    ("feature/dashboard/src/main/res", ["trigger_calendar"]),
    ("feature/settings/src/main/res", ["calendar_permission", "calendar_permission_sub"]),
]

LOCALES = ["", "ar", "de", "es", "fr", "hi", "ja", "pt", "ru", "tr", "zh-rCN"]


def normalize(path):
    return os.path.normpath(path).replace("\\", "/")


def locale_of(path):
    m = re.search(r"values-([a-z]{2}(?:-r[A-Z]{2})?)", path)
    return m.group(1) if m else ""


def add_string(path, key, value):
    # io.open on Windows needs native separators.
    native = os.path.normpath(path)
    with io.open(native, "r", encoding="utf-8-sig") as f:
        content = f.read()
    newline = "\r\n" if "\r\n" in content else "\n"
    if re.search(r'<string name="%s">' % re.escape(key), content):
        return False
    # Insert before </resources>
    line = '    <string name="%s">%s</string>' % (key, value.replace("&", "&amp;").replace('"', '\\"'))
    content = content.rstrip()
    if content.endswith("</resources>"):
        content = content[: -len("</resources>")].rstrip() + newline + line + newline + "</resources>" + newline
    else:
        content += newline + line + newline
    # Atomic write: temp file + rename, so a concurrent lock or crash can never
    # leave the target XML truncated.
    tmp = native + ".tmp"
    with io.open(tmp, "w", encoding="utf-8", newline="") as f:
        f.write(content)
    os.replace(tmp, native)
    return True


changed = 0
for mod, keys in MODULES:
    for path in glob.glob(normalize(mod) + "/values*/strings.xml"):
        path = normalize(path)
        loc = locale_of(path)
        for key in keys:
            value = STRINGS[key].get(loc)
            if value is None:
                continue
            if add_string(path, key, value):
                changed += 1
                print("ADDED:", path.replace("\\", "/"), key)

print("Total strings added:", changed)
