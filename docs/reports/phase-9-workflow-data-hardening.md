# المرحلة 9 — تقوية التحكم في التدفق والبيانات typed

## ما أصبح أكثر متانة

لا يستخدم NexaFlow interpreter جديداً. تم توسيع `WorkflowInterpreter` و`WorkflowNode` القائمين فقط، مع المحافظة على sequence وparallel وbranch وloop والـ rollback الموجودة.

| المجال | التغيير | الأثر |
|---|---|---|
| الحلقات | `LoopNode.MAX_ITERATIONS = 1000` والتحقق في constructor | يمنع imports أو workflows تالفة من بناء حلقة ضخمة غير محكومة |
| الإلغاء | `runAction` يعيد رمي `CancellationException` | لا يختلط إلغاء structured concurrency مع فشل action أو checkpoint عادي |
| قيم الإضافات | `PluginValueRuntimeAdapter` | يحول primitive/list/map فقط إلى `RuntimeValue` typed |
| حدود payload | عمق افتراضي 8، 256 item، 4096 حرف | يمنع payload متداخل أو كبير من إدخال data runtime |
| الأرقام والمفاتيح | يرفض non-finite والمفاتيح الفارغة أو الأطول من 256 | يمنع قيم لا يمكن تخزينها أو مقارنتها بشكل حتمي |

## الحالة الدقيقة

التفرع `if/else` والحلقات ذات العدد الثابت والـ parallel والتوقف عند الفشل كانت موجودة بالفعل في interpreter. لا يعلن هذا التغيير دعماً لحلقات `while` غير المحدودة أو break/continue أو retry من data runtime؛ تلك features لا تدخل قبل وجود مخطط workflow serializable وحدود timeout/checkpoint قابلة للاستئناف لكل node.

كما أن `PluginValueRuntimeAdapter` جاهز للـ output variables، لكن plugin base action لا تنتج outputs عامة موثقة بعد. لذلك لا يكتب adapter أي متغير runtime تلقائياً ولا يعرض output variables في UI.

## الاختبارات المضافة

تشمل التغطية الجديدة تحويل primitive/list/map، رفض depth والمفتاح غير الصالح، رفض loop فوق الحد، وانتشار `CancellationException`. وتُنفذ بوابة التجميع/الاختبارات الكاملة في مرحلة الجودة النهائية فقط، بعد اكتمال تغييرات الاستيراد والملاحظة.
