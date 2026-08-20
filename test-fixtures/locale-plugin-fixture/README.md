# Locale Plugin Test Fixture

هذه وحدة اختبار فقط تنتج APK خارجيًا بالحزمة `com.nexaflow.testfixture.locale`. وهي غير مرتبطة بوحدة `:app` ولا يجوز إضافتها كاعتماد إنتاجي. الغرض الوحيد منها هو توفير مكونات Locale متوافقة وحتمية لاختبار الاكتشاف والاستدعاء وحالات النتيجة وcondition protocol على Android فعلي.

> لا تمثل هذه الوحدة capability للمستخدم، ولا تنفذ أي أمر نظام أو عملية مميزة أو تعديل ملفات أو إعدادات. جميع آثارها تقتصر على نتيجة ordered broadcast في ذاكرة العملية.

## مسارات البروتوكول المتاحة

| المكوّن | Android intent | السلوك الحتمي |
|---|---|---|
| `LocalePluginEditActivity` | `EDIT_SETTING` | يعيد bundle primitive بحالة `success` وblurb. |
| `LocalePluginFireReceiver` | `FIRE_SETTING` | يعيد `success` أو `failure` أو `pending` أو `cancelled` أو تأخير `timeout` حسب `fixtureOutcome`. |
| `LocalePluginConditionEditActivity` | `EDIT_CONDITION` | يعيد bundle primitive بحالة condition `satisfied`. |
| `LocalePluginConditionReceiver` | `QUERY_CONDITION` | يعيد `satisfied` أو `unsatisfied` أو `unknown` حسب `fixtureConditionState`. |

## بروتوكول التحقق الخارجي

يُنفذ هذا البروتوكول فقط على بيئة Android فيها SDK و`adb` وجهاز/محاكي متصل؛ لا يُنفذ في بيئة لا تحتوي جهازاً. يبنى APK ويثبت قبل تشغيل class اختبار NexaFlow المقابل:

```bash
./gradlew :test-fixtures:locale-plugin-fixture:assembleDebug
adb install -r test-fixtures/locale-plugin-fixture/build/outputs/apk/debug/locale-plugin-fixture-debug.apk
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexaflow.app.validation.LocalePluginFixtureAndroidTest
```

تحفظ نتيجة JUnit XML و`adb logcat` المقصوص ومعرّف الجهاز/API. إن كان APK غير مثبت، يعرض الاختبار `MISSING_DEPENDENCY` عبر skip؛ **لا يعد ذلك نجاحاً ولا دليلاً على Android integration**.

## حدود الدليل

نجاح `LocalePluginFixtureAndroidTest` يثبت package discovery وexplicit ordered-broadcast transport ونقل نتائج البروتوكول بين APKين فقط. ولا يثبت بمفرده:

| غير مثبت بهذه الوحدة | سبب الحاجة لاختبار منفصل |
|---|---|
| `PluginCapabilityBackend` الكامل | يتطلب workflow محفوظاً وrepository وapproval/configuration مرتبطين بالفعل. |
| verification خارجي للفعل | بروتوكول Locale الأساسي لا يعطي post-condition مستقلاً؛ fixture لا ينفذ side effect عمداً. |
| Plugin events عبر `PluginEventReceiver` | يتطلب Android API 34+ وهوية مرسل broadcast فعلية وربط receiver الديناميكي. |
| process unavailable من عملية plugin حقيقية | يتطلب تثبيت fixture وتشغيل سيناريو force-stop مضبوط على جهاز. |
| سلوك OEM أو الخلفية | يحتاج جهازاً مادياً وartefacts منفصلة. |
