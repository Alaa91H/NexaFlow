# تقرير المرحلة 3 — TaskManager Hardening

**الحالة:** مكتملة ومتحقق منها.  
**الالتزام المعماري:** جرى توسيع `TaskManager` و`PriorityQueue` والـ worker القائمين فقط؛ لم يُنشأ scheduler أو queue أو service خلفية موازية.

## التحسينات المنفذة

أضيفت حالات تشغيل صريحة هي `QUEUED`, `RUNNING`, `RETRY_WAIT`, `CANCEL_REQUESTED`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `DEADLINE_EXCEEDED`, `CANCELLED` و`REJECTED`. يوفر `TaskStatus` تدفق حالة واحداً عبر `statuses` لكل task id مع attempt ووقت تحديث ورسالة عند توفرها. يحتفظ المدير بآخر 500 status كي لا تنمو الذاكرة في جلسات الأتمتة الطويلة، بينما يحتفظ بسجل النتائج النهائي بحد 200 كما كان.

أضيف `TaskManagerLimits` لحد queue، وأقصى timeout، وسعات الموارد المنطقية. صارت `PendingTask` تدعم `deadlineAtMs` للـ lifecycle الكامل ومجموعة موارد منطقية `NETWORK`, `BLUETOOTH`, `LOCATION`, `FILE_IO`, `HEAVY_COMPUTE`. المورد هنا lock تنفيذي فقط؛ لا يمنح permission أو يلتف على سياسة Android. يتم الحصول على permits في ترتيب ثابت ثم تحريرها في finally عبر coroutine semaphore، لذلك لا يوجد lock leak عند timeout أو cancellation.

أضيفت `submit` كواجهة قبول منظمة ترجع `TaskAdmission.Accepted` أو `TaskAdmission.Rejected` بسبب مسمى. تشمل الأسباب shutdown، task id مكرر، queue ممتلئة، timeout يتجاوز السياسة، مورد معطل، أو deadline منتهٍ قبل الدخول. تحتفظ `enqueue` بتوقيعها القديم وتفوض إلى `submit` للتوافق الخلفي؛ ويظهر الرفض القديم في `results` و`statuses` بدلاً من سقوط صامت.

يحترم manager الآن deadline قبل كل attempt وبعد retry wait، ويصدر `TaskResult.DeadlineExceeded` وحالة `DEADLINE_EXCEEDED` من دون تشغيل action متأخر. كما أصبح `cancel` يعيد false للمعرف غير النشط، يمرر حالة `CANCEL_REQUESTED` عند الإلغاء، ثم ينهي إلى `CANCELLED` في كل المسارات: انتظار queue، قبل attempt، بعد attempt، أو CancellationException. يسجل `shutdown` queued work كـ cancelled ويطلب إلغاء running children قبل إغلاق worker.

## التوافق ودورة الحياة

لا يزال ترتيب queue `CRITICAL → HIGH → NORMAL → LOW` وFIFO داخل priority واحد كما هو. وما زال worker واحداً، ولذلك لا تدخل device-state actions في تزامن غير مقصود. تحسّن permits هنا سلامة العقود والاستعداد لتوسعة تزامن مدروسة لاحقاً، لكن لا تغير parallelism الفعلي. تبقى retry policy الحالية، ويتحول التأخير بين المحاولات إلى حالة `RETRY_WAIT` مرئية.

| جانب | قبل المرحلة | بعد المرحلة |
|---|---|---|
| قبول task | `enqueue` صامت دائماً | `submit` بنتيجة قبول/رفض منظمة مع `enqueue` متوافق. |
| الحالة | `isRunning` ونتيجة نهائية | StateFlow للحالة الكاملة ومحاولات التنفيذ. |
| timeout | timeout لكل محاولة | timeout لكل محاولة + deadline مطلق للـ lifecycle. |
| الموارد | لا عقد | موارد منطقية محدودة بـ semaphore وترتيب ثابت. |
| الإلغاء | نتيجة final في المسار الأساسي | request/terminal states في كل مسار وإغلاق منظم. |
| الذاكرة | results محدودة فقط | results 200 وstatus 500. |

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| اختبارات الأولوية وFIFO الموجودة | ناجحة. |
| retry success/failure والمهلة القديمة | ناجحة. |
| cancellation queued/running وwake-up وpending count | ناجحة. |
| رفض deadline المنتهي وtimeout المخالف ومورد NETWORK المعطل | ناجح. |
| deadline ينتهي أثناء الانتظار ولا ينفذ action | ناجح. |
| duplicate id يرفض حتى تصل المهمة الأصلية إلى حالة نهائية | ناجح. |
| إلغاء task يتنقل إلى `CANCELLED` وتظهر النتيجة | ناجح. |
| الأمر المعاد تشغيله | `:core:execution:testDebugUnitTest --tests TaskManagerTest --tests TaskManagerHardeningTest --rerun-tasks --no-parallel --max-workers=1` ناجح في 18 ثانية. |

## القيود المقصودة

حالة TaskManager لا تزال in-memory. لا تُستعاد المهام أو permit ownership بعد قتل العملية بعد؛ يعالج ذلك `ActiveExecutionStore`/Room وcheckpoint/recovery في المرحلة الرابعة، لا داخل queue جديدة. ولا يقوم مدير المهام بتنفيذ WorkManager أو AlarmManager؛ الاختيار بين وقت محدد/event/background policy يبقى في المسارات الموجودة إلى أن يدمجها durable coordinator لاحقاً.

## الخطوة التالية

تنتقل الخطة إلى **المرحلة 4: الحالة المتينة ونقاط التحقق والاستعادة وidempotency**. ستُوسّع `ActiveExecutionStore` و`StateTransactionStore` وexecution history القائمة بعقد run/checkpoint/recovery، من دون second database أو إعادة تنفيذ action غير مؤكدة.
