# NexaFlow — خط أساس توسعة القدرات والإضافات

**النطاق:** توسعة توافق الإضافات وكتالوج القدرات مع الالتزام بعدم إنشاء Runtime أو Workflow Engine أو Queue أو Scheduler أو Variable Store أو Logging أو Recovery موازٍ.

## القرار المعماري

> كل تفاعل خارجي، سواء أكان من Plugin أم من قدرة Android أصلية، يدخل من **adapter محدود** ثم يتحول إلى `CapabilityRequest`، ويُحل عبر السياسة و`CapabilityResolver` وbackend واضح، ويعود بنتيجة منظمة قابلة للتحقق والاستعادة.

لا يدخل Plugin إلى `ExecutionEngine` أو `SystemController` أو secure storage أو repositories الداخلية مباشرةً. وينشر أي event خارجي إلى `NexaFlowEventBus` فقط؛ ولا يسمح له بتشغيل workflow من داخل receiver أو callback.

## ما هو موجود في الإصدار الحالي

| المجال | المكوّن الموجود | الحالة الفعلية | حد التوسعة الصحيح |
|---|---|---|---|
| Runtime | `ExecutionEngine` + `ActionRegistry` | إنتاجي، تسلسلي مع checkpoints | يظل نقطة تنفيذ actions ولا يُستبدل |
| Workflow | `WorkflowInterpreter` | Sequence/Parallel/Branch/Loop وtimeouts وrollback | يمدد بعقد nodes جديدة فقط |
| Events | `NexaFlowEventBus` + `TriggerIndex` | إنتاجي؛ فهرسة O(1) حسب المصدر | Plugin events تتحول إلى event bus ثم trigger index |
| Data | `RuntimeValue` + `ScopedDataRuntime` | typed/scoped مع snapshot/restore | يبقى representation الداخلي canonical |
| Capabilities | Registry/Resolver/ExecutionService | validation/policy/retry/verification موجودة؛ الخلفيات العامة محدودة | Plugin backend والخلفيات العائلية تُسجل هنا |
| Recovery | `ActiveExecutionStore` + coordinator | checkpoints/idempotency/recovery-required موجودة | نتيجة plugin/action تستخدم مسار checkpoint ذاته |
| Logging | `LogStore` + redaction | سجل محدود ومنقى | observability الجديدة تكتب metadata منقحة فقط |
| Plugin SDK | `LocaleContract`, parser, edit activity, fire receiver | يدعم إعدادات و`FIRE_SETTING` و`QUERY_CONDITION` على مستوى SDK | لا يزال host discovery/action bridge محدوداً |
| Host plugin execution | `PluginFireHandler` + `PluginFireClient` | action plugin مباشر عبر ordered broadcast مع timeout | ينقل تدريجياً إلى PluginAdapter → CapabilityRequest → PluginBackend |
| Plugin discovery | `PluginRepositoryImpl` | يفهرس receivers لـ `FIRE_SETTING` فقط | يتحول إلى registry مفهرس وكاش محدود ولا يعيد مسح التطبيقات دورياً |

## فجوات موثقة قبل التنفيذ

| الفجوة | السبب | المسار المسموح |
|---|---|---|
| نوع Plugin/إصدار protocol/metadata/schema غير مفهرسة | `PluginInfo` يحوي package/receiver/label فقط | `PluginRegistry` موسع مع discovery adapters عامة |
| action plugins تتجاوز policy/capability resolver | `PluginFireHandler` يستدعي client مباشرة | capability `PLUGIN_ACTION` عبر backend محدد |
| لا يوجد event plugin bridge | لا يوجد تحويل callback خارجي إلى NexaFlowEvent | adapter مقيد payload/rate/dedup إلى EventBus |
| لا يوجد state plugin bridge ثلاثي القيمة | لم تُنمذج TRUE/FALSE/UNKNOWN | `ConditionEvaluation` صريح ولا يحول UNKNOWN إلى FALSE |
| config محفوظ opaque بلا bridge typed للمتغيرات/output | parser يحد Bundle لكنه يعيد `Map<String, Any?>` | `PluginValueAdapter` يسمح primitives/list/map فقط إلى `RuntimeValue` |
| لا توجد trust/policy models للإضافات | manifest الحالي metadata محدودة | `PluginTrustLevel` وdeclared capability وinvocation policy |
| كتالوج القدرات الأصلية ضيق | الخلفيات العامة: package read/device state/intent فقط | capability families مصنفة، وخلفية واحدة لكل مسار قابل للدعم |

## حدود غير قابلة للتجاوز

| القيد | التطبيق |
|---|---|
| لا نجاحات وهمية | backend غير المدعوم يعيد `UNSUPPORTED`/`UNAVAILABLE`/`PERMISSION_DENIED` ولا `SUCCESS` |
| لا secrets في plugin bundles أو logs | secrets تحمل كـ `SecretReference` فقط؛ redactor يحمي السجل |
| لا shell string من workflow | Root/Shizuku، إن أضيفا، يقبلان request typed ومقيداً فقط |
| لا workflow execution من receiver | receiver ينشر event؛ trigger index يقرر التطابق؛ المحرك وحده ينشئ run |
| لا full app scan دائم | discovery lazy ومخزن ومبني على package lifecycle أو refresh صريح |
| لا Bundle/Android objects داخل RuntimeValue | adapter يسند primitives/maps/lists المسموح بها فقط |
| لا retry أعمى لside effects مجهولة | verification أو `UNKNOWN`/`RECOVERY_REQUIRED` أولاً |

## ترتيب التنفيذ المعتمد

1. بحث البروتوكولات العامة والتحقق من نقاط الاكتشاف وقيود Android الحالية.
2. عقود plugin registry والثقة والسياسة والقيم والنتائج المنظمة.
3. discovery/configuration/action bridge، ثم events/conditions، كلها عبر adapters.
4. عائلات capabilities الأصلية حسب دعم Android الفعلي، مع verification وavailability صريحين.
5. توسيع data/flow/triggers/conditions ضمن runtime الحالي فقط.
6. import/export/templates/observability/UI requirements، ثم test matrix وقياس الأداء والإصدار.
