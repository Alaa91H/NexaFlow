# خارطة توسعة تنفيذات NexaFlow: روتينات متكررة وقدرات مقيدة

> **الحكم الهندسي:** لا يحتاج NexaFlow إلى محرك جديد أو EventBus أو Queue جديد لهذه التوسعة. نموذج `Automation.actions: List<Action>`، المجدول الزمني، كتالوج التوافق، وطبقات الـhandler القائمة تدعم التنفيذات المتعددة بالفعل. الفجوة الأساسية هي **الترتيب، إظهار القدرة الصحيحة، وإضافة عدد صغير من Intent-backed actions الآمنة**.

## 1. ما هو موجود بالفعل

يدعم المحرر الحالي كتالوجاً واسعاً يضم الصوت والـDND، الشبكة، الموقع، Bluetooth، NFC، فتح التطبيقات والمتاجر والإعدادات، التحكم في جلسة الوسائط، HTTP، الإشعارات، المنبهات، الملفات والـPlugins. كما أن محفز الوقت يدعم `DAILY` و`WEEKDAYS` و`WEEKENDS` و`SPECIFIC_DAYS` و`MONTHLY` و`MONTHLY_WEEKDAY` والنوافذ الزمنية. لذلك فإن «روتين صباحي/عمل/مساء يومي أو أسبوعي أو شهري» قابل للبناء حالياً من الناحية البنيوية.

| حاجة شائعة | وضع NexaFlow الحالي | التصنيف المنتج الصحيح |
|---|---|---|
| تغيير الصوت وDND والسطوع والوضع الداكن | إجراءات موجودة ومسارات backend قائمة | متاح حسب صلاحية الإعداد المحددة |
| تشغيل/إيقاف وسائط، التالي، السابق، إيقاف، تقديم وإرجاع | إجراءات موجودة عبر Media key dispatch | متاح، لكن يوجّه جلسة الوسائط النشطة ولا يضمن أغنية أو قائمة من تطبيق معين |
| فتح تطبيق أو صفحة إعداد أو صفحة متجر | إجراءات موجودة عبر package launch / Intents | متاح عند وجود المستقبل أو التطبيق |
| فتح تحديثات Play أو Galaxy Store | إجراءات موجودة | فتح صفحة/تطبيق المتجر فقط، **ليس** تحديثاً تلقائياً لتطبيقات الغير |
| تحديث NexaFlow نفسه | لا يمثل ذلك الإجراء الموجود | يمكن في المستقبل استخدام Play Core لتحديث NexaFlow فقط، بموافقة تدفق Google Play [1] |
| SMS | `SYSTEM_SEND_SMS` موجود تقنياً | مقيد بدور SMS/سياسة Play/الموافقة؛ لا يروّج كإجراء آلي عام [2] [3] |
| رد تطبيقات التواصل | لا مسار موثوق عام قائم | غير مدعوم كادعاء عام؛ RemoteInput/Notification Access مسار اختياري مقيد حسب التطبيق [4] |
| تكرار يومي/أسبوعي/شهري | مدعوم في `TimeTriggerCalculator` | متاح في المحفز، ويجب إبراز ذلك في القوالب |

## 2. ترتيب الكتالوج المقترح

يضاف إلى منتقي التنفيذ الثابت قسم أول باسم **الروتينات المتكررة**. ليس نوع فعل جديداً ولا يكرر البيانات؛ بل هو مجموعة مرتبة من `ActionOption` القائمة والموجات الجديدة. يبقى كل فعل في فئته الأصلية أيضاً.

| ترتيب العرض | البطاقة | السبب | حالة التنفيذ |
|---:|---|---|---|
| 1 | تشغيل/إيقاف الموسيقى | روتين منزل/عمل/سيارة شائع | موجود؛ يتحكم في الجلسة النشطة |
| 2 | مستوى الصوت / DND / وضع الرنين | تركيز، نوم، اجتماع، روتين صباحي | موجود |
| 3 | Wi-Fi / Bluetooth / الموقع / NFC | الدخول والخروج من المنزل والعمل | موجود، مع وسم الصلاحية المناسب |
| 4 | فتح تطبيق محدد | تشغيل مشغل موسيقى أو خرائط أو أي تطبيق عمل | موجود |
| 5 | البحث وتشغيل موسيقى | طلب المستخدم المباشر لبدء محتوى صوتي | جديد، best effort عبر Intent مستقبل متحقق منه [5] |
| 6 | منبه ومؤقت | إنتاجية يومية، طبخ، استراحة، نوم | منبه موجود؛ مؤقت جديد عبر Intent موثق [5] |
| 7 | إشعار وتذكير وWebhook | روتينات بلا امتياز وحالات عمل | موجود |
| 8 | فتح تحديثات التطبيقات | تذكير صريح لمراجعة تحديثات المتجر | موجود؛ يفتح صفحة المتجر فقط [6] |
| 9 | فتح تحديث النظام | فحص OTA يترك القرار للنظام/المستخدم | جديد؛ يفتح صفحة إعدادات النظام فقط [7] |
| 10 | فتح Galaxy Store | أجهزة Samsung | موجود؛ يفتح التطبيق إن وُجد وإلا fallback |

## 3. موجة التنفيذ الآمنة

تقتصر هذه الموجة على ثلاثة أنواع تنفيذ جديدة ذات Android contract واضح، وتستخدم `SystemController` و`SystemActionsHandler` و`CommandCatalog` القائمة فقط:

| ActionType جديد | المخرج الخارجي الصحيح | البيانات | قناة التنفيذ | القيود |
|---|---|---|---|---|
| `SYSTEM_SET_TIMER` | محاولة بدء مؤقت في تطبيق ساعة يستقبل `ACTION_SET_TIMER` | `seconds`، `message`، `skipUi` | Universal Intent | يجب فحص مستقبل Intent؛ قد يعرض تطبيق الساعة تأكيداً [5] |
| `SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS` | فتح صفحة تحديث النظام | لا شيء | Universal Intent | قد لا توجد صفحة في OEM؛ لا فحص OTA ولا تثبيت [7] |
| `SYSTEM_MEDIA_PLAY_FROM_SEARCH` | تفويض query لمشغل موسيقى يدعم intent | `query`، `package` اختياري | Universal Intent | تشغيل best-effort؛ ينجح فقط إذا وجد مستقبل مناسب [5] |

## 4. القدرات التي لا تنفذ في هذه الموجة

| الطلب | السبب | البديل الصادق |
|---|---|---|
| تحديث تطبيقات Google Play أو Galaxy Store تلقائياً | Play Core يحد in-app update بالتطبيق المستدعي؛ ملكية الحزمة/المثبت وموافقة المستخدم تقيد التحديثات الخارجية [1] [8] | فتح إدارة تحديثات المتجر، أو تفعيل auto-update في إعدادات المتجر، أو MDM/store owner خارج التطبيق |
| تثبيت تحديث نظام تلقائياً | يتطلب امتياز OTA/OEM/Device Owner ولا توجد API عامة لتطبيق عادي | فتح صفحة تحديث النظام وتسجيل أن المستخدم يتخذ القرار |
| رد تلقائي عام على WhatsApp/Telegram/Instagram وغيرها | لا API Android موحد؛ Notification actions وRemoteInput تعتمد على ما يعلن التطبيق وتحتاج Notification Access [4] | Plugin رسمي للمزود، أو مسار RemoteInput مضبوط بقائمة سماح وموافقة واختبارات أجهزة في إصدار لاحق |
| إرسال SMS/ردود SMS كميزة عادية | Google Play يقيد أذونات SMS ويطلب default handler أو استثناء وسياسة وإقرار [2] [3] | تظل البطاقة خلف حالة `SMS_ROLE_REQUIRED`، أو تستخدم مشاركة/فتح عميل SMS مع تفاعل المستخدم |
| Accessibility كإجراء عام للضغط/الرد | السياسة تحظر الأفعال الذاتية غير الحتمية وتفرض إفصاحاً وموافقة في حالات الأتمتة [9] | قواعد حتمية محددة بإفصاح، أو Plugin/OEM integration؛ لا توسعة عامة في هذه الموجة |

## 5. تصميم الوصول والتمييز

سيبقى المنتقي ثابتاً ومتعدد الاختيار وزر الإضافة في أسفله وفق التصميم المعتمد. البطاقات المضافة متناوبة بالرمادي الفاتح والرمادي الداكن، وتُفتح البطاقة الجديدة وتُطوى القديمة. قسم الروتينات المتكررة يظهر أولاً في المنتقي، ثم تبقى الأقسام التخصصية كاملة للبحث المتقدم. يميّز كل خيار في الوصف بين الحالات التالية بدلاً من وصف موحد مضلل:

| الحالة | المعنى في واجهة المستخدم |
|---|---|
| **متاح محلياً** | يستخدم API أو Intent عاماً ولا يحتاج امتيازاً إضافياً غير إذن صريح إن لزم |
| **يتطلب إذناً أو دوراً** | لا يمكن تشغيله قبل منح المستخدم الإذن أو Role المطلوب |
| **يتطلب Shizuku/Root/Device Owner** | يظهر في المتقدم، ولا يضاف إلى الروتينات اليومية الافتراضية |
| **تكامل مزود/Plugin** | يفتح إعداد Plugin أو رابط مزود موثق؛ لا يتم تخمين أوامر التطبيق |
| **غير مدعوم** | لا يظهر كقدرة قابلة للتنفيذ ولا يحول البحث إلى نجاح زائف |

## 6. بوابة التحقق

تضاف اختبارات وحدة للتحقق من إدراج التنفيذات الثلاثة في `CommandCatalog` و`SystemActionsHandler` وكتالوج الواجهة، واختبارات Android متصلة للـIntents عند توافر جهاز. تظل نتيجة intent الخارجي مقيدة بـ `ANDROID_INTEGRATION_TESTED` أو `REAL_DEVICE_VERIFIED` فقط بعد تشغيلها فعلياً على هدف Android؛ نجاح البناء لا يرفع التصنيف.

## المراجع

[1]: https://developer.android.com/guide/playcore/in-app-updates "Android Developers — In-app updates"
[2]: https://developer.android.com/guide/topics/permissions/default-handlers "Android Developers — Default handlers"
[3]: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en "Google Play — SMS and Call Log permissions"
[4]: https://developer.android.com/reference/android/service/notification/NotificationListenerService "Android Developers — NotificationListenerService"
[5]: https://developer.android.com/guide/components/intents-common "Android Developers — Common intents"
[6]: https://support.google.com/googleplay/answer/113412?hl=en "Google Play Help — Update Android apps"
[7]: https://developer.android.com/reference/android/provider/Settings "Android Developers — Settings"
[8]: https://source.android.com/docs/setup/create/app-ownership "AOSP — App update ownership"
[9]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Google Play — Use of the AccessibilityService API"
