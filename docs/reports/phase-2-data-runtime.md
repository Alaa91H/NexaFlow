# تقرير المرحلة 2 — Typed Data Runtime والتعبيرات الآمنة

**الحالة:** مكتملة ومتحقق منها باختبارات معزولة.  
**المبدأ:** وُسّع `VariableRepository` وممر تنفيذ الإجراءات القائمان؛ لم يُنشأ مخزن متغيرات موازٍ، ولم تُبدّل دلالات `%NAME` النصية الحالية.

## ما نُفذ

أضيفت `RuntimeValue` كجبر قيم مغلق وقابل للتسلسل يضم null والنص والـ boolean وint وlong وdouble المحدود والقائمة والكائن. لا يمكن لهذا النموذج حمل كائنات Android أو Lambdas أو handles أو كائنات تنفيذ عشوائية، لذلك يبقى قابلاً للتخزين والمقارنة والتشخيص بصورة حتمية. يتولى `RuntimeValueCodec` تمثيل JSON typed وعرض قيمة نصية متوافقاً لاستبدال `%NAME` القديم؛ وتحول القيم النصية القديمة تلقائياً إلى `StringValue` من دون إعادة كتابة البيانات.

أضيفت `VariableScope` بالنطاقات GLOBAL وWORKFLOW وEXECUTION وNODE وACTION، و`RuntimeVariable` مع revision موجب و`VariableSnapshot` غير قابل للتعديل. يوفر `ScopedDataRuntime` أولوية حل `ACTION → NODE → EXECUTION → WORKFLOW → GLOBAL`، ويمنع التسرب التصاعدي بين النطاقات. تبقى GLOBAL في `VariableRepository` القائم، بينما تبقى النطاقات المحلية داخل execution الواحد. ينسخ scope EXECUTION فقط إلى `WorkflowRunContext` الموجود عند المسار الداخلي `$.nexaflowRuntimeVars`، مع حد السياق الذري 256KB المطبق سابقاً.

تم توسيع `VariableRepository` نفسه بواجهات `get` و`exists` و`resolve` و`set` و`delete` و`snapshot` و`restore`. تعمل هذه العمليات case-insensitively للتوافق مع `%NAME`. تحفظ `set` قيمة العرض مع JSON typed اختياري، وتزيد revision من القيمة المحفوظة. لا تقبل repository إلا GLOBAL؛ محاولة تخزين NODE أو ACTION فيها مرفوضة صراحةً. تقوم `restore` باستعادة snapshot فقط عندما لا تكون النسخة المحلية أحدث، فلا يعيد snapshot قديم الكتابة فوق تعديل لاحق.

أضيفت حقول Room تراكمية إلى `global_variables`: `version` في migration 12→13 و`serializedValue` في migration 13→14. تبقى الصفوف القديمة بتمثيل null للـ typed JSON وrevision 1. المتغير الحساس يحتفظ بالـ JSON typed في `SecureStorage` الموجود، وتبقى قاعدة البيانات محتوية على marker فقط. لا يتغير جدول automations أو workflow JSON أو أنواع actions/triggers.

أضيف `ExpressionEngine` محدود داخل `core:execution`: parsing deterministic من دون `eval` أو reflection أو shell أو وصول ملف/شبكة. يدعم `AND`, `OR`, `NOT`, المقارنات، `contains`, `startsWith`, `endsWith` والدوال المعلنة `length`, `lower`, `upper`, `contains`, `exists`. وتولد العبارة غير المعروفة أو الدالة غير المعلنة أو النوع غير المتوافق `ExpressionException` منظمة بدلاً من تنفيذ صامت أو وصول حر.

## التكامل الحقيقي

أصبح `ExecutionEngine` ينشئ `ScopedDataRuntime` واحداً لكل `runAutomation` ويرسله داخل `ActionExecutionContext`. تُستخرج globals الآن عبر `VariableRepository.snapshot` typed ثم تحول عند حد الاستبدال فقط إلى النص المتوافق. لذلك ترى الإجراءات النص المحلول في `Action.config` كما كانت، ويمكنها في الوقت نفسه الوصول إلى `RuntimeValue` typed عبر `ctx.dataRuntime` من دون قراءة repository خام. تبقى مسارات الخروج والـ workflow runner التي لا تملك run context متوافقة بقيمة `dataRuntime = null`.

| المكوّن | المسؤولية بعد المرحلة |
|---|---|
| `VariableRepository` | المصدر الوحيد للـ GLOBAL persistence، revision، snapshot وrestore. |
| `ScopedDataRuntime` | النطاقات المحلية وprecedence وعزل NODE/ACTION وربط execution context. |
| `WorkflowRunContext` | delta JSON محدود لكل run؛ لا يتحول إلى repository عام. |
| `ExpressionEngine` | تقييم شرط deterministic وآمن فوق `RuntimeValue` فقط. |
| `ExecutionEngine` | إنشاء facade واحد لكل run وتمريره للإجراءات. |

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `ScopedDataRuntimeTest` | ناجح: precedence، isolation، حذف scope، context sync، global snapshot/restore. |
| `ExpressionEngineTest` | ناجح: المنطق، المقارنات، الدوال، ورفض constructs غير المدعومة والأنواع غير المتوافقة. |
| `ExecutionEngineVariableResolutionTest` | ناجح: `%NAME` القديم، globals typed، وتمرير `RuntimeValue.IntValue(85)` إلى handler. |
| `MigrationTest` معزول بـ `--rerun-tasks --no-parallel --max-workers=1` | ناجح في 38 ثانية، بما فيه ترقية 12→14 الجديدة. |
| Data/Expression/Execution suite معاد التشغيل | ناجح في 25 ثانية. |

تم أيضاً إصلاح هشاشة قديمة في `MigrationTest`: كان كل الاختبارات يستخدم ملف SQLite ثابتاً في `/tmp`، ما جعل WAL/SHM المتبقي يسبب أخطاء I/O وcorruption غير حتمية. يستخدم كل اختبار الآن `TemporaryFolder` وملفاً مستقلاً، فتتحقق migrations فعلياً بدلاً من التأثر بتلوث اختبار آخر.

## القيود المقصودة

النطاقات WORKFLOW/EXECUTION/NODE/ACTION غير متينة عبر قتل العملية بعد؛ ستُحفظ وتُستعاد في مرحلة التنفيذ المتين ونقاط التحقق. وتبقى الأسرار الحساسة محصورة في GLOBAL/Keystore في هذه المرحلة؛ تُرفض الأسرار غير العالمية إلى أن تضيف مرحلة Vault مراجع أسرار صريحة. Expression Engine لا يسمح بالدوال المخصصة أو الوصول إلى الشبكة أو النظام؛ تلك الامتدادات، إن لزم الأمر، يجب أن تصل لاحقاً عبر capability policy لا عبر evaluator.

## الخطوة التالية

تنتقل الخطة إلى **المرحلة 3: تقوية TaskManager**. سيُوسّع `TaskManager` القائم بحالات تنفيذ صريحة، cancellation، deadlines، limits/resources، وownership داخل الـ queue الموجودة، من دون بناء scheduler أو queue موازية.
