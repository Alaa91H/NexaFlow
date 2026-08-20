# حالة التحكم في التدفق والتحويلات

**الحالة:** تحديث هندسي واقعي للإصدار التالي. يحدد هذا المستند حدود الدعم كما هي في الشيفرة والاختبارات، ولا يعد بتوفر ما لم يملك مسار تنفيذ مثبتاً.

## ما نُفذ في محرك واحد

وسع `WorkflowInterpreter` القائم عقد `WorkflowNode` بصورة إضافية. لا يوجد interpreter أو scheduler أو مسار recovery ثانٍ. تدعم الشجرة الداخلية الآن العقد التالية، وكلها تتعاون مع إلغاء coroutine وتملك حدوداً صريحة:

| العقدة | الدلالة المنفذة | الحد أو سلوك الفشل | دليل التنفيذ |
|---|---|---|---|
| `DelayNode` | تأخير تعاوني | حتى 300,000 ms | `WorkflowInterpreter.runDelay` |
| `RetryNode` | إعادة تنفيذ جسم فاشل | 1–100 محاولة وbackoff خطي محدود | `runRetry` واختبار retry |
| `TimeoutNode` | إلغاء الجسم عند انتهاء المهلة | 1–300,000 ms | `runTimeout` واختبار timeout |
| `RaceNode` | أول فرع يكتمل يحسم النتيجة وتلغى الفروع النشطة | لا يقبل قائمة فارغة | `runRace` واختبار race |
| `WhileNode` | يعيد تقييم شرط pure قبل كل دورة | حد أقصى 1,000 دورة | `runWhile` واختبار while |
| `TryNode` | body ثم catch عند failure وأخيراً finally دائماً | لا يبتلع `CancellationException` | `runTry` واختبار try/catch/finally |
| `WaitUntilNode` | polling محدود لشرط pure | مهلة حتى 300,000 ms وفاصل أدنى 10 ms | `runWaitUntil` واختبار wait |

تبقى `SequenceNode` و`ParallelNode` و`BranchNode` و`LoopNode` متوافقة في المعنى. يحافظ المسار القديم `AutomationWorkflowMapper` على تحويل الأتمتة المسطحة إلى الشجرة القديمة، ولذلك لا يتغير سلوك workflows المحفوظة الموجودة.

## الحدود الصريحة

> لا تصبح العقد الجديدة بعد جزءاً من نموذج `Automation` المحفوظ أو محرر workflows للمستخدم. إنها **قدرات runtime داخلية قابلة للاختبار** إلى أن يضاف format إصدار جديد، وترحيل additive، وواجهة configuration مقيّدة. لا يجوز للتقرير أو واجهة التطبيق وصف ForEach/Switch/Break/Continue/Debounce/Throttle/Subworkflow أو تحويلات JSON المتقدمة بأنها مدعومة في هذا الإصدار.

يظل `ExpressionEngine` الموجود هو evaluator التحويلي/الشرطي الآمن الحالي لـ `RuntimeValue`. يدعم المقارنات المنطقية والعددية والنصية و`length` و`lower` و`upper` و`contains` و`exists` بلا `eval` أو reflection. لم يضف هذا التغيير مفسر نصوص أو كود تنفيذي أو محرك بيانات موازياً.

## الاختبارات المرجعية

- `WorkflowInterpreterTest`: المسارات المتوافقة، rollback، والإلغاء المنظم.
- `WorkflowInterpreterFlowControlTest`: retry، while، try/catch/finally، timeout، race وwait المحدود.

## أثر التوافق

لا تُقرأ العقد الجديدة من بيانات legacy ولا تغير إصدار `Automation` المحفوظ. وبذلك لا توجد migration صامتة أو إعادة تفسير للـ workflows القديمة. أي إتاحة لاحقة للمستخدم النهائي يجب أن تمر عبر versioning و`WorkflowValidator` و`WorkflowDryRunService` القائمين.
