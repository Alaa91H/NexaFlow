# تدقيق المستودع: محرك تحديث التطبيقات المتدرج الإمكانات

**الحالة:** مرحلة التدقيق فقط — لم يُضف أي منطق تنفيذ أو تنزيل أو تثبيت جديد.
**النطاق:** تحديد ما يمكن لـ NexaFlow إعادة استخدامه لبناء مسار تحديث آمن، وما ينقصه قبل ادعاء دعم تحديثات Google Play أو أي تحديثات تطبيقات خارجية.

> **خلاصة التدقيق:** يملك NexaFlow لبنات قوية للتنفيذ المميز، وسجل التنفيذ، وإعادة المحاولة، وتنزيل APK الخاص به مع تحقق SHA‑256. لكنه لا يملك حالياً اكتشاف تحديثات Google Play، أو مصدر حزم رسمي لتطبيقات أخرى، أو فاحص APK خارجي لهوية الحزمة/التوقيع/التقسيمات، أو صلاحية جهاز مُدار. لذلك لا يوجد حالياً مسار آمن لتحديث تطبيقات Google أو تطبيقات Play الأخرى دون واجهة المتجر.

## المكوّنات القائمة والفجوات

| القدرة القائمة | التنفيذ الحالي | مكوّن قابل لإعادة الاستخدام | الفجوة المطلوبة | التغيير المطلوب لاحقاً | الخطر |
|---|---|---|---|---|---|
| اكتشاف تحديث NexaFlow نفسه | `feature/settings/UpdateChecker.kt` يقرأ إصدار GitHub وينتقي APK وملف SHA‑256 | جلب HTTP، تنزيل متدفق، SHA‑256، فصل التحليل عن I/O | لا يعرف كتالوج Play أو إصدارات تطبيقات أخرى | مصدر تحديث موثوق ومصرح به لكل حزمة | لا يجوز تعميم مصدر GitHub لتطبيقات خارجية |
| تثبيت APK عادي | `UpdateChecker.install()` يمرر APK إلى المثبّت عبر `FileProvider` | تسليم URI صحيح للمثبّت | التثبيت تفاعلي ولا يتحقق من هوية أو توقيع APK | لا يُستخدم لتحديث صامت أو مصدر مجهول | مطالبة مستخدم إلزامية ومخاطر مصدر |
| تثبيت مميز لملف محلي | `SystemController.installApk(path)` ينفذ `pm install -r` عبر `PrivilegedRunner` | تنفيذ Shell مقيّد وإرجاع نتيجة | لا يوجد اكتشاف، تحقق توقيع، جلسات، splits أو تحقق بعد التثبيت | لا يجوز استخدامه مباشرةً؛ يلزم مسار ثابت المصدر والتحقق أولاً | يمكن أن يثبت APK خاطئاً إن استُخدم بلا حواجز |
| Root | `PrivilegedRunner.isRootAvailable()` و`runRootOperation()` | كشف فعلي لـ `su`، تنفيذ مهلة محددة، نتيجة قابلة للتدقيق | Root لا يمنح مصدر تحديث Google Play ولا بيانات كتالوج رسمية | استخدامه لاحقاً فقط للتثبيت بعد قبول مصدر وحزمة متحقق منهما | Root ليس بديلاً عن السلطة أو المصدر الرسمي |
| Shizuku | `PrivilegedRunner.isShizukuRunning()` و`isShizukuGranted()` و`ShizukuShellBridge` | فصل حالة الخادم عن الإذن وBinder وUserService | لا توجد قدرة مثبتة للوصول إلى كتالوج Play أو سلطة PackageInstaller صامتة | يجب فحص الميزة المطلوبة صراحةً وعدم معاملته كـRoot | اختلاف صلاحيات shell حسب نظام التشغيل وإصدار Shizuku |
| قدرات مميزة مكتوبة بأنواع | `PrivilegedCapabilityBackends.kt` | اختيار Backend صريح، نتائج وفشل مميزان، Root/Shizuku مفصولان | القدرات الحالية تقتصر على force-stop وenable/disable وإعدادات ونسخ ملفات | إضافة قدرة فقط عند توثيق API/عملية ثابتة ومراجعتها | تجنب أوامر shell العامة أو fallback تلقائي خطير |
| ملف قدرات الجهاز | `RomCapabilityProvider.kt` و`DeviceProfileDetector.kt` | كشف Root وShizuku وROM ومستوى التكامل | لا يرصد Device Owner أو Managed Google Play أو سلطة installer أو اكتشاف مصدر | توسيع مصفوفة القدرات بمدخلات مثبتة فقط | لا يجوز استنتاج الإدارة من وجود Play Store أو Root |
| توقيع التطبيق | `SystemAppStatusDetector.isPlatformSigned()` | نمط API 28+ لاستخراج توقيع حزمة مثبتة | يتحقق من NexaFlow مقابل منصة Android فقط؛ لا يفحص APK خارجياً ولا تدوير الشهادة | فاحص مستقل لحزمة مرشحة قبل التثبيت | مقارنة توقيع بسيطة قد لا تكفي مع تاريخ تدوير المفاتيح |
| الحزم والتقسيمات | لا يوجد مكوّن إنتاجي لمسح `PackageArchiveInfo` أو `splitSourceDirs` أو archives الخارجية | لا شيء مباشر | لا تحقق من packageName أو `versionCode` أو توقيع archive أو اكتمال splits | بناء فاحص APK/split فقط عند وجود مصدر موثوق | لا يجوز اعتبار APK منفرداً صالحاً لتطبيق مقسّم |
| مصدر التثبيت الأصلي | لا يوجد استخدام لـ `InstallSourceInfo` أو `getInstallerPackageName` | لا شيء مباشر | لا يمكن حتى تصنيف حزمة مثبتة من Play بدقة عبر المسار الحالي | مستعلم PackageManager للميتاداتا المحلية فقط | معرف المثبّت لا يثبت توافر تحديث أو مصدر تنزيل |
| تنزيل عام | لا يوجد تنزيل إنتاجي عام غير `UpdateChecker` | `HttpURLConnection` المتدفق مع timeout وSHA‑256 | لا توجد سياسة ثقة بالمضيف أو pins أو manifests موقعة لتطبيقات خارجية | لا تنزيل خارج مصدر موثق ومصرح | تنزيل APK من الإنترنت عشوائياً محظور |
| إعادة المحاولة | `domain/workflow/RetryExecutor.kt` | backoff وjitter ومفتاح idempotency وتصنيف أخطاء | لا توجد حالات تحديث محددة لكل حزمة | إعادة استخدام السياسة بعد تعريف حالات عملية تحديث | يجب عدم إعادة محاولة رفض أمني أو فشل توقيع |
| الاستعادة بعد موت العملية | `ExecutionRecoveryCoordinator` و`WorkflowRunContext` | نمط checkpoint وverify/compensate واستعادة مدعومة | لا توجد checkpoint لحزمة أو مرحلة تحديث | استخدام النمط الحالي لا Runtime جديد | تثبيت مقطوع يحتاج تحققاً بعدياً لا إعادة تنزيل عمياء |
| السجل التنفيذي | `ExecutionRecord` و`ActionExecutionResult` وHistoryRepository | نتيجة لكل Action مع المدة وقناة التنفيذ | لا توجد حقول لكل حزمة/مرحلة/مصدر | تمثيل نتائج الحزم داخل رسالة Action أو امتداد متوافق للبيانات | تغييرات مخطط قاعدة البيانات قد تحتاج ترحيل متوافق |
| الجدولة | `ReminderScheduler` ومحرك الأتمتة القائم | تشغيل Action ضمن workflow القائم | لا حاجة إلى Scheduler جديد | ربط Action مستقبلية بالمحفزات الحالية فقط | لا يجوز تكرار التنفيذ غير المتزامن أو إنشاء Queue موازية |

## المسارات القائمة ذات الصلة

| الطبقة | نقطة الدخول | الملاحظة |
|---|---|---|
| عقد Action | `domain/.../Automation.kt` | يحتوي حالياً `SYSTEM_OPEN_PLAY_UPDATES` فقط، وهو ليس محرك تحديث. |
| التنفيذ | `SystemActionsHandler` → `SystemController.openPlayStoreUpdates()` | يطلق رابط `https://play.google.com/store/apps` فقط. لا يكتشف أو ينزل أو يثبت. |
| التوافق | `CommandCatalog` | يسجل فتح صفحة التحديث كقدرة عامة؛ لا يثبت وجود تحديث صامت. |
| العرض | `ActionPresentation.kt` وموارد `feature/automation-builder`/`feature/automations` | يقدم نصوصاً لفتح صفحة المتجر، ولا توجد واجهة تخطيط أو نتيجة لحزم متعددة. |
| الأمان المميز | `PrivilegedRunner`, `PrivilegedCapabilityBackends`, `RomCapabilityProvider` | أساس مناسب لاكتشاف امتيازات مثبتة، وليس تفويضاً ضمنياً لتحديثات Play. |

## الاستنتاجات قبل التنفيذ

لا يجوز إعادة استخدام `SYSTEM_OPEN_PLAY_UPDATES` كاسم أو سلوك للمحرك المطلوب، لأنه يفتح واجهة المتجر، وهو مخالف صراحةً لمعيار القبول. ولا يجوز إعادة استخدام `installApk(path)` مباشرةً لأنه يفتقد كل الحواجز اللازمة: مصدر موثوق، هوية، إصدار أعلى، توقيع متوافق، splits، ومعاملة فشل واستعادة لكل حزمة.

المسار الأدنى الآمن لا بد أن يبدأ بكشف القدرات ثم يقرر **عدم التوفر** عندما لا يوجد مصدر تحديث رسمي قابل للوصول. على هاتف شخصي، حتى مع Root أو Shizuku، لا يثبت التدقيق الحالي ولا المصادر الرسمية وجود API لتصفح كتالوج Google Play أو تنزيل حزم تحديثاته. أما Android Enterprise/Managed Google Play فهو مسار مؤسسي مستقل، ولا توجد له حالياً هوية مؤسسة أو تسجيل أجهزة أو عميل Android Management API في NexaFlow.

## المراجع الرسمية الحاكمة

تؤكد وثائق Google أن In-app updates تخص تحديث التطبيق الطالب نفسه، لا تحديث تطبيقات أخرى.[1] وتؤكد سياسة Google Play أن `REQUEST_INSTALL_PACKAGES` يتطلب تثبيتاً يبدأه المستخدم ووظيفة أساسية مؤهلة، وليس جسراً لتحديثات Play الشاملة.[2] أما التحديثات الصامتة الخاضعة لسياسة رسمية فتتاح عبر Managed Google Play على الأجهزة المُدارة، وبقيود الإدارة والتسجيل المؤسسي.[3] [4]

## أدلة إضافية للمصفوفة

يوثّق `PackageInstaller` أن أي تطبيق يستطيع إنشاء جلسة installation وتحميل APK واحد أو عدة APK splits إليها، لكنه يحدد أن الالتزام قد يتطلب تفاعل المستخدم؛ الاستثناء الموثّق للإكمال التلقائي هو **Device Owner** أو **Affiliated Profile Owner**. كما يفرض أن تتطابق كل splits في اسم الحزمة و`versionCode` وشهادة التوقيع، مع وجود base APK وحيد.[5] لذلك تثبت هذه الواجهة كيفية تثبيت bytes موثوقة مسبقاً، ولا توفر كتالوج Google Play أو آلية تنزيل حزمة تحديث صحيحة.

أما Google Play Developer API فهو API خاص بناشر التطبيق وPlay Console: يرفع إصدارات تطبيق الناشر وينشرها ويضبط tracks والقوائم. لا يصف API لإدارة تحديثات التطبيقات المثبتة لدى مستخدم عادي أو لتنزيل حزم Google Play من جهازه.[6]

## المراجع

[1]: https://developer.android.com/guide/playcore/in-app-updates "Android Developers — In-app updates"
[2]: https://support.google.com/googleplay/android-developer/answer/12085295?hl=en "Google Play policy — REQUEST_INSTALL_PACKAGES"
[3]: https://developers.google.com/android/management/control-app-updates "Google for Developers — Control app updates"
[4]: https://source.android.com/docs/devices/admin "Android Open Source Project — Device management overview"
[5]: https://developer.android.com/reference/android/content/pm/PackageInstaller "Android Developers — PackageInstaller"
[6]: https://developers.google.com/android-publisher "Google Play Developer APIs"
