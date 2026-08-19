# تدقيق معمارية تنفيذ القدرات متعددة الطبقات في NexaFlow

**المؤلف:** Manus AI  
**الحالة:** خط أساس معماري قبل التنفيذ  
**النطاق:** تحويل مسار تنفيذ الإجراءات تدريجياً من نتائج تحكم نصية مرتبطة بالمتحكمات إلى طبقة قدرة منظمة تفصل ما يطلبه سير العمل عن الخلفية التي تنفذه.

> **مبدأ التصميم:** يطلب سير العمل `Capability` موصوفة وسياسة تنفيذ، ولا يطلب أمراً لـ `su` أو Shizuku أو واجهة Android بعينها. يختار محلل الخلفيات أفضل تنفيذ متاح وموثوق، ثم يتحقق من النتيجة قبل تسجيلها.

## ملخص تنفيذي

NexaFlow لا يبدأ من الصفر. لدى المشروع بالفعل محرك تنفيذ ناضج نسبياً، وسجل إجراءات قابل للتمديد، ومفسر سير عمل يدعم التسلسل والتوازي والفروع والحلقات والمهل والإلغاء التعاوني، إضافةً إلى مسار توافق للمهام القديمة. كما يملك دعماً قائماً لـ Root وShizuku واكتشاف ROM وقدراته، لكنه موزع حول `SystemController` و`PrivilegedRunner` ويُرجع غالباً `SystemControlResult(success, message)`، وهو عقد لا يستطيع تمثيل **القدرة المطلوبة والخلفية المختارة وحالة التحقق وسبب الفشل الآلي**.

لذلك فإن المسار الصحيح هو **توسعة توافقية** لا إعادة كتابة: تبقى `ActionType` و`ActionHandler` و`ActionRegistry` ومسارات Automation القديمة صالحة، ويضاف محول يحوّل الإجراءات المختارة إلى `CapabilityRequest`. يمر الطلب إلى `CapabilityResolver` وسياسة تنفيذ وخلفية مناسبة، ثم يعاد إلى المعالج كـ `SystemControlResult` متوافق مع التاريخ الحالي، مع حفظ النتيجة المنظمة تدريجياً في خط الأحداث/السجل.

| قرار معماري | السبب | الأثر العملي |
|---|---|---|
| لا محرك Workflow جديد | `WorkflowInterpreter` ومسار التوافق موجودان ويغطيان التوازي والفروع والإلغاء. | تحافظ المهام الحالية على سلوكها. |
| لا تمرير أوامر shell من الإجراءات | `PrivilegedRunner` و`SystemController` مرتبطان حالياً بنصوص أوامر. | كل أمر ممتد يخرج فقط من backend داخلي مُصادق ومقتبس. |
| Android API أولاً | الخلفيات الممتازة أعلى صلاحية لا يجب اختيارها لأن الجهاز Root فقط. | تقليل المخاطر وتحسين قابلية النقل. |
| `PackageInstaller` لا يساوي نجاحاً صامتاً | جلسة التثبيت قد تحتاج إجراء مستخدم؛ النجاح الفعلي هو نتيجة الجلسة والتحقق اللاحق. [5] | حالات `PENDING_USER_ACTION` و`VERIFICATION_FAILED` صريحة. |
| كشف التطبيقات قد يكون جزئياً | Android 11+ يرشح رؤية الحزم افتراضياً؛ قائمة الحزم حساسة. [4] | تقرير التوفر يعلن نطاق الرؤية ولا يدعي جرداً كاملاً. |

## البنية الحالية

### مسار التنفيذ وسير العمل

يبدأ التنفيذ التقليدي في `core:execution` عبر `ExecutionEngine`، بينما يربط `AutomationWorkflowRunner` المهام القديمة بالمفسر الحديث. يقرأ `ActionRegistryExecutor` نوع الإجراء من `ActionRegistry` ثم يفوضه إلى `ActionHandler`. هذا فصل جيد يجب الاحتفاظ به؛ فالمفسر لا يعرف كيف ينفذ الإجراء، ويعالج التسلسل والتوازي والفروع والحلقات والمهل والإلغاء التعاوني. لكن العقد النهائي لا يزال `SystemControlResult` النصي فقط، وسياق الإجراء يعتمد مباشرة على `SystemController`.

| المكوّن | الوضع الحالي | ما يعاد استخدامه |
|---|---|---|
| `WorkflowInterpreter` | تسلسل/توازٍ/فرع/حلقة/مهلة وإلغاء تعاوني. | يبقى مصدر التحكم في DAG ولا يتغير. |
| `ActionRegistry` و`ActionHandler` | سجل واحد قابل للتمديد ويمنع تعارض `ActionType`. | يظل مدخل الإجراءات؛ يضاف معالج قدرة أو محول فقط. |
| `ActionExecutionContext` | سياق تطبيق/متحكم/تشغيل/قناة/معرّف مهمة. | يوسع اختيارياً بسياق capability من دون كسر الإنشاءات الحالية. |
| `WorkflowRunContext` | تمرير نواتج JSON بين عقد السير. | يحفظ مخرجات capability الآمنة والملخصة. |
| `ExecutionEngine` | تاريخ التنفيذ، قيود، متغيرات، خروج وإرجاع حالة. | يستقبل نتيجة منظمة ويستمر في دعم السجل القديم. |

### المحفزات والحالة والجدولة

تنسق `MonitoringService` مجموعة مراقبات واسعة تشمل البطارية والاتصال والموقع وBluetooth والتقويم والحساسات والحزم والوسائط والصوت والإعدادات وwebhook. وينظم `AutomationScheduler` محفزات الزمن، بينما يستخدم مسار الشروط `ConstraintSnapshot` و`ConstraintEvaluator`. يثبت هذا أن المشروع يملك مصادر حالة فعلية بالفعل، لكن القراءة قبل التنفيذ ليست بعدُ **خدمة حالة موحدة ذات صلاحية زمنية ومصدر وتحديثات**؛ بعض فروع الشروط والمحفزات تقرأ Android APIs مباشرة.

المسار المقترح لا يستبدل المراقبات. في المرحلة الأولى يعرّف `DeviceStateProvider` يجمع اللقطة الموجودة ويوسعها بحقول مصدرية وطابع زمني. وفي مرحلة لاحقة تعيد المراقبات نشر تحديثات محددة إلى cache مركزي. بهذا تبقى سياسة التنفيذ والشروط قادرة على القراءة من لقطة متناسقة لا من مصادر متباينة.

### التكاملات المرتفعة الصلاحية وROM

`core:rom-integration` يحتوي عملياً على أساس مهم: يكشف `PrivilegedRunner` حالة Shizuku وRoot، ويتحقق من سلسلة الأوامر عبر `SafeCommandBuilder`، ويفضل Shizuku ثم Root. ويوفر `ShizukuShellBridge` خدمة مستخدم مرتبطة مع إدارة إعادة الربط ومسار توافق قديم. كما يملك `SystemController` عمليات موجودة لـ force-stop ومسح بيانات التطبيق وتثبيت APK وإلغاء التثبيت وتمكين/تعطيل الحزم. ويعرض `RomIntegrationManager` و`RomCapabilityProvider` كشفاً لمستوى التكامل وقدرات ROM.

هذه الوظائف **قابلة لإعادة الاستخدام وليست جاهزة بعد كطبقة قدرة عامة**. فالاختيار في `PrivilegedRunner` ثابت على مستوى shell لا على مستوى القدرة، وحالة النتيجة لا تعلن الخلفية أو رمز الخطأ أو المدة أو نتيجة التحقق. كذلك لا يجب أن يصبح المسار الانعكاسي القديم لـ `Shizuku.newProcess` خياراً دائماً؛ الدليل الرسمي يوصي بخدمة المستخدم، ويبين أن قدرات shell تختلف عن root. [6]

### الواجهات والتخزين والمراقبة

توجد شاشة `CapabilityCenterScreen` لكنها تعرض `RomCapability` كحالة متعلقة بالـ ROM في الأساس، لا قابلية تنفيذ Workflow مع سبب منظم. ويملك المشروع Room وMigrations تراكمية وسجل تنفيذ مع نتائج إجراءات JSON، ما يسمح بإضافة سجل capability/policy بصورة تدريجية بدل متجر منفصل. كما توجد طبقة Plugin SDK، ومعالج HTTP قائم لديه مهلة وإعادة محاولة وidempotency key؛ لذلك سيغلف لاحقاً كخلفية شبكة بدلاً من نسخ transport جديد.

## المكونات القابلة لإعادة الاستخدام والفجوات

| المجال | أصول قائمة قابلة لإعادة الاستخدام | الفجوة التي يجب سدها |
|---|---|---|
| توجيه الإجراءات | `ActionRegistry`, `ActionHandler`, `ActionRegistryExecutor` | محول من Action إلى CapabilityRequest وحل backend لكل قدرة. |
| سير التنفيذ | `WorkflowInterpreter`, `WorkflowRunContext` | نتيجة منظمة ومسار تحقق وإعادة محاولة مستقلان عن ActionType. |
| Root | `PrivilegedRunner`, `SafeCommandBuilder`, `RootPermissionGranter` | منفذ root مبني على مواصفة operation مسموح بها، stdout/stderr منفصلان وإلغاء صريح. |
| Shizuku | `ShizukuShellBridge`, UserService, كشف binder/grant | Backend معلن قدراته وصحته، لا يعالج string command من Action مباشرة. |
| إدارة الحزم | `SystemController` وAndroid `PackageManager` | عقد package read/mutate، رصد رؤية الحزم، تحقق قبل/بعد، اختيار Android API أو privileged backend. |
| تثبيت APK | `SystemController.installApk` | جلسات PackageInstaller، انتظار حالة، user action، split APK، تحقق توقيع/إصدار. |
| الشبكة | `HttpRequestHandler` و`RetryExecutor` | واجهة network capability ونتيجة منظمة وإخفاء الأسرار في telemetry. |
| التوفر والواجهة | `RomIntegrationManager`, `CapabilityCenterScreen` | حالة capability متعددة القيم: available/partial/permission-required/unavailable مع سبب وخلفيات متاحة. |
| السجل والتخزين | Room history والمهاجرات | metadata منظمة: capability/backend/error/verification/retry/duration. |

## التصميم المستهدف المتوافق

```text
WorkflowInterpreter / ExecutionEngine
          │
          ▼
ActionRegistry → CapabilityActionHandler / CapabilityRequestMapper
          │
          ▼
CapabilityExecutionService
   ├── CapabilityRegistry
   ├── ExecutionPolicyEvaluator
   ├── CapabilityResolver
   ├── BackendRegistry
   ├── VerificationEngine
   └── Retry / Cancellation adapter
          │
          ▼
Android API | Intent | PackageManager | PackageInstaller | Shizuku | Root | Network | OEM extension
          │
          ▼
CapabilityResult → compatible SystemControlResult → History / telemetry / run context
```

### عقود المرحلة الأساسية

تعيش النماذج المحايدة لـ Android داخل `domain`، وتعيش resolver والخلفيات في `core:execution` و`core:rom-integration` بحسب المسؤولية. يجب أن يتضمن `CapabilityResult` معرف القدرة والخلفية والحالة ورمز خطأ قابل للمعالجة والرسالة الآمنة والمدة ومعلومات التحقق وmetadata غير الحساسة. تُمنع الأسرار ومسارات الملفات الكاملة وأوامر root ومعاملات المصادقة من السجل الافتراضي.

| العقد | مسؤولياته الأساسية |
|---|---|
| `CapabilityId` و`PrivilegeLevel` و`RiskLevel` | تمثيل ثابت وقابل للتسلسل للقدرات والحدود. |
| `CapabilityRequest` | العملية المطلوبة والمعطيات المقيدة وسياسة التنفيذ والتحقق. |
| `CapabilityDescriptor` | Android/API الأدنى، المتطلبات، الخلفيات والدرجة والمخاطر. |
| `CapabilityBackend` | `availability(deviceContext)`, `supports(request)`, `execute(request, context)`, وhealth. |
| `CapabilityResolver` | ترتيب الخلفيات حسب policy والتوفر والدعم الفعلي، لا حسب root وحده. |
| `CapabilityResult` | success/status/errorCode/backend/duration/verification/metadata. |
| `VerificationEngine` | يتحقق من الحالة الفعلية بعد التنفيذ ولا يساوي exit code صفراً بالنجاح. |
| `ExecutionPolicy` | Wi‑Fi، بطارية، شحن، شاشة، حرارة، مهلة، ترتيب خلفيات، retry. |
| `CapabilityDiagnostics` | جهاز وSDK وخلفيات وقدرات وحالات متاحة جزئياً مع أسباب. |

## الأولويات التنفيذية ونطاق أول إصدار

لا يمكن تنفيذ كل الخلفيات المتصورة دفعة واحدة من دون زيادة المخاطر. الإصدار الأول الحقيقي يجب أن يغطي **الهيكل الكامل** مع capabilities متحققة فعلياً للحزم، ويبقي نقاط الامتداد لبقية الخلفيات. الترتيب المقترح أدناه يحقق قيمة عملية، ويمنع ادعاء دعم Google Play أو ADB أو OEM حين لا تتوفر صلاحية صحيحة.

| الدفعة | محتواها | معيار القبول |
|---|---|---|
| A | نماذج capability/result، registry/resolver/policy، تشخيص، اختبارات نقية. | Resolver يختار Android API قبل Shizuku/Root عندما تكون القدرة مدعومة. |
| B | `PACKAGE_READ` و`PACKAGE_FORCE_STOP` و`PACKAGE_ENABLE_DISABLE` و`APP_DATA_CLEAR` عبر backends حقيقية. | لا Action يستدعي shell؛ الخلفية والسبب يظهران في النتيجة. |
| C | `PACKAGE_INSTALL`/`PACKAGE_UNINSTALL` عبر `PackageInstaller` ومسار user action، مع privileged fallback فقط إن دعمه resolver. | حالة session ليست نجاحاً، والتحقق بعد النتيجة إلزامي. |
| D | Action `UPDATE_APPS` بسياسة scope/network/charging/battery/retry/verify. | لا UI automation لـ Google Play، والحالة غير المدعومة صريحة. |
| E | موفر حالة موحد، verification/retry/cancellation/telemetry، ثم شاشة التشخيص. | الفشل غير القابل للإصلاح لا يعاد بلا نهاية؛ لا تسرب أسرار. |
| F | نقاط امتداد Accessibility وOEM وNative وADB وnetwork. | لا تدخل الخلفيات الاختيارية في core كاعتمادات إجبارية. |

## ملفات التعديل والإنشاء المتوقعة

| المسار | الإجراء | الغرض |
|---|---|---|
| `domain/.../models/Automation.kt` | تعديل توافقي | إضافة `ActionType` وسياسات قابلة للتسلسل عند اكتمال المعالج. |
| `domain/.../capability/*` | إنشاء | models: capability, result, policy, retry, verification, privilege. |
| `core/execution/.../capability/*` | إنشاء | registry/resolver/service/request mapper/diagnostics/verification. |
| `core/execution/.../handler/ActionHandler.kt` | تعديل محدود | إضافة capability runtime اختياري للسياق فقط. |
| `core/execution/.../handler/ActionRegistry.kt` | تعديل | تسجيل معالج package/update الجديد. |
| `core/execution/.../handler/PackageCapabilityActionHandler.kt` | إنشاء | تحويل Action الحزم إلى طلبات قدرة. |
| `core/rom-integration/.../SystemController.kt` | تعديل تدريجي | الإبقاء كـ adapter للوظائف القديمة وإخراج عمليات الحزم إلى backends. |
| `core/rom-integration/.../PrivilegedRunner.kt` | تعديل تدريجي | جعله منفذاً داخلياً آمناً لا API تنفيذي عام للإجراءات. |
| `core/rom-integration/.../capability/*` | إنشاء | Android package/root/Shizuku/package-installer backends. |
| `core/rom-integration/.../RomIntegrationManager.kt` | تعديل | مصدر جزء من diagnostics لا resolver وحيد. |
| `app/.../di/AppModule.kt` | تعديل | تزويد registry/resolver/runtime عبر Hilt. |
| `core/capability-manager/...` | تعديل | عرض diagnostics ونطاق توفر حقيقي. |
| `core/database/.../Migrations.kt` وكيانات history | تعديل لاحق | تخزين capability telemetry/policy/retry بصورة تراكمية. |
| اختبارات `domain`, `core:execution`, `core:rom-integration` | إنشاء/تعديل | resolver/policy/backend/verification/security/regression. |

## قيود Android والمخاطر الأمنية

Android 11+ يرشح قائمة الحزم، ولذلك لا يجوز لخاصية «كل التطبيقات» أن تدعي الاكتمال خارج نطاق الرؤية المصرح به. كما أن `QUERY_ALL_PACKAGES` حالة استثنائية تخضع لمراجعة Google Play. [4] يتيح `PackageInstaller` تثبيت وترقية وإزالة الحزم، لكنه قد يطلب تفاعل المستخدم، ولا يكون الإكمال الصامت متاحاً عادةً إلا لمالك جهاز أو ملف عمل مرتبط. [5]

لا توفر Google Play واجهة رسمية عامة لتطبيق طرف ثالث كي يتحكم في كل تحديثات المتجر. لذلك سيكون `UPDATE_APPS` في الإصدار الأول **تشغيلاً لتحديث APK معروف ومصدّره موثوق، أو نتيجة `UNSUPPORTED_CAPABILITY`/`PENDING_USER_ACTION` دقيقة**؛ ولن يستخدم إحداثيات أو محاكاة واجهة متجر كحل افتراضي. ويتعامل resolver مع Shizuku كخلفية اختيارية: تتغير قدراته بين هوية shell وroot، ولا تكفي عبارة «Shizuku متاح» لإثبات دعم العملية. [6]

يجب أن تكون قائمة أوامر root ثابتة ومشتقة من data models متحققة، مع اقتباس كل argument بواسطة `SafeCommandBuilder`. ويحظر إدخال نص shell خام من Workflow أو plugin، وتُخفى القيم الحساسة من logs. وكل backend يصرح بمستوى privilege وrisk، ويجب رفض الطلب قبل التنفيذ إن لم تسمح له policy أو صلاحية المستخدم أو توافق Android.

## استراتيجية الاختبار والهجرة

تبدأ الاختبارات بوحدات نقية لـ descriptor/registry/resolver/policy/retry/error mapping وverification. تستخدم اختبارات Robolectric فقط للواجهات القابلة للمحاكاة (PackageManager/PackageInstaller callbacks/Intent resolution)، ولا تدعي نجاح Root أو Shizuku من mock إنتاجي. تخصص اختبارات تكامل واضحة لأجهزة مضبوطة بها Root أو Shizuku، وتعيد `BACKEND_UNAVAILABLE` في البيئات الأخرى. وبعد كل دفعة: `git diff --check`، lint، اختبار الوحدة للوحدات المتأثرة، ثم البناء المناسب وتحليل الانحدار.

تظل Actions وAutomations المسجلة كما هي. تضاف capabilities الجديدة كـ ActionTypes جديدة أو يتم ربط ActionType قائم بواسطة mapper؛ لا يعاد تفسير JSON القديم ولا تفرض ترقية للمستخدم. في بداية الهجرة تبقى `SystemControlResult` واجهة توافق خارجية وتُخزن بيانات capability المنظمة كـ metadata/JSON، ثم يوسع التخزين Room بمهاجرة additive بعد استقرار العقد.

## المراجع

[4]: https://developer.android.com/training/package-visibility "Package visibility filtering on Android"
[5]: https://developer.android.com/reference/android/content/pm/PackageInstaller "PackageInstaller API reference"
[6]: https://github.com/RikkaApps/Shizuku-API "Shizuku API developer guide"
