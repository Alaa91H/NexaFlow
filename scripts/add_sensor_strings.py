"""Add localized sensor-trigger strings (P2-2) across all 10 locales using safe
atomic writes. Values containing an apostrophe are wrapped in double quotes per
the project's XML convention (unescaped ' breaks aapt2)."""
import os
import re
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LOCALES = {
    "values": {
        "trigger_type_sensor": "Sensor",
        "sensor_kind": "Sensor",
        "sensor_proximity": "Proximity",
        "sensor_shake": "Shake",
        "sensor_light": "Light",
        "sensor_step": "Steps",
        "sensor_event_covered": "Covered",
        "sensor_event_uncovered": "Uncovered",
        "sensor_event_above": "Above",
        "sensor_event_below": "Below",
        "sensor_threshold_label": "Above %d lux",
        "sensor_sensitivity_label": "Sensitivity: %d",
        "sensor_step_hint": "Runs every time you take a step.",
        "sensor_permission_hint": "Step counting needs the activity recognition permission.",
    },
    "values-ar": {
        "trigger_type_sensor": "مستشعر",
        "sensor_kind": "نوع المستشعر",
        "sensor_proximity": "القرب",
        "sensor_shake": "اهتزاز",
        "sensor_light": "الضوء",
        "sensor_step": "الخطوات",
        "sensor_event_covered": "مغطى",
        "sensor_event_uncovered": "مكشوف",
        "sensor_event_above": "أعلى من",
        "sensor_event_below": "أقل من",
        "sensor_threshold_label": "أعلى من %d لوكس",
        "sensor_sensitivity_label": "الحساسية: %d",
        "sensor_step_hint": "تُنفَّذ مع كل خطوة تخطوها.",
        "sensor_permission_hint": "عدّ الخطوات يتطلب إذن التعرف على النشاط.",
    },
    "values-de": {
        "trigger_type_sensor": "Sensor",
        "sensor_kind": "Sensorart",
        "sensor_proximity": "Nähe",
        "sensor_shake": "Schütteln",
        "sensor_light": "Licht",
        "sensor_step": "Schritte",
        "sensor_event_covered": "Abgedeckt",
        "sensor_event_uncovered": "Aufgedeckt",
        "sensor_event_above": "Über",
        "sensor_event_below": "Unter",
        "sensor_threshold_label": "Über %d Lux",
        "sensor_sensitivity_label": "Empfindlichkeit: %d",
        "sensor_step_hint": "Wird bei jedem Schritt ausgeführt.",
        "sensor_permission_hint": "Die Schritterkennung benötigt die Berechtigung zur Aktivitätserkennung.",
    },
    "values-es": {
        "trigger_type_sensor": "Sensor",
        "sensor_kind": "Tipo de sensor",
        "sensor_proximity": "Proximidad",
        "sensor_shake": "Agitar",
        "sensor_light": "Luz",
        "sensor_step": "Pasos",
        "sensor_event_covered": "Cubierto",
        "sensor_event_uncovered": "Descubierto",
        "sensor_event_above": "Por encima",
        "sensor_event_below": "Por debajo",
        "sensor_threshold_label": "Por encima de %d lux",
        "sensor_sensitivity_label": "Sensibilidad: %d",
        "sensor_step_hint": "Se ejecuta con cada paso.",
        "sensor_permission_hint": "El conteo de pasos necesita el permiso de reconocimiento de actividad.",
    },
    "values-fr": {
        "trigger_type_sensor": "Capteur",
        "sensor_kind": "Type de capteur",
        "sensor_proximity": "Proximité",
        "sensor_shake": "Secousse",
        "sensor_light": "Lumière",
        "sensor_step": "Pas",
        "sensor_event_covered": "Couvert",
        "sensor_event_uncovered": "Découvert",
        "sensor_event_above": "Au-dessus",
        "sensor_event_below": "En dessous",
        "sensor_threshold_label": "Au-dessus de %d lux",
        "sensor_sensitivity_label": "Sensibilité: %d",
        "sensor_step_hint": "S'exécute à chaque pas.",
        "sensor_permission_hint": "Le comptage de pas nécessite l'autorisation de reconnaissance d'activité.",
    },
    "values-hi": {
        "trigger_type_sensor": "सेंसर",
        "sensor_kind": "सेंसर प्रकार",
        "sensor_proximity": "निकटता",
        "sensor_shake": "हिलाना",
        "sensor_light": "रोशनी",
        "sensor_step": "कदम",
        "sensor_event_covered": "ढका हुआ",
        "sensor_event_uncovered": "खुला",
        "sensor_event_above": "ऊपर",
        "sensor_event_below": "नीचे",
        "sensor_threshold_label": "%d लक्स से ऊपर",
        "sensor_sensitivity_label": "संवेदनशीलता: %d",
        "sensor_step_hint": "हर कदम पर चलता है।",
        "sensor_permission_hint": "कदम गिनती के लिए गतिविधि पहचान की अनुमति चाहिए।",
    },
    "values-ja": {
        "trigger_type_sensor": "センサー",
        "sensor_kind": "センサーの種類",
        "sensor_proximity": "近接",
        "sensor_shake": "シェイク",
        "sensor_light": "光",
        "sensor_step": "歩数",
        "sensor_event_covered": "覆われた",
        "sensor_event_uncovered": "覆われていない",
        "sensor_event_above": "以上",
        "sensor_event_below": "以下",
        "sensor_threshold_label": "%d ルクス以上",
        "sensor_sensitivity_label": "感度: %d",
        "sensor_step_hint": "歩くたびに実行されます。",
        "sensor_permission_hint": "歩数計測にはアクティビティ認識の許可が必要です。",
    },
    "values-pt": {
        "trigger_type_sensor": "Sensor",
        "sensor_kind": "Tipo de sensor",
        "sensor_proximity": "Proximidade",
        "sensor_shake": "Agitar",
        "sensor_light": "Luz",
        "sensor_step": "Passos",
        "sensor_event_covered": "Coberto",
        "sensor_event_uncovered": "Descoberto",
        "sensor_event_above": "Acima",
        "sensor_event_below": "Abaixo",
        "sensor_threshold_label": "Acima de %d lux",
        "sensor_sensitivity_label": "Sensibilidade: %d",
        "sensor_step_hint": "Executa a cada passo.",
        "sensor_permission_hint": "A contagem de passos precisa da permissão de reconhecimento de atividade.",
    },
    "values-ru": {
        "trigger_type_sensor": "Датчик",
        "sensor_kind": "Тип датчика",
        "sensor_proximity": "Приближение",
        "sensor_shake": "Встряска",
        "sensor_light": "Свет",
        "sensor_step": "Шаги",
        "sensor_event_covered": "Закрыт",
        "sensor_event_uncovered": "Открыт",
        "sensor_event_above": "Выше",
        "sensor_event_below": "Ниже",
        "sensor_threshold_label": "Выше %d лк",
        "sensor_sensitivity_label": "Чувствительность: %d",
        "sensor_step_hint": "Запускается при каждом шаге.",
        "sensor_permission_hint": "Подсчёт шагов требует разрешения на распознавание активности.",
    },
    "values-tr": {
        "trigger_type_sensor": "Sensör",
        "sensor_kind": "Sensör türü",
        "sensor_proximity": "Yakınlık",
        "sensor_shake": "Sallama",
        "sensor_light": "Işık",
        "sensor_step": "Adımlar",
        "sensor_event_covered": "Kapalı",
        "sensor_event_uncovered": "Açık",
        "sensor_event_above": "Üstünde",
        "sensor_event_below": "Altında",
        "sensor_threshold_label": "%d lüks üzerinde",
        "sensor_sensitivity_label": "Hassasiyet: %d",
        "sensor_step_hint": "Her adımda çalışır.",
        "sensor_permission_hint": "Adım sayımı, etkinlik tanıma izni gerektirir.",
    },
    "values-zh-rCN": {
        "trigger_type_sensor": "传感器",
        "sensor_kind": "传感器类型",
        "sensor_proximity": "距离",
        "sensor_shake": "摇晃",
        "sensor_light": "光线",
        "sensor_step": "步数",
        "sensor_event_covered": "遮挡",
        "sensor_event_uncovered": "未遮挡",
        "sensor_event_above": "高于",
        "sensor_event_below": "低于",
        "sensor_threshold_label": "高于 %d 勒克斯",
        "sensor_sensitivity_label": "灵敏度: %d",
        "sensor_step_hint": "每走一步都会运行。",
        "sensor_permission_hint": "计步需要活动识别权限。",
    },
}


def update_file(path, new_map):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    changed = False
    for key, value in new_map.items():
        if re.search(r'name="%s"' % re.escape(key), content):
            continue
        if "'" in value:
            snippet = '    <string name="%s">"%s"</string>\n' % (key, value)
        else:
            snippet = '    <string name="%s">%s</string>\n' % (key, value)
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
