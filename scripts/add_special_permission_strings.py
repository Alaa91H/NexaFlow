"""Adds special-permission explanation strings to the 10 automation-builder locales atomically."""
import os
import tempfile

BASE = "feature/automation-builder/src/main/res"

# key -> {locale: text}
STRINGS = {
    "special_write_settings_title": {
        "values": "Modify system settings",
        "values-ar": "تعديل إعدادات النظام",
        "values-de": "Systemeinstellungen ändern",
        "values-es": "Modificar ajustes del sistema",
        "values-fr": "Modifier les paramètres système",
        "values-hi": "सिस्टम सेटिंग्स बदलें",
        "values-ja": "システム設定の変更",
        "values-pt": "Modificar configurações do sistema",
        "values-ru": "Изменение системных настроек",
        "values-tr": "Sistem ayarlarını değiştir",
        "values-zh-rCN": "修改系统设置",
    },
    "special_write_settings_body": {
        "values": "NexaFlow needs this to adjust brightness, screen timeout, rotation and more while your tasks run.",
        "values-ar": "يحتاج نيكسافلو إلى هذا لضبط السطوع ومهلة الشاشة والتدوير وغيرها أثناء تشغيل مهامك.",
        "values-de": "NexaFlow benötigt dies, um Helligkeit, Bildschirm-Timeout, Rotation und mehr während deiner Aufgaben anzupassen.",
        "values-es": "NexaFlow lo necesita para ajustar brillo, tiempo de pantalla, rotación y más mientras se ejecutan tus tareas.",
        "values-fr": "NexaFlow en a besoin pour ajuster la luminosité, le délai d'extinction, la rotation et plus pendant vos tâches.",
        "values-hi": "आपके कार्य चलते समय चमक, स्क्रीन टाइमआउट, रोटेशन और अधिक समायोजित करने के लिए NexaFlow को इसकी आवश्यकता है।",
        "values-ja": "タスク実行中に明るさや画面のタイムアウト、回転などを調整するために必要です。",
        "values-pt": "O NexaFlow precisa disso para ajustar brilho, tempo de tela, rotação e mais enquanto suas tarefas são executadas.",
        "values-ru": "NexaFlow нужен этот доступ для настройки яркости, тайм-аута экрана, поворота и другого во время выполнения задач.",
        "values-tr": "NexaFlow'un görevleriniz çalışırken parlaklık, ekran zaman aşımı, döndürme ve daha fazlasını ayarlaması için gereklidir.",
        "values-zh-rCN": "NexaFlow 需要此权限在任务运行时调整亮度、屏幕超时、旋转等设置。",
    },
    "special_dnd_title": {
        "values": "Do Not Disturb access",
        "values-ar": "الوصول إلى عدم الإزعاج",
        "values-de": "Nicht-stören-Zugriff",
        "values-es": "Acceso a No molestar",
        "values-fr": "Accès Ne pas déranger",
        "values-hi": "डू नॉट डिस्टर्ब पहुंच",
        "values-ja": "おやすみモードへのアクセス",
        "values-pt": "Acesso a Não perturbar",
        "values-ru": "Доступ к режиму «Не беспокоить»",
        "values-tr": "Rahatsız Etmeyin erişimi",
        "values-zh-rCN": "勿扰模式访问",
    },
    "special_dnd_body": {
        "values": "Lets your tasks silence notifications or restore sound automatically.",
        "values-ar": "يتيح لمهامك كتم الإشعارات أو إعادة الصوت تلقائياً.",
        "values-de": "Lässt deine Aufgaben Benachrichtigungen stummschalten oder den Ton automatisch wiederherstellen.",
        "values-es": "Permite que tus tareas silencien notificaciones o restauren el sonido automáticamente.",
        "values-fr": "Permet à vos tâches de couper les notifications ou de rétablir le son automatiquement.",
        "values-hi": "आपके कार्यों को सूचनाएँ म्यूट करने या ध्वनि स्वचालित रूप से बहाल करने देता है।",
        "values-ja": "タスクが通知をミュートしたり、サウンドを自動的に復元したりできるようにします。",
        "values-pt": "Permite que suas tarefas silenciem notificações ou restaurem o som automaticamente.",
        "values-ru": "Позволяет задачам автоматически отключать уведомления или восстанавливать звук.",
        "values-tr": "Görevlerinizin bildirimleri sessize almasını veya sesi otomatik olarak geri yüklemesini sağlar.",
        "values-zh-rCN": "允许您的任务自动静音通知或恢复声音。",
    },
    "special_notification_access_title": {
        "values": "Notification access",
        "values-ar": "الوصول إلى الإشعارات",
        "values-de": "Benachrichtigungszugriff",
        "values-es": "Acceso a notificaciones",
        "values-fr": "Accès aux notifications",
        "values-hi": "सूचना पहुंच",
        "values-ja": "通知へのアクセス",
        "values-pt": "Acesso a notificações",
        "values-ru": "Доступ к уведомлениям",
        "values-tr": "Bildirim erişimi",
        "values-zh-rCN": "通知访问",
    },
    "special_notification_access_body": {
        "values": "Lets tasks read app notifications, react to them, or block them for you.",
        "values-ar": "يتيح للمهام قراءة إشعارات التطبيقات أو التفاعل معها أو حظرها.",
        "values-de": "Lässt Aufgaben App-Benachrichtigungen lesen, darauf reagieren oder sie für dich blockieren.",
        "values-es": "Permite que las tareas lean notificaciones de apps, reaccionen o las bloqueen por ti.",
        "values-fr": "Permet aux tâches de lire les notifications, d'y réagir ou de les bloquer pour vous.",
        "values-hi": "कार्यों को ऐप सूचनाएँ पढ़ने, उन पर प्रतिक्रिया देने या उन्हें ब्लॉक करने देता है।",
        "values-ja": "タスクがアプリの通知を読み取り、反応し、またはブロックできるようにします。",
        "values-pt": "Permite que as tarefas leiam notificações de apps, reajam ou bloqueiem por você.",
        "values-ru": "Позволяет задачам читать уведомления приложений, реагировать на них или блокировать их.",
        "values-tr": "Görevlerin uygulama bildirimlerini okumasını, tepki vermesini veya sizin için engellemesini sağlar.",
        "values-zh-rCN": "让任务读取应用通知、对其做出反应或为您阻止它们。",
    },
    "special_accessibility_title": {
        "values": "Accessibility service",
        "values-ar": "خدمة إمكانية الوصول",
        "values-de": "Barrierefreiheitsdienst",
        "values-es": "Servicio de accesibilidad",
        "values-fr": "Service d'accessibilité",
        "values-hi": "एक्सेसिबिलिटी सेवा",
        "values-ja": "ユーザー補助サービス",
        "values-pt": "Serviço de acessibilidade",
        "values-ru": "Служба специальных возможностей",
        "values-tr": "Erişilebilirlik hizmeti",
        "values-zh-rCN": "无障碍服务",
    },
    "special_accessibility_body": {
        "values": "NexaFlow uses it to detect when apps open and to close them automatically.",
        "values-ar": "يستخدمه نيكسافلو لمعرفة فتح التطبيقات وإغلاقها تلقائياً.",
        "values-de": "NexaFlow nutzt ihn, um zu erkennen, wann Apps geöffnet werden, und sie automatisch zu schließen.",
        "values-es": "NexaFlow lo usa para detectar cuándo se abren las apps y cerrarlas automáticamente.",
        "values-fr": "NexaFlow l'utilise pour détecter l'ouverture des applications et les fermer automatiquement.",
        "values-hi": "NexaFlow इसका उपयोग ऐप खुलने का पता लगाने और उन्हें स्वचालित रूप से बंद करने के लिए करता है।",
        "values-ja": "アプリの起動を検出して自動的に閉じるために使用します。",
        "values-pt": "O NexaFlow usa para detectar quando apps abrem e fechá-los automaticamente.",
        "values-ru": "NexaFlow использует её для определения открытия приложений и их автоматического закрытия.",
        "values-tr": "NexaFlow, uygulamaların ne zaman açıldığını algılamak ve otomatik kapatmak için kullanır.",
        "values-zh-rCN": "NexaFlow 使用它检测应用何时打开并自动关闭它们。",
    },
    "special_shizuku_title": {
        "values": "Shizuku",
        "values-ar": "شيزوكو",
        "values-de": "Shizuku",
        "values-es": "Shizuku",
        "values-fr": "Shizuku",
        "values-hi": "शिज़ुकु",
        "values-ja": "Shizuku",
        "values-pt": "Shizuku",
        "values-ru": "Shizuku",
        "values-tr": "Shizuku",
        "values-zh-rCN": "Shizuku",
    },
    "special_shizuku_body": {
        "values": "Lets advanced tasks control system settings without root. Start Shizuku and grant access once.",
        "values-ar": "يتيح للمهام المتقدمة التحكم في إعدادات النظام بدون جذر. شغّل شيزوكو وامنح الوصول مرة واحدة.",
        "values-de": "Lässt erweiterte Aufgaben Systemeinstellungen ohne Root steuern. Starte Shizuku und erteile einmalig Zugriff.",
        "values-es": "Permite que tareas avanzadas controlen ajustes del sistema sin root. Inicia Shizuku y concede acceso una vez.",
        "values-fr": "Permet aux tâches avancées de contrôler les paramètres sans root. Démarrez Shizuku et accordez l'accès une fois.",
        "values-hi": "उन्नत कार्यों को बिना रूट के सिस्टम सेटिंग्स नियंत्रित करने देता है। शिज़ुकु शुरू करें और एक बार पहुंच दें।",
        "values-ja": "高度なタスクがrootなしでシステム設定を操作できるようにします。Shizukuを起動し、一度だけアクセスを許可してください。",
        "values-pt": "Permite que tarefas avançadas controlem configurações do sistema sem root. Inicie o Shizuku e conceda acesso uma vez.",
        "values-ru": "Позволяет расширенным задачам управлять настройками без root. Запустите Shizuku и предоставьте доступ один раз.",
        "values-tr": "Gelişmiş görevlerin root olmadan sistem ayarlarını kontrol etmesini sağlar. Shizuku'yu başlatın ve bir kez erişim verin.",
        "values-zh-rCN": "让高级任务无需 root 即可控制系统设置。启动 Shizuku 并授予一次访问权限。",
    },
    "special_root_title": {
        "values": "Root access",
        "values-ar": "صلاحيات الجذر",
        "values-de": "Root-Zugriff",
        "values-es": "Acceso root",
        "values-fr": "Accès root",
        "values-hi": "रूट पहुंच",
        "values-ja": "rootアクセス",
        "values-pt": "Acesso root",
        "values-ru": "Root-доступ",
        "values-tr": "Root erişimi",
        "values-zh-rCN": "Root 访问",
    },
    "special_root_body": {
        "values": "Lets tasks run commands that need root (su). Grant the superuser request when it appears.",
        "values-ar": "يتيح للمهام تشغيل أوامر تحتاج صلاحيات الجذر (su). امنح طلب المستخدم الفائق عند ظهوره.",
        "values-de": "Lässt Aufgaben Befehle ausführen, die Root (su) benötigen. Erlaube die Superuser-Anfrage, wenn sie erscheint.",
        "values-es": "Permite que las tareas ejecuten comandos que necesitan root (su). Concede la solicitud de superusuario cuando aparezca.",
        "values-fr": "Permet aux tâches d'exécuter des commandes nécessitant root (su). Accordez la demande de superutilisateur lorsqu'elle apparaît.",
        "values-hi": "कार्यों को ऐसे कमांड चलाने देता है जिन्हें रूट (su) चाहिए। दिखने पर सुपरयूज़र अनुरोध दें।",
        "values-ja": "タスクがroot (su) を必要とするコマンドを実行できるようにします。表示されたらスーパーユーザー要求を許可してください。",
        "values-pt": "Permite que tarefas executem comandos que precisam de root (su). Conceda a solicitação de superusuário quando aparecer.",
        "values-ru": "Позволяет задачам выполнять команды, требующие root (su). Разрешите запрос суперпользователя, когда он появится.",
        "values-tr": "Görevlerin root (su) gerektiren komutları çalıştırmasını sağlar. Göründüğünde süper kullanıcı isteğini onaylayın.",
        "values-zh-rCN": "让任务运行需要 root (su) 的命令。出现超级用户请求时请授予。",
    },
    "special_elevated_title": {
        "values": "Elevated privileges",
        "values-ar": "صلاحيات موسّعة",
        "values-de": "Erweiterte Berechtigungen",
        "values-es": "Privilegios elevados",
        "values-fr": "Privilèges élevés",
        "values-hi": "उन्नत विशेषाधिकार",
        "values-ja": "昇格した権限",
        "values-pt": "Privilégios elevados",
        "values-ru": "Расширенные привилегии",
        "values-tr": "Yükseltilmiş ayrıcalıklar",
        "values-zh-rCN": "提升的权限",
    },
    "special_elevated_body": {
        "values": "This action needs root, Shizuku, or system-app privileges to control protected settings.",
        "values-ar": "يحتاج هذا الإجراء إلى الجذر أو شيزوكو أو صلاحيات تطبيق نظام للتحكم في الإعدادات المحمية.",
        "values-de": "Diese Aktion benötigt Root, Shizuku oder System-App-Berechtigungen, um geschützte Einstellungen zu steuern.",
        "values-es": "Esta acción necesita root, Shizuku o privilegios de app del sistema para controlar ajustes protegidos.",
        "values-fr": "Cette action nécessite root, Shizuku ou des privilèges d'application système pour contrôler les paramètres protégés.",
        "values-hi": "संरक्षित सेटिंग्स नियंत्रित करने के लिए इस क्रिया को रूट, शिज़ुकु या सिस्टम-ऐप विशेषाधिकार चाहिए।",
        "values-ja": "保護された設定を操作するには、root、Shizuku、またはシステムアプリの権限が必要です。",
        "values-pt": "Esta ação precisa de root, Shizuku ou privilégios de app do sistema para controlar configurações protegidas.",
        "values-ru": "Для управления защищёнными настройками этому действию нужны root, Shizuku или привилегии системного приложения.",
        "values-tr": "Bu eylem, korumalı ayarları kontrol etmek için root, Shizuku veya sistem uygulaması ayrıcalıkları gerektirir.",
        "values-zh-rCN": "此操作需要 root、Shizuku 或系统应用权限来控制受保护的设置。",
    },
    "special_bluetooth_title": {
        "values": "Bluetooth",
        "values-ar": "البلوتوث",
        "values-de": "Bluetooth",
        "values-es": "Bluetooth",
        "values-fr": "Bluetooth",
        "values-hi": "ब्लूटूथ",
        "values-ja": "Bluetooth",
        "values-pt": "Bluetooth",
        "values-ru": "Bluetooth",
        "values-tr": "Bluetooth",
        "values-zh-rCN": "蓝牙",
    },
    "special_bluetooth_body": {
        "values": "NexaFlow needs Bluetooth turned on to detect paired devices and run tasks when they connect.",
        "values-ar": "يحتاج نيكسافلو إلى تفعيل البلوتوث لاكتشاف الأجهزة المقترنة وتشغيل المهام عند اتصالها.",
        "values-de": "NexaFlow benötigt eingeschaltetes Bluetooth, um gekoppelte Geräte zu erkennen und Aufgaben bei Verbindung auszuführen.",
        "values-es": "NexaFlow necesita Bluetooth activado para detectar dispositivos emparejados y ejecutar tareas cuando se conectan.",
        "values-fr": "NexaFlow a besoin du Bluetooth activé pour détecter les appareils appariés et exécuter des tâches à leur connexion.",
        "values-hi": "जोड़े गए डिवाइस का पता लगाने और उनके कनेक्ट होने पर कार्य चलाने के लिए NexaFlow को ब्लूटूथ चालू चाहिए।",
        "values-ja": "ペアリング済みデバイスを検出し、接続時にタスクを実行するためにBluetoothの有効化が必要です。",
        "values-pt": "O NexaFlow precisa do Bluetooth ativado para detectar dispositivos pareados e executar tarefas quando conectam.",
        "values-ru": "NexaFlow нужен включённый Bluetooth для обнаружения сопряжённых устройств и запуска задач при подключении.",
        "values-tr": "NexaFlow'un eşleştirilmiş cihazları algılaması ve bağlandıklarında görevleri çalıştırması için Bluetooth'un açık olması gerekir.",
        "values-zh-rCN": "NexaFlow 需要开启蓝牙来检测已配对的设备并在它们连接时运行任务。",
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


# Insert all keys right after the <string name="permission_generic_body"> line.
ANCHOR = '<string name="permission_generic_body">'


def escape_value(text):
    # aapt2: apostrophes must be escaped or the whole value wrapped in double quotes.
    if "'" in text and not (text.startswith('"') and text.endswith('"')):
        return '"%s"' % text
    return text


for locale in STRINGS["special_write_settings_title"]:
    path = os.path.join(BASE, locale, "strings.xml")
    content = read(path)
    if "special_write_settings_title" in content:
        print("SKIP (exists): %s" % locale)
        continue
    anchor_idx = content.find(ANCHOR)
    if anchor_idx < 0:
        print("ERROR anchor not found: %s" % locale)
        continue
    line_end = content.find("</string>", anchor_idx) + len("</string>")
    block = "\n".join(
        '    <string name="%s">%s</string>' % (key, escape_value(STRINGS[key][locale]))
        for key in STRINGS
    )
    content = content[:line_end] + "\n" + block + "\n" + content[line_end:]
    write_atomic(path, content)
    print("OK: %s" % locale)

print("Done.")
