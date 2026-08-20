# Android Connected-Test Execution Protocol

هذا البروتوكول يصف تشغيل مصادر `app/src/androidTest` التي أضيفت في مرحلة التحقق الواقعي. لا يعد وجود أي class دليلاً على Android integration؛ لا تُسجل نتيجة إلا بعد تنفيذها على هدف متصل وحفظ artifact الناتج.

> البيئة التي أُعدّت فيها المصادر لا تملك Android SDK أو `adb` أو جهازاً متصلاً. لذلك لم تُشغّل الأوامر أدناه في هذه المرحلة، ولا ترقية لأي تصنيف نتجت عنها.

## الاختبارات والمتطلبات

| Class | ما يلمسه فعلياً | متطلبات التشغيل | أثر التحقق | لا يثبت |
|---|---|---|---|---|
| `WorkflowRunContextAndroidTest` | `WorkflowRunContext` المنتج | جهاز API 26+ | JSONPath/budget/snapshot داخل process التطبيق | فعل Android خارجي. |
| `ActiveExecutionStoreAndroidTest` | Preferences DataStore المنتج | جهاز API 26+ | قراءة checkpoint عبر facade جديد، claim مرة واحدة، تنظيف | قتل عملية Android حقيقي. |
| `ExecutionRecoveryCoordinatorAndroidTest` | checkpoint + recovery coordinator | جهاز API 26+ | `ACTION_UNKNOWN` → `VERIFY_OR_COMPENSATE_REQUIRED` وعدم blind replay | نقطة قتل قبل/أثناء/بعد فعل خارجي. |
| `WorkflowInterpreterAndroidTest` | interpreter المنتج | جهاز API 26+ | retry/timeout/cancellation تعاوني | تنفيذ action Android خارجي. |
| `TaskManagerResourceAndroidTest` | `TaskManager` وsemantics `withPermit` | جهاز API 26+ | تحرير `FILE_IO` permit بعد cancellation | قياس استخدام CPU/بطارية أو resource OS. |
| `ReminderSchedulerAndroidTest` | `AlarmManager` وPendingIntent المنتج | جهاز، وسياسة alarms تسمح بالتسجيل | PendingIntent قابل للاسترداد ثم الإلغاء | delivery مستقبلي/reboot/OEM idle. |
| `NotificationAndroidTest` | receiver + NotificationManager | notifications enabled وgrant عند API 33+ | posted notification read-back/actions | وصول من alarm مستقبلي أو interaction full engine. |
| `PluginEventIngressAndroidTest` | TriggerIndex + ingress + EventBus المنتج | جهاز API 26+ | approval/canonical payload/dedup داخل process | identity من APK plugin أو dynamic receiver API 34+. |
| `TriggerIndexAndroidTest` | TriggerIndex المنتج | جهاز API 26+ | rebuild وenabled filtering من Flow | Room source أو Android broadcast delivery. |
| `LocalePluginFixtureAndroidTest` | PackageManager discovery + ordered broadcast بين APKين | fixture APK مثبت | discovery, success/failure/pending/cancel/timeout, condition states | `PluginCapabilityBackend` workflow-bound resolution أو side-effect verification. |

## أوامر التنفيذ المقيدة

بعد توفير Android SDK وهدف متصل، تبدأ بنظافة بيانات التطبيق أو profile مخصص للتحقق:

```bash
adb devices -l
./gradlew :app:connectedDebugAndroidTest --no-parallel --max-workers=1 --console=plain
```

اختبار fixture الخارجي له شرط إضافي موثق في `test-fixtures/locale-plugin-fixture/README.md`. يمكن تشغيله منفصلاً بعد تثبيت fixture:

```bash
./gradlew :test-fixtures:locale-plugin-fixture:assembleDebug
adb install -r test-fixtures/locale-plugin-fixture/build/outputs/apk/debug/locale-plugin-fixture-debug.apk
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexaflow.app.validation.LocalePluginFixtureAndroidTest
```

لا تُشغّل الاختبارات التي تمس privileged/OEM state مع حساب مستخدم أو بيانات فعلية. Fixture لا ينفذ side effect، واختبار reminder يلغي الـ PendingIntent الذي أنشأه، واختبار notification يلغي إشعار validation في `@After`.

## artefact المطلوب لكل تشغيل

| الحقل | مثال أو قاعدة |
|---|---|
| أمر التنفيذ | Gradle task مع class filter إن استعمل. |
| البيئة | ADB serial مستعار، الشركة والطراز، Android version/API، ABI، emulator أو physical. |
| المتطلبات | notification state، exact-alarm access، fixture version، Root/Shizuku/Accessibility إن انطبق. |
| النتيجة | JUnit XML مع أعداد passed/failed/skipped. |
| سجل مختصر | `adb logcat` مقصوص حول وقت الاختبار فقط، بعد تنقية المعرفات والأسرار. |
| الأثر الخارجي | لقطة أو read-back مثل `activeNotifications`، discovery descriptor أو package state، من دون payload حساس. |
| الحكم | `ANDROID_INTEGRATION_TESTED` فقط عند pass فعلي؛ `MISSING_DEPENDENCY` أو `ENVIRONMENT_UNAVAILABLE` عند غياب المتطلبات؛ `IMPLEMENTATION_FAILURE` فقط إذا فشل المسار مع وجودها. |

## ما لا ينفذ تلقائياً

لا توجد هنا بوابة آلية لقياس البطارية أو الأداء أو سلوك OEM أو Root أو Shizuku أو Accessibility أو delivery بعد reboot/process kill. هذه تتطلب خطة جهاز مادي منفصلة وartefacts محددة، ولا تستبدلها الاختبارات المتصلة الآمنة أعلاه.
