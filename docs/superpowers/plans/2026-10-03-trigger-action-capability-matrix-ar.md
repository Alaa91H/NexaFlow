# مصفوفة توسيع المحفزات والإجراءات والتخصيص

هذه المصفوفة هدف تطوير وليست إعلان دعم راهن. كان خط الأساس عند c664c54b يضم 57 TriggerType و176 ActionType، وأضيفت إليه أربعة إجراءات اتصالات؛ تبقى الأنواع الأصلية محفوظة ويصبح إجمالي الكتالوج 57/180. ويحصي سجل التعريف القانوني 134 target و46 operation و12 predicate؛ هذه مفاتيح identity وليست ميزات كاملة. الفهرس العائلي 19 عائلة. المرئيات الفعلية تنقص عند legacy-hidden أو unsupported capability. يُضاف النوع للقائمة القابلة للاختيار فقط بعد schema ومحرر وتخزين وruntime واختبارات مترابطة.

## تعريفات الحالة والدليل

| الحالة | معناها |
|---|---|
| VERIFIED_LOCAL | تحقق نوعي في التطبيق والخدمة على مستوى نظام/جهاز/إصدار مسجل. |
| IMPLEMENTED_UNVERIFIED | شيفرة مكتوبة لكن لا توجد أدلة التشغيل المتطلبة. |
| PUBLIC_API_PERMISSION_REQUIRED | ممكن عبر Android APIs العامة بعد تبرير ومنح الإذن أو الدور. |
| COMPANION_REQUIRED | يحتاج تطبيقًا مرافقًا أو جهازًا/موفرًا مقترنًا. |
| PRIVILEGED_BACKEND_REQUIRED | يحتاج Shizuku أو Root أو Device Owner أو OEM محدد ومختبر. |
| UNAVAILABLE_CURRENT_DEVICE | العتاد/الإذن/الموفر غير موجود أو مسحوب الآن. |
| UNSUPPORTED | لا توجد طريقة معتمدة من مساحة التطبيق والصلاحية المتاحة. |

unknown لا يترجم إلى available. عرض سبب الفشل والخطوة التالية. ميزة معلقة/مهجورة تبقى غير مختارة افتراضيًا.

## عقود المحفزات

كل Trigger يوثق نوع الدلالة:
- الحدث: source ID وoccurrence ID ووقت وصول/مصدر وحمولة ذات شكل ثابت وسياسة إزالة تكرار/replay/الاحتفاظ والخصوصية.
- الحالة: true/false/unknown وقراءة source وlast refresh/during permission loss وسياسة initial state وعند الدخول والخروج.
- العتبة: وحدات وحدود inclusive/exclusive ومقارنة وهسترة ومدة ثبات وحد أخذ عينات وسلوك sensor missing.
- الجدول: IANA/local timezone وDST والإعادة ونافذة البداية/النهاية والدقة الفعلية وسياسة missed run/reboot.
- الحدث الخارجي: provider identity/authentication/TTL/volume/signature/replay/retry/offline health.

لكل محرّك: source ID مستقر، previous/current state، initial observation، edge duration، debounce/dedupe keys، minimum interval، clock، permission/capability dependencies، نوع بيانات PII، reason code، event payload version، restart policy، failure state، localized preview. لا تجمع بين event ALL والاستدلال التاريخي: ANY/ALL الحاليان يستخدمان الحالة الحية ووقوع الحدث الجاري. عقد A ثم B خلال N مدة يحتاج DSL/restart state بإصدار جديد.

## عقود الإجراءات

كل Action يحدد target picker، ترتيب أو batch selection، operation ID/version، required/optional typed input/default/unit/limits/expression source، schema output، local/cloud data classification، capability/permission/backend، timeout/cancel، retry/idempotency، requested vs observed completion، failure code، rollback/exit strategy، وdry-run preview.
إن فتح app/settings/Intent معلق دليل تسليم فقط. لا تعاود delete/send/install/purchase إلا مع idempotency أو حالة موثقة. لا تعرض restore إلا بعد حفظ حالة سابقة قابلة للقراءة والاستعادة.

## مصفوفة المجالات

| المجال | محفزات: توسيع مطلوب وحقول تخصيص | إجراءات: توسيع مطلوب وحقول تخصيص | اعتماد Android / الموفر |
|---|---|---|---|
| الوقت والتقويم | وقت دخول/خروج/قرب حدث، أيام استثناء، تقويم ومطابقة توافر/عنوان، منطقة زمنية، offset وDST، missed-run policy | موعد/تذكير محلي مع تاريخ/منطقة وevent ID/idempotency؛ إنشاء/تحرير calendar عبر connector | alarm d/t وضبط الوقت وpermission، مزوّد التقويم، exact alarm لا يعد دقيقًا دون إذن |
| الاتصال والشبكات | نوع النقل من/إلى، validated/captive/metered/roaming/VPN، SSID/BSSID allowlist، DNS/HTTPS check مع timeout وثبات | الاتصال بشبكة معروفة عبر API المسموح أو Settings؛ probe وHTTP bounded HTTPS مع headers/auth ref/body/status/size/schema | WiFi restrictions لإصدارات النظام، CONNECTIVITY callbacks، DNS/SSRF/redirect rules، موفر محلي |
| Bluetooth/BLE/NFC/USB | جهاز/profile paired، beacon/service UUID/RSSI/hysteresis/scan count، NFC tag/record filter، USB vendor/product/interface | GATT read/write UUID وقيم/مهلة، NFC write مع تأكيد، USB طلب بصلاحية مستخدم | عتاد/ميزة، location/nearby permission، scan throttle وDoze، driver/companion |
| المكان والحركة | geofence dwell/accuracy/source, مقارنة مكان/متعدد ANY-ALL، activity transition, سرعة/حركة/اتجاه, حالتا enter/exit | قراءة موقع بدقة متوقعة/timeout، navigation destination/travel mode، سجل محلي مع موافقة/stop | background location/activity permission، Play services حسب البيئة، battery/performance |
| البطارية والجهاز | battery threshold/charger/source/temp/hysteresis، thermal/idle/storage/free-space crossing، reboot/boot completed | الطاقة كحزمة إجراءات آمنة؛ فحص قياسات/readback وإظهار إعداد عند الحاجة | نقرأ فقط ما يكشفه النظام؛ الكتابة/charge cap غالبا OEM/Root أو غير مدعوم |
| الشاشة والصوت | orientation/display connect/audio output/stream level/DND policy/high contrast/screen unlock | fade volume stream+duration، display profile/brightness range، audio route/playback، temporary DND | DND notification policy access، API/ROM differences، cancellation |
| التطبيقات | package foreground enter/leave/selected-app subset، install/update/version/availability/وقت استخدام | launch action allowlisted component/deep link/shortcut/extras typed/resolved activity؛ فتح صفحة التطبيق | package visibility/usage access/accessibility/service policy؛ Intent لا يثبت اكتمال الإجراء |
| الإشعارات | package/channel/category/title/text match/masked/ongoing/group/new-vs-update/expiry/debounce | إخطار Nova channel/title/action buttons/snooze؛ إزالة مجموعة مرخصة؛ reply فقط RemoteInput متاح | notification runtime permission وNotificationListener user grant؛ PII/DND restrictions |
| الاتصال/SMS/البريد | call state/change/duration وSMS sender/SIM/text filter مع redaction contact allowlist | خياران مستقلان: عرض composer أو إرسال موثوق؛ SIM/recipient/content/template/multipart/rate/retry | role/permissions/default dialer/SMS app وسياسات إصدار؛ OAuth/provider للـemail |
| الملفات/المستندات | إنشاء/تعديل/اكتمال تنزيل تحت URI مملوك أو SAF user grant، MIME/name/size/pattern/stability | SAF read/write/append/move/copy/rename/delete/archive/checksum/name conflict/max bytes/atomic commit | grant persisted/revoked، مزود document، staging وتحقق عدم الكتابة خارج URI |
| الإشارات والمصدر الخارجي | webhook/provider events مع source identity/signature/schema/content filter/expiry/dedupe | طلب HTTP/download/upload/multipart/pagination/auth reference/status transform/response constraints | TLS/cloud secrets؛ local explicit allowance؛ DNS rebinding/private address/redirect defense |
| البيانات والمتغيرات | state variable change/threshold/scope/source/retention، workflow output event | صياغة/JSON query وmutate/array mapping/CSV/regex محدود/date math/atomic increment/set values | حدود CPU/input/history/depth؛ لا arbitrary code execution |
| تدفق التحكم | workflow run/widget/manual/shortcut; event sequencing A ثم B مع window/reset | شروط/فرع/switch/repeat bounded/parallel-join/wait-until/subworkflow typed inputs/outputs/cancel | planner/compiler versioning، checkpoint/idempotency/history/privacy |
| المنزل والخدمات | تغير device/scene/provider sensor/contact and source status | approved scene/entity state/command, dynamic schema+picker+connector auth result | لا universal smart-home API؛ integration/bridge فعلي ومقترن مع token scope |
| الإضافات والمرافق | typed plugin event/manifests/connection health/revocation/source version | plugin action متوافق وموقّع مع output/timeout/idempotency | SDK توافق/توقيع ومراجعة الصلاحيات؛ لا إتاحة executable unrestricted |
| Wear/Desktop | watch button/sensor/event/local queue/device relation/connection health | ack command, watch haptic/notification/workflow input, companion sync with timeout/cancel | Wear Data Layer/USB-LAN relay identity/pairing/revocation/offline queue |
| ROM/Accessibility/المؤسسة | user-approved pattern/managed profile/OEM settings change مع source/version | OS/OEM semantic operation عبر capability version/status/readback; settings fallback labeled only-handoff | Accessibility explicit enable، Shizuku/Root/Device Owner/OEM specificity؛ لا تحكم عام مفترض |
| الذكاء الاصطناعي | result/event لا يثق به دون schema/origin/time/privacy; model/provider health | تصنيف/استخراج/تلخيص/تحويل إلى output schema/version/tokens/cost/timeout/approval | موفر محلي/cloud وقدرات النموذج؛ request budgets وPII policy؛ المرور بمسار automation الطبيعي |

## قالب لكل نوع جديد

إنشاء تذكرة implementation تحتوي:
1. Stable canonical identity target/operation/predicate أو trigger source ID مع version وتوضيح هل reuse variant أم capability جديدة.
2. typed input/output schemas والافتراضيات والحدود والمحرر ورسائل التعذر والترجمة وmask/SecretRef/URI policies.
3. Trigger/action semantics ومدى الوقت وتكرار المصدر وحالات unknown، قابلية الإلغاء وإعادة التشغيل والآثار وتعويضها.
4. تحقق Android وAPI range والجهاز/الشريك/الموفر/الإذن/الدور وبيانات source، واستراتيجية unsupported.
5. ملف owner في catalog، editor screen/summary، mapping/migration/export/import، monitor/handler/compiler/planner/router.
6. سيناريو JVM وCompose وAndroid instrumentation وفشل/crash/revoke/replay/battery. اذكر أي سيناريو يتطلب هاتفًا فعليًا.
7. علامة evidence في المصفوفة ورابط CI run/head SHA/device build matrix قبل جعل card ظاهرة أو enabled.

## ترتيب الشحن

A. جرد كامل للعقد الحالية والتحقق من إنجازها ومخرجاتها؛ B. typed editors وcapability UI وvalidations؛ C. canonical-only typed workflow persistence/scheduler/runtime path؛ D. موجة أحداث محلية منخفضة المخاطر والبيانات؛ E. إجراءات local/public APIs بعد اختبار الصلاحيات؛ F. network/files/communication مع التدقيق والحماية؛ G. external providers/companion/plugin signed contracts؛ H. privilege/OEM بعد قياس؛ I. AI actions/events مع output schema وbudgets.

لا يوسع Wave نوعا لمجرد أن schema ظهرت في شاشة البناء. اكتماله يتطلب واجهة قانونية وكتابة/قراءة وتسلسل وتحقق وجدولة وتنفيذ وفشل/إلغاء/رصد واختبار.

## سلوك Android غير القابل للوعد العام

قد يقيد النظام العمل بالخلفية، تشغيل foreground service من الخلفية، الدقة المستمرة، أجهزة BLE، exact alarms، صلاحيات الإشعارات، اتصال WiFi وclipboard. اطلب فقط الإذن عند الحاجة، اشرح الأثر، وفر Settings path عند الرفض، افحص المنح عند التنفيذ، ثم سجل latency/availability العملية. لا تعطل قيود النظام بهواتف OEM أو بتنبيهات مخفية. مصادر Android: https://developer.android.com/develop/background-work/services/alarms ، https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ، https://developer.android.com/guide/topics/permissions/overview .
