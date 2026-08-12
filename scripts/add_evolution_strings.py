# -*- coding: utf-8 -*-
"""Adds the Evolution X integration strings to every locale except values/ (English).

Line-based and EOL-preserving; idempotent (skips keys that already exist).
"""
import io
import os
import re

BASE = "core/capability-manager/src/main/res"

translations = {
    "ar": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "إصدار الروم: %1$s",
        "evolution_build_type": "نوع البناء: %1$s",
        "evolution_system_app_hint": "ثبّت التطبيق كتطبيق نظام لفتح كل القدرات المميزة دون root لكل أمر.",
        "evolution_install_system_app": "تثبيت كتطبيق نظام",
        "evolution_install_system_app_confirm": "إنشاء وحدة Magisk تضع NexaFlow في /system/priv-app مع القائمة المميزة الكاملة؟ إعادة التشغيل تفعّلها. تبقى الوحدة قابلة للإزالة من تطبيق Magisk.",
        "evolution_install_done": "تم تثبيت الوحدة. أعد التشغيل لتفعيل NexaFlow كتطبيق نظام.",
        "evolution_install_failed": "فشل التثبيت",
        "evolution_custom_settings": "إعدادات الروم المخصصة",
        "evolution_custom_settings_sub": "تصفّح مفاتيح Evolver الخاصة بـ Evolution X / LineageOS الموجودة على الجهاز",
        "evolution_no_custom_settings": "لا توجد إعدادات مخصصة — شغّل مع root أو Shizuku لعرضها",
        "evolution_setting_value": "القيمة: %1$s",
        "evolution_key_required": "المفتاح",
        "evolution_setting_write": "كتابة",
        "evolution_edit_setting": "تعديل الإعداد",
        "cancel": "إلغاء",
        "save": "حفظ",
        "ok": "موافق",
        "evolution_already_system_app": "أنت تطبيق نظام بالفعل",
        "evolution_not_elevated": "مطلوب root أو Shizuku",
        "evolution_cancel": "إلغاء",
    },
    "de": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "ROM-Version: %1$s",
        "evolution_build_type": "Build-Typ: %1$s",
        "evolution_system_app_hint": "Als System-App installieren, um jede privilegierte Fähigkeit ohne Root pro Befehl freizuschalten.",
        "evolution_install_system_app": "Als System-App installieren",
        "evolution_install_system_app_confirm": "Ein Magisk-Modul erstellen, das NexaFlow mit der vollständigen privilegierten Whitelist in /system/priv-app platziert? Neustart aktiviert es. Das Modul bleibt im Magisk-App entfernbar.",
        "evolution_install_done": "Modul installiert. Neustart, um NexaFlow als System-App zu aktivieren.",
        "evolution_install_failed": "Installation fehlgeschlagen",
        "evolution_custom_settings": "Benutzerdefinierte ROM-Einstellungen",
        "evolution_custom_settings_sub": "Evolver-Schlüssel von Evolution X / LineageOS auf diesem Gerät durchsuchen",
        "evolution_no_custom_settings": "Keine benutzerdefinierten ROM-Einstellungen gefunden — mit Root oder Shizuku ausführen, um sie aufzulisten",
        "evolution_setting_value": "Wert: %1$s",
        "evolution_key_required": "Schlüssel",
        "evolution_setting_write": "Schreiben",
        "evolution_edit_setting": "Einstellung bearbeiten",
        "cancel": "Abbrechen",
        "save": "Speichern",
        "ok": "OK",
        "evolution_already_system_app": "Bereits eine System-App",
        "evolution_not_elevated": "Root oder Shizuku erforderlich",
        "evolution_cancel": "Abbrechen",
    },
    "es": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "Versión del ROM: %1$s",
        "evolution_build_type": "Tipo de compilación: %1$s",
        "evolution_system_app_hint": "Instálalo como app de sistema para desbloquear todas las capacidades privilegiadas sin root por comando.",
        "evolution_install_system_app": "Instalar como app de sistema",
        "evolution_install_system_app_confirm": "¿Crear un módulo Magisk que coloque NexaFlow en /system/priv-app con la lista blanca privilegiada completa? El reinicio lo activa. El módulo sigue siendo removible desde la app de Magisk.",
        "evolution_install_done": "Módulo instalado. Reinicia para activar NexaFlow como app de sistema.",
        "evolution_install_failed": "Instalación fallida",
        "evolution_custom_settings": "Ajustes personalizados del ROM",
        "evolution_custom_settings_sub": "Explorar las claves Evolver de Evolution X / LineageOS en este dispositivo",
        "evolution_no_custom_settings": "No se encontraron ajustes personalizados — ejecuta con root o Shizuku para listarlos",
        "evolution_setting_value": "Valor: %1$s",
        "evolution_key_required": "Clave",
        "evolution_setting_write": "Escribir",
        "evolution_edit_setting": "Editar ajuste",
        "cancel": "Cancelar",
        "save": "Guardar",
        "ok": "Aceptar",
        "evolution_already_system_app": "Ya es una app de sistema",
        "evolution_not_elevated": "Se requiere root o Shizuku",
        "evolution_cancel": "Cancelar",
    },
    "fr": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "Version du ROM : %1$s",
        "evolution_build_type": "Type de build : %1$s",
        "evolution_system_app_hint": "Installez comme application système pour débloquer toutes les capacités privilégiées sans root par commande.",
        "evolution_install_system_app": "Installer comme application système",
        "evolution_install_system_app_confirm": "Créer un module Magisk qui place NexaFlow dans /system/priv-app avec la liste blanche privilégiée complète ? Le redémarrage l'active. Le module reste supprimable depuis l'application Magisk.",
        "evolution_install_done": "Module installé. Redémarrez pour activer NexaFlow comme application système.",
        "evolution_install_failed": "Échec de l'installation",
        "evolution_custom_settings": "Paramètres ROM personnalisés",
        "evolution_custom_settings_sub": "Parcourir les clés Evolver d'Evolution X / LineageOS présentes sur cet appareil",
        "evolution_no_custom_settings": "Aucun paramètre personnalisé trouvé — exécutez avec root ou Shizuku pour les lister",
        "evolution_setting_value": "Valeur : %1$s",
        "evolution_key_required": "Clé",
        "evolution_setting_write": "Écrire",
        "evolution_edit_setting": "Modifier le paramètre",
        "cancel": "Annuler",
        "save": "Enregistrer",
        "ok": "OK",
        "evolution_already_system_app": "Déjà une application système",
        "evolution_not_elevated": "Root ou Shizuku requis",
        "evolution_cancel": "Annuler",
    },
    "hi": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "ROM संस्करण: %1$s",
        "evolution_build_type": "बिल्ड प्रकार: %1$s",
        "evolution_system_app_hint": "प्रत्येक कमांड के लिए root के बिना सभी विशेषाधिकार क्षमताओं को अनलॉक करने के लिए सिस्टम ऐप के रूप में इंस्टॉल करें।",
        "evolution_install_system_app": "सिस्टम ऐप के रूप में इंस्टॉल करें",
        "evolution_install_system_app_confirm": "एक Magisk मॉड्यूल बनाएं जो NexaFlow को पूर्ण विशेषाधिकार व्हाइटलिस्ट के साथ /system/priv-app में रखता है? रिबूट इसे सक्रिय करता है। मॉड्यूल Magisk ऐप से हटाने योग्य रहता है।",
        "evolution_install_done": "मॉड्यूल इंस्टॉल हो गया। NexaFlow को सिस्टम ऐप के रूप में सक्रिय करने के लिए रिबूट करें।",
        "evolution_install_failed": "इंस्टॉल विफल",
        "evolution_custom_settings": "कस्टम ROM सेटिंग्स",
        "evolution_custom_settings_sub": "इस डिवाइस पर मौजूद Evolution X / LineageOS Evolver कुंजियाँ ब्राउज़ करें",
        "evolution_no_custom_settings": "कोई कस्टम ROM सेटिंग नहीं मिली — सूचीबद्ध करने के लिए root या Shizuku के साथ चलाएँ",
        "evolution_setting_value": "मान: %1$s",
        "evolution_key_required": "कुंजी",
        "evolution_setting_write": "लिखें",
        "evolution_edit_setting": "सेटिंग संपादित करें",
        "cancel": "रद्द करें",
        "save": "सहेजें",
        "ok": "ठीक है",
        "evolution_already_system_app": "पहले से ही सिस्टम ऐप",
        "evolution_not_elevated": "root या Shizuku आवश्यक",
        "evolution_cancel": "रद्द करें",
    },
    "ja": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "ROMバージョン: %1$s",
        "evolution_build_type": "ビルドタイプ: %1$s",
        "evolution_system_app_hint": "システムアプリとしてインストールすると、コマンドごとのrootなしで全ての特権機能が解放されます。",
        "evolution_install_system_app": "システムアプリとしてインストール",
        "evolution_install_system_app_confirm": "NexaFlowを完全な特権ホワイトリスト付きで/system/priv-appに配置するMagiskモジュールを作成しますか? 再起動で有効になります。モジュールはMagiskアプリから削除可能です。",
        "evolution_install_done": "モジュールがインストールされました。再起動してNexaFlowをシステムアプリとして有効にしてください。",
        "evolution_install_failed": "インストールに失敗しました",
        "evolution_custom_settings": "カスタムROM設定",
        "evolution_custom_settings_sub": "このデバイスにあるEvolution X / LineageOSのEvolverキーを閲覧",
        "evolution_no_custom_settings": "カスタムROM設定が見つかりません — rootまたはShizukuで実行して一覧表示してください",
        "evolution_setting_value": "値: %1$s",
        "evolution_key_required": "キー",
        "evolution_setting_write": "書き込み",
        "evolution_edit_setting": "設定を編集",
        "cancel": "キャンセル",
        "save": "保存",
        "ok": "OK",
        "evolution_already_system_app": "すでにシステムアプリです",
        "evolution_not_elevated": "rootまたはShizukuが必要です",
        "evolution_cancel": "キャンセル",
    },
    "pt": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "Versão do ROM: %1$s",
        "evolution_build_type": "Tipo de compilação: %1$s",
        "evolution_system_app_hint": "Instale como aplicativo de sistema para desbloquear todas as capacidades privilegiadas sem root por comando.",
        "evolution_install_system_app": "Instalar como aplicativo de sistema",
        "evolution_install_system_app_confirm": "Criar um módulo Magisk que coloca o NexaFlow em /system/priv-app com a lista de permissões privilegiadas completa? A reinicialização o ativa. O módulo permanece removível no app do Magisk.",
        "evolution_install_done": "Módulo instalado. Reinicie para ativar o NexaFlow como aplicativo de sistema.",
        "evolution_install_failed": "Falha na instalação",
        "evolution_custom_settings": "Configurações personalizadas do ROM",
        "evolution_custom_settings_sub": "Navegar pelas chaves Evolver do Evolution X / LineageOS neste dispositivo",
        "evolution_no_custom_settings": "Nenhuma configuração personalizada encontrada — execute com root ou Shizuku para listá-las",
        "evolution_setting_value": "Valor: %1$s",
        "evolution_key_required": "Chave",
        "evolution_setting_write": "Escrever",
        "evolution_edit_setting": "Editar configuração",
        "cancel": "Cancelar",
        "save": "Salvar",
        "ok": "OK",
        "evolution_already_system_app": "Já é um aplicativo de sistema",
        "evolution_not_elevated": "Requer root ou Shizuku",
        "evolution_cancel": "Cancelar",
    },
    "ru": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "Версия ROM: %1$s",
        "evolution_build_type": "Тип сборки: %1$s",
        "evolution_system_app_hint": "Установите как системное приложение, чтобы разблокировать все привилегированные возможности без root для каждой команды.",
        "evolution_install_system_app": "Установить как системное приложение",
        "evolution_install_system_app_confirm": "Создать модуль Magisk, размещающий NexaFlow в /system/priv-app с полным привилегированным списком? Перезагрузка активирует его. Модуль можно удалить из приложения Magisk.",
        "evolution_install_done": "Модуль установлен. Перезагрузите устройство, чтобы активировать NexaFlow как системное приложение.",
        "evolution_install_failed": "Ошибка установки",
        "evolution_custom_settings": "Пользовательские настройки ROM",
        "evolution_custom_settings_sub": "Просмотр ключей Evolver Evolution X / LineageOS на этом устройстве",
        "evolution_no_custom_settings": "Пользовательские настройки не найдены — запустите с root или Shizuku, чтобы вывести их",
        "evolution_setting_value": "Значение: %1$s",
        "evolution_key_required": "Ключ",
        "evolution_setting_write": "Записать",
        "evolution_edit_setting": "Изменить настройку",
        "cancel": "Отмена",
        "save": "Сохранить",
        "ok": "ОК",
        "evolution_already_system_app": "Уже системное приложение",
        "evolution_not_elevated": "Требуется root или Shizuku",
        "evolution_cancel": "Отмена",
    },
    "tr": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "ROM sürümü: %1$s",
        "evolution_build_type": "Derleme türü: %1$s",
        "evolution_system_app_hint": "Her komut için root olmadan tüm ayrıcalıklı yetenekleri açmak için sistem uygulaması olarak kurun.",
        "evolution_install_system_app": "Sistem uygulaması olarak kur",
        "evolution_install_system_app_confirm": "NexaFlow'u tam ayrıcalıklı beyaz listeyle /system/priv-app içine yerleştiren bir Magisk modülü oluşturulsun mu? Yeniden başlatma etkinleştirir. Modül Magisk uygulamasından kaldırılabilir kalır.",
        "evolution_install_done": "Modül kuruldu. NexaFlow'u sistem uygulaması olarak etkinleştirmek için yeniden başlatın.",
        "evolution_install_failed": "Kurulum başarısız",
        "evolution_custom_settings": "Özel ROM ayarları",
        "evolution_custom_settings_sub": "Bu cihazdaki Evolution X / LineageOS Evolver anahtarlarına göz atın",
        "evolution_no_custom_settings": "Özel ROM ayarı bulunamadı — listelemek için root veya Shizuku ile çalıştırın",
        "evolution_setting_value": "Değer: %1$s",
        "evolution_key_required": "Anahtar",
        "evolution_setting_write": "Yaz",
        "evolution_edit_setting": "Ayarı düzenle",
        "cancel": "İptal",
        "save": "Kaydet",
        "ok": "Tamam",
        "evolution_already_system_app": "Zaten bir sistem uygulaması",
        "evolution_not_elevated": "root veya Shizuku gerekli",
        "evolution_cancel": "İptal",
    },
    "zh-rCN": {
        "section_evolution": "Evolution X",
        "evolution_rom_version": "ROM 版本: %1$s",
        "evolution_build_type": "构建类型: %1$s",
        "evolution_system_app_hint": "安装为系统应用，无需为每条命令 root 即可解锁所有特权能力。",
        "evolution_install_system_app": "安装为系统应用",
        "evolution_install_system_app_confirm": "创建 Magisk 模块，将 NexaFlow 连同完整特权白名单放入 /system/priv-app？重启后生效。模块可从 Magisk 应用移除。",
        "evolution_install_done": "模块已安装。重启后激活 NexaFlow 为系统应用。",
        "evolution_install_failed": "安装失败",
        "evolution_custom_settings": "自定义 ROM 设置",
        "evolution_custom_settings_sub": "浏览此设备上 Evolution X / LineageOS 的 Evolver 键",
        "evolution_no_custom_settings": "未找到自定义 ROM 设置 — 使用 root 或 Shizuku 运行以列出",
        "evolution_setting_value": "值: %1$s",
        "evolution_key_required": "键",
        "evolution_setting_write": "写入",
        "evolution_edit_setting": "编辑设置",
        "cancel": "取消",
        "save": "保存",
        "ok": "确定",
        "evolution_already_system_app": "已是系统应用",
        "evolution_not_elevated": "需要 root 或 Shizuku",
        "evolution_cancel": "取消",
    },
}

NEW_KEYS = [
    "section_evolution", "evolution_rom_version", "evolution_build_type",
    "evolution_system_app_hint", "evolution_install_system_app",
    "evolution_install_system_app_confirm", "evolution_install_done",
    "evolution_install_failed", "evolution_custom_settings",
    "evolution_custom_settings_sub", "evolution_no_custom_settings",
    "evolution_setting_value", "evolution_key_required",
    "evolution_setting_write", "evolution_edit_setting",
    "cancel", "save", "ok",
    "evolution_already_system_app", "evolution_not_elevated", "evolution_cancel",
]


def xml_escape(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;")
             .replace(">", "&gt;").replace('"', "&quot;"))


def main():
    with io.open(os.path.join(BASE, "values", "strings.xml"), encoding="utf-8") as f:
        english = f.read()

    for locale in sorted(translations.keys()):
        path = os.path.join(BASE, "values-%s" % locale, "strings.xml")
        with io.open(path, encoding="utf-8") as f:
            content = f.read()
        eol = "\r\n" if "\r\n" in content else "\n"
        closing = "</resources>"
        idx = content.rfind(closing)
        if idx == -1:
            print("!! %s: no closing tag" % path)
            continue
        body = content[:idx]
        added = []
        for key in NEW_KEYS:
            if re.search(r'<string name="%s"' % re.escape(key), content):
                continue
            val = translations[locale].get(key)
            if val is None:
                print("!! %s missing translation for %s" % (locale, key))
                continue
            added.append('    <string name="%s">%s</string>' % (key, xml_escape(val)))
        if added:
            new_content = body + eol.join(added) + eol + closing + eol
            with io.open(path, "w", encoding="utf-8", newline="") as f:
                f.write(new_content)
            print("OK %s: +%d strings" % (path, len(added)))
        else:
            print("OK %s: no changes" % path)


if __name__ == "__main__":
    main()
