# مصفوفة قدرات محرك تحديث التطبيقات

**تاريخ التحقق:** 20 أغسطس 2026
**حالة الدليل:** توثيق رسمي + تدقيق كود NexaFlow. لا توجد في هذه المرحلة ملاحظة من جهاز Android فعلي لمحرك التحديث المقترح.

> هذه المصفوفة تفرق عمداً بين: **اكتشاف تحديث من Google Play**، و**الحصول على bytes رسمية**، و**التحقق من APK متاح محلياً**، و**التثبيت الصامت**. نجاح مرحلة لا يثبت الأخرى.

## تصنيفات الإثبات

| التصنيف | المعنى |
|---|---|
| `OFFICIAL_PUBLIC_API` | واجهة عامة موثقة ويمكن للتطبيق العادي استدعاؤها ضمن شروطها. |
| `OFFICIAL_MANAGED_ONLY` | واجهة رسمية متاحة فقط لبيئة Android Enterprise/الجهاز المُدار. |
| `PRIVILEGED_CAPABILITY` | قدرة على ملف محلي بعد تحقق مستقل؛ لا تمنح مصدر Google Play. |
| `DEVICE_DEPENDENT` | يتوقف على ROM أو مزود الامتياز ولا يوجد إثبات ثابت من NexaFlow بعد. |
| `NOT_EXPOSED` | لا توجد واجهة رسمية مكشوفة لهذا السياق. |
| `NOT_SAFE` | مسار يخالف سلامة المصدر أو سياسة المتجر أو معيار القبول. |
| `NOT_IMPLEMENTABLE` | غير قابل للتنفيذ في NexaFlow بالبيئة المعنية مع الدليل المتاح. |

## المصفوفة الحاسمة

| القدرة | Stock Android | Shizuku | Root | جهاز مُدار مع Managed Google Play |
|---|---|---|---|---|
| كشف التطبيقات المثبتة محلياً | `OFFICIAL_PUBLIC_API` عبر PackageManager، مع مراعاة package visibility | `OFFICIAL_PUBLIC_API` | `OFFICIAL_PUBLIC_API` | `OFFICIAL_PUBLIC_API` |
| كشف تحديثات Google Play المتاحة | `NOT_EXPOSED` | `NOT_EXPOSED`؛ Shizuku لا يضيف API متجر | `NOT_EXPOSED`؛ Root لا يوفّر كتالوج رسمي | `OFFICIAL_MANAGED_ONLY` عبر سياسة المؤسسة وManaged Google Play، لا API عميل داخل NexaFlow |
| الحصول على حزمة تحديث رسمية من Play | `NOT_EXPOSED` | `NOT_EXPOSED` | `NOT_EXPOSED` | `OFFICIAL_MANAGED_ONLY`؛ Play/DPC يديران التوزيع وفق السياسة |
| تنزيل APK خارجي معروف المصدر | `OFFICIAL_PUBLIC_API` كنقل bytes فقط، لا كاكتشاف Play | نفسه | نفسه | ليس مطلوباً عادةً؛ Managed Play يدير التنزيل |
| فحص SHA‑256 لملف محلي | `OFFICIAL_PUBLIC_API`؛ يوجد `UpdateChecker.sha256` | نفسه | نفسه | نفسه |
| فحص هوية/إصدار/توقيع/splits لملف محلي | `OFFICIAL_PUBLIC_API` ممكن عبر PackageManager/PackageInstaller، **غير منفذ في NexaFlow حالياً** | نفسه | نفسه | نفسه |
| تثبيت APK محلي مفرد | `OFFICIAL_PUBLIC_API` لكن `INTERACTIVE_REQUIRED` عادةً | `DEVICE_DEPENDENT`؛ لا يثبت مبدأً أهلية صامتة | `PRIVILEGED_CAPABILITY` لملف محلي، يوجد مسار `pm install -r` بدائي فقط | `OFFICIAL_MANAGED_ONLY`؛ Device Owner/Affiliated Profile Owner يمكنه إكمال جلسة التثبيت تلقائياً |
| تثبيت splits موثقة | `OFFICIAL_PUBLIC_API` لكن تفاعلي عادةً | `DEVICE_DEPENDENT` | `PRIVILEGED_CAPABILITY` نظرياً؛ **غير منفذ/غير مختبر في NexaFlow** | `OFFICIAL_MANAGED_ONLY` عند إعطاء الحزم الصحيحة للجهاز المالك |
| تحديث تطبيقات Google Play بلا UI | `NOT_IMPLEMENTABLE` | `NOT_IMPLEMENTABLE` | `NOT_IMPLEMENTABLE` ضمن مسار رسمي؛ Root لا يخلق مصدر Play | `OFFICIAL_MANAGED_ONLY` عبر EMM/Android Device Policy، ويتبع القيود والسياسات |
| تحديث كل التطبيقات المؤهلة | `NOT_IMPLEMENTABLE` | `NOT_IMPLEMENTABLE` | `NOT_IMPLEMENTABLE` دون مصدر موثوق لكل حزمة | `OFFICIAL_MANAGED_ONLY` للحزم المنشورة والمطبقة ضمن سياسة المؤسسة |

## مصادر الحكم

### 1. PackageInstaller لا يساوي متجر Play

توثّق Android أن `PackageInstaller` يتعامل مع APK واحد أو splits متعددة **بعد توفيرها له**. يفرض تطابق اسم الحزمة و`versionCode` وشهادات التوقيع ووجود base APK، وقد يطلب التزام الجلسة تفاعل المستخدم. الاستثناء الموثّق للإكمال التلقائي هو Device Owner أو Affiliated Profile Owner.[1]

> لا يتضمن هذا العقد اكتشاف إصدارات Google Play أو تنزيل الحزمة المناسبة أو الحصول على تراخيص متجر Play.

### 2. Play Core وGoogle Play Developer API ليسا حل «Update all» للمستخدم

تصف Google In-app updates بأنها تحديث للتطبيق الطالب نفسه.[2] وتصف Google Play Developer API مهام ناشر التطبيق وPlay Console مثل رفع إصدار تطبيق الناشر ونشره وإدارة tracks؛ لا يصف API لجلب تحديثات كل التطبيقات المثبتة على جهاز مستخدم.[3]

### 3. Root وShizuku يغيران قناة تنفيذ محلية لا سلطة المصدر

يملك NexaFlow كاشف Root فعلياً وقناة Shell مميزة، وكذلك فحص Shizuku للـBinder والإذن وUserService. لكنه لا يملك في أي منهما كاشف كتـالوج Google Play أو مصدر APK رسمي أو فاحص توقيع archive خارجي. وعليه، لا تسمح المصفوفة بترقية Root أو Shizuku إلى دعم تحديثات Play. أي استخدام لاستخراج ملفات من Play Store أو مفاتيح/رموز حساب أو API داخلية مصنف `NOT_SAFE` ومرفوض.

### 4. البيئة المُدارة هي المسار الرسمي المختلف

تسمح Android Management API لسياسات Managed Google Play بضبط التحديث التلقائي الافتراضي أو أولوية عالية لكل حزمة. وفي الوضع الافتراضي، تحدث التطبيقات وفق شروط مثل Wi‑Fi والشحن وعدم الاستخدام؛ وفي الأولوية العالية تحدث عند إتاحة الإصدار.[4] تشرح Google أن التطبيق المصاحب Android Device Policy يتلقى السياسات ويتولى الإدارة على الجهاز.[5] كما توضح Android أن Device Owner ناتج عن Managed Provisioning، وليس إذناً عادياً يحصل عليه التطبيق أثناء الاستعمال.[6]

لذلك لا تكون هذه القدرة قابلة للاستدعاء داخل NexaFlow ما لم يصبح المشروع جزءاً من حل EMM مُسجّل، مع مؤسسة وسياسة خادمية وجهاز مُدار. هذا متطلب معماري وتشغيلي خارج كود التطبيق الحالي.

## دليل كود NexaFlow الحالي

| الدليل | الملاحظة |
|---|---|
| `SystemController.openPlayStoreUpdates()` | يفتح رابط متجر Play فقط؛ لا يكتشف ولا ينزّل ولا يثبت. |
| `SystemController.installApk(path)` | ينفذ `pm install -r` على مسار محلي؛ لا يدعم المصدر أو التوقيع أو splits أو post-verify. |
| `UpdateChecker` | تنزيل GitHub الخاص بـ NexaFlow والتحقق من SHA‑256 ثم تثبيت تفاعلي. ليس محرك تطبيقات خارجية. |
| `SystemAppStatusDetector.isPlatformSigned()` | يقارن توقيع NexaFlow بتوقيع منصة Android؛ ليس فاحص APK مرشح. |
| `PrivilegedCapabilityBackends` | لا يعرّف قدرة install/update؛ قنوات Root/Shizuku الحالية محدودة ومختارة صراحة. |
| `RomCapabilityProvider` | لا يكتشف Device Owner أو Managed Google Play أو سلطة PackageInstaller. |

## الحدود التنفيذية

لا يجب أن تنفذ Action جديدة عملية تنزيل أو تثبيت عندما تكون نتيجة `Google Play update discovery` هي `NOT_EXPOSED`. النتيجة الصحيحة في هذه البيئات هي `SKIPPED` مع السبب التقني الدقيق، لا `FAILED`، ما لم تبدأ عملية موثقة فعلاً ثم تفشل.

## المراجع

[1]: https://developer.android.com/reference/android/content/pm/PackageInstaller "Android Developers — PackageInstaller"
[2]: https://developer.android.com/guide/playcore/in-app-updates "Android Developers — In-app updates"
[3]: https://developers.google.com/android-publisher "Google Play Developer APIs"
[4]: https://developers.google.com/android/management/control-app-updates "Google for Developers — Control app updates"
[5]: https://developers.google.com/android/management "Google for Developers — Android Management API"
[6]: https://developer.android.com/reference/android/app/admin/DevicePolicyManager "Android Developers — DevicePolicyManager"
