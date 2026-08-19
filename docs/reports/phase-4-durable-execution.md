# تقرير المرحلة 4 — التنفيذ المتين ونقاط التحقق والاستعادة

**الحالة:** مكتملة ومتحقق منها باختبارات معاد تشغيلها.  
**المبدأ:** توسعة `ActiveExecutionStore` و`ExecutionEngine` وتهيئة التطبيق القائمة؛ لا توجد قاعدة بيانات ثانية ولا queue أو scheduler أو replay تلقائي موازٍ.

## ما نُفذ

أضيف نموذج `DurableExecutionCheckpoint` المتسلسل بسجل صغير ومحدود داخل DataStore الموجود. يحمل السجل `runId` وautomation id وعدد actions والمؤشر التالي ومجموعة actions المكتملة ومفاتيح idempotency والحالة والوقت ورسالة محدودة الطول. لا يخزن action config أو payload أو output أو Android objects أو command text أو secrets. يدعم المخطط version صريحاً لضمان ترقية منضبطة لاحقاً.

أضيفت حالات متينة هي `STARTED`, `ACTION_STARTED`, `ACTION_COMPLETED`, `ACTION_UNKNOWN`, `EXIT_PENDING`, `COMPLETED`, `RECOVERY_CLAIMED` و`RECOVERY_REQUIRED`. يسجل المحرك checkpoint قبل أي side effect. وقبل كل action يحجز مفتاح idempotency على صيغة `runId:index:ActionType`، وبعد عودة handler يثبت completion ويقدم cursor مرة واحدة. إذا قُطع coroutine أثناء action، يتحول checkpoint إلى `ACTION_UNKNOWN` ولا يسمح مسار recovery بإعادة التنفيذ بصورة عمياء.

يرفض `ExecutionEngine` التنفيذ قبل side effects عندما يتعذر قبول checkpoint المتين، ويسجل ذلك كـ `CHECKPOINT_REJECTED` في التاريخ. لا يتعارض هذا مع gate الشروط: التنفيذ المحجوب بسبب constraints لا ينشئ checkpoint. بعد حفظ `ExecutionRecord` بنجاح يزال checkpoint، بينما يبقى `ActiveExecutionStore.markStarted/consumeStarted` الحالي مستقلاً لتسليح end behavior ومنع تكراره.

يقدم `claimRecoveryCandidates` مطالبة ذرية لكل سجل غير نهائي بعد restart ويبدله إلى `RECOVERY_CLAIMED`. يحتفظ الحقل `recoverySourceStatus` بالحالة قبل المطالبة، لذلك يستطيع recovery أن يميز بين boundary مكتمل يمكن تحضيره لاستئناف واعٍ بالـ workflow وبين action بدأ أو مجهول يتطلب تحققاً أو تعويضاً. ولا يمكن لعامل recovery ثانٍ مطالبة السجل نفسه.

أضيف `ExecutionRecoveryCoordinator` فوق السجل القائم وربط بمسار bootstrap في `NexaFlowApplication` عبر `appScope`. المسح في البداية أفضل جهد ولا يعيد تشغيل أي action. يصنف الحالات كالتالي:

| آخر حالة مؤكدة | القرار الآمن |
|---|---|
| `STARTED` أو `ACTION_COMPLETED` | مرشح لاستئناف لاحق واعٍ بتعريف workflow وإصداره وقدراته. |
| `ACTION_STARTED` أو `ACTION_UNKNOWN` | تحقق أو تعويض مطلوب؛ ممنوع replay تلقائي. |
| `EXIT_PENDING` | إعادة مطابقة trigger قبل تنفيذ end behavior. |
| حالة غير قابلة للتصنيف | تشخيص يدوي صريح. |

## التوافق والحدود

لا يتغير ترتيب actions ولا Action Registry ولا worker أو scheduler أو WorkManager. يبقى `ExecutionRecord` التاريخي المصدر النهائي للنتيجة المنتهية، ويبقى سجل checkpoint خاصاً بما هو جارٍ فقط. حد السجل هو 128 checkpoint، ورسائل التشخيص مقتصرة على 512 حرفاً؛ يمنع ذلك نمو DataStore عبر التشغيل طويل الأجل.

الحالة المتينة تمنع الادعاء بأن action مكتمل بلا checkpoint، لكنها لا تستطيع إثبات نتيجة side effect خارج التطبيق من تلقاء نفسها. لذلك لا ينفذ coordinator استئنافاً فعلياً بعد restart في هذه المرحلة؛ يلزم verifier/compensator خاص بالـ action وversion/capability checks قبل السماح بالاستئناف المرشح. هذا intentional safety boundary وليس نقصاً مخفياً.

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `ActiveExecutionStoreCheckpointTest` | ناجح: begin، منع run id المكرر، action start/complete، cursor، idempotency key، claim مرة واحدة، recovery-required، complete/clear. |
| حالة action interrupted | ناجحة: تتحول إلى `ACTION_UNKNOWN` وتحتفظ بالتشخيص. |
| `ExecutionRecoveryCoordinatorTest` | ناجح: `ACTION_STARTED` يصنف `VERIFY_OR_COMPENSATE_REQUIRED` ولا يُعاد replay. |
| `ExecutionEngineVariableResolutionTest` و`ExecutionEngineConstraintsTest` | ناجحة ضمن بوابة المرحلة، ما يثبت عدم كسر Data Runtime وcondition gate. |
| أمر بوابة checkpoint/المحرك | ناجح بعد إعادة التشغيل بـ `--rerun-tasks --no-parallel --max-workers=1`؛ استغرق أول تشغيل 6:38 بسبب تنزيل Robolectric system artifact، ثم اكتملت إعادة التحقق اللاحقة في 23 ثانية. |

## الخطوة التالية

تنتقل الخطة إلى **المرحلة 5: التحقق والقدرات والخلفيات والتشخيص المتوافق**. ستبني فوق capability resolver المضاف سابقاً لإكمال validation contracts وavailability/details وverification policy والخلفيات الرسمية/الاختيارية، مع منع أي تصعيد Root أو Shizuku ضمن fallback خفي.
