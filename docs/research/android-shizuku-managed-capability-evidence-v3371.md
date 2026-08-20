# أدلة خارجية لتصميم قدرات NexaFlow الديناميكية

**تاريخ الجمع:** 20 أغسطس 2026  
**الغرض:** تثبيت الحدود الفعلية للتحقق من Device Owner وShizuku قبل ربطها بواجهة ديناميكية أو كتالوج إجراءات.

## Device Owner وDevice Policy Manager

يوضح مرجع Android الرسمي أن `DevicePolicyManager` يدير سياسات وقيود الجهاز، وأن التطبيق الإداري لا يحصل على سلطات الإدارة إلا بوصفه Device Administrator أو Device Policy Controller أو بحيازة صلاحيات/أدوار مناسبة. ويعرّف Device Owner بوصفه أقوى نوع من Device Policy Controller ويمكنه التأثير عبر الجهاز؛ كما أن provisioning هو العملية التي تعترف بالتطبيق كـDevice Owner أو Profile Owner بعد شاشات تعليم وموافقة. [1]

**قرار التصميم:** لا تكفي قراءة وجود `DevicePolicyManager` أو system feature لإعلان enterprise capability. يجب أن يستعلم detector وضع المالك/الملف الشخصي الفعلي للتطبيق الحالي، ثم يربط كل enterprise descriptor بواجهة API محددة ومتحققة. لا يظهر `MANAGED_GOOGLE_PLAY` أو package management enterprise قبل وجود backend typed منفذ ومختبر.

## Shizuku

توضح وثائق Shizuku أن Shizuku/Sui يمكنه تشغيل Java/JNI بهوية root أو shell، لكنه تطبيق Android قياسي ويحتاج تثبيت/بدء مناسب. وتوصي الوثائق بتتبع حياة binder عبر listeners للـbinder received/dead وعدم استدعاء API قبل حياة binder. كما تصف طلب permission بوصفه مماثلاً لصلاحية runtime. [2]

توضح الوثائق أيضاً أن Shizuku قد يعمل بهوية root (UID 0) أو ADB shell (UID 2000)، وأن قدرات ADB تختلف بوضوح عن root بحسب صلاحيات Android وLinux وSELinux؛ لا يتيح shell مثلاً الوصول العام إلى بيانات التطبيقات. [2]

**قرار التصميم:** لا يعدّ `Shizuku installed` أو `binder received` قدرة كاملة. يعرّف detector الحالة على مراحل: binder/server، permission، UserService للـoperation التي تحتاجه، ثم availability الخاص بكل backend typed. تستخدم listeners المتاحة لتحديث `StateFlow`، لا polling. لا يُسوّى Shizuku مع Root، ولا يُسوّى Shizuku المبني فوق ADB مع Shizuku/Sui بهوية root.

## مراجع

[1]: https://developer.android.com/reference/android/app/admin/DevicePolicyManager "DevicePolicyManager — Android Developers"
[2]: https://github.com/RikkaApps/Shizuku-API "Shizuku API developer guide"
