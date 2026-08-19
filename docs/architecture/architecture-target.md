# NexaFlow — Target Production-Hardening Architecture

**الغرض:** نقل المحرك من تنفيذ عملي متفرق إلى runtime متين ومتوافق، عبر عقود وإضافات على المسارات الحالية فقط.

## التدفق المستهدف

```text
Existing Android monitor / scheduler / webhook / UI
          │
          ▼
EventAdapter ────────────────┐
          │                  │
          ▼                  │
NexaFlowEventBus             │
          │                  │
          ▼                  │
TriggerIndex + workflow matching
          │
          ▼
TaskManager (priority, lifecycle, resources, cancellation)
          │
          ▼
WorkflowInterpreter (existing DAG interpreter)
          │
          ▼
ActionRegistry → CapabilityRequest adapter
          │
          ▼
Execution policy → CapabilityResolver → registered backend
          │                                    │
          │                       Android API / Intent / Package / Shizuku / Root
          ▼
Checkpoint → Verification → Durable execution result
          │
          ▼
History + structured LogStore + debug observer
```

## طبقات التوسعة

| العقد/المكوّن | مكانه | دوره | ما لا يفعله |
|---|---|---|---|
| `NexaFlowEventBus` | `core:execution` | نشر/اشتراك/filter/dedup للأحداث المطبوعة. | لا يسجل Android receivers ولا يفسر DAG. |
| `EventAdapter` | `core:automation-engine` | يحول monitor signals إلى `NexaFlowEvent`. | لا ينفذ Workflow مباشرة. |
| `TypedValue` و`ScopedDataRuntime` | `domain` + `core:execution` | قيم typed وقواعد scope فوق `WorkflowRunContext` و`VariableRepository`. | لا يخزن Android objects أو أسرار plaintext. |
| `ExpressionEngine` | `core:execution` | parsing/evaluation sandboxed للشرط/branch/policy. | لا ينفذ كود أو Action أو shell. |
| `TaskManager` hardened | `core:execution:task` | lifecycle/priority/retry/resource leases/cancellation. | لا يصبح queue مستقلاً عن الموجود. |
| `ExecutionStateRepository` | `domain` interface + `data`/Room | lifecycle/checkpoints/unknown/recovery state. | لا يفسر workflows ولا يعيد تنفيذ node بنفسه. |
| `CapabilityExecutionService` | `core:execution` | policy/resolution/normalized result. | لا يربط Workflow مباشرة بـ Root/Shizuku. |
| `VerificationStrategy` | capability/backend boundary | برهنة الأثر قبل success/commit. | لا يعتبر exit code نجاحاً نهائياً وحده. |
| `ResourceManager` | داخل TaskManager runtime | leases مرتبة، timeout، release في finally. | لا يضيف scheduler آخر. |
| `CredentialVault` adapter | `core:security` + `data` | aliases/references فوق SecureStorage وredaction. | لا يخزن credential في Automation JSON. |
| `PluginRegistry` | فوق Plugin SDK الحالي | manifest/API compatibility/trust/capability boundary. | لا يعطي plugin امتيازاً ضمنياً. |
| `LogStore` structured extension | `core:logging` | correlation/status/backend/error/redaction/metrics. | لا يسجل secrets أو raw privileged commands. |

## Lifecycle والتدفق المتين

```text
QUEUED → RUNNING → VERIFYING → SUCCESS
             │          │
             │          └──────────────→ FAILED
             ├──────────────→ RETRYING → RUNNING
             ├──────────────→ WAITING / PAUSED
             ├──────────────→ CANCELLED
             └─ process death → UNKNOWN → RECOVERING → verify → SUCCESS | FAILED | PAUSED
```

تُكتب checkpoint قبل side effect وبعد verification. وإذا قتل Android العملية بعد الإرسال وقبل النتيجة لا تصف runtime العملية بأنها failed أو success؛ تحفظ `UNKNOWN` وتستدعي verification عند recovery. لا يعاد تنفيذ `NON_IDEMPOTENT` أو `UNKNOWN` بلا دليل.

## الأمن والسياسات

| boundary | قرار التنفيذ |
|---|---|
| Android public API | أولوية افتراضية عندما تدعم capability حقيقية. |
| Intent/PackageManager/PackageInstaller | نتيجة منظمة و`PENDING_USER_ACTION` إذا طلب النظام تأكيداً. |
| Shizuku/Root/ADB | opt-in policy + backend health + permission + operation schema؛ لا raw command. |
| Accessibility | backend اختياري؛ لا coordinates hardcoded ولا fallback افتراضي. |
| Plugin | capability request فقط؛ manifest/trust/policy قبل backend. |
| Secret | alias/reference فقط؛ logger redacts قبل persistence/export/debug. |

## التوافق مع Android

تعتمد الأعمال الموثوقة القابلة للتأجيل أو الاستعادة على WorkManager/JobScheduler حيث يلائمها Android، بينما AlarmManager للأوقات الدقيقة وforeground service فقط لمراقبة ظاهرة للمستخدم. لا يُفترض بقاء process أو service في الخلفية. [1] [2]

| الجهاز/الحالة | السلوك المتوقع |
|---|---|
| process killed | recovery loads durable state ويحل UNKNOWN بالتحقق. |
| reboot | WorkManager/bootstrap يعيدان scheduling/recovery idempotently. |
| permission revoked | backend availability تصبح `PERMISSION_REQUIRED`؛ لا fake success. |
| Shizuku died / Root lost | backend unavailable؛ resolver لا يكرر action على backend آخر بعد side effect غير مؤكد. |
| network lost | policy/retry يقرران WAITING/RETRYING بحسب العملية. |

## المراجع

[1]: https://developer.android.com/develop/background-work/background-tasks/persistent "Persistent background work with WorkManager"
[2]: https://developer.android.com/about/versions/oreo/background "Android background execution limits"
