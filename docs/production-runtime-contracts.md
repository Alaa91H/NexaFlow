# عقود Production Runtime في NexaFlow

**الحالة:** تصميم قبل التنفيذ.  
**قاعدة التوافق:** لا تتغير دلالات `Automation` أو `Action` أو `WorkflowInterpreter` الحالية. تدخل العقود الجديدة من خلال adapters وخيارات افتراضية آمنة.

## 1. Event وState Runtime

### Event Contract

`NexaFlowEvent` هو شكل الإشارة الداخلي الوحيد الذي تتعامل معه مطابقة Workflows. يطبع كل monitor أو alarm أو webhook أو accessibility source مدخلات Android إلى event موحد؛ ولا تسجل Workflow receiver مستقلاً لكل signal.

| الحقل | الغرض |
|---|---|
| `id` | UUID فريد للحدث. |
| `type` | نوع محدد مثل `BATTERY_CHANGED` أو `PACKAGE_CHANGED` أو `WEBHOOK_RECEIVED`. |
| `occurredAt` | وقت الرصد بالـ epoch milliseconds. |
| `source` | معرف مصدر ثابت من `EventSource` القائم أو provider إضافي. |
| `correlationId` | يجمع أحداث workflow أو request واحد. |
| `deduplicationKey` | يمنع معالجة event نفسه أكثر من مرة في نافذة محددة. |
| `payload` | بيانات JSON-safe ومحدودة الحجم؛ لا secrets أو raw intents. |

`EventBus.publish` غير حاجب، و`subscribe(filter)` يعيد token يمكن إلغاؤه. تخصصات ordering محلية لمصدر واحد فقط؛ لا توعد المنصة بترتيب عالمي بين broadcast وwebhook وalarm. يحتفظ `TriggerIndex` بمهمة matching السريع، بينما يقرر dispatcher متى يسلم event إلى `ExecutionScheduler`.

### State Contract

`DeviceStateProvider` ينتج slices جزئية من الحالة مع timestamp ومصدر وصلاحية. يجمع `DeviceStateEngine` slices إلى snapshot واحدة عند بدء policy/condition، فلا تقرأ كل شرط Android APIs ثانيةً. تسري صلاحية cache قصيرة وقابلة للتكوين؛ القيم غير المقروءة تمثل `Unknown` لا `false`، وتفشل الشروط الحساسة بأمان عند عدم القدرة على التحقق.

| مبدأ | التطبيق |
|---|---|
| مشاركة المراقبات | المراقبات الحالية تنشر state events بدلاً من إنشاء receiver إضافي. |
| لا polling سريع | event-driven أولاً، وWorkManager/AlarmManager فقط لأعمال محددة. [7] |
| كشف تغيرات ثابت | `becomes`, `changes`, `remainsFor` مبنية على timestamps وdeduplication. |
| لقطة ذرية | policy وconditions تأخذ snapshot واحدة بالقدر الممكن. |

## 2. Data وExpression Runtime

### القيم والنطاقات

يُعرّف `RuntimeValue` كتسلسل آمن من: null، boolean، long، double، string، array، object وbinary reference (معرف فقط، لا bytes). ولا يسمح بكائن Android أو دالة أو reflection داخل variable store. تكون قواعد lookup، من الأقل إلى الأعلى: `GLOBAL → WORKFLOW → EXECUTION → NODE → ACTION`. ولا يصعد أي write محلي تلقائياً إلى نطاق أوسع.

`WorkflowRunContext` يبقى implementation للـ execution delta، لكن يكتسب adapter typed/snapshot بدل تحويله إلى مخزن Room مباشرة. أما global variables الحساسة فتبقى في `SecureStorage` ويظهر للـ workflow `SecretReference` لا القيمة في تعريف JSON.

### التعبيرات الآمنة

يعرّف `ExpressionEngine` parser/interpreter محدوداً من دون `eval`, Kotlin script أو shell. يقبل literals، paths، المقارنات، `AND/OR/NOT`, `contains`, `startsWith`, `endsWith`, `matches`, `in`, `isNull`. يحدد grammar وAST صغيرين؛ وتقارن الأرقام رقمياً فقط عندما يكون النوعان رقمين، وتفشل المقارنات غير المتوافقة برسالة `INVALID_EXPRESSION` بدلاً من coercion خفي.

```text
expression  := or
or          := and ("OR" and)*
and         := unary ("AND" unary)*
unary       := "NOT" unary | comparison | "(" expression ")"
comparison  := operand operator operand | function
```

لا تملك expression صلاحية شبكة أو ملفات أو root أو وصول Android. تستعملها condition/branch/policy فقط، بينما تغير الحالة يمر عبر capability execution.

## 3. التنفيذ المتين والاستعادة

### State Machine

تضاف حالة تنفيذ محفوظة منفصلة عن history append-only:

```text
QUEUED → RUNNING → WAITING | RETRYING | PAUSED
RUNNING → SUCCESS | FAILED | CANCELLED | RECOVERING
RECOVERING → RUNNING | SUCCESS | FAILED | PAUSED
```

يتضمن execution record: `executionId`, `automationId/workflowId`, schemaVersion، currentNode، completedNodeIds، pending node، variables snapshot المصرح، retry state، resource leases، idempotency keys، backend/verification state وtimestamps. تحفظ نقطة checkpoint **قبل** تنفيذ عقدة side-effecting وبعد verified completion. ويبقى payload السرّي reference فقط.

### Recovery وIdempotency

تُصنّف capability/action بـ `IDEMPOTENT`, `NON_IDEMPOTENT`, أو `UNKNOWN`. عند process death لا يعيد recovery عقدة RUNNING. ينفذ verifier أولاً؛ فإذا تحقق الأثر تسجل completed، وإذا كانت idempotent ويمكن إثبات عدم التنفيذ يعاد جدولتُها، وإلا تتحول إلى failed أو paused للمراجعة. عند وجود idempotency key (مثل HTTP) يعاد استخدامه في retry/recovery.

المعاملات تكون best-effort: `prepare → execute → verify → commit`؛ ويعلن descriptor إن كان rollback أو compensating action متاحاً. لا يعد Android operation ACID، ولا يحذف هذا القيد من telemetry.

## 4. Scheduler والموارد والإلغاء

`TaskManager` هو أساس الـ scheduler. يضاف له adapter لا Queue ثانية: يحول execution durable إلى `PendingTask` ويضيف limits مجمعة بمتطلبات workflow. يظل الافتراضي محافظاً لتجنب التوازي العشوائي في تغييرات الجهاز؛ ويسمح بالتوازي للعمليات المصنفة الآمنة فقط.

| capability class | lease افتراضي |
|---|---|
| Package install/uninstall/enable/clear | `PACKAGE_MANAGER` حصري. |
| Root operation | `ROOT` حصري. |
| Shizuku operation | `SHIZUKU` حصري. |
| UI automation | `ACCESSIBILITY` حصري وforeground-aware. |
| HTTP read | `NETWORK` مشترك ضمن حد التوازي. |
| File/APK staging | `STORAGE` حصري للمسار. |

`ResourceManager.acquireAll` يرتب الموارد order ثابتاً لمنع deadlock، ويفرض timeout. يحتفظ scope بكل lease ثم يحررها في `finally` عند success/failure/cancellation. ولا يحاول استعادة lock داخل عملية مقتولة؛ بدلاً من ذلك يعاد بناء lease من state عند recovery بعد expiration آمن.

## 5. Vault، Trust وPlugin Extensions

### Vault

`CredentialVault` هو adapter فوق `SecureStorage`: `put`, `resolve`, `delete`, `contains` ويستخدم alias non-sensitive. توفر workflows `CredentialReference(alias)` فقط. يطبق logger redaction على أسماء الحقول الحساسة والقيم المحتملة، وتستثني import/export/history/debug snapshots كل secret plaintext. يوفر Android Keystore مفاتيح لا تدخل عملية التطبيق ويمكن تقييد استخدامها. [9]

### Trust Model

| trust level | السلوك |
|---|---|
| `TRUSTED` | مكونات NexaFlow الموقعة والمراجعة. |
| `NORMAL` | capabilities عادية داخل policy. |
| `PRIVILEGED` | تتطلب موافقة وإثبات backend مثل Shizuku. |
| `DANGEROUS` | root/clear data/uninstall/modify-system؛ confirmation policy وتسجيل audit. |

confirmation policy مستقلة عن trust: `ALWAYS`, `ONCE`, `NEVER`, `ONLY_FOR_DANGEROUS`. لا تجعل confirmation امتيازاً بحد ذاته ولا تتجاوز permission check.

### Plugins

يتوسع Plugin SDK بواسطة manifest قابل للتحليل يعلن: API version، actions/triggers/conditions/capabilities/backends، permissions، needsNetwork/storage/root/accessibility وUI metadata. يبقى Locale plugin path متوافقاً. لا يسجل plugin backend في resolver إلا إذا مر API-compatibility وtrust/policy، ولا يحصل تلقائياً على capability لم يطلبها.

## 6. Workflow Versioning، Validation وDebug

يُغلف export في envelope يضم `schemaVersion`, `engineVersion`, workflow definition، metadata، required capabilities وplugin requirements، ولا يضم secrets. تتسلسل migrations كنقلات صافية قابلة للاختبار من schema n إلى n+1. يُكتب الملف الجديد فقط بعد نجاح parse/validation/migration، وتحفظ النسخة المصدرية سليمة عند الخطأ.

الـ validator يمر بـ schema → DAG cycle → plugin compatibility → capability availability → permissions → variable references → policy. يعرض المشاكل قبل التنفيذ قدر الإمكان.

يوفر dry run: matching events/conditions، policy، selected backend، permissions وresource conflict المتوقعة، لكنه يعلن صراحةً أن availability وverification في وقت التنفيذ قد تتغير. تستند breakpoints/debugger إلى runtime observer/checkpoints؛ وتعرض values بعد redaction ولا توقف background operation الحساسة في منتصف side effect من دون safe cancellation point.

## 7. Observability وLifecycle

يتطور `LogStore` إلى events منظمة: timestamp, level, executionId, workflowId, nodeId, component, event, status, capability, backend, errorCode, duration/retry/verification. تسجل metrics داخلية قابلة للتصدير مستقبلاً، لا Prometheus dependency في المرحلة الأولى. وتنتج redactor جميع سجلات الأخطاء وmetadata قبل التخزين.

يربط bootstrap في `NexaFlowApplication` أحداث boot/process restart/backend loss/network changes بعملية re-evaluation/recovery idempotent. تستعمل الأعمال الموثوقة بعد إعادة التشغيل أو إعادة الجهاز WorkManager، بينما تبقى AlarmManager للأوقات الدقيقة فقط؛ تخضع الخدمات الخلفية والبثود لقيود Android الحديثة. [7] [8]

## 8. اختبار العقود

كل عقد جديد pure Kotlin قدر الإمكان: expression parser، scope resolver، event dedup، state transitions، scheduler/resource ordering، idempotency/recovery، version migrations، redaction وtrust policy. تختبر Room migrations بـ schema fixtures. وتبقى Android/PackageInstaller/Accessibility/Root/Shizuku integration tests صريحة ومشروطة ببيئة حقيقية؛ لا تحول عدم توفر البيئة إلى نجاح مزيف.

## المراجع

[7]: https://developer.android.com/develop/background-work/background-tasks/persistent "Persistent background work with WorkManager"
[8]: https://developer.android.com/about/versions/oreo/background "Android background execution limits"
[9]: https://developer.android.com/privacy-and-security/keystore "Android Keystore system"
