"""Adds permission-explanation strings to the 10 automation-builder locales atomically."""
import os
import tempfile

BASE = "feature/automation-builder/src/main/res"

# key -> {locale: text}
STRINGS = {
    "permission_explain_title": {
        "values": "Why is this permission needed?",
        "values-ar": "لماذا نحتاج هذا الإذن؟",
        "values-de": "Warum wird diese Berechtigung benötigt?",
        "values-es": "¿Por qué se necesita este permiso?",
        "values-fr": "Pourquoi cette autorisation est-elle nécessaire ?",
        "values-hi": "इस अनुमति की आवश्यकता क्यों है?",
        "values-ja": "この権限が必要な理由",
        "values-pt": "Por que essa permissão é necessária?",
        "values-ru": "Зачем нужно это разрешение?",
        "values-tr": "Bu izin neden gerekli?",
        "values-zh-rCN": "为什么需要此权限？",
    },
    "permission_continue": {
        "values": "Continue",
        "values-ar": "متابعة",
        "values-de": "Weiter",
        "values-es": "Continuar",
        "values-fr": "Continuer",
        "values-hi": "जारी रखें",
        "values-ja": "続ける",
        "values-pt": "Continuar",
        "values-ru": "Продолжить",
        "values-tr": "Devam",
        "values-zh-rCN": "继续",
    },
    "permission_not_now": {
        "values": "Not now",
        "values-ar": "ليس الآن",
        "values-de": "Später",
        "values-es": "Ahora no",
        "values-fr": "Pas maintenant",
        "values-hi": "अभी नहीं",
        "values-ja": "今はしない",
        "values-pt": "Agora não",
        "values-ru": "Не сейчас",
        "values-tr": "Şimdi değil",
        "values-zh-rCN": "暂时不用",
    },
    "permission_location_title": {
        "values": "Location access",
        "values-ar": "الوصول إلى الموقع",
        "values-de": "Standortzugriff",
        "values-es": "Acceso a la ubicación",
        "values-fr": "Accès à la localisation",
        "values-hi": "स्थान पहुंच",
        "values-ja": "位置情報へのアクセス",
        "values-pt": "Acesso à localização",
        "values-ru": "Доступ к местоположению",
        "values-tr": "Konum erişimi",
        "values-zh-rCN": "位置信息访问",
    },
    "permission_location_body": {
        "values": "NexaFlow uses your location to run tasks when you enter or leave a place.",
        "values-ar": "يستخدم نيكسافلو موقعك لتشغيل المهام عند دخولك مكاناً أو مغادرته.",
        "values-de": "NexaFlow verwendet deinen Standort, um Aufgaben beim Betreten oder Verlassen eines Ortes auszuführen.",
        "values-es": "NexaFlow usa tu ubicación para ejecutar tareas al entrar o salir de un lugar.",
        "values-fr": "NexaFlow utilise votre position pour exécuter des tâches à l'entrée ou à la sortie d'un lieu.",
        "values-hi": "किसी स्थान में प्रवेश या निकास पर कार्य चलाने के लिए NexaFlow आपके स्थान का उपयोग करता है।",
        "values-ja": "NexaFlowは場所への出入り時にタスクを実行するため、位置情報を使用します。",
        "values-pt": "O NexaFlow usa sua localização para executar tarefas ao entrar ou sair de um local.",
        "values-ru": "NexaFlow использует ваше местоположение для запуска задач при входе или выходе из места.",
        "values-tr": "NexaFlow, bir yere girerken veya çıkarken görevleri çalıştırmak için konumunuzu kullanır.",
        "values-zh-rCN": "NexaFlow 使用您的位置信息，在您进入或离开某个地点时运行任务。",
    },
    "permission_sms_title": {
        "values": "Read messages",
        "values-ar": "قراءة الرسائل",
        "values-de": "Nachrichten lesen",
        "values-es": "Leer mensajes",
        "values-fr": "Lire les messages",
        "values-hi": "संदेश पढ़ें",
        "values-ja": "メッセージを読む",
        "values-pt": "Ler mensagens",
        "values-ru": "Чтение сообщений",
        "values-tr": "Mesajları oku",
        "values-zh-rCN": "读取消息",
    },
    "permission_sms_body": {
        "values": "This lets tasks react when a message arrives, and reply automatically.",
        "values-ar": "يتيح ذلك للمهام التفاعل عند وصول رسالة، والرد تلقائياً.",
        "values-de": "So können Aufgaben auf eingehende Nachrichten reagieren und automatisch antworten.",
        "values-es": "Permite que las tareas reaccionen cuando llega un mensaje y respondan automáticamente.",
        "values-fr": "Permet aux tâches de réagir à l'arrivée d'un message et de répondre automatiquement.",
        "values-hi": "इससे संदेश आने पर कार्य प्रतिक्रिया कर सकते हैं और स्वतः उत्तर दे सकते हैं।",
        "values-ja": "メッセージ到着時にタスクが反応し、自動返信できるようになります。",
        "values-pt": "Permite que as tarefas reajam quando uma mensagem chega e respondam automaticamente.",
        "values-ru": "Позволяет задачам реагировать на приход сообщения и отвечать автоматически.",
        "values-tr": "Mesaj geldiğinde görevlerin tepki vermesini ve otomatik yanıtlamasını sağlar.",
        "values-zh-rCN": "这允许任务在收到消息时做出反应，并自动回复。",
    },
    "permission_send_sms_title": {
        "values": "Send messages",
        "values-ar": "إرسال الرسائل",
        "values-de": "Nachrichten senden",
        "values-es": "Enviar mensajes",
        "values-fr": "Envoyer des messages",
        "values-hi": "संदेश भेजें",
        "values-ja": "メッセージを送信",
        "values-pt": "Enviar mensagens",
        "values-ru": "Отправка сообщений",
        "values-tr": "Mesaj gönder",
        "values-zh-rCN": "发送消息",
    },
    "permission_send_sms_body": {
        "values": "This lets a task send a message as one of its actions.",
        "values-ar": "يتيح ذلك للمهمة إرسال رسالة كأحد إجراءاتها.",
        "values-de": "So kann eine Aufgabe als eine ihrer Aktionen eine Nachricht senden.",
        "values-es": "Permite que una tarea envíe un mensaje como una de sus acciones.",
        "values-fr": "Permet à une tâche d'envoyer un message parmi ses actions.",
        "values-hi": "इससे कोई कार्य अपनी क्रियाओं में से एक के रूप में संदेश भेज सकता है।",
        "values-ja": "タスクがアクションの1つとしてメッセージを送信できるようになります。",
        "values-pt": "Permite que uma tarefa envie uma mensagem como uma de suas ações.",
        "values-ru": "Позволяет задаче отправлять сообщение в качестве одного из действий.",
        "values-tr": "Bir görevin eylemlerinden biri olarak mesaj göndermesini sağlar.",
        "values-zh-rCN": "这允许任务作为其操作之一发送消息。",
    },
    "permission_camera_title": {
        "values": "Camera access",
        "values-ar": "الوصول إلى الكاميرا",
        "values-de": "Kamerazugriff",
        "values-es": "Acceso a la cámara",
        "values-fr": "Accès à la caméra",
        "values-hi": "कैमरा पहुंच",
        "values-ja": "カメラへのアクセス",
        "values-pt": "Acesso à câmera",
        "values-ru": "Доступ к камере",
        "values-tr": "Kamera erişimi",
        "values-zh-rCN": "相机访问",
    },
    "permission_camera_body": {
        "values": "The flashlight action uses the camera flash. No photo is ever taken.",
        "values-ar": "يستخدم إجراء المصباح فلاش الكاميرا. لا يتم التقاط أي صورة.",
        "values-de": "Die Taschenlampen-Aktion nutzt den Kamerablitz. Es wird nie ein Foto aufgenommen.",
        "values-es": "La acción de linterna usa el flash de la cámara. Nunca se toma una foto.",
        "values-fr": "L'action lampe torche utilise le flash de la caméra. Aucune photo n'est jamais prise.",
        "values-hi": "टॉर्च क्रिया कैमरा फ्लैश का उपयोग करती है। कोई तस्वीर नहीं ली जाती।",
        "values-ja": "懐中電灯アクションはカメラのフラッシュを使用します。写真は撮影されません。",
        "values-pt": "A ação de lanterna usa o flash da câmera. Nenhuma foto é tirada.",
        "values-ru": "Действие фонарика использует вспышку камеры. Фото никогда не делается.",
        "values-tr": "El feneri eylemi kamera flaşını kullanır. Asla fotoğraf çekilmez.",
        "values-zh-rCN": "手电筒操作使用相机闪光灯。绝不会拍照。",
    },
    "permission_notifications_title": {
        "values": "Notifications",
        "values-ar": "الإشعارات",
        "values-de": "Benachrichtigungen",
        "values-es": "Notificaciones",
        "values-fr": "Notifications",
        "values-hi": "सूचनाएँ",
        "values-ja": "通知",
        "values-pt": "Notificações",
        "values-ru": "Уведомления",
        "values-tr": "Bildirimler",
        "values-zh-rCN": "通知",
    },
    "permission_notifications_body": {
        "values": "NexaFlow sends you a notification when a task runs, so you know what happened.",
        "values-ar": "يرسل لك نيكسافلو إشعاراً عند تشغيل مهمة، لتعرف ما حدث.",
        "values-de": "NexaFlow sendet dir eine Benachrichtigung, wenn eine Aufgabe ausgeführt wird, damit du weißt, was passiert ist.",
        "values-es": "NexaFlow te envía una notificación cuando se ejecuta una tarea, para que sepas qué ocurrió.",
        "values-fr": "NexaFlow vous envoie une notification lorsqu'une tâche s'exécute, pour vous informer.",
        "values-hi": "कोई कार्य चलने पर NexaFlow आपको सूचना भेजता है, ताकि आप जान सकें कि क्या हुआ।",
        "values-ja": "タスク実行時にNexaFlowから通知が届き、何が起こったか確認できます。",
        "values-pt": "O NexaFlow envia uma notificação quando uma tarefa é executada, para você saber o que aconteceu.",
        "values-ru": "NexaFlow отправляет уведомление при запуске задачи, чтобы вы знали, что произошло.",
        "values-tr": "Bir görev çalıştığında NexaFlow size bildirim gönderir, böylece ne olduğunu bilirsiniz.",
        "values-zh-rCN": "NexaFlow 在任务运行时向您发送通知，让您了解发生了什么。",
    },
    "permission_bluetooth_title": {
        "values": "Nearby devices",
        "values-ar": "الأجهزة القريبة",
        "values-de": "Geräte in der Nähe",
        "values-es": "Dispositivos cercanos",
        "values-fr": "Appareils à proximité",
        "values-hi": "आस-पास के डिवाइस",
        "values-ja": "近くのデバイス",
        "values-pt": "Dispositivos próximos",
        "values-ru": "Устройства рядом",
        "values-tr": "Yakındaki cihazlar",
        "values-zh-rCN": "附近的设备",
    },
    "permission_bluetooth_body": {
        "values": "This lets tasks detect when your chosen Bluetooth device connects or disconnects.",
        "values-ar": "يتيح ذلك للمهام اكتشاف اتصال جهاز البلوتوث المختار أو انقطاعه.",
        "values-de": "So erkennen Aufgaben, wenn sich dein gewähltes Bluetooth-Gerät verbindet oder trennt.",
        "values-es": "Permite que las tareas detecten cuándo tu dispositivo Bluetooth elegido se conecta o desconecta.",
        "values-fr": "Permet aux tâches de détecter la connexion ou la déconnexion de votre appareil Bluetooth choisi.",
        "values-hi": "इससे कार्य यह पता लगा सकते हैं कि आपका चुना हुआ ब्लूटूथ डिवाइस कब जुड़ा या अलग हुआ।",
        "values-ja": "選択したBluetoothデバイスの接続・切断をタスクが検出できるようになります。",
        "values-pt": "Permite que as tarefas detectem quando o seu dispositivo Bluetooth escolhido conecta ou desconecta.",
        "values-ru": "Позволяет задачам определять подключение или отключение выбранного устройства Bluetooth.",
        "values-tr": "Görevlerin seçtiğiniz Bluetooth cihazının bağlanıp bağlanmadığını algılamasını sağlar.",
        "values-zh-rCN": "这允许任务检测您选择的蓝牙设备何时连接或断开。",
    },
    "permission_calendar_title": {
        "values": "Calendar access",
        "values-ar": "الوصول إلى التقويم",
        "values-de": "Kalenderzugriff",
        "values-es": "Acceso al calendario",
        "values-fr": "Accès au calendrier",
        "values-hi": "कैलेंडर पहुंच",
        "values-ja": "カレンダーへのアクセス",
        "values-pt": "Acesso ao calendário",
        "values-ru": "Доступ к календарю",
        "values-tr": "Takvim erişimi",
        "values-zh-rCN": "日历访问",
    },
    "permission_calendar_body": {
        "values": "This lets tasks run around events in your calendar, like before a meeting starts.",
        "values-ar": "يتيح ذلك للمهام التشغيل حول أحداث التقويم، مثل قبل بدء اجتماع.",
        "values-de": "So können Aufgaben rund um Kalenderereignisse ausgeführt werden, z. B. vor Beginn eines Meetings.",
        "values-es": "Permite que las tareas se ejecuten alrededor de eventos del calendario, como antes de una reunión.",
        "values-fr": "Permet aux tâches de s'exécuter autour d'événements du calendrier, comme avant une réunion.",
        "values-hi": "इससे कार्य कैलेंडर ईवेंट के आसपास चल सकते हैं, जैसे बैठक शुरू होने से पहले।",
        "values-ja": "会議の開始前など、カレンダーの予定に合わせてタスクを実行できるようになります。",
        "values-pt": "Permite que as tarefas sejam executadas em torno de eventos do calendário, como antes de uma reunião.",
        "values-ru": "Позволяет задачам запускаться вокруг событий календаря, например перед началом встречи.",
        "values-tr": "Görevlerin toplantı başlamadan önce gibi takvim etkinliklerinin çevresinde çalışmasını sağlar.",
        "values-zh-rCN": "这允许任务围绕日历事件运行，例如在会议开始前。",
    },
    "permission_generic_title": {
        "values": "Permission needed",
        "values-ar": "إذن مطلوب",
        "values-de": "Berechtigung erforderlich",
        "values-es": "Permiso necesario",
        "values-fr": "Autorisation requise",
        "values-hi": "अनुमति आवश्यक",
        "values-ja": "権限が必要です",
        "values-pt": "Permissão necessária",
        "values-ru": "Требуется разрешение",
        "values-tr": "İzin gerekli",
        "values-zh-rCN": "需要权限",
    },
    "permission_generic_body": {
        "values": "This permission lets the chosen task work as expected.",
        "values-ar": "يتيح هذا الإذن للمهمة المختارة العمل كما هو متوقع.",
        "values-de": "Diese Berechtigung lässt die gewählte Aufgabe wie erwartet funktionieren.",
        "values-es": "Este permiso permite que la tarea elegida funcione como se espera.",
        "values-fr": "Cette autorisation permet à la tâche choisie de fonctionner comme prévu.",
        "values-hi": "यह अनुमति चुने गए कार्य को अपेक्षित रूप से काम करने देती है।",
        "values-ja": "この権限により、選択したタスクが期待どおり動作します。",
        "values-pt": "Esta permissão permite que a tarefa escolhida funcione como esperado.",
        "values-ru": "Это разрешение позволяет выбранной задаче работать как ожидается.",
        "values-tr": "Bu izin, seçilen görevin beklendiği gibi çalışmasını sağlar.",
        "values-zh-rCN": "此权限让所选任务按预期工作。",
    },
}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write_atomic(path, content):
    d = os.path.dirname(path)
    fd, tmp = tempfile.mkstemp(dir=d, suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


# Insert all keys right after the <string name="grant"> line so they live together.
ANCHOR = '<string name="grant">'


def build_anchor_block():
    lines = []
    for key in STRINGS:
        lines.append('    <string name="%s">%s</string>' % (key, STRINGS[key]))
    return "\n".join(lines)


for locale, _ in STRINGS["permission_explain_title"].items():
    path = os.path.join(BASE, locale, "strings.xml")
    content = read(path)
    if "permission_explain_title" in content:
        print("SKIP (exists): %s" % locale)
        continue
    anchor_idx = content.find(ANCHOR)
    if anchor_idx < 0:
        print("ERROR anchor not found: %s" % locale)
        continue
    line_end = content.find("</string>", anchor_idx) + len("</string>")
    block = "\n".join(
        '    <string name="%s">%s</string>' % (key, STRINGS[key][locale])
        for key in STRINGS
    )
    content = content[:line_end] + "\n" + block + "\n" + content[line_end:]
    write_atomic(path, content)
    print("OK: %s" % locale)

print("Done.")
