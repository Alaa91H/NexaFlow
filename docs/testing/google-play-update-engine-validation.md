# تحقق محرك تحديثات Google Play المتدرج

**تاريخ التحقق:** 20 أغسطس 2026
**نسخة الدليل:** قبل إنشاء إصدار جديد
**نطاق التنفيذ:** Action باسم `SYSTEM_UPDATE_GOOGLE_PLAY_APPS` يتحقق من إمكانات الجهاز ومسار التحديث الرسمي من دون فتح واجهة Google Play أو تنزيل APK أو محاكاة النقر.

> **النتيجة التنفيذية:** المحرك لا يدّعي تنفيذ تحديث عندما يكون اكتشاف المصدر الرسمي غير مكشوف. على هاتف شخصي، مع أو بدون Root أو Shizuku، يسجل نتيجة `SKIPPED` تشرح أن اكتشاف تحديثات Google Play غير متاح للتطبيق. المسار المؤسسي فقط يُميّز كمسار سياسة Managed Google Play، ولا يبدأ من NexaFlow ما لم يوجد تكامل EMM فعلي.

## المكوّنات المنفذة

| المكوّن | المسار | الدور | حالة التحقق |
|---|---|---|---|
| عقد الطلب والقرار | `domain/.../updates/GooglePlayUpdateModels.kt` | إعدادات آمنة، بيئة قدرات، قرار لا يرقّي Root/Shizuku إلى مصدر Play | UNIT_TESTED |
| Action جديدة | `Automation.ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS` | Action منفصلة عن `SYSTEM_OPEN_PLAY_UPDATES` | UNIT_TESTED |
| التنفيذ | `SystemController.updateGooglePlayApps()` | يكشف Device Owner/Profile Owner وRoot وShizuku؛ لا يطلق Intent ولا ينزّل حزمة | UNIT_TESTED_ONLY عبر ترجمة الوحدة واختبار طبقة القرار |
| المعالج | `SystemActionsHandler` | يمرر الإعدادات إلى المتحكم | UNIT_TESTED |
| التوافق والواجهة | CommandCatalog، خيار Apps، روتينات، عرض التفاصيل و11 لغة | ظهور منضبط في المحرر والسجل | UNIT_TESTED |
| سجل التنفيذ | `ExecutionRecord` القائم | يسجل رسالة Action ومدة التنفيذ وقناة التنفيذ في السجل القائم | CONTRACT_REUSED |

## بروتوكول الدليل

| القدرة | البيئة | الدليل | الملاحظة | النتيجة |
|---|---|---|---|---|
| الإعدادات الآمنة | JVM | `GooglePlayUpdateModelsTest.default request is conservative` | `dryRun=true` و`requireSilentInstall=true` وWi‑Fi/charging فقط | UNIT_TESTED |
| مقاومة إعدادات config الضارة | JVM | `GooglePlayUpdateModelsTest.config parsing...` | القيم العددية مقيدة، والقيم المنطقية غير الصحيحة تعود للوضع الآمن | UNIT_TESTED |
| Root/Shizuku لا يثبتان مصدر Play | JVM | `GooglePlayUpdateModelsTest.root and shizuku...` | القرار `PLAY_DISCOVERY_NOT_EXPOSED` | UNIT_TESTED |
| قرار السياسة المُدارة | JVM | `GooglePlayUpdateModelsTest.only a managed policy...` | لا يتغير القرار إلا مع قناة سياسة EMM مثبتة | UNIT_TESTED |
| ملكية المعالج | JVM | `SystemActionsHandlerExpansionTest` | Action تملكها طبقة SystemActionsHandler | UNIT_TESTED |
| حفظ إعدادات بطاقة المحرر | JVM/Compose | `ActionDraftTest.googlePlayUpdateDraft...` | القيم الآمنة تحفظ عند الإضافة | UNIT_TESTED |
| ظهور الروتين | JVM/Compose | `AutomationOptionCatalogTest.recurringRoutineCategory...` | يظهر قبل فتح صفحة Play ويحفظ فئة Apps | UNIT_TESTED |
| توازن الموارد | محلي | `python3 scripts/auto_fix.py --check` | مفاتيح العرض متاحة في 11 لغة بكل وحدة | STATIC_CHECKED |

## الاختبار المحلي المنفذ

نفّذ الأمر التالي بنجاح:

```text
./gradlew :domain:testDebugUnitTest --tests com.nexaflow.domain.updates.GooglePlayUpdateModelsTest \
  :feature:automation-builder:testDebugUnitTest --tests com.nexaflow.feature.builder.ActionDraftTest \
  --tests com.nexaflow.feature.builder.AutomationOptionCatalogTest \
  :core:execution:testDebugUnitTest --tests com.nexaflow.core.execution.handler.SystemActionsHandlerExpansionTest \
  :core:rom-integration:testDebugUnitTest
```

## ما لم يُدّعَ اختباره

لم يُنفذ اختبار قبول على جهاز فعلي لتحديث تطبيقات Google Play، لأن NexaFlow لا يملك مصدراً رسمياً مكشوفاً لتنزيل تلك الحزم أو سياسة EMM متصلة في بيئة الاختبار. لا يمكن لتجميع ناجح أو اختبار وحدة أن يحل محل هذا الاختبار، ولا يرفع حالة التحديث الفعلي إلى `REAL_DEVICE_VERIFIED`.

ولتنفيذ اختبار جهاز حقيقي في المستقبل، يلزم أحد الشرطين الآتيين: إما مؤسسة Android Enterprise حقيقية مع جهاز مسجّل وسياسة Managed Google Play، أو مصدر APK رسمي محدد ومفوض لكل حزمة مع اختبار توقيع/splits منفصل. لا يكفي Root أو Shizuku وحدهما لتلبية شرط مصدر Google Play.

## الضمانات الأمنية

لا يستخرج التنفيذ حساب Google أو رمز Play Store أو ملفات تعريف الارتباط. ولا يفتح صفحة متجر أو صفحة تحديث أو صفحة تطبيق، ولا يستخدم Accessibility أو API خاصة أو موقع APK خارجي. ويرفض ضمنياً الانتقال إلى تنزيل/تثبيت عند غياب مصدر رسمي قابل للوصول.
