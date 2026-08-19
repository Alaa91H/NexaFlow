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
