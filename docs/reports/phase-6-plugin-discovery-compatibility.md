# المرحلة 6 — سجل الإضافات والتوافق البروتوكولي

## النتيجة

أضيف سجل `PluginDiscoveryRegistry` بوصفه المصدر الواحد لاكتشاف مكونات Locale/Tasker العامة. لا ينفذ السجل plugin ولا يحمل كوده ولا يمرر Context أو Bundle خارج طبقة Android. ينتج فقط `PluginDescriptor` محدوداً يصف package والمكونات والنوع والبروتوكول والتوافق والثقة الافتراضية.

| المسار | السلوك المنفذ |
|---|---|
| Locale setting | يطابق `EDIT_SETTING` مع receiver واحد فقط لـ `FIRE_SETTING` داخل package نفسها |
| Locale condition | يطابق `EDIT_CONDITION` مع receiver واحد فقط لـ `QUERY_CONDITION` |
| Tasker event | يكتشف edit action العام ويعلن `PARTIALLY_COMPATIBLE` حتى تكتمل طبقة correlation/EventBus، ولا يشغّل workflow |
| component check | يقرأ exported/enabled/application state وقدرة المضيف على إرسال intent والتثبيت في التخزين الداخلي |
| نتائج ناقصة | `MISSING_EDIT_ACTIVITY` أو `MISSING_RECEIVER` أو `AMBIGUOUS_RECEIVER` أو `DISABLED` أو `PERMISSION_DENIED` بدلاً من إدراج plugin كأنه قابل للتشغيل |
| cache | snapshot immutable محدود بـ 256 entry افتراضياً؛ يعاد الفحص فقط عند refresh صريح أو بعد invalidate من PackageMonitor الموجود |

## التكامل

أصبح `PluginDiscoveryRegistry` singleton في `AppModule`. يستخدمه `PluginRepositoryImpl` للحفاظ على `PluginInfo` وواجهة picker القديمة، لكن لا يعرض للمستخدم إلا setting plugins المتوافقة فعلياً. ويعيد `PackageMonitor` الموجود إبطال cache عند install/remove/update؛ لا يوجد polling أو receiver إضافي.

حمل `PluginInfo` حقل `editActivityClass` اختيارياً ومتوافقاً خلفياً. يستخدم builder هذا الحقل لإنشاء `ComponentName` صريح عند فتح `EDIT_SETTING` وحفظه داخل config. وبذلك لا يعتمد إعداد plugin جديد على `package` فقط عند وجود عدة edit activities. أما entries القديمة التي لا تحمل edit activity فتبقى قابلة للتنفيذ، ويمكن محاولة إعادة تهيئتها بالـ package-scoped legacy path فقط.

## قبول نتيجة الإعداد

لا يحفظ builder إعداداً جديداً إلا إذا عادت activity بـ `RESULT_OK` و`EXTRA_BUNDLE` وblurb غير فارغ. تظل bundle مقيدة بـ parser الموجود وحجم Locale (25KB) وتتحول إلى JSON قابل للحفظ؛ أما أسرار plugin فلا تدخل هذا المسار.

## اختبارات مضافة

- `PluginModelsTest`: الثقة الافتراضية، اتساق components، حدود policy، وحظر manifest بلا بروتوكول أو بقدرات مكررة.
- `PluginDiscoveryRegistryTest`: pairing لـ Locale setting ووصف activity بلا receiver كـ metadata غير متاح.
- توسيع `CapabilityRuntimeTest`: قبول `OPAQUE_REFERENCE` فقط لـ `PLUGIN_ACTION` ورفض JSON/Bundle كنص parameter.

> لم يُعلن بعد دعم event invocation أو output variables أو condition execution في مسار الإنتاج؛ تُعرض كحالة غير مكتملة إلى أن تربطها adapters بالـ EventBus وCapabilityBackend في المرحلة التالية.
