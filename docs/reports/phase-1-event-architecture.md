# تقرير المرحلة 1 — Event Architecture

**الحالة:** التنفيذ مكتمل؛ اجتازت اختبارات العقود وتجميع وحدة المراقبات.  
**الالتزام المعماري:** لا Trigger Engine جديد، ولا Event service مستقل، ولا Queue إضافية، ولا استبدال للمراقبات أو `WorkflowInterpreter`.

## المنفذ

أضيفت صيغة حدث داخلية typed هي `NexaFlowEvent` تضم `eventId`, `type`, `source`, `occurredAt`, `payload`, `correlationId` و`deduplicationKey`. يقتصر payload على `JsonObject` بحد 64KB؛ لذلك لا يتسرب Intent أو Binder أو كائن Android أو secret إلى عقدة المطابقة.

أضيف `NexaFlowEventBus` بعقود `EventFilter` و`EventSubscription` و`EventPublishResult` و`unsubscribe`. تنفذ `InMemoryNexaFlowEventBus` القناة الداخلية الواحدة: FIFO لكل اشتراك، backpressure محدود لكل subscriber، deduplication في نافذة زمنية، عزل استثناءات المستمعين، وإغلاق لا يقبل أحداثاً/اشتراكات جديدة بعد طلبه. تُلتقط قائمة المستمعين تحت mutex ثم يُسلَّم الحدث خارج القفل، فلا يستطيع مستمع بطيء حجب `unsubscribe` أو shutdown؛ ويعامل إغلاق اشتراك متزامن أثناء التسليم كسلوك lifecycle متوقع لا كفشل للحدث المقبول. لا يسجل bus receivers Android ولا ينشئ process أو service جديداً.

رُبط `DeviceEventMonitor` كأول adapter إنتاجي. يستمر في تسجيل BroadcastReceiver الحالي، وإعادة تسليح `ActiveTriggerStore`، واستعادة exit behavior، لكن يحول signal إلى `SYSTEM_EVENT` عبر `MonitorEventAdapter`. يستهلك الاشتراك الحدث ثم يستخدم `TriggerIndex` القائم عندما يكون جاهزاً، مع fallback إلى `AutomationRepository` خلال bootstrap لمنع فقد أول broadcast قبل أول emission من index. حافظ هذا على الدلالات السابقة لحدث الشاشة والطاقة والسماعة والـ cooldown والـ exit lifecycle.

## الملفات المنشأة أو المعدلة

| الملف | التغيير |
|---|---|
| `domain/.../events/NexaFlowEvent.kt` | عقود الحدث، payload JSON، filter، subscription، publish result وbus. |
| `core:execution/.../events/InMemoryNexaFlowEventBus.kt` | تنفيذ bus و`MonitorEventAdapter` فوق `EventSource` القائم. |
| `core:execution/.../events/InMemoryNexaFlowEventBusTest.kt` | اختبارات العقود والتسليم والـ dedup وfailure isolation. |
| `core:automation-engine/.../di/EngineModule.kt` | singleton EventBus على `@ApplicationScope`. |
| `core:automation-engine/.../DeviceEventMonitor.kt` | adapter حقيقي من broadcast قائم إلى canonical event ثم matching. |
| `core:automation-engine/.../DeviceEventMonitorExitReconcileTest.kt` | تحديث اختبار lifecycle إلى dependencies الحقيقية الجديدة. |
| `domain`, `core:execution`, `core:automation-engine` Gradle files | اعتماد `kotlinx-serialization-json` الصريح، وcoroutines test لوحدة التنفيذ. |

## التغييرات المعمارية

```text
DeviceEventMonitor BroadcastReceiver
        ↓
MonitorEventAdapter
        ↓
NexaFlowEventBus
        ↓
EventFilter (source=device, type=SYSTEM_EVENT)
        ↓
TriggerIndex عند الجاهزية / repository bootstrap fallback
        ↓
نفس مطابقة trigger والقيد وExecutionEngine السابقين
```

يستمر `DeviceEventMonitor` في كونه monitor واحداً ضمن `MonitoringService`. والـ EventBus في نطاق التطبيق وتُشاركُه adapters لاحقاً؛ لا يوجد `EventEngine` جديد ولا تفعيل موازٍ لمسار التنفيذ. ويظل `TriggerIndex` مصدر mapping من source إلى automations، لا قاعدة بيانات أو index بديل.

## الاختبارات المنفذة والناجحة

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `InMemoryNexaFlowEventBusTest` | ناجح. |
| توجيه event حسب source/type والمحافظة على ترتيب subscriber | ناجح. |
| رفض duplicate key داخل نافذة dedup ثم قبولها بعد انتهائها | ناجح. |
| `unsubscribe` الذري ومنع التسليم اللاحق | ناجح. |
| عزل exception من subscriber واحد عن subscribers الآخرين | ناجح. |
| `MonitorEventAdapter` فوق `EventSource` قائم | ناجح. |
| `DeviceEventMonitorExitReconcileTest` | ناجح؛ لم ينكسر exit reconciliation بعد adapter. |
| `RomSettingMonitorLifecycleTest` | ناجح؛ تجميع وحدة المراقبات مع Hilt وEventBus ناجح. |

اكتملت البوابة الأولية في **3 دقائق و18 ثانية**؛ كان معظم الزمن أول تنزيل لـ Robolectric platform dependency، وليس تعليقاً في EventBus أو coroutine. وبعد مراجعة تسليم backpressure، اجتازت بوابة الجودة النهائية في **13 ثانية** بالأوامر التالية:

```text
:domain:testDebugUnitTest
:core:execution:testDebugUnitTest
  --tests CapabilityRuntimeTest
  --tests InMemoryNexaFlowEventBusTest
:core:automation-engine:testDebugUnitTest
  --tests DeviceEventMonitorExitReconcileTest
  --tests RomSettingMonitorLifecycleTest
```

## القيود المعروفة

هذه المرحلة تُحوّل `DeviceEventMonitor` فقط كمثال إنتاجي مضبوط. لا تزال battery/connectivity/location/package/notification/webhook/time monitors على dispatch المباشر إلى أن تُنقل **واحداً في كل دفعة اختبارية** بنفس adapter، كي لا يتحول الترحيل إلى rewrite عالي المخاطر. ولا يوجد بعد persisted event journal؛ أحداث runtime الحية لا تُعاد بعد process death، وهو مقصود لحين مرحلة durable execution/checkpointing.

لا يعيد EventBus تعريف matching الخاص بكل trigger؛ payload هو envelope typed عام فقط، ولا يفسر config أو ينفذ workflow. كما أن عدم توفر subscriber أو backend لا يسجل نجاحاً وهمياً.

## أثر الترحيل

لا يوجد Room migration أو تعديل Workflow JSON أو تغيير TriggerType/ActionType. ويستمر DeviceEventMonitor في fallback إلى repository فقط قبل جاهزية TriggerIndex، ما يحافظ على أول event بعد start وعلى Workflows القديمة.

## المرحلة التالية

اكتملت **بوابة المرحلة 1** بفحص static واختبارات العقود والانحدار المتأثرة، ولذلك تنتقل الخطة الآن إلى **المرحلة 2: Typed Data Runtime وVariableRepository والتعبيرات الآمنة**. لن يبدأ durable execution أو ResourceManager أو plugin/schema work قبل اكتمال بوابة هذه المرحلة الجديدة.
