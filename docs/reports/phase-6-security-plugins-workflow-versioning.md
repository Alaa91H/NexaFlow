# تقرير المرحلة 6 — Vault references وRedaction والإضافات وإصدار Workflow

**الحالة:** مكتملة ومتحقق منها.  
**المبدأ:** استُخدمت `SecureStorage` و`LogStore` و`Plugin SDK` و`Automation` وRoom الموجودة؛ لم يُنشأ مخزن أسرار أو سجل Plugins أو قاعدة Workflows منفصلة.

## Vault والمراجع

أضيف `SecretReference` في domain كمؤشر opaque متسلسل يحمل المفتاح والغرض فقط. يتحقق من الصيغة والطول ولا يحتوي قيمة سرية. تستطيع تعريفات workflow أو export أو plugin bundle حمل المرجع، لا secret نفسه. وأضيف `SecretVault` فوق `SecureStorage`: يحفظ ويحُل ويحذف بالمرجع بعد prefix داخلي `vault:`، ولا يقدم enumeration أو export أو logging للقيم. يقيد حجم السر إلى 16KB وصيغة المفتاح إلى مجموعة ثابتة آمنة.

يبقى `VariableRepositoryImpl` مصدر متغيرات GLOBAL الحساسة الحالي ويستعمل Keystore عبر `SecureStorage`؛ لا يعيد Vault كتابة مخزن المتغيرات أو تغيير `%NAME`. يمثل Vault مسار الأسرار الصريح للإجراءات/الإضافات الجديدة، بينما تستمر القيم الحساسة القديمة في قاعدة التوافق حتى تُرحل باستعمال reference واعٍ بالواجهة.

## تنقية السجلات

أضيف `SecretRedactor` و`RedactingLogStore`. ينقي الغلاف المطبق في AppModule timeline/error logs قبل وصولها إلى `InMemoryLogStore` أو أي store لاحق. يغطي Bearer tokens ومفاتيح query الشائعة (`token`, `apiKey`, `password`, `secret`, `authorization`) وassignment text وJSON keys وVault keys. تميل القاعدة إلى الحجب عند الشك، وتحافظ على id/source/timestamps وقياسات الأداء. لا تعدل `LogStore` الأصلية، لذا يعمل الغلاف مع أي implementation Room/DataStore لاحق.

## الإضافات

أضيف `PluginManifest` و`PluginManifestValidator` فوق Locale SDK القائم. يعلن manifest package/receiver/label وprotocol version وminimum host version وaction IDs ثابتة. لا يحمل manifest code أو permission grant أو command أو secret. يتحقق validator من package/class name وlabel وprotocol المتوافق وحد أقصى 128 action ID وصيغة action ID. تظل الإضافة تنفذ عبر Locale receiver/protocol القائم؛ لا يوجد dynamic loading أو تنزيل dependency أو تنفيذ shell.

## إصدار Workflow

أضيف `workflowVersion` إلى `Automation` و`AutomationEntity` وmapper مع default إصدار 1. ارتفع Room إلى v15 مع migration 14→15 تضيف العمود بقيمة 1 فقط؛ لا تغير migration actions أو triggers أو constraints أو exit JSON. يتيح ذلك إقرار migrations صريحة عند رفع schema مستقبلاً بدلاً من تخمين تعريف workflow من حقوله.

| الحد | السلوك |
|---|---|
| Workflow JSON/Export | يحمل `workflowVersion` لا السر. |
| Secret في config/log | reference أو `[REDACTED]`، لا plaintext. |
| Plugin | manifest metadata وتحقق protocol فقط. |
| متغير حساس قديم | يبقى في `SecureStorage` الحالي للتوافق. |
| Room 14→15 | workflowVersion = 1، تعريف المهمة بلا تغيير. |

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `SecretVaultTest` | ناجح: store/resolve/delete والمفتاح غير الآمن مرفوض. |
| `RedactingLogStoreTest` | ناجح: secret لا يظهر في timeline/message/stacktrace ويظهر `[REDACTED]`. |
| `PluginManifestTest` | ناجح: manifest Locale متوافق يقبل، والـ protocol/action id غير الآمن يرفض. |
| `MigrationTest` | ناجح: migration 14→15 يحفظ automation ويضيف workflowVersion=1؛ وتشمل السلسلة الاختبارات السابقة. |
| أمر البوابة | security + logging + plugin-sdk + database MigrationTest معاد التشغيل بـ `--rerun-tasks --no-parallel --max-workers=1` ناجح في 24 ثانية. |

## القيود المقصودة

لا تتحول القيم الحساسة القديمة تلقائياً إلى `SecretReference` لأن ذلك يحتاج اختيار المستخدم ومسار UI قابل للمراجعة. ولا يسمح manifest للإضافة بإعلان code أو طلب permission أو تنفيذ capability مباشرة. كما لا تعالج redaction كل secret محتمل رياضياً؛ لذلك تظل القاعدة الأساسية أن لا يمر plaintext إلى log API أصلاً، والغلاف دفاع ثانٍ.

## الخطوة التالية

تنتقل الخطة إلى **المرحلة 7: validation والاستيراد والتصدير وواجهة debug وdry run والمراقبة**. ستضيف preflight موحداً وdry-run يستعمل capability resolver نفسه، وعقود import/export versioned وtelemetry/status قابلة للعرض، ثم تختم ببوابة الجودة والرفع والإصدار.
