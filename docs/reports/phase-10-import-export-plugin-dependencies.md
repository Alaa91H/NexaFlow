# المرحلة 10 — الاستيراد والتصدير وتبعيات الإضافات

أضيف `PluginDependencyScanner` إلى طبقة backup. يستخرج من `PLUGIN_FIRE` فقط metadata مراجعة غير سرية: workflow وpackage وreceiver وedit activity والبروتوكول ومصدر action، إضافةً إلى مؤشر `requiresReconfiguration` للإدخالات التراثية التي لا تملك `pluginInstance` وموافقة صريحة.

| boundary | السلوك |
|---|---|
| export | يضاف `pluginDependencies` المشتق إلى `BackupFile` مع بقاء config الأصلي في action فقط |
| import preflight | يعيد validator فحص workflow ثم يعيد بناء dependency index من actions، ولا يثق بالـ metadata القادمة |
| import save | يبقى automation المستورد disabled حتى مراجعة المستخدم كما في السلوك السابق |
| secrets / payload | لا ينسخ scanner `bundleJson` أو Vault reference أو data runtime payload إلى index |

حقل `pluginDependencies` له default فارغ، لذا تقبل النسخ الاحتياطية الأقدم من دون migration. ولا يغيّر هذا التحديث نسخة backup لأن metadata اختيارية ومشتقة؛ أي حزمة جديدة تستطيع أيضاً حذفها بأمان، إذ يعيد NexaFlow حسابها عند preflight.

يشمل الاختبار الجديد حالة run/exit plugin actions وحالة action ناقصة لا تنتج dependency كاذبة.
