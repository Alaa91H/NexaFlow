# -*- coding: utf-8 -*-
"""Adds Task-6 notification strings to all locale files of the affected modules."""
import io, os, sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> {locale-suffix: translation}
# locale suffix "" is the default values/ folder.
STRINGS = {
    "trigger_type_notification": {
        "": "Notification", "-ar": "الإشعارات", "-de": "Benachrichtigung",
        "-es": "Notificación", "-fr": "Notification", "-hi": "सूचना",
        "-ja": "通知", "-pt": "Notificação", "-ru": "Уведомление",
        "-tr": "Bildirim", "-zh-rCN": "通知",
    },
    "trigger_notification": {
        "": "App notification", "-ar": "إشعار التطبيق", "-de": "App-Benachrichtigung",
        "-es": "Notificación de app", "-fr": "Notification d'app", "-hi": "ऐप सूचना",
        "-ja": "アプリの通知", "-pt": "Notificação do app", "-ru": "Уведомление приложения",
        "-tr": "Uygulama bildirimi", "-zh-rCN": "应用通知",
    },
    "trigger_notification_sub": {
        "": "Trigger when an app posts a notification", "-ar": "تشغيل عند إصدار تطبيق إشعاراً",
        "-de": "Auslösen, wenn eine App eine Benachrichtigung sendet",
        "-es": "Activar cuando una app publique una notificación",
        "-fr": "Déclencher quand une app publie une notification",
        "-hi": "जब कोई ऐप सूचना भेजे तो ट्रिगर करें",
        "-ja": "アプリが通知を投稿したときに実行",
        "-pt": "Executar quando um app publicar uma notificação",
        "-ru": "Срабатывать, когда приложение отправляет уведомление",
        "-tr": "Bir uygulama bildirim gönderdiğinde tetikle",
        "-zh-rCN": "当应用发布通知时触发",
    },
    "notification_contains": {
        "": "Keyword (optional)", "-ar": "كلمة مفتاحية (اختياري)", "-de": "Stichwort (optional)",
        "-es": "Palabra clave (opcional)", "-fr": "Mot-clé (facultatif)", "-hi": "कीवर्ड (वैकल्पिक)",
        "-ja": "キーワード（任意）", "-pt": "Palavra-chave (opcional)", "-ru": "Ключевое слово (необязательно)",
        "-tr": "Anahtar kelime (isteğe bağlı)", "-zh-rCN": "关键词（可选）",
    },
    "notification_contains_hint": {
        "": "e.g. delivery, message", "-ar": "مثال: توصيل، رسالة", "-de": "z. B. Lieferung, Nachricht",
        "-es": "p. ej. entrega, mensaje", "-fr": "ex. livraison, message", "-hi": "जैसे डिलीवरी, संदेश",
        "-ja": "例: 配達、メッセージ", "-pt": "ex.: entrega, mensagem", "-ru": "например: доставка, сообщение",
        "-tr": "örn. teslimat, mesaj", "-zh-rCN": "例如：配送、消息",
    },
    "any_app": {
        "": "Any app", "-ar": "أي تطبيق", "-de": "Beliebige App",
        "-es": "Cualquier app", "-fr": "Toute application", "-hi": "कोई भी ऐप",
        "-ja": "すべてのアプリ", "-pt": "Qualquer app", "-ru": "Любое приложение",
        "-tr": "Herhangi bir uygulama", "-zh-rCN": "任何应用",
    },
    "notification_access_hint": {
        "": "Needs notification access to read notifications",
        "-ar": "يتطلب الوصول إلى الإشعارات لقراءتها",
        "-de": "Benötigt Benachrichtigungszugriff zum Lesen",
        "-es": "Necesita acceso a notificaciones para leerlas",
        "-fr": "Nécessite l'accès aux notifications pour les lire",
        "-hi": "सूचनाएँ पढ़ने के लिए सूचना एक्सेस आवश्यक है",
        "-ja": "通知を読み取るには通知へのアクセスが必要です",
        "-pt": "Requer acesso a notificações para lê-las",
        "-ru": "Требуется доступ к уведомлениям для их чтения",
        "-tr": "Bildirimleri okumak için bildirim erişimi gerekir",
        "-zh-rCN": "需要通知使用权才能读取通知",
    },
    "action_block_notification": {
        "": "Block notifications", "-ar": "حظر الإشعارات", "-de": "Benachrichtigungen blockieren",
        "-es": "Bloquear notificaciones", "-fr": "Bloquer les notifications", "-hi": "सूचनाएँ ब्लॉक करें",
        "-ja": "通知をブロック", "-pt": "Bloquear notificações", "-ru": "Заблокировать уведомления",
        "-tr": "Bildirimleri engelle", "-zh-rCN": "屏蔽通知",
    },
    "action_block_notification_sub": {
        "": "Silence all notifications from an app",
        "-ar": "كتم جميع إشعارات تطبيق",
        "-de": "Alle Benachrichtigungen einer App stummschalten",
        "-es": "Silenciar todas las notificaciones de una app",
        "-fr": "Silencer toutes les notifications d'une app",
        "-hi": "किसी ऐप की सभी सूचनाएँ म्यूट करें",
        "-ja": "アプリのすべての通知をミュート",
        "-pt": "Silenciar todas as notificações de um app",
        "-ru": "Отключить все уведомления приложения",
        "-tr": "Bir uygulamanın tüm bildirimlerini sessize al",
        "-zh-rCN": "静音某个应用的所有通知",
    },
    "action_clear_app_notifications": {
        "": "Clear app notifications", "-ar": "مسح إشعارات تطبيق", "-de": "App-Benachrichtigungen löschen",
        "-es": "Borrar notificaciones de la app", "-fr": "Effacer les notifications de l'app",
        "-hi": "ऐप सूचनाएँ साफ़ करें", "-ja": "アプリの通知を消去",
        "-pt": "Limpar notificações do app", "-ru": "Очистить уведомления приложения",
        "-tr": "Uygulama bildirimlerini temizle", "-zh-rCN": "清除应用通知",
    },
    "action_clear_app_notifications_sub": {
        "": "Dismiss notifications of a specific app",
        "-ar": "إلغاء إشعارات تطبيق محدد",
        "-de": "Benachrichtigungen einer bestimmten App verwerfen",
        "-es": "Descartar notificaciones de una app específica",
        "-fr": "Rejeter les notifications d'une app spécifique",
        "-hi": "किसी विशिष्ट ऐप की सूचनाएँ हटाएँ",
        "-ja": "特定のアプリの通知を破棄",
        "-pt": "Dispensar notificações de um app específico",
        "-ru": "Отклонить уведомления конкретного приложения",
        "-tr": "Belirli bir uygulamanın bildirimlerini kapat",
        "-zh-rCN": "关闭特定应用的通知",
    },
    "block_label": {
        "": "Block", "-ar": "حظر", "-de": "Blockieren", "-es": "Bloquear", "-fr": "Bloquer",
        "-hi": "ब्लॉक करें", "-ja": "ブロック", "-pt": "Bloquear", "-ru": "Заблокировать",
        "-tr": "Engelle", "-zh-rCN": "屏蔽",
    },
    "notification_access": {
        "": "Notification access", "-ar": "الوصول إلى الإشعارات", "-de": "Benachrichtigungszugriff",
        "-es": "Acceso a notificaciones", "-fr": "Accès aux notifications", "-hi": "सूचना एक्सेस",
        "-ja": "通知へのアクセス", "-pt": "Acesso a notificações", "-ru": "Доступ к уведомлениям",
        "-tr": "Bildirim erişimi", "-zh-rCN": "通知使用权",
    },
    "notification_access_sub": {
        "": "Read and manage notifications", "-ar": "قراءة الإشعارات وإدارتها",
        "-de": "Benachrichtigungen lesen und verwalten", "-es": "Leer y gestionar notificaciones",
        "-fr": "Lire et gérer les notifications", "-hi": "सूचनाएँ पढ़ें और प्रबंधित करें",
        "-ja": "通知の読み取りと管理", "-pt": "Ler e gerenciar notificações",
        "-ru": "Чтение и управление уведомлениями", "-tr": "Bildirimleri oku ve yönet",
        "-zh-rCN": "读取和管理通知",
    },
    "notification_listener_label": {
        "": "NexaFlow notification access", "-ar": "وصول NexaFlow للإشعارات",
        "-de": "NexaFlow Benachrichtigungszugriff", "-es": "Acceso a notificaciones de NexaFlow",
        "-fr": "Accès aux notifications NexaFlow", "-hi": "NexaFlow सूचना एक्सेस",
        "-ja": "NexaFlow 通知アクセス", "-pt": "Acesso a notificações do NexaFlow",
        "-ru": "Доступ NexaFlow к уведомлениям", "-tr": "NexaFlow bildirim erişimi",
        "-zh-rCN": "NexaFlow 通知使用权",
    },
}

# module res dir -> keys to add
MODULES = {
    "feature/automation-builder/src/main/res": [
        "trigger_type_notification", "trigger_notification", "notification_contains",
        "notification_contains_hint", "any_app", "notification_access_hint",
        "action_block_notification", "action_block_notification_sub",
        "action_clear_app_notifications", "action_clear_app_notifications_sub", "block_label",
    ],
    "feature/automations/src/main/res": [
        "trigger_notification", "trigger_notification_sub",
        "action_block_notification", "action_block_notification_sub",
        "action_clear_app_notifications", "action_clear_app_notifications_sub",
    ],
    "feature/dashboard/src/main/res": ["trigger_notification"],
    "feature/settings/src/main/res": ["notification_access", "notification_access_sub"],
    "core/automation-engine/src/main/res": ["notification_listener_label"],
}


def inject(res_dir, locale, key, value):
    folder = os.path.join("values" + locale)
    path = os.path.normpath(os.path.join(BASE, res_dir, folder, "strings.xml"))
    if not os.path.exists(path):
        print("MISSING FILE:", path)
        return
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        content = f.read()
    if 'name="%s"' % key in content:
        print("SKIP (exists):", os.path.relpath(path, BASE), key)
        return
    if not content.strip().endswith("</resources>"):
        print("BAD FILE (no </resources>):", path)
        return
    nl = "\r\n" if "\r\n" in content else "\n"
    line = '    <string name="%s">%s</string>%s' % (key, value, nl)
    idx = content.rfind("</resources>")
    content = content[:idx] + line + content[idx:]
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(content)
    print("ADDED:", os.path.relpath(path, BASE), key)


def main():
    locales = ["", "-ar", "-de", "-es", "-fr", "-hi", "-ja", "-pt", "-ru", "-tr", "-zh-rCN"]
    for res_dir, keys in MODULES.items():
        for key in keys:
            translations = STRINGS[key]
            for loc in locales:
                if loc not in translations:
                    print("MISSING TRANSLATION:", key, loc)
                    sys.exit(1)
                inject(res_dir, loc, key, translations[loc])


if __name__ == "__main__":
    main()
