# المرحلة 7 — التحقق والاستيراد وDry Run

**الحالة:** مكتملة محلياً ومجتازة لبوابة الجودة الجزئية.  
**النطاق:** `domain` و`data` و`core:execution` و`feature:settings` وقاعدة Room.

## الهدف

تجعل هذه المرحلة كل workflow يمر عبر **تحقق بنيوي موحد قبل أي حفظ أو استيراد أو محاكاة**. كما تضيف مسار dry-run لا ينفذ actions ولا يكتب history أو checkpoints أو variables، لكنه يتحقق من صلاحية تعريف workflow ومن قابلية حل capabilities المعلنة على الجهاز الحالي.

> **مبدأ التنفيذ:** لا يُعد workflow قابلاً للتشغيل لمجرد إمكان فك JSON. يجب أن يجتاز تعريفه وإصدارُه وحدود بياناته ثم يعتمد مسارات القدرات التي سيستخدمها.

## ما أُضيف

| المكوّن | المسؤولية | الضمان الرئيسي |
|---|---|---|
| `WorkflowValidator` | تحقق نقي من الإصدار والمعرفات وcooldown وحجم config | لا Android context ولا I/O ولا side effect |
| `WorkflowValidationResult` | يجمّع أخطاء التحذير/الرفض برموز ثابتة ومسارات واضحة | قابل للعرض في UI أو الاستيراد أو الاختبارات |
| `BackupPreflight` | نتيجة تحقق مهيكلة قبل استيراد النسخة الاحتياطية | لا يتم حفظ workflow غير صالح |
| `ImportResult.InvalidWorkflow` | يعيد سبب الرفض بدلاً من فشل عام أو استثناء صامت | شاشة الإعدادات تشرح الرفض للمستخدم |
| `BackupManager.preflight()` | يفك ويحلل النسخة في الذاكرة ثم يتحقق من كل automation | لا mutation قبل اكتمال preflight |
| `WorkflowDryRunService` | محاكاة قابلة لإعادة الإنتاج تستعمل `WorkflowValidator` و`CapabilityResolver` نفسه | لا action execution ولا تعديل حالة runtime |
| `WorkflowDryRunResult` | يفصل أخطاء التعريف عن capability غير المتاحة وتحذيرات الجهاز | نتيجة صالحة للواجهة أو diagnostics |

## قواعد WorkflowValidator

يتحقق الـ validator من أن الإصدار يطابق إصدار workflow المدعوم، وأن المعرّف والاسم غير فارغين وضمن الحدود، وأن cooldown وحقول scheduling منطقية، وأن action config لا تتجاوز الحدود الآمنة. يطبق ذلك على **الـ domain model بعد فك البيانات**؛ أما JSON غير القابل للفك أو الإصدار المخالف لشرط بناء `Automation` فيُرفض في طبقة decoding/preflight قبل الوصول إلى workflow قابل للإنشاء.

| القيد | المعالجة |
|---|---|
| إصدار workflow غير مدعوم | رفض عند parsing/إنشاء النموذج أو إبلاغ validator عندما تتوفر البنية |
| معرّف/اسم فارغ أو غير صالح | `WorkflowValidationIssue` قابل للعرض |
| cooldown/قيم جدولة غير منطقية | رفض قبل الحفظ أو التشغيل |
| قيمة config طويلة جداً | `CONFIG_VALUE_TOO_LONG`؛ لا تمر إلى backend أو export |
| capability غير قابلة للحل | خطأ dry-run واضح؛ لا محاولة تنفيذ |

## مسار الاستيراد والتصدير

`BackupManager` يحتفظ بمسار تصدير متوافق ونسخي. عند الاستيراد، يُفك payload أولاً، ثم يُشغل `preflight()` على كل automation. إذا ظهرت مشكلة بنيوية يعيد `ImportResult.InvalidWorkflow` مع المعرّف وقائمة القضايا، ولا يكتب أي workflow. تعرض `SettingsScreen` نتيجة الرفض بدلاً من رسالة نجاح أو انهيار عام.

لا يحمل النسخ الاحتياطي أي قيمة Vault plaintext؛ تكامل المرحلة السادسة يبقي `SecretReference` مرجعاً opaque، بينما تستمر طبقة تنقية السجلات في حماية diagnostics من التسرب العرضي.

## Dry Run

`WorkflowDryRunService` لا يستعمل محرك التنفيذ ولا يطلق intent ولا يكتب DataStore/Room. ترتيبه هو:

1. التحقق البنيوي بواسطة `WorkflowValidator`.
2. تحليل/حل كل capability معلنة عبر `CapabilityResolver` نفسه المستخدم في التنفيذ الحقيقي.
3. الاستعلام المقيد عن `deviceStateProvider` عند الحاجة لصياغة تحذير فقط.
4. إعادة تقرير بالـ validation issues والقدرات القابلة/غير القابلة للحل، من دون side effects.

هذا يجعل dry-run بوابة تخطيط وتشخيص لا بديلاً عن تحقق runtime: حالة الجهاز أو permission يمكن أن تتغير بين المحاكاة والتشغيل، ولذلك يظل `CapabilityExecutionService` مسؤولاً عن validation وtimeout وretry وverification لحظة التنفيذ.

## الاختبارات والنتائج

| الفحص | النتيجة |
|---|---|
| `git diff --check` | ناجح. |
| `WorkflowValidatorTest` | ناجح: workflow محدود وصالح يقبل؛ قيمة config المفرطة ترفض برمز ثابت. |
| `WorkflowDryRunServiceTest` | ناجح: preflight بلا side effects ومسارات capability غير المتاحة مرئية. |
| `BackupManagerTest` | ناجح: النسخة غير الصالحة تعيد `InvalidWorkflow` ولا تُستورد. |
| `MigrationTest` | ناجح: سلسلة Room تشمل schema v15 و`workflowVersion`. |
| `:feature:settings:compileDebugKotlin` | ناجح: معالجة نتيجة الاستيراد تتكامل مع Compose/Hilt. |
| بوابة المرحلة | الأمر المعاد بـ `--rerun-tasks --no-parallel --max-workers=1` نجح في **41 ثانية** مع **240 مهمة Gradle**. |

## الحدود المقصودة

لا يحاول dry-run محاكاة التأثيرات الخارجية أو تخمين نتائج intent أو تجاوز permissions. ولا يستبدل preflight التحقق لحظة التنفيذ، ولا يغير WorkflowValidator محتوى workflow تلقائياً؛ التصحيح مسؤولية builder/import UX صريحين. كذلك لا يُستورد أي سر إلى النص الصريح، ولا يسمح مسار التحقق بتجاوز `SecretVault` أو redaction.

## الخطوة التالية

تنتقل الخطة إلى **بوابة الجودة الشاملة والإصدار**: مراجعة diff، تنفيذ `testDebugUnitTest` على المشروع، بناء التطبيق، مراجعة ملفات Room schema، ثم commit ورفع إلى `main` ومتابعة Android CI قبل إنشاء وسم الإصدار التالي.
