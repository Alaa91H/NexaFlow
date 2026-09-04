# NexaFlow — Current Architecture Freeze

**الحالة:** خط أساس إلزامي قبل Production Hardening.  
**المبدأ:** المكونات التالية هي مصادر المسؤولية الحالية ولا يعاد بناؤها كمحركات موازية.

## خريطة الوحدات والمسؤوليات

| الوحدة | المسؤولية الحالية | قرار hardening |
|---|---|---|
| `domain` | Automation/Trigger/Action/Constraint، DAG وretry، repositories وعقود النطاق. | تضيف contracts typed ومحايدة لـ Android فقط. |
| `core:execution` | `ExecutionEngine`، `WorkflowInterpreter`، `ActionRegistry`، `WorkflowRunContext`، `TaskManager`، state transactions. | يبقى مركز التفسير والتنفيذ؛ تضاف adapters/stores/registries إليه. |
| `core:automation-engine` | المراقبات، scheduler، trigger index، monitoring service وwebhook. | يبقى مصدر Android events؛ يطبع event إلى bus موحد. |
| `core:rom-integration` | SystemController وRoot وShizuku وROM detection. | يصبح backend/adaptor خلف capability resolver، لا API مباشر للـ Workflow. |
| `core:datastore` | lifecycle markers وpreferences. | يظل للمؤشرات الخفيفة؛ لا يُستخدم كبديل لحالة execution المتينة. |
| `core:database` و`data` | Room automations/history/variables ومهاجرات متدرجة. | يضاف إليه persistence تنفيذ/checkpoints بصورة additive. |
| `core:security` | SafeCommandBuilder وSecureStorage. | هو حدود command security وVault references/redaction. |
| `core:logging` | timeline/errors/metrics، افتراضياً in-memory. | يتوسع إلى structured/redacted events؛ لا يضاف telemetry system ثانٍ. |
| `core:plugin-sdk` | Locale plugin contracts واكتشاف/تشغيل plugin. | أساس manifest/registry/boundary الجديد. |
| `core:capability-manager` | واجهة حالة ROM/capability. | مستهلك لـ diagnostics الناتجة من registry، لا مصدر قرار التنفيذ. |

## تدفق التنفيذ الحالي

```text
Android monitor / scheduler / UI run-now
          │
          ▼
ExecutionEngine أو AutomationWorkflowRunner
          │
          ▼
WorkflowInterpreter / ActionRegistryExecutor
          │
          ▼
ActionRegistry → ActionHandler
          │
          ▼
SystemController / HTTP handler / PluginFireClient
          │
          ▼
SystemControlResult + History + LogStore
```

`WorkflowInterpreter` هو مفسر DAG المركزي. يدعم sequence وparallel وbranch وloop والمهلة والإلغاء التعاوني وrollback داخل التسلسل. لا تستبدله hardening ولا تنقل منطق DAG إلى service جديد.

`ActionRegistry` هو نقطة dispatch الوحيدة لـ `ActionHandler` ويكشف التعارضات عند التسجيل. يمر Action إلى handler عبر `ActionExecutionContext` الذي يحمل controller وإعدادات الإشعار وقناة التوافق والسياق الجاري. لا تنشأ action queue أو action runtime موازية؛ توسع context بعقود اختيارية وتضيف adapters إلى registry.

## TaskManager

`TaskManager` هو queue التنفيذ القائمة. يقدم priority (LOW/NORMAL/HIGH/CRITICAL)، FIFO داخل الأولوية، retry/backoff، timeout، cancellation، `StateFlow` للنتائج وقياساً محدوداً. يشغل حالياً مستهلكاً واحداً؛ وهذا يمنع توازي تغييرات حالة الجهاز بصورة افتراضية.

> **قرار:** يصبح `TaskManager` نقطة lifecycle والتزامن والموارد. لا يضاف queue ثانٍ أو scheduler execution منافس.

## المحفزات والمراقبات

`MonitoringService` ينسق مراقبات البطارية والاتصال والموقع وBluetooth وringer والتقويم والحساسات والإعدادات والحزم والوسائط والصوت وwebhook. ويعيد `AutomationScheduler` جدولة محفزات الوقت. ويقدم `EventSource` lifecycle موحداً `start/stop` وخريطة `TriggerSource`.

تتجه بعض المراقبات اليوم إلى `ExecutionEngine` مباشرة. هذا مسار قائم يجب تغليفه بـ `EventAdapter → NexaFlowEvent → EventBus → TriggerIndex/Workflow matching`، من دون إنشاء Trigger Engine موازٍ أو تسجيل Workflows مباشرة على broadcasts.

## الحالة والبيانات والمتغيرات

`ConstraintSnapshot` هو لقطة بسيطة للشروط، و`TriggerStateEvaluator` يقرأ بعض حالات Android مباشرة. أما `WorkflowRunContext` فيملك JSON merge-patch context مع JSONPath و256KB budget لكل run. و`VariableRepository` يحتفظ بمتغيرات عالمية فقط؛ يستعمل `VariableRepositoryImpl` `SecureStorage` للمتغيرات الحساسة فلا يخزن النص الصريح في Room.

> **قرار:** يمتد `WorkflowRunContext` إلى typed execution data ولا يستبدل. يمتد `VariableRepository` بالنطاق والعمليات المطلوبة، ولا ينشأ repository متغيرات ثانٍ. يصاغ State Engine كـ adapters فوق المراقبات واللقطات، لا كمراقبات Android مكررة.

## الاستمرارية والاستعادة

يحفظ `ActiveExecutionStore` فقط automation ids التي دخلت lifecycle؛ وهو مفيد لمنع exit duplication لكنه لا يسجل node cursor أو executionId أو verification. و`DeviceStateTransactionStore` يحتفظ بتراجع الحالة في الذاكرة. أما Room فيملك migrations additive وhistory تتضمن channel وaction results JSON.

> **قرار:** يضاف `ExecutionStateRepository` فوق طبقة البيانات/Room فقط. لا يعيد تعريف ExecutionEngine؛ ويعامل node RUNNING بعد crash كـ `UNKNOWN` حتى تمر verification، ولا يعاد تنفيذ non-idempotent node بلا دليل.

## Root وShizuku والقدرات

يوفر `PrivilegedRunner` اكتشاف Shizuku/Root وتنفيذ أوامر متحقق منها، بينما يدير `ShizukuShellBridge` UserService وإعادة الربط. ويتضمن `SystemController` عمليات package/system عديدة. وتوفر `RomIntegrationManager` كشف قدرات ROM.

> **قرار:** تبقى هذه طبقات تنفيذ، لكنها لا تستقبل command من Workflow أو Plugin. يطلب الأعلى `CapabilityRequest` ويفاضل resolver بين Android API وPackageManager/Installer وShizuku وRoot وفق policy والتوفر الفعلي.

## Plugins، logging وlifecycle

يكتشف `PluginRepositoryImpl` Locale receivers ويشغّل `PluginFireClient` broadcast منضبطاً بمهلة. وتمتلك `NexaFlowApplication` bootstrap لـ WorkManager وscheduler وmonitoring وShizuku، وهي نقطة lifecycle/recovery المناسبة. ويملك `LogStore` timeline/errors/metrics لكن التخزين الافتراضي memory فقط.

> **قرار:** تبقى Plugins على أساسها الحالي وتكتسب manifest/registry/capability boundaries. يوسع `LogStore` إلى structured redaction/observability ولا يُنشأ telemetry pipeline منفصل.

## القيود غير القابلة للتفاوض

| ممنوع | البديل الصحيح |
|---|---|
| استبدال `WorkflowInterpreter` | adapters وverification/checkpoints حوله. |
| بناء Runtime Engine أو Queue ثانية | توسعة `TaskManager` و`ExecutionEngine`. |
| Event Engine موازٍ | `EventBus` كطبقة تطبيع بين monitors الحالية وmatching. |
| workflow/plugin → shell/root/shizuku مباشرة | capability/policy/resolver/backend contracts. |
| تخزين secret في workflow أو log أو export | `SecureStorage` وcredential references وredaction. |
| إعادة node غير مؤكدة | `UNKNOWN → verify → resume/failed`. |

## بنية هذا المسار (Architecture pass)

### TaskManager — ملكية الحالة
- **المالك الوحيد للنهايات (terminal outcomes):** `processEnvelope` هو الناشر الوحيد لـ `TaskResult.Cancelled` بعد `job.join()` عبر فحص `job.isCancelled`؛ الطفل (child) لا ينشر أبداً بل يرمي `CancellationException` (يثبّت إلغاء job) ويعود الـ join طبيعياً. هذا يلغي سباق publish مكرر/مفقود بين فكّ إلغاء الطفل وتنظيف `cancelledIds`.
- **عقد shutdown:** `shutdown()` = abandon صريح للطابور (publish Cancelled) + إلغاء running jobs + close للـ wake-up channel قبل `scope.cancel()` + تنظيف `finally` في `processEnvelope` يضمن إزالة الـ ledgers مهما كان مكان الإلغاء. لا يوجد drain بعد shutdown.
- **ترتيب submit:** `publishStatus(QUEUED)` يتم **داخل نفس lock** الذي يضيف للطابور؛ وإلا سبق الـ worker (RUNNING) على الـ QUEUED ويرفض `updateStatus` الانتقال (سباق حقيقي اكتشفه الاختبار 200×).

### حذف الأتمتة — مالك واحد
- `ExecutionEngine.deleteAutomation(automationId)` هي المالك الوحيد: حذف من repository + `clearSnapshot` + broadcast `ACTION_AUTOMATIONS_CHANGED`.
- `AutomationDetailsViewModel.delete` و`DashboardViewModel.deleteAutomation` يفوّضان إليه فقط — لا side-effects مكررة.

### الحدود بين الوحدات (ثابتة)
- `core:execution` = TaskManager/engine؛ `core:automation-engine` = monitors/scheduler؛ `feature:*` = UI فقط بلا سياسة lifecycle.
- لا تُنشأ Queue أو Runtime Engine ثانية؛ كل توسعة على `TaskManager`/`ExecutionEngine` القائمين.

### التحقق
- `:core:execution` 307 tests أخضر (13 TaskManagerTest + 8 TaskManagerHardeningTest + الباقي).
- `:feature:automations` و`:feature:dashboard` أخضر؛ `:app:compileDebugAndroidTestKotlin` يترجم. اختبار `ImmediateConditionEvaluationAndroidTest` غير منفَّذ بعد (لا جهاز ولا emulator مثبت على هذا المضيف) — قرار: انتظار جهاز، دون تحميل ~2GB لمكدس الـ emulator. أمر التشغيل عند توفر الجهاز: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nexaflow.app.validation.ImmediateConditionEvaluationAndroidTest`.
- `:core:datastore` 14/23 فشل **قائم مسبقاً وبيئي (Windows host فقط)** — تشخيص نهائي: يتكرر في plain JVM بدون Robolectric وبدون أي كود من المشروع (فقط `PreferenceDataStoreFactory`)، وفي الإصدارين 1.1.7 و1.2.1، في `%TEMP%` ومسار آخر على D:؛ بينما نفس تسلسل الملفات (write tmp + fsync + `Files.move REPLACE_EXISTING`) ينجح 100/100 يدوياً. الخلاصة: مسار الكتابة الداخلي لـ DataStore يفشل في rename الملف الثاني على هذه الآلة، وليس علاقته بأي تعديل في الشجرة. الحلول المجرّبة دون جدوى: تنظيف الملفات بين الاختبارات، مهلة 500ms، تغيير datastore إلى 1.1.7. التوصية: تشغيل هذه الاختبارات على Linux CI (حيث `rename` يستبدل دائماً) أو استثناء مجلد الاختبارات من فحص الحماية على Windows.

## تشغيل اختبار الجهاز (ImmediateConditionEvaluationAndroidTest) — وصفة مثبتة

النتيجة على `23049PCD8G` (Android 16): **3/3 أخضر في 6 تشغيلات متتالية** بعد الإصلاح.

```
# بعد توصيل الجهاز (وكل عملية تشغيل — Gradle يلغي تثبيت الـ APK بعد كل run):
./gradlew :app:installDebug
adb shell pm grant com.nexaflow.app android.permission.WRITE_SECURE_SETTINGS   # أساسي: الكتابة على Settings.Global لا تكفيها appop WRITE_SETTINGS
adb shell appops set com.nexaflow.app android:write_settings allow
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexaflow.app.validation.ImmediateConditionEvaluationAndroidTest
```

**فخوخ موثقة:**
- التثبيت فوق نسخة موقّعة بمفتاح مختلف يفشل بـ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` → إلغاء تثبيت النسخة القديمة أولاً (بموافقة المستخدم؛ يمسح بياناتها).
- مهمة `connectedDebugAndroidTest` **تلغي تثبيت** تطبيق + APK الاختبار بعد كل run → الصلاحيات تُمحى → أعد منحها قبل كل run.
- التطبيق الحقيقي (`NexaFlowApplication.onCreate`) يبدأ `MonitoringService` الحقيقي داخل عملية الاختبار؛ مراقبوه يشاركون ملف `ActiveTriggerStore` على الجهاز و**يحذفون علامات الاختبار** لأن الأتمتة موجودة فقط في Room داخل الذاكرة (سبب التذبذب `activeKeys == 0`). الاختبار الآن يوقف الخدمة في `@BeforeClass` ويلغي scope كل harness في `tearDown` — لا تعيد هذه التغييرات.
- التقارير: `app/build/outputs/androidTest-results/connected/debug/*.xml` (لاحظ أن XML قد ينسب الفشل لاسم testcase خاطئ — اقرأ الـ stack الداخلي).
