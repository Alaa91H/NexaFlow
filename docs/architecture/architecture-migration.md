# NexaFlow — Incremental Architecture Migration

**القاعدة:** كل خطوة تضيف contract ثم adapter ثم persistence ثم integration ثم test. لا تُستبدل المكونات القائمة دفعة واحدة، ولا تنقل بيانات المستخدم destructively قبل نجاح migration والتحقق.

## خريطة الهجرة

| المكوّن القائم | التوسعة المطلوبة | العقد الجديد | مسار الهجرة | أثر التوافق |
|---|---|---|---|---|
| `EventSource` وAndroid monitors | تطبيع الحدث وdedup/subscription. | `NexaFlowEvent`, `EventFilter`, `EventBus`, `EventAdapter`. | تنشر monitor adapters event مع إبقاء dispatch المباشر خلف adapter مؤقتاً؛ ثم تربط matching. | لا تتغير TriggerType أو registration الحالية. |
| `TriggerIndex` | يطابق event المطبّع بالـ workflows. | `EventMatcher`. | يتلقى event source/type ويحافظ على O(1) index الحالي. | لا تُسجل Workflow على broadcasts. |
| `WorkflowRunContext` | typed execution/node/action scopes وsnapshot. | `RuntimeValue`, `ScopedDataRuntime`. | adapter فوق JSONPath؛ القيمة القديمة تبقى قابلة للقراءة. | لا يتغير JSON patch أو 256KB budget من دون migration. |
| `VariableRepository` | typed scope operations وsnapshot/restore. | `ScopedVariableRepository` extension. | توسع interface/repository مع default/adapter للـ global rows. | global variables والـ sensitive encryption تبقى صالحة. |
| `TaskManager` | lifecycle state/resource leases/limits. | `ExecutionLifecycle`, `ResourceManager`. | تضيف state/leases إلى envelope القائم. | لا Queue ثانية ولا تغير priority semantics. |
| `ActiveExecutionStore` | state/cursor/checkpoints/recovery. | `ExecutionStateRepository`. | يبقى لمؤشر exit lifecycle؛ تستخدم Room records للتنفيذ المتين. | لا يكسر exit behavior أو active trigger logic. |
| `StateTransactionStore` | transaction metadata وverification. | `VerificationStrategy`, `CompensationPlan`. | تغلف snapshot rollback الحالي وتحفظ حدود عدم الاستعادة. | revert-on-exit يبقى كما هو. |
| `ActionRegistry` / handlers | capability intent adapters. | `CapabilityRequestMapper`, `CapabilityActionHandler`. | تسجل ActionTypes جديدة أو تحيط الإجراءات القائمة تدريجياً. | كل handler قديم يعمل بالمسار نفسه. |
| `SystemController`/Root/Shizuku | backends منظمة. | `CapabilityBackend`, `CapabilityResolver`. | تعاد استخدام العمليات، ويمنع الوصول المباشر من Workflow. | لا تفرض Shizuku أو Root. |
| `SecureStorage` وvariables الحساسة | aliases/credential references/redaction. | `CredentialVault`, `SecretReference`, `Redactor`. | workflow يخزن reference فقط؛ التطبيق يقرأ السر عند التنفيذ ويزيله عملياً من السياق. | لا تنقل أو تفك تشفير secrets القديمة خارج SecureStorage. |
| `PluginRepository`/`PluginFireClient` | manifest/version/trust/capability request. | `PluginManifest`, `PluginRegistry`. | Locale discovery يستمر ويضاف metadata قابلة للتحليل. | plugins الحالية تظل قابلة للتشغيل. |
| `LogStore` | structured, correlated, redacted fields. | `RuntimeLogEvent`. | إضافة fields اختيارية/adapters قبل persistence. | timeline/errors/metrics الحالية تبقى قابلة للعرض. |
| Automation JSON/Room | schema version/import/export. | `WorkflowEnvelope`, `MigrationRegistry`. | default `schemaVersion` للصفوف القديمة؛ migrations صافية قابلة للاختبار. | لا تحتاج workflows القديمة لإعادة إنشاء. |

## ترتيب التنفيذ الإلزامي

```text
1. Contracts
2. Adapters
3. Persistence
4. Runtime integration
5. Existing action migration
6. Unit / integration tests
7. Recovery tests
8. New UI/features
```

### المرحلة 0: Architecture Freeze

توثق الملفات الثلاثة في `docs/architecture/`. لا يتغير runtime behavior. ثم تجرى بوابة static/unit baseline قبل حدث جديد.

### المرحلة 1: Event Architecture

تضاف contracts وin-memory production-safe EventBus، ثم adapters للمراقبات القائمة بموضع واحد. لا تحذف `EventSource`، ولا تستبدل `TriggerIndex`، ولا تنشأ خدمة Event مستقلة. تجرى اختبارات publishing/filter/dedup/unsubscribe/order per source والـ regression للمراقبات.

### المرحلة 2: Typed Data/Variables/Expressions

تضاف `RuntimeValue` وscope resolver ثم توسع `VariableRepository` بعقود مضبوطة. يليها parser expression side-effect-free. لا تستخدم eval أو reflection أو shell، ولا تستبدل WorkflowRunContext.

### المرحلة 3: Task Lifecycle/Resources

تضاف transitions محددة إلى TaskManager مع ResourceManager داخل المسار القائم. يضمن `finally` release عند success/failure/cancellation/timeout، وتختبر global lock ordering لمنع deadlock.

### المرحلة 4: Durable State/Recovery

تضاف Room tables ومهاجرات additive ثم checkpoint writer/repository. تبقى history append-only. تستدعي `NexaFlowApplication` recovery scan idempotently؛ وتتحول العمليات غير المؤكدة إلى `UNKNOWN` لا success/failure مصطنع.

### المرحلة 5+: Capabilities, Vault, Plugins, Versioning, Debug/Observability

تُدمج capability runtime الموجودة تدريجياً بعد اختبار العقود، ثم redaction/Vault references وplugin manifest/schema import/export/debug API. لا يُنقل UI أولاً؛ يبدأ كل جزء بعقد واختبارات ثم adapter ثم شاشة مستهلكة.

## بوابة كل مرحلة

| فحص | شرط النجاح |
|---|---|
| static | `git diff --check` نظيف ولا secrets في diff أو logs. |
| unit | اختبارات العقود الجديدة ومسارات الانحدار المتأثرة تمر. |
| integration | Android API/Room/monitor test عند وجوده؛ Root/Shizuku لا يصطنعان نجاحاً. |
| compatibility | Workflow/Action/Trigger/Schedule سابق يقرأ ويعمل وفق سلوكه. |
| recovery | الاختبارات تغطي crash/unknown/retry أو يسجل القيد بوضوح. |
| documentation | تقرير المرحلة يذكر implemented/files/tests/limitations/migration/next. |

## سياسة التراجع

كل commit مرحلي قابل للتراجع لأنه additive قدر الإمكان. لا تحذف columns أو تعيد كتابة JSON القديمة في نفس release الذي يقدم migration جديدة. تحفظ import/export schema الأصلية عند فشل validation. وإذا لم تتوفر capability أو permission أو backend فالنتيجة `UNSUPPORTED` أو `PERMISSION_REQUIRED` أو `UNKNOWN` بحسب الواقع، ولا توجد fake implementation.
