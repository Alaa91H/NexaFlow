# المرحلة 8 — توصيل Runtime للقدرات العامة

**الحالة:** مكتملة محلياً ومجتازة لتجميع التطبيق.  
**الهدف:** تحويل طبقة capability من عقود واختبارات مستقلة إلى مسار إنتاج محدود وآمن داخل `ExecutionEngine`.

## القرار الهندسي

أظهر تدقيق نقاط الاستدعاء أن `CapabilityExecutionService` و`CapabilityResolver` والخلفيات العامة كانت مكتملة العقد والاختبار، لكنها لم تكن تُنشأ من حقن التطبيق ولم يعبر أي action إنتاجي خلالها. لذلك لا يصح وصفها كطبقة تنفيذ حية قبل إضافة جسر صريح.

بدلاً من تحويل جميع `ActionType` إلى capabilities بصورة عامة، أضيف `CapabilityActionMapper` بقاعدة **allowlist**. يحوّل فقط `SYSTEM_OPEN_URL` إلى `CapabilityId.INTENT_LAUNCH` مع حقل `url` نفسه المستخدم سابقاً. لا يحوّل Root أو Shizuku أو Accessibility أو أوامر shell أو actions التي تتطلب واجهة Android مميزة. تعود تلك الأفعال إلى handlers التراثية المراجعة كما كانت.

| المسار | الحالة بعد التوصيل | سبب الحد |
|---|---|---|
| `SYSTEM_OPEN_URL` | يمر بـ validation ثم resolver ثم backend العام `INTENT` | handoff عام آمن إلى Android Activity |
| `PACKAGE_READ` و`DEVICE_STATE_READ` | مسجّلان في registry للخدمة وdry-run/diagnostics | لا يوجد ActionType تراثي مكافئ آمن متصل بعد |
| Root/Shizuku/ADB/Accessibility | لا يمر عبر mapper | يحتاج consent وbackend وسياسة خاصة صريحة |
| بقية SystemController actions | تبقى على handlers الحالية | لا تدّعي الخلفية العامة دعماً غير موجود |

## الحقن ومسار التنفيذ

سجل `AppModule` كائنات singleton واحدة من `CapabilityRegistry` و`CapabilityResolver` و`CapabilityExecutionService` و`WorkflowDryRunService`. تحتوي registry فقط على `AndroidPublicCapabilityBackend` و`AndroidIntentCapabilityBackend` وcatalog العام. تستخدم الخدمتان snapshot واحدة من `AndroidCapabilityDeviceStateReader` لتقييم policy. ثم يمرر AppModule الخدمة إلى `ExecutionEngine`.

عند تنفيذ رابط URL، يبني المحول request مع `VerificationMode.NONE` لأن تشغيل Android intent هو handoff ظاهر للمستخدم ولا يمكن للتطبيق إثبات اكتمال التطبيق الهدف. يعامل المحرك `SUCCESS` و`PENDING_USER_ACTION` كبدء ناجح؛ أما فشل validation أو policy أو availability فيصبح `SystemControlResult.fail` مسجلاً ضمن نتيجة action. تظل المدة والـ retry والـ timeout والتحقق الموحدين مسؤولية `CapabilityExecutionService`.

> **ضمان أمني:** لا توجد fallback إلى `SystemController` إذا فشل مسار capability لفتح URL. URL غير HTTPS أو الفارغ أو غير القابل للحل يرفض بنتيجة capability صريحة بدلاً من توسيع المسار إلى shell أو integration مميز.

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `CapabilityActionMapperTest` | ناجح: URL يتحول إلى `INTENT_LAUNCH`، و`ADVANCED_ROOT` لا يتحول. |
| `:app:compileDebugKotlin` | ناجح: Hilt ينشئ registry/resolver/service ويمررها إلى المحرك. |
| بوابة التوصيل | نجحت في **72 ثانية** مع **309 مهام Gradle** بعد إعادة تنفيذ المهام. |

## نطاق مقصود للإصدارات التالية

`TaskManager` ما زال عقد جدولة مستقل ولا يلتف حول action loop الحالي؛ لذلك لا يعرض التطبيق ادعاءً زائفاً بأن كل action يحصل على queue/resource admission منه. وبالمثل، لم تُضف شاشة Debug مخصصة في هذه الدفعة لأن واجهة التشخيص يجب أن تقرأ عدادات runtime حية (queue, event backpressure, recovery) لا بيانات ثابتة. الخطوة الصحيحة التالية لها هي توفير diagnostics facade موحداً ثم إظهارها في قسم الخبراء.
