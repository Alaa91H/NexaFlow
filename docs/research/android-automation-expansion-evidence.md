# أدلة بحث توسعة تنفيذات Android

> **حالة المسودة:** بحث جارٍ؛ تُستكمل المراجع والقرار الهندسي قبل توسيع الكتالوج أو أي backend جديد.

## النتائج الرسمية الأولية

| المجال | الحقيقة المثبتة | النتيجة العملية لـ NexaFlow | المصدر |
|---|---|---|---|
| تحديث التطبيقات من Google Play | واجهة Google Play Core لـ in-app updates تخص **تحديث التطبيق المستدعي نفسه**؛ يختار المستخدم تدفقاً مرناً أو فورياً وتتولى Google Play التنزيل والتثبيت. | يمكن اقتراح/بدء فحص تحديث **NexaFlow نفسه** فقط. لا يمكن الادعاء بتحديث تطبيقات طرف ثالث من Google Play عبر هذه الواجهة. | [Android Developers — In-app updates][1] |
| متاجر/مثبتات التطبيقات | Android 14 يتيح واجهات PackageInstaller محسّنة للمتاجر والمثبتات، ومنها ملكية التحديث وقيود التثبيت؛ مثبت آخر يحتاج غالباً موافقة مستخدم صريحة. | فحص أو فتح متجر Google Play/Galaxy Store مسار آمن؛ التحديث الآلي لتطبيقات طرف ثالث يتطلب امتياز installer/store owner حقيقياً ولا يصنف كقدرة عادية. | [Android 14 features — Improvements for app stores][2] |
| Accessibility | سياسة Google Play تسمح بالأتمتة **الحتمية المبنية على قاعدة معرّفة من المستخدم**، لكنها تحظر الاستخدام الذي يمكّن التطبيق من التخطيط أو اتخاذ قرار أو تنفيذ أفعال ذاتية مستقلة. يلزم الإفصاح والموافقة والنموذج في Play Console للتطبيقات غير المصنفة كأداة إتاحة. | يمكن النظر في تنفيذ واجهة محدود وقابل للتدقيق لقاعدة ثابتة بعد إظهار إفصاح وموافقة، لكنه ليس وسيلة عامة لإرسال رسائل أو الضغط في تطبيقات الغير بلا قيود. | [Google Play — Accessibility API policy][3] |
| الرسائل والبيانات الحساسة | الإفصاح في سياسة Accessibility يسرد SMS ورسائل التطبيقات ضمن البيانات الحساسة الممكن الوصول إليها، مع متطلبات إفصاح منفصل وموافقة. | الرد على SMS لا يضاف كقدرة افتراضية عامة؛ يلزم تدقيق دور SMS الافتراضي وسياسة Google Play قبل تصميمه. أما رسائل تطبيقات التواصل فتعتمد على API رسمي لكل مزود أو تكامل Plugin صريح. | [Google Play — Accessibility API policy][3] |

## فرضيات ممنوعة حتى اكتمال الدراسة

لا تمنح NexaFlow حالياً أي تسمية تنفيذ مؤكدة للآتي: التحديث التلقائي لتطبيقات طرف ثالث من Google Play أو Galaxy Store، تثبيت/تحديث حزم بلا موافقة، إرسال أو رد تلقائي عام في تطبيقات تواصل طرف ثالث، أو استخدام Accessibility كبديل غير معلن لواجهات رسمية.

## المراجع

[1]: https://developer.android.com/guide/playcore/in-app-updates "Android Developers — In-app updates"
[2]: https://developer.android.com/about/versions/14/features "Android Developers — Android 14 features"
[3]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Google Play — Use of the AccessibilityService API"

## نتائج إضافية: الرسائل وملكية التحديث والمتاجر

| المجال | الحقيقة المثبتة | النتيجة العملية لـ NexaFlow | المصدر |
|---|---|---|---|
| دور SMS | يعرّف Android دور `ROLE_SMS` ويشترط أن يستوفي التطبيق متطلبات الدور ثم يطلب موافقة المستخدم عبر Intent مخصص؛ لا يجوز افتراض توافر الدور بل يجب الاستعلام عنه أولاً. | يمكن دراسة `Reply SMS` كقدرة **مقيدة بدور SMS الافتراضي** مع طلب دور صريح، وليس كتنفيذ صامت متاح لكل تثبيت. | [Android Developers — RoleManager][4] |
| ملكية تحديث الحزم | في Android 14 يمكن للمتجر/المثبت الأول أن يعلن ملكية التحديث؛ حتى المثبت الذي يملك `INSTALL_PACKAGES` يحتاج لمعالجة موافقة المستخدم عندما يملك متجر آخر التحديث. | لا تنفذ NexaFlow «تحديث كل التطبيقات تلقائياً» كإجراء عادي. أقصى مسار عادي هو فتح صفحة المتجر/إجراء فحص مرئي؛ بينما يلزم امتياز متجر/MDM/مُثبّت حقيقي لمسار حزم مُدار. | [AOSP — App update ownership][5] |
| Galaxy Store | Galaxy Store Developer API هي واجهة خادم-إلى-خادم لإدارة تطبيقات البائع، binaries، staged rollout، وتعليقات المشترين، وليست واجهة جهاز للمستخدم لتحديث تطبيقات طرف ثالث. | لا يضاف تنفيذ «تحديث من Galaxy Store» كأنه API للجهاز. يمكن في المستقبل دعم فتح صفحة Galaxy Store أو Plugin/OEM contract موثق فقط. | [Samsung Developer — Galaxy Store Developer API][6] |

## قرار تصميم مبدئي للكتالوج

يجب أن تميز كل بطاقة تنفيذ بين أربع حالات واضحة: **متاح محلياً**، **يتطلب موافقة/دور مستخدم**، **يتطلب امتياز جهاز مُدار أو Shizuku/Root**، و**غير مدعوم عبر API رسمي ويُترك لـ Plugin أو لا ينفذ**. هذه العلامة ليست تجميلية؛ بل تمنع اختيار تنفيذ لا يمكن للمحرك إتمامه من دون حالة امتياز مثبتة.

[4]: https://developer.android.com/reference/android/app/role/RoleManager#ROLE_SMS "Android Developers — RoleManager ROLE_SMS"
[5]: https://source.android.com/docs/setup/create/app-ownership "AOSP — Configure and handle update ownership for apps"
[6]: https://developer.samsung.com/galaxy-store/galaxy-store-developer-api.html "Samsung Developer — Galaxy Store Developer API"

## نتائج إضافية: الموسيقى وSMS وIntents

| المجال | الحقيقة المثبتة | النتيجة العملية لـ NexaFlow | المصدر |
|---|---|---|---|
| تشغيل الموسيقى | MediaSession يخص المشغل الذي يملك الجلسة؛ تتلقى الجلسة أوامر التحكم ثم تفوضها إلى المشغل المرتبط بها. | لا يدعم Android API عاماً لتشغيل قائمة أو أغنية داخل أي تطبيق موسيقى عشوائي. التنفيذ الآمن هو: فتح تطبيق/رابط موسيقى محدد، تفويض `ACTION_VIEW` أو intent مخصص موثق من مزود، أو Plugin للمزود. | [Android Developers — Media sessions][7] |
| Intents العامة | الـimplicit intent يبدأ تطبيقاً قادراً على معالجة الفعل ويجب فحص `resolveActivity()` لتجنب فشل عدم وجود مستقبل. توثق Android أمثلة مثل منبهات، مؤقتات، تقويم، فتح محتوى، ووسائط. | يضاف صف تنفيذات «فتح/تفويض تطبيق» و«إنشاء منبه/مؤقت» كقدرات محلية عالية القيمة مع فحص المستقبل ونتيجة قابلة للتسجيل، وليس كضمان أن تطبيقاً محدداً نفذ سلوكاً خاصاً داخلياً. | [Android Developers — Common intents][8] |
| SMS وGoogle Play | الوصول إلى بيانات SMS أو إرسالها ضمن تطبيق منشور في Google Play مقيد عموماً بتسجيل التطبيق كمعالج افتراضي لوظيفة الرسائل، بما فيها موافقة المستخدم وامتلاك وظيفة إرسال الرسائل فعلياً؛ وتطلب السياسة نموذج إقرار للأذونات المقيدة. | تبقى `Send/Reply SMS` خارج التمكين الافتراضي. إن تقرر دعمها، توضع تحت مسار **SMS default-role only** منفصل، مع طلب دور ثم إذن ثم اختبار جهاز وسياسة نشر. | [Android Developers — Default handlers][9] [Google Play — SMS and Call Log permissions][10] |

## تنفيذات يومية ذات أولوية عالية ومخاطر منخفضة

| أولوية | عائلة التنفيذ | مثال استخدام متكرر | التصنيف المبدئي |
|---:|---|---|---|
| 1 | فتح تطبيق/صفحة إعداد | فتح الموسيقى عند وصول المنزل، فتح صفحة البطارية أو تفاصيل تطبيق | متاح محلياً إذا حُلّ Intent |
| 1 | منبه/مؤقت/تذكير | مؤقت طبخ أو منبه أيام العمل | Intent موثق أو scheduler قائم |
| 1 | صوت وإزعاج وإضاءة واتصال | روتين صباحي/عمل/نوم | يعتمد على backend والصلاحية الموجودة لكل إجراء |
| 1 | شبكة HTTP/ويب | إرسال webhook أو API شخصي متكرر | متاح مع موافقة الشبكة وسياسة وقت/إعادة محاولة |
| 2 | موسيقى موجهة لمزود | فتح Spotify/YouTube Music/مشغل محدد مع deep link أو Plugin | مزود/Intent/Plugin محدد؛ ليس تحكمًا عالمياً |
| 2 | إشعار/رسالة جاهزة | تذكير محلي أو مشاركة نص عبر chooser | متاح محلياً أو عبر Intent؛ لا يعني الإرسال الصامت داخل تطبيق آخر |
| 3 | SMS صريح | رد تلقائي ثابت في حالة محددة | دور SMS افتراضي + موافقة + اختبار جهاز + تدقيق Play |
| 3 | تحديثات التطبيقات | فحص تحديث NexaFlow أو فتح المتجر للمستخدم | تحديث NexaFlow فقط عبر Play Core؛ التطبيقات الأخرى متاجر/امتيازات مقيدة |

[7]: https://developer.android.com/media/legacy/mediasession "Android Developers — Using a media session"
[8]: https://developer.android.com/guide/components/intents-common "Android Developers — Common intents"
[9]: https://developer.android.com/guide/topics/permissions/default-handlers "Android Developers — Permissions used only in default handlers"
[10]: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en "Google Play — Use of SMS or Call Log permission groups"

## مؤشرات السوق لترتيب التنفيذات

صفحات Tasker وAutomate في Google Play ليست دليلاً على إتاحة كل قدرة في كل جهاز أو على انطباق سياسة Google Play على NexaFlow، لكنها تؤكد أن عائلات الاستخدام المتكررة هي: إعدادات الجهاز، الوقت/الموقع، الشبكة، الملفات والنسخ الاحتياطي، HTTP/webhooks، الإعلامات، والتكاملات الرسمية عبر Plugins. كما تعرض هذه الأدوات صراحةً أن بعض الأفعال تحتاج Root أو Automation UI/Accessibility أو أوامر shell، وهو متسق مع القيود الرسمية الموثقة أعلاه.

| إشارة سوقية | الدلالة لتصميم NexaFlow | قرار المنتج |
|---|---|---|
| Tasker يبرز الوقت والموقع والشبكة والمكالمات/SMS والموسيقى، ويدعم Plugins وHTTP وShizuku. | البحث والوصول السريع يجب أن يبدأ بالعائلات اليومية المفهومة، لا بقائمة طويلة من أوامر النظام. | إبراز «روتين يومي»، «صوت/تركيز»، «اتصال»، «تطبيقات»، «ملفات/ويب» في أعلى الكتالوج. |
| Automate يبرز النسخ الاحتياطي والملفات والويب وبدء المهام بالوقت والموقع والحالة والتكاملات. | أكثر التنفيذات إنتاجية قابلة للتسليم مبكراً هي التي تعتمد على app-scoped files وHTTP وIntents موثقة وإشعارات. | إعطاء أولوية للملفات المحلية، webhooks، التنبيهات، وفتح/تفويض التطبيقات. |
| كلا المنتجين يذكران تكاملات طرف ثالث وAccessibility/sh​ell كمسارات منفصلة. | لا ينبغي خلط العمل المحلي المؤكد مع التحكم في تطبيقات الغير أو الامتيازات العالية. | بطاقات capability تحمل ملصق الامتياز وتطلب تهيئة Plugin/صلاحية بدلاً من وعد المستخدم بنتيجة عامة. |

[11]: https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm&hl=en_US "Google Play — Tasker"
[12]: https://play.google.com/store/apps/details?id=com.llamalab.automate&hl=en_US "Google Play — Automate"
[13]: https://llamalab.com/automate/ "LlamaLab — Automate"

## نتائج إضافية: الرد عبر إشعارات التطبيقات

| المجال | الحقيقة المثبتة | النتيجة العملية لـ NexaFlow | المصدر |
|---|---|---|---|
| Notification listener | `NotificationListenerService` يتلقى أحداث نشر/إزالة الإشعارات بعد أن يتصل النظام بالخدمة، ويتطلب تعريف خدمة وصلاحية الربط، ويمكن أن تقيد OEM/work profile أو النظام توافره. | يمكن بناء تنفيذ مراقبة/تصنيف لإشعارات التطبيقات كمسار اختياري مشروط بمنح Notification Access واختبار الجهاز، لا كقناة موثوقة في جميع البيئات. | [Android Developers — NotificationListenerService][14] |
| Direct reply | يصف Android `RemoteInput` كحقل رد لنص يقدمه المستخدم ضمن `PendingIntent` خاص بفعل الإشعار؛ كما تملك `Notification.Action` دلالة `SEMANTIC_ACTION_REPLY` ويمكن أن تحتوي RemoteInputs. | التنفيذ الآمن عالي القيمة هو: **اقتراح رد أو مطالبة المستخدم بتأكيد/إدخال الرد ضمن NexaFlow** عندما يثبت أن الإشعار الحالي يعلن Reply/RemoteInput. لا يجوز تسمية ذلك «رد تلقائي عام لكل تطبيق» أو تجاوزه عندما لا توجد RemoteInput/صلاحية/قابلية إرسال. | [Android Developers — Create a notification][15] [Android Developers — Notification.Action][16] |

## سياسة منتج مقترحة للرسائل

1. **المرحلة الأولى:** إشعار NexaFlow محلي مع إجراءات فتح المحادثة، نسخ النص، تأجيل، أو إرسال webhook مخصص؛ لا يصل إلى محتوى أو يرسل أي رسالة طرف ثالث تلقائياً.
2. **المرحلة الثانية (مشروطة):** تكامل Notification Access يستخرج metadata ورسالة قابلة للرد فقط بعد موافقة صريحة، ثم يعرض معاينة رد حتمي ومطالبة تأكيد. يتطلب اختبار جهاز/تطبيق لكل مزود.
3. **المرحلة الثالثة (مقيدة جداً):** رد حتمي على Action RemoteInput موثق بعد تفعيل المستخدم، kill switch، سجل تدقيق، rate limit، وقائمة سماح تطبيقات. يظل `PARTIAL` حتى اختبار أجهزة وتطبيقات حقيقية.
4. **SMS:** لا يخلط مع المسار أعلاه؛ يظل قدرته منفصلة ومقيدة بدور SMS الافتراضي وسياسة Google Play.

[14]: https://developer.android.com/reference/android/service/notification/NotificationListenerService "Android Developers — NotificationListenerService"
[15]: https://developer.android.com/develop/ui/compose/notifications/create-notification "Android Developers — Create a notification"
[16]: https://developer.android.com/reference/android/app/Notification.Action "Android Developers — Notification.Action"

## نتائج إضافية: تحديثات النظام والتطبيقات والمؤقتات

| المجال | الحقيقة المثبتة | النتيجة العملية لـ NexaFlow | المصدر |
|---|---|---|---|
| تحديث النظام | توثق `Settings` فعلاً مخصصاً لعرض إعدادات تحديث النظام، وتكرر الوثائق أن بعض إعدادات النظام قد لا تملك Activity مستقبل في كل جهاز؛ يجب حراسة التشغيل بفحص مستقبل Intent. | يمكن إضافة «فتح تحديث النظام» كتنفيذ يومي آمن: يفتح صفحة النظام إن كانت متاحة ويسجل عدم التوافر بوضوح. لا يدعي فحص/تثبيت OTA أو تحديد وجود تحديث. | [Android Developers — Settings][17] |
| تحديثات Google Play | Google Play يشرح أن المستخدم يضبط التحديث التلقائي من متجر Play نفسه، وأن بعض التحديثات تتطلب أذونات جديدة أو إعادة تشغيل. | بطاقة «تحديثات التطبيقات» الحالية يجب أن تصاغ كـ **فتح إدارة التحديثات**؛ ويعرض المنتج إرشاداً لتفعيل تحديث Play التلقائي بدلاً من وعد NexaFlow بتنفيذها. | [Google Play Help — Update Android apps][18] |
| مؤقتات متكررة | توثق Android `ACTION_SET_TIMER` مع مدة ورسالة وخيار تخطي الواجهة، إضافة إلى `ACTION_SET_ALARM`؛ يجب فحص وجود المستقبل قبل التشغيل. | يضاف «بدء مؤقت» كتنفيذ محلي جديد عالي القيمة، وكذلك تحسين محرر المنبه ليمرر رسالة/تكرار عندما يدعمها تطبيق الساعة. | [Android Developers — Common intents][8] |
| الموسيقى | توثق Android أن البحث/التشغيل يعتمد على intent ومستقبل قادر على معالجته؛ لا تضمن المنصة أن كل مشغل يدعم الأمر أو يستجيب له. | يمكن إدراج «بحث وتشغيل موسيقى» فقط بمسار **best effort** يختبر `resolveActivity()` ويقبل Package اختياري، مع نتيجة `UNAVAILABLE` واضحة؛ لا يستخدم لتسويق تشغيل مضمون داخل Spotify أو YouTube Music. | [Android Developers — Common intents][8] |

[17]: https://developer.android.com/reference/android/provider/Settings "Android Developers — Settings"
[18]: https://support.google.com/googleplay/answer/113412?hl=en "Google Play Help — Update Android apps"
