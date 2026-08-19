# تقرير المرحلة 5 — التحقق والقدرات والخلفيات والتشخيص

**الحالة:** مكتملة ومتحقق منها.  
**الالتزام المعماري:** جرى إكمال `CapabilityRegistry` و`CapabilityResolver` و`CapabilityExecutionService` الموجودة. لا يوجد command runner عام، ولا fallback تلقائي إلى Root أو Shizuku أو ADB.

## حدود الطلب الآمن

أضيف `CapabilityParameterSpec` و`CapabilityParameterType` إلى descriptor. يدعم المخطط معاملات string وboolean وinteger وpackage name وHTTPS URL وcontent URI، مع required/length/range/allowlist. descriptor فارغ المعاملات يعني أن capability لا تقبل أي معامل. ويرفض `CapabilityRequestValidator` قبل resolver أو availability أو execution: capability مجهولة، أكثر من 32 معامل، parameter غير معلن، required parameter مفقود، control character، طول مفرط، type غير صالح أو عدد خارج المجال. لا توجد parameter من نوع shell command، ولا يسمح validator بمفتاح حر مثل `command`.

تنتج أخطاء التحقق `CapabilityResult` منظماً برمز `INVALID_CONFIGURATION` وقائمة validation codes فقط في metadata؛ لا يسجل value أو secret أو command محتمل.

## التنفيذ والسياسة

أصبح `CapabilityExecutionService` يمر بالتسلسل التالي: validation، ثم policy/device state، ثم availability وترتيب resolver، ثم attempt محدود بمهلة policy، ثم retry فقط للرموز المعلنة في `retryableErrors`، ثم verification. ويضيف عدد المحاولات إلى metadata المنظمة. لا يعاد retry لخطأ permission أو configuration أو verification أو cancellation افتراضياً.

أضيف verification hook اختياري إلى `CapabilityBackend`. backend الذي لا يوفر post-condition يرجع `attempted=false` و`verified=false`. وعند `VerificationMode.REQUIRED` تتحول نتيجة transport الناجحة غير المثبتة إلى `FAILED / VERIFICATION_FAILED`. وبذلك لا تُترجم استجابة call أو handoff إلى نجاح مثبت بلا observable check. تبقى `VerificationMode.NONE` خياراً صريحاً للحالات التي تتعمد عدم التحقق.

يبقى resolver محافظاً: الخلفيات المميزة `SHIZUKU`, `ROOT`, `ADB` لا تدخل المرشحين إلا مع `allowPrivilegedBackends=true` صراحةً. كما يفضل Android API وIntent وPackage Manager قبلها حين يكونان متاحين. لا يكفي أن يكون Root متوفراً لاختياره.

## الخلفيات المضافة

أضيف `AndroidPublicCapabilityCatalog` وbackendان محدودان:

| الخلفية | القدرات | السلوك | التحقق |
|---|---|---|---|
| `AndroidPublicCapabilityBackend` | `PACKAGE_READ`, `DEVICE_STATE_READ` | PackageManager وخدمات Android العامة فقط | يتأكد من بقاء package مرئياً؛ قراءة الحالة تثبت مصدرها العام. |
| `AndroidIntentCapabilityBackend` | `INTENT_LAUNCH` للـ HTTPS فقط | يطلق activity system handoff | النتيجة `PENDING_USER_ACTION`، ولا يدعي اكتمال التطبيق الهدف. |

تتعمد هذه الخلفيات عدم استدعاء `SystemController` عند الفشل، لأن controller قد يختار تكاملاً مميزاً. الخلفيات المميزة أو PackageInstaller أو Shizuku/Root ستضاف فقط عبر implementations مستقلة وإذن policy واضح وربط تشخيص منفصل في مرحلة integration، لا ضمن fallback صامت.

## التشخيص

`CapabilityDiagnostics` يبقى واجهة قراءة availability للفهرس نفسه. وأصبح resolver يعرض validation يمكن أن تستخدمه شاشة dry-run أو تفاصيل الإجراء قبل التنفيذ. تظهر أسباب policy والحالة وbackend/error code بصورة قابلة للعرض من دون parsing رسالة حرة.

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| تفضيل Android API على Root المتاح | ناجح. |
| حجب Wi-Fi policy قبل availability/backend | ناجح. |
| منع Root من دون opt-in صريح | ناجح. |
| رفض `command` غير المعلن قبل backend | ناجح. |
| REQUIRED verification يحول نتيجة غير مثبتة إلى `VERIFICATION_FAILED` | ناجح. |
| retry لرمز `NETWORK_ERROR` المعلن فقط ثم نجاح attempt الثاني | ناجح. |
| مدة التنفيذ وbackend المختار في النتيجة المنظمة | ناجح. |
| الأمر المعاد تشغيله | `:core:execution:testDebugUnitTest --tests CapabilityRuntimeTest --rerun-tasks --no-parallel --max-workers=1` ناجح في 13 ثانية. |

## القيود المقصودة

لا تحاول هذه المرحلة أتمتة Google Play أو تجاوز تأكيدات المستخدم أو تثبيت APK من مسار حر. تثبيت/إلغاء/تحديث الحزم، إذا أضيفت، يجب أن تستخدم `PackageInstaller` وpending user action والتوقيع/verification policy، أو backend privileged منفصل ومصرح به. كما أن intent handoff لا يعد نجاحاً للـ target app.

## الخطوة التالية

تنتقل الخطة إلى **المرحلة 6: Vault references وredaction وسجل الإضافات وإصدارات workflow**. ستبني فوق `SecureStorage` و`VariableRepository` وplugin SDK الحاليين لضمان أن الأسرار لا تدخل workflow JSON أو logs، وأن الإضافات/الـ workflow تحمل إصدارات وعقوداً قابلة للتحقق.
