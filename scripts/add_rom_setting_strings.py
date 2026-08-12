# -*- coding: utf-8 -*-
"""Adds the ROM setting trigger strings to every locale except values/ (English).

Line-based and EOL-preserving; idempotent (skips keys that already exist).
"""
import io
import os
import re

BASE = "feature/automation-builder/src/main/res"

translations = {
    "ar": {
        "trigger_type_rom_setting": "إعداد ROM",
        "rom_setting_namespace": "مساحة الأسماء",
        "rom_setting_namespace_system": "النظام",
        "rom_setting_namespace_secure": "آمن",
        "rom_setting_namespace_global": "عام",
        "rom_setting_key": "مفتاح الإعداد",
        "rom_setting_pick_from_rom": "اختر مفتاحاً من هذا الروم",
        "rom_setting_pick_title": "إعدادات الروم الموجودة على هذا الجهاز",
        "rom_setting_pick_empty": "لا توجد مفاتيح Evolution X / LineageOS. هذه الميزة تحتاج root أو Shizuku أو تثبيت النظام.",
        "rom_setting_current_value": "الحالي: %1$s",
        "rom_setting_operator": "الشرط",
        "rom_setting_equals": "يساوي",
        "rom_setting_not_equals": "لا يساوي",
        "rom_setting_value": "القيمة المستهدفة",
        "rom_setting_hint": "يعمل عند تطابق إعداد الروم الحقيقي. يُقرأ من الجهاز دون أذونات.",
    },
    "de": {
        "trigger_type_rom_setting": "ROM-Einstellung",
        "rom_setting_namespace": "Namensraum",
        "rom_setting_namespace_system": "System",
        "rom_setting_namespace_secure": "Secure",
        "rom_setting_namespace_global": "Global",
        "rom_setting_key": "Einstellungsschlüssel",
        "rom_setting_pick_from_rom": "Schlüssel aus diesem ROM wählen",
        "rom_setting_pick_title": "Auf diesem Gerät gefundene ROM-Einstellungen",
        "rom_setting_pick_empty": "Keine Evolution X / LineageOS-Schlüssel gefunden. Diese Funktion benötigt root, Shizuku oder eine Systeminstallation.",
        "rom_setting_current_value": "Aktuell: %1$s",
        "rom_setting_operator": "Bedingung",
        "rom_setting_equals": "Gleich",
        "rom_setting_not_equals": "Ungleich",
        "rom_setting_value": "Zielwert",
        "rom_setting_hint": "Läuft, wenn die echte ROM-Einstellung übereinstimmt. Vom Gerät gelesen, keine Berechtigung nötig.",
    },
    "es": {
        "trigger_type_rom_setting": "Ajuste de ROM",
        "rom_setting_namespace": "Espacio de nombres",
        "rom_setting_namespace_system": "Sistema",
        "rom_setting_namespace_secure": "Seguro",
        "rom_setting_namespace_global": "Global",
        "rom_setting_key": "Clave del ajuste",
        "rom_setting_pick_from_rom": "Elegir una clave de esta ROM",
        "rom_setting_pick_title": "Ajustes de ROM encontrados en este dispositivo",
        "rom_setting_pick_empty": "No se encontraron claves de Evolution X / LineageOS. Esta función necesita root, Shizuku o una instalación de sistema.",
        "rom_setting_current_value": "Actual: %1$s",
        "rom_setting_operator": "Condición",
        "rom_setting_equals": "Igual",
        "rom_setting_not_equals": "No igual",
        "rom_setting_value": "Valor objetivo",
        "rom_setting_hint": "Se ejecuta cuando el ajuste real de la ROM coincide. Se lee del dispositivo, sin permisos.",
    },
    "fr": {
        "trigger_type_rom_setting": "Réglage de la ROM",
        "rom_setting_namespace": "Espace de noms",
        "rom_setting_namespace_system": "Système",
        "rom_setting_namespace_secure": "Sécurisé",
        "rom_setting_namespace_global": "Global",
        "rom_setting_key": "Clé du réglage",
        "rom_setting_pick_from_rom": "Choisir une clé de cette ROM",
        "rom_setting_pick_title": "Réglages de la ROM trouvés sur cet appareil",
        "rom_setting_pick_empty": "Aucune clé Evolution X / LineageOS trouvée. Cette fonction nécessite root, Shizuku ou une installation système.",
        "rom_setting_current_value": "Actuel : %1$s",
        "rom_setting_operator": "Condition",
        "rom_setting_equals": "Égal",
        "rom_setting_not_equals": "Non égal",
        "rom_setting_value": "Valeur cible",
        "rom_setting_hint": "Se déclenche quand le réglage réel de la ROM correspond. Lu depuis l'appareil, sans autorisation.",
    },
    "hi": {
        "trigger_type_rom_setting": "ROM सेटिंग",
        "rom_setting_namespace": "नेमस्पेस",
        "rom_setting_namespace_system": "सिस्टम",
        "rom_setting_namespace_secure": "सुरक्षित",
        "rom_setting_namespace_global": "ग्लोबल",
        "rom_setting_key": "सेटिंग कुंजी",
        "rom_setting_pick_from_rom": "इस ROM से कुंजी चुनें",
        "rom_setting_pick_title": "इस डिवाइस पर मिली ROM सेटिंग्स",
        "rom_setting_pick_empty": "कोई Evolution X / LineageOS कुंजी नहीं मिली। इस सुविधा के लिए root, Shizuku या सिस्टम इंस्टॉल चाहिए।",
        "rom_setting_current_value": "वर्तमान: %1$s",
        "rom_setting_operator": "शर्त",
        "rom_setting_equals": "बराबर",
        "rom_setting_not_equals": "बराबर नहीं",
        "rom_setting_value": "लक्ष्य मान",
        "rom_setting_hint": "जब वास्तविक ROM सेटिंग मेल खाती है तो चलता है। बिना अनुमति डिवाइस से पढ़ा जाता है।",
    },
    "ja": {
        "trigger_type_rom_setting": "ROM設定",
        "rom_setting_namespace": "名前空間",
        "rom_setting_namespace_system": "システム",
        "rom_setting_namespace_secure": "セキュア",
        "rom_setting_namespace_global": "グローバル",
        "rom_setting_key": "設定キー",
        "rom_setting_pick_from_rom": "このROMからキーを選ぶ",
        "rom_setting_pick_title": "このデバイスで見つかったROM設定",
        "rom_setting_pick_empty": "Evolution X / LineageOS のキーが見つかりません。この機能には root、Shizuku、またはシステムインストールが必要です。",
        "rom_setting_current_value": "現在: %1$s",
        "rom_setting_operator": "条件",
        "rom_setting_equals": "等しい",
        "rom_setting_not_equals": "等しくない",
        "rom_setting_value": "目標値",
        "rom_setting_hint": "実際のROM設定が一致したときに実行します。権限なしでデバイスから読み取ります。",
    },
    "pt": {
        "trigger_type_rom_setting": "Configuração da ROM",
        "rom_setting_namespace": "Espaço de nomes",
        "rom_setting_namespace_system": "Sistema",
        "rom_setting_namespace_secure": "Seguro",
        "rom_setting_namespace_global": "Global",
        "rom_setting_key": "Chave da configuração",
        "rom_setting_pick_from_rom": "Escolher uma chave desta ROM",
        "rom_setting_pick_title": "Configurações da ROM encontradas neste dispositivo",
        "rom_setting_pick_empty": "Nenhuma chave Evolution X / LineageOS encontrada. Este recurso precisa de root, Shizuku ou instalação de sistema.",
        "rom_setting_current_value": "Atual: %1$s",
        "rom_setting_operator": "Condição",
        "rom_setting_equals": "Igual",
        "rom_setting_not_equals": "Diferente",
        "rom_setting_value": "Valor alvo",
        "rom_setting_hint": "Executa quando a configuração real da ROM corresponde. Lido do dispositivo, sem permissões.",
    },
    "ru": {
        "trigger_type_rom_setting": "Настройка ROM",
        "rom_setting_namespace": "Пространство имён",
        "rom_setting_namespace_system": "Система",
        "rom_setting_namespace_secure": "Защищённые",
        "rom_setting_namespace_global": "Глобальные",
        "rom_setting_key": "Ключ настройки",
        "rom_setting_pick_from_rom": "Выбрать ключ из этой ROM",
        "rom_setting_pick_title": "Настройки ROM, найденные на устройстве",
        "rom_setting_pick_empty": "Ключи Evolution X / LineageOS не найдены. Эта функция требует root, Shizuku или установки в систему.",
        "rom_setting_current_value": "Текущее: %1$s",
        "rom_setting_operator": "Условие",
        "rom_setting_equals": "Равно",
        "rom_setting_not_equals": "Не равно",
        "rom_setting_value": "Целевое значение",
        "rom_setting_hint": "Срабатывает, когда реальная настройка ROM совпадает. Читается с устройства без разрешений.",
    },
    "tr": {
        "trigger_type_rom_setting": "ROM ayarı",
        "rom_setting_namespace": "Ad alanı",
        "rom_setting_namespace_system": "Sistem",
        "rom_setting_namespace_secure": "Güvenli",
        "rom_setting_namespace_global": "Genel",
        "rom_setting_key": "Ayar anahtarı",
        "rom_setting_pick_from_rom": "Bu ROM'dan bir anahtar seç",
        "rom_setting_pick_title": "Bu cihazda bulunan ROM ayarları",
        "rom_setting_pick_empty": "Evolution X / LineageOS anahtarı bulunamadı. Bu özellik root, Shizuku veya sistem kurulumu gerektirir.",
        "rom_setting_current_value": "Geçerli: %1$s",
        "rom_setting_operator": "Koşul",
        "rom_setting_equals": "Eşittir",
        "rom_setting_not_equals": "Eşit değil",
        "rom_setting_value": "Hedef değer",
        "rom_setting_hint": "Gerçek ROM ayarı eşleştiğinde çalışır. İzin gerekmeden cihazdan okunur.",
    },
    "zh-rCN": {
        "trigger_type_rom_setting": "ROM 设置",
        "rom_setting_namespace": "命名空间",
        "rom_setting_namespace_system": "系统",
        "rom_setting_namespace_secure": "安全",
        "rom_setting_namespace_global": "全局",
        "rom_setting_key": "设置键",
        "rom_setting_pick_from_rom": "从此 ROM 选择键",
        "rom_setting_pick_title": "在此设备上找到的 ROM 设置",
        "rom_setting_pick_empty": "未找到 Evolution X / LineageOS 键。此功能需要 root、Shizuku 或系统安装。",
        "rom_setting_current_value": "当前：%1$s",
        "rom_setting_operator": "条件",
        "rom_setting_equals": "等于",
        "rom_setting_not_equals": "不等于",
        "rom_setting_value": "目标值",
        "rom_setting_hint": "当真实 ROM 设置匹配时运行。无需权限，从设备读取。",
    },
}

ANCHOR = '<string name="webhook_url_hint">'

def main():
    for locale, strings in translations.items():
        path = os.path.join(BASE, "values-" + locale, "strings.xml")
        if not os.path.isfile(path):
            print(f"SKIP {locale}: no file")
            continue
        with io.open(path, "r", encoding="utf-8", newline="") as f:
            content = f.read()
        # Detect EOL style
        eol = "\r\n" if "\r\n" in content else "\n"
        lines = content.splitlines()
        existing = {re.match(r'\s*<string name="([^"]+)"', ln).group(1)
                    for ln in lines if '<string name="' in ln}
        new_lines = []
        inserted = False
        for ln in lines:
            new_lines.append(ln)
            if not inserted and '<string name="webhook_url_hint">' in ln:
                for key in sorted(strings.keys()):
                    if key in existing:
                        continue
                    new_lines.append(f'    <string name="{key}">{strings[key]}</string>')
                inserted = True
        if not inserted:
            print(f"WARN {locale}: anchor not found")
        with io.open(path, "w", encoding="utf-8", newline="") as f:
            f.write(eol.join(new_lines) + eol)
        print(f"OK {locale}: {len(strings)} strings")

if __name__ == "__main__":
    main()
