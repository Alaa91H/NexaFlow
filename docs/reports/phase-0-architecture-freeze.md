# تقرير المرحلة 0 — Architecture Freeze

**الحالة:** مكتملة وقابلة للانتقال إلى مرحلة Event Architecture.  
**تاريخ التنفيذ:** 19 أغسطس 2026.

## المنفذ

تم توثيق الوضع الحالي الحقيقي للمشروع وتجميد الحدود المعمارية التي تمنع إعادة بناء Runtime أو Queue أو Event Engine موازٍ. وثقت الخريطة أن `WorkflowInterpreter` يبقى مفسر DAG المركزي، و`TaskManager` يبقى queue التنفيذ، و`VariableRepository` و`SecureStorage` يبقيان مصادر المتغيرات والأسرار، وتبقى Android monitors مصادر الأحداث وتُطبع لاحقاً عبر Event Bus.

كما أضيفت عقود capability منظمة ومحايدة عن Android (`CapabilityRequest`, policy, backend, result, diagnostics) وresolver أساسي قابل للاختبار. لا يُدمج هذا الأساس في existing Actions بعد؛ فهو عقد تأسيسي لا يغير سلوك Workflows الموجودة.

## الملفات المنشأة أو المعدلة

| الملف | التغيير |
|---|---|
| `docs/architecture/architecture-current.md` | توثيق المسؤوليات والمسار الحالي والقيود المعمارية. |
| `docs/architecture/architecture-target.md` | توثيق التدفق المستهدف والعقود وحدود Android والأمن. |
| `docs/architecture/architecture-migration.md` | خطة migration additive وخريطة كل مكوّن قائم إلى امتداده. |
| `docs/capability-execution-architecture-audit.md` | تدقيق طبقة capability والخلفيات. |
| `docs/capability-execution-contracts.md` | عقد resolver/policy/backend/result والتحقق. |
| `docs/production-runtime-architecture-audit.md` | تدقيق الفجوات الإنتاجية الموجودة. |
| `docs/production-runtime-contracts.md` | عقود event/state/data/durable/resource/vault/observability. |
| `domain/.../capability/CapabilityModels.kt` | نماذج capability والسياسة والنتيجة والحالة المنظمة. |
| `core:execution/.../capability/CapabilityRuntime.kt` | registry/resolver/policy evaluator/execution service/diagnostics. |
| `core:execution/.../CapabilityRuntimeTest.kt` | اختبارات اختيار backend وحجب policy ومنع privileged escalation. |

## التغييرات المعمارية

لم يُستبدل `WorkflowInterpreter` أو `TaskManager` أو `ActionRegistry` أو `EventSource` أو `VariableRepository`. أضيفت contracts فقط؛ لا يوجد service execution جديد، ولا queue ثانية، ولا monitor Android إضافي، ولا وصول مباشر جديد من Workflow إلى Root أو Shizuku.

تختار capability resolver الخلفية الأقل امتيازاً المتاحة قبل الخلفيات العالية، وتمنع Root/Shizuku/ADB ما لم تسمح policy صراحة. وتعيد السياسة/الخلفية رموزاً آلية مثل `POLICY_NOT_SATISFIED`, `BACKEND_UNAVAILABLE` و`PERMISSION_DENIED` بدلاً من نجاح أو فشل نصي غامض.

## الاختبارات المنفذة

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `:domain:testDebugUnitTest` | ناجح. |
| `:core:execution:testDebugUnitTest --tests com.nexaflow.core.execution.capability.CapabilityRuntimeTest` | ناجح. |
| اختبار أولوية Android API على Root المتاح | ناجح. |
| اختبار حجب Wi‑Fi policy قبل backend invocation | ناجح. |
| اختبار منع Root من دون opt-in policy | ناجح. |
| اختبار تطبيع backend/duration في النتيجة | ناجح. |

## القيود المعروفة

لا يوجد بعد adapter من Android monitors إلى Event Bus؛ وهذا هو نطاق المرحلة التالية فقط. لا يوجد بعد `ExecutionStateRepository` أو checkpoint/recovery؛ لا يجوز لذا الادعاء بأن التنفيذ الحالي crash-resilient. لا توجد خلفيات package/root/shizuku منظمة مستعملة من handlers بعد؛ يظل السلوك القديم قائماً إلى أن تكتمل مراحل capability integration والتحقق.

## أثر الترحيل

**صفر** على Workflows وActions وTriggers وSchedules وplugins القائمة، لأن العقود الجديدة غير موصولة بعد بمسار dispatch الإنتاجي. لا توجد Room migration في هذه المرحلة ولا تغير في JSON المحفوظ.

## المرحلة التالية

تنفيذ **المرحلة 1: Event Architecture** فقط: عقود `NexaFlowEvent`, `EventFilter`, `EventSubscription`, `EventBus` وadapters فوق `EventSource`/المراقبات الحالية، مع اختبارات publish/filter/dedup/unsubscribe. لن يُنفذ Typed Data Runtime أو TaskManager hardening أو durable persistence قبل اجتياز بوابة المرحلة 1.
