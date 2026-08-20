# المرحلة 7 — جسر Plugin Action إلى Capability Runtime

## النتيجة الفعلية

أصبح `ActionType.PLUGIN_FIRE` الذي يهيئه المستخدم من الآن يمر عبر المسار المنتج الموحد:

```text
Builder configuration Activity
  → persisted Action config (Bundle JSON + pluginInstance + approval)
  → CapabilityActionMapper
  → CapabilityRequest(PLUGIN_ACTION, pluginInstance)
  → CapabilityExecutionService
  → CapabilityResolver / policy / timeout / verification
  → PluginCapabilityBackend
  → PluginDiscoveryRegistry validation
  → Locale ordered FIRE_SETTING broadcast
```

لا يحمل `CapabilityRequest` `Bundle` أو receiver أو package أو JSON. يحمل فقط `pluginInstance` opaque وworkflow/execution/action identity. يعيد backend تحميل workflow من المستودع، ويطابق instance مع action `PLUGIN_FIRE` المحفوظة، ويتحقق من موافقة المستخدم والمكونات المكتشفة المتوافقة، ثم يعيد بناء Bundle بصرامة وينفذ broadcast مرتباً بمهلة الخدمة المركزية.

| حالة | النتيجة المنظمة |
|---|---|
| workflow/action/instance لا يطابق | `PLUGIN_UNAVAILABLE` أو `PLUGIN_MISSING_DEPENDENCY` بلا broadcast |
| user approval غير موجود | `PLUGIN_NOT_APPROVED` بلا broadcast |
| package أو receiver اختفى أو أصبح غير متوافق | `PLUGIN_MISSING_DEPENDENCY` أو `PLUGIN_UNAVAILABLE` |
| JSON فارغ أو تالف | `INVALID_CONFIGURATION` بلا Bundle فارغ |
| لا يستجيب receiver قبل المهلة | `TIMEOUT` |
| plugin يرد `OK` | `SUCCESS` مع backend `PLUGIN` |
| plugin يرد `PENDING` بلا completion adapter | `PARTIAL`، ولا يتحول إلى نجاح |
| plugin يرد `CANCELED` أو `FAILED` | `CANCELLED` أو فشل منظم |

## التوافق الخلفي

لا تتعطل actions القديمة: `CapabilityActionMapper` يعترض فقط action التي تحتوي `pluginInstance` وموافقة محفوظة. أي automation قديمة لا تزال تمر عبر `PluginFireHandler` المعتمد حتى يعيد المستخدم تهيئتها؛ وبذلك لا يغير التحديث السلوك المحفوظ فجأة.

## ما لم يُعلن دعمه

رُصدت عقود `PLUGIN_CONDITION_READ` وTasker event في نماذج التصميم فقط، لكنهما **غير مسجلين في catalog أو backend**. لا يوجد adapter عام مكتمل وآمن يحول callback خارجي إلى workflow execution بدون إثبات مرسل وcorrelation ودلالات حالة موثقة؛ لذلك لا تعرض واجهة NexaFlow هذه الميزات كأنها متاحة ولا تؤدي إلى نجاحات زائفة.

## بوابة التحقق المستهدفة

نجحت الاختبارات والتجميعات التالية بعد إصلاح اعتماد coroutines لـ plugin SDK وclass path AppModule:

```text
:core:plugin-sdk:testDebugUnitTest
:core:execution:testDebugUnitTest
:data:compileDebugKotlin
:core:automation-engine:compileDebugKotlin
:feature:automation-builder:compileDebugKotlin
:app:compileDebugKotlin
```

تغطي الاختبارات الجديدة pairing والاكتشاف، manifest contracts، strict JSON، mapper وlegacy fallback، ومسار تكامل حقيقي إلى receiver Robolectric معلن في manifest عبر ordered broadcast.
