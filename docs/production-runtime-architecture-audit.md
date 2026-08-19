# تدقيق معمارية NexaFlow كـ Production-Grade Automation Runtime

**المؤلف:** Manus AI  
**الحالة:** تدقيق قبل التنفيذ المتدرج  
**القرار الحاكم:** تُوسَّع مكونات NexaFlow الحالية عبر عقود مستقلة وتخزين إضافي متوافق. لا يُستبدل محرك Workflow ولا تُبنى خدمة Runtime موازية.

> **المبدأ التنفيذي:** `Workflow → Intent/Capability → Policy → Resolver → Backend → Verification → Durable Result`.
>
> لا يمر أي Workflow أو Plugin مباشرة إلى أمر shell أو Root أو خدمة Accessibility.

## الخلاصة

NexaFlow يمتلك قاعدة قوية بالفعل: مفسر سير عمل يدعم DAG الداخلي، سجل معالجات إجراءات، مراقبات Android ومجدولاً، سياق تشغيل JSON محدود الحجم، متغيرات عالمية مشفرة عند الحساسية، Queue محلي بأولوية وإلغاء ومهلة، سجل أداء، Root/Shizuku، وتنفيذ Plugins بنمط Locale. لكنه ليس Runtime إنتاجياً متيناً بعد؛ الحالة الجارية ما زالت في الذاكرة في عدة مواضع، والـ event layer لا يطبع events في bus موحد، والسجل والنتائج نصية غالباً، ولا توجد استعادة تنفيذ/إدارة موارد/تعبيرات/إصدارات workflow مستقلة.

| النتيجة | قرار التطوير |
|---|---|
| Workflow/DAG قائم ومناسب | يبقى `WorkflowInterpreter` ومحولات Automation الحالية مصدر التنفيذ المنطقي. |
| Event sources موجودة وموزعة | تُطبع إلى `NexaFlowEvent` مركزي عبر bus؛ لا تُسجل Workflows على Android broadcasts مباشرة. |
| Data runtime جزئي | يُعاد استخدام `WorkflowRunContext` للـ execution scope و`VariableRepository` للـ global scope، مع طبقات scope وواجهة قيمة typed. |
| Queue محلي ناضج جزئياً | يُوسع `TaskManager` أو يُغلف كـ scheduler runtime؛ لا يُنشأ queue ثانٍ. |
| معاملات revert موجودة في الذاكرة | تتطور إلى lifecycle جرى التحقق منه ومخزن recovery متين للعقد غير المؤكدة. |
| Secret storage قائم | يتطور إلى credential references وredaction؛ لا تُنشأ SharedPreferences للأسرار. |
| Plugin support قائم لكنه محدود | يبقى Locale action integration، ويضاف extension manifest/registry منفصل تدريجياً. |

## ما هو قائم حالياً

### 1. Workflow، الإجراءات وDAG

`WorkflowInterpreter` في `core:execution` يدعم sequence وparallel وbranch وloop، ومهلة العقدة والإلغاء التعاوني وrollback على مستوى المسار. و`ActionRegistry` هو نقطة التمديد الوحيدة لـ `ActionHandler`، بينما يبني `ActionRegistryExecutor` مسار الإنتاج من السجل إلى handler. هذا هو الموضع الصحيح لتمرير التنفيذ الجديد عبر capability layer لا موضع لإعادة كتابة DAG.

| المكوّن القائم | قيمة إعادة الاستخدام | الفجوة الإنتاجية |
|---|---|---|
| `WorkflowInterpreter` | ترتيب العقد والتوازي والفروع والإلغاء. | لا يسجل cursor متيناً أو node state قابلاً للاستئناف. |
| `WorkflowRunContext` | JSONPath، merge-patch، حد 256KB، معرّف run. | execution-local فقط؛ لا snapshot persisted أو typed scopes. |
| `ActionRegistry` / handlers | extensibility مضمونة ومنع تعارض الأنواع. | لا contract موحد لـ idempotency/transaction/resource needs. |
| `ExecutionEngine` | gate للقيود، سجل history، lifecycle للـ exit behavior. | active state وسجل النتائج غير كافيين للاستعادة المعتمدة على العقد. |

### 2. المحفزات، الأحداث والحالة

`MonitoringService` يشغل مراقبات مشتركة للبطارية والاتصال والموقع وBluetooth والإعدادات والحزم والوسائط والصوت والحساسات وwebhook. ويوفر `EventSource` بالفعل lifecycle موحداً `start/stop` وخريطة من trigger إلى source. ويهتم `AutomationScheduler` بمحفزات الوقت، بينما تقدم `AppTriggerAccessibilityService` مثالاً لمصدر حدث وصولي لحالة التطبيق الأمامية.

هذه أصول مهمة، لكن الأحداث تقود التنفيذ من المراقب مباشرةً ولا تحمل بعد correlation ID أو canonical payload أو deduplication أو ordering. كما أن `ConstraintSnapshot` لقطة بسيطة للقيود، وليست State Engine بموفرين مشتركين وطابع زمني وصلاحية cache. لذلك تكون المرحلة الصحيحة هي إضافة `EventNormalizer` و`NexaFlowEventBus` و`DeviceStateProvider` على رأس المراقبات لا استبدالها.

### 3. البيانات والمتغيرات والأسرار

يدعم `WorkflowRunContext` بيانات تشغيلية محلية بنطاق التنفيذ، وفيه JSONPath ونسخ آمنة وحد ذاكرة. كما أن `VariableRepositoryImpl` يخزن المتغيرات العالمية، ويستخدم `SecureStorage`/Keystore عندما تكون القيمة حساسة؛ لا تحتوي قاعدة البيانات النص الصريح للسر. هذه نواة Vault عملية يجب توسيعها إلى `CredentialReference` وredaction، لا استبدالها.

| النطاق المطلوب | الأصل الحالي | مسار التوسعة |
|---|---|---|
| Global | `VariableRepository` وRoom، مع `sensitive`. | يحافظ عليه، ويضيف typed wrapper وmetadata. |
| Workflow | لا تخزين مخصص. | تعريف workflow metadata/JSON additive بعد schema versioning. |
| Execution | `WorkflowRunContext`. | snapshot قابل للاستعادة مع whitelist للأنواع وbudget. |
| Node/Action/Temporary | غير صريح. | stack scopes في runtime لا تتسرب خارج التنفيذ. |
| Secret/Credential | `SecureStorage` وقيم global حساسة. | references فقط في workflow، منع export/log/plain UI. |

### 4. الاستمرارية والاستعادة والمعاملات

`ActiveExecutionStore` يسجل معرفات automations التي بدأت حتى يمنع duplicate end behavior، لكنه لا يخزن executionId أو node cursor أو backend state. و`DeviceStateTransactionStore` يلتقط snapshot للتراجع عن حالة الجهاز في خريطة داخل الذاكرة، لذلك لا يضمن استعادة بعد قتل العملية. أما Room فله مسار migrations صريح متدرج وhistory يحتفظ بالقناة ونتائج الإجراءات JSON، ما يجعله المكان الطبيعي لجداول `workflow_executions` و`node_executions` و`execution_checkpoints` الإضافية.

لا يجوز استئناف عقدة غير idempotent بعد crash من دون دليل. لذلك يطبق recovery الآتي: العقدة التي كانت `RUNNING` تتحول إلى `RECOVERING`، يجري verifier الخاص بها، ثم تُعلّم completed إذا تحقق الأثر، أو failed/manual-review إذا لم يمكن إثباته. لا يعاد التنفيذ تلقائياً إلا لعقد معلنة idempotent أو مصنفة safe-to-retry.

### 5. التزامن والموارد والإلغاء

`TaskManager` موجود بالفعل: priority queue، FIFO داخل الأولوية، مهلة، retry، cancellation ونتائج `StateFlow`. وهو عمداً single consumer لضمان عدم توازي إجراءات الجهاز. يمثل ذلك بذرة `ExecutionScheduler` بدلاً من Queue ثانية. التطوير سيكون بتعريف `ExecutionRequest` مستمر وإضافة policy للحد الأقصى للتوازي حسب فئة العمل، مع الانتقال تدريجياً من memory queue إلى Room-backed handoff عند الحاجة.

يمنع `ResourceManager` التعارض عبر resources مرتبة globally ومغلقة في ترتيب ثابت، ومهلة acquisition وإلغاء يحرر الأقفال في `finally`. المرحلة الأولى تغطي `PACKAGE_MANAGER`, `ROOT`, `SHIZUKU`, `ACCESSIBILITY`, `NETWORK`, `STORAGE`; وهي لا تحاول فرض ACID على نظام Android بل توفر exclusive/shared leases ومعلومات WAITING واضحة.

### 6. القدرات والخلفيات المرتفعة

طبقة Capability المكتوبة في هذا المسار هي أساس الفصل بين intent والخلفية. يمتلك المشروع `PrivilegedRunner` و`ShizukuShellBridge` و`SystemController` وقدرات ROM؛ لذلك يعاد تغليفها لا نسخها. ويبقى Android public API وPackageManager وPackageInstaller مفضلة على الخلفيات الأعلى. يعلن resolver النقص كـ `UNSUPPORTED`, `PERMISSION_REQUIRED` أو `PENDING_USER_ACTION` ولا يسجل fake success. توثق Android قيود رؤية الحزم وضرورة تدخل المستخدم في جلسات التثبيت غير المميزة. [1] [2]

### 7. Plugins والتوافق والتشخيص

يكتشف `PluginRepositoryImpl` حالياً Locale broadcast receivers ويشغّل `PluginFireClient` plugin actions مع مهلة. هذا ليس extension SDK كاملاً، لكنه مدخل صالح لتوسعة plugin manifest: API version، capabilities مطلوبة، permissions، trust level وbackend registrations. لا يحمل plugin امتياز Root أو السر أو الشبكة ضمنياً؛ runtime يطلب capability وتقرر policy السماح.

`RomIntegrationManager` و`CapabilityCenterScreen` يمثلان أساس diagnostics الحالي. سيتوسعان إلى compatibility matrix ناتجة من descriptors وbackend health وAndroid SDK وROM/permissions، لا جدولاً ثابتاً في Compose.

### 8. دورة الحياة والطاقة والمراقبة

`NexaFlowApplication` يهيئ Shizuku وWorkManager وmaintenance وlocation schedule وAlarm scheduler وMonitoringService مع fallback عند رفض foreground-service start. هذا موضع Bootstrap صحيح لـ recovery scan وتحديث lifecycle state. لا يضاف loop قصير دائم؛ تستعمل المراقبات event-driven الحالية، AlarmManager للأوقات الدقيقة، WorkManager للأعمال المؤجلة/القابلة للقيود، وforeground service فقط حين يبرر trigger monitoring ذلك.

`LogStore` يملك timeline/errors/metrics لكن تنفيذه الافتراضي in-memory. تتوسع نماذجه لتتضمن executionId/workflowId/nodeId/capability/backend/status/error/verification مع redaction، ثم تُضاف persistence/export قابلة للضبط بعد ثبات القياس. لا تنقل secrets أو raw commands إلى telemetry.

## قائمة الفجوات وأولوية التنفيذ

| الأولوية | الفجوة | المكوّن المقترح | سببها واختبارها |
|---|---|---|---|
| P0 | event canonicalization وstate snapshot | EventBus + State providers | يمنع مراقبة مكررة ويتيح matching/dedup؛ اختبارات event ordering وstate transitions. |
| P0 | capability/action structured outcome | Capability registry/resolver/result | يعزل backend ويمنع fake success؛ اختبارات resolver/error/verification. |
| P0 | durable cursor/recovery | execution state machine وRoom checkpoints | يمنع duplicate side effects بعد crash؛ اختبارات recovery/fault injection. |
| P0 | resource serialization | ResourceManager على `TaskManager` | يمنع تنافس package/root/accessibility؛ اختبارات lock ordering/cancellation. |
| P1 | typed scoped variables/expressions | Data Runtime + safe parser | يسمح شروطة/مخرجات بدون eval؛ اختبارات parser/scope/security. |
| P1 | idempotency/transaction lifecycle | operation contracts + verifier | قرار retry آمن؛ اختبارات duplicate/recover/compensate. |
| P1 | credentials vault/reference | Credential facade فوق SecureStorage | يحمي tokens/export/logs؛ اختبارات redaction/lost-key. |
| P1 | workflow schema/migration/import | versioned envelope + migration registry | توافق نسخ قديمة؛ اختبارات non-destructive migrations. |
| P2 | debugger/dry run | runtime observer/checkpoint API | تشخيص دون side effects؛ اختبارات dry-run limitations. |
| P2 | plugin extension registry | manifest + trust gates | قابلية توسع OEM/network؛ اختبارات API compatibility/isolation. |

## ملفّات التوسعة المتوقعة

| النطاق | المسار المقترح |
|---|---|
| نماذج capability/runtime العامة | `domain/.../capability`, `domain/.../runtime` |
| event/state/data/expression | `core:execution/.../event`, `state`, `data`, `expression` |
| الاستمرارية/الـ scheduler/resources | `core:execution/.../durable`, `task`, `resource`; Room في `core:database` |
| الخلفيات Android/Root/Shizuku/package | `core:rom-integration/.../capability` |
| الأسرار | `core:security` مع repository adapter في `data` |
| التهيئة | `app/.../di/AppModule.kt`, `NexaFlowApplication.kt` |
| diagnostics/UI | `core:capability-manager` و`feature:settings` |
| tests | وحدات domain/execution/rom/database وinstrumented tests واضحة للـ Android APIs |

## استراتيجية الهجرة والجودة

تُنفذ الدفعات الصغيرة بالترتيب: العقود والـ pure tests، event/state، data/expression، execution state/recovery، resource/concurrency، capabilities/backends، ثم metadata/UI. يبقى كل `Automation` قديم صالحاً: تضاف schemaVersion بقراءتها الافتراضية، ولا تعدل JSON المخزن destructively قبل نجاح migration. وتبقى `SystemControlResult` طبقة توافق للواجهة الحالية حتى يتحول history إلى structured metadata تدريجياً.

بعد كل دفعة: فحص whitespace/static analysis، اختبارات الوحدة المتأثرة، اختبار Robolectric/تكامل مناسب، مراجعة عدم تسرب سر، ثم regression لمسارات actions/triggers/schedules القائمة. لا تعد بيئة الاختبار Root أو Shizuku متاحة؛ تستخدم اختبارات تكامل موسومة تتخطى بوضوح عند غياب الخلفية، ولا تستبدل ذلك بنتيجة نجاح وهمية.

## المراجع

[1]: https://developer.android.com/training/package-visibility "Package visibility filtering on Android"
[2]: https://developer.android.com/reference/android/content/pm/PackageInstaller "PackageInstaller API reference"
