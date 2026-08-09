# NexaFlow — خطة التحسين الشاملة (Improvement Plan)

> مصدر الخطة: ثلاث جولات بحثية موسّعة (2025–2026) عبر 22 تقريراً بحثياً + فحص مجهري
> للكود. كل بند يذكر: الفجوة، الأثر، الجهد، والمرجع.

---

## مصفوفة الأولويات الكلية

| الأولوية | البند | المحور | الحالة |
|---|---|---|---|
| 🔴 P0-1 | WorkManager للصيانة الدورية (تنظيف سجل التنفيذ/النسخ الاحتياطي) | موثوقية | ✅ |
| 🔴 P0-2 | Sentry لرصد الأعطال والـ ANR (تفعيل اختياري) | جودة | ✅ |
| 🔴 P0-3 | Baseline Profiles + Macrobenchmark | أداء | ✅ |
| 🔴 P0-4 | تشفير الأسرار (Keystore) للمتغيرات الحساسة | أمان | ✅ |
| 🔴 P0-5 | قواعد R8 لنماذج Gson + اختبار استيراد في release | موثوقية | ✅ (تحقق) |
| 🟠 P1-1 | ACCESS_LOCAL_NETWORK لـ targetSdk 37 + شاشة شرح | توافق | ✅ |
| 🟠 P1-2 | liveRegion بدل announceForAccessibility | توافق API 36 | ✅ (تحقق) |
| 🟠 P1-3 | فحص insets في كل الشاشات (edge-to-edge) | واجهة | ✅ (تحقق) |
| 🟠 P1-4 | إعادة جدولة المنبّهات عند TIME_SET/TIMEZONE_CHANGED | دقة | ✅ |
| 🟠 P1-5 | مستقبلات TIME_SET/TIMEZONE_CHANGED/OFFSET + اختبارات DST | دقة | ✅ |
| 🟠 P1-6 | تدقيق المكوّنات المصدّرة (تحقق الحمولة + exported=false) | أمان | ✅ (تحقق) |
| 🟡 P2-1 | RemoteInput — ردود نصية من الإشعار إلى متغيرات %var | ميزة | ✅ |
| 🟡 P2-2 | مشغّلات استشعارات (قرب/اهتزاز/ضوء/خطوات) | ميزة | ✅ |
| 🟡 P2-3 | تحكم MediaSession (تشغيل/إيقاف/تخطي) | ميزة | ✅ (تحقق) |
| 🟡 P2-4 | Webhook/HTTP server محلي كـ trigger | ميزة | ✅ |
| 🟡 P2-5 | روابط عميقة + Shortcuts ديناميكية لتشغيل المهام | ميزة | ✅ |
| 🟡 P2-6 | فحص تحديثات GitHub داخل التطبيق + SHA-256 + PackageInstaller | نشر | ✅ |
| 🟡 P2-7 | Dynamic Color + تحسين الودجات المتكيفة | واجهة | ✅ |
| 🟡 P2-8 | Version Catalog + Configuration Cache + Detekt | بناء | ✅ |
| 🟡 P2-9 | notificationTimeout 250-500ms + تصفية أحداث accessibility | بطارية | ✅ |
| 🟡 P2-10 | Gson → kotlinx.serialization في BackupManager | أداء | ✅ |
| 🟡 P2-11 | rememberSaveable لمسودة المحرر | واجهة | ✅ |

---

## 🔴 P0 — موثوقية واستقرار

### P0-1: WorkManager للصيانة الدورية
- **الفجوة**: لا يوجد أي استخدام لـ WorkManager (0 نتائج). التنظيف الدوري لسجل
  التنفيذ (احتفاظ 60 يوماً) والنسخ الاحتياطي يُداران ضمن الخدمة فقط — إذا قُتلت
  الخدمة تتوقف الصيانة.
- **الحل**: `PeriodicWorkRequest` يومي ينفّذ التنظيف والنسخ، باحترام Doze/battery
  saver، مع `ExistingPeriodicWorkPolicy.KEEP`.
- **المصدر**: Android Developers — WorkManager basics.

### P0-2: Sentry لرصد الأعطال والـ ANR
- **الفجوة**: لا يوجد أي crash reporting. تطبيق بإجراءات امتيازية (root/Shizuku/
  accessibility) بدون رؤية للأعطال.
- **الحل**: `io.sentry:sentry-android`، تفعيل اختياري (خصوصية) عبر إعداد،
  التقاط ANR عبر `ApplicationExitInfo`.
- **المصدر**: Sentry docs — ANR tracking.

### P0-3: Baseline Profiles + Macrobenchmark
- **الفجوة**: لا يوجد. R8 فقط بدون AOT للتدفقات الحرجة.
- **الحل**: موديول `:baseline-profile` يولّد `baseline-prof.txt` من تدفقات
  (الرئيسية → المحرر → تنفيذ)، وقياس عبر Macrobenchmark.
- **المصدر**: Android Developers — Baseline Profiles.

### P0-4: تشفير الأسرار
- **الفجوة**: لا يوجد Keystore/EncryptedSharedPreferences؛ المتغيرات العامة
  والرؤوس الحساسة نص عادي.
- **الحل**: تشفير قيم المتغيرات الحساسة عبر Keystore (AES-GCM) — ملاحظة:
  `EncryptedSharedPreferences` أُهملت 2026 (keyset corruption) — استخدم Keystore
  مباشرة أو security-crypto بحذر.
- **المصدر**: ProAndroidDev — Goodbye EncryptedSharedPreferences (2026).

### P0-5: قواعد R8 لنماذج Gson
- **الفجوة**: `BackupManager` يستخدم Gson انعكاسياً، وrelease مفعّل R8 —
  خطر انهيار الاستيراد في الإصدار المُصغَّر («يعمل في debug ويفشل في release»).
- **الحل**: keep rules لنماذج النسخ + اختبار دورة تصدير/استيراد على release.
- **المصدر**: Gson Troubleshooting / R8 FAQ.

---

## 🟠 P1 — توافق Android 16/17 وأمان

### P1-1: ACCESS_LOCAL_NETWORK (targetSdk 37)
- إجراء HTTP الحالي يتصل بجهاز LAN؛ على Android 17 الإذن إلزامي.
- شاشة شرح سامسونغ في مركز القدرات + طلب الإذن.

### P1-2: liveRegion بدل announceForAccessibility
- API 36 يحظر الإعلانات الصوتية المزعجة؛ استبدالها بـ `Modifier.semantics
  { liveRegion = LiveRegionMode.Polite }`.

### P1-3: فحص insets لكل الشاشات
- edge-to-edge إلزامي على API 36+. تحقق `Scaffold`/`WindowInsets` في كل شاشة.

### P1-4/P1-5: مستقبلات تغيّر الوقت + اختبارات DST
- لا يوجد `ACTION_TIME_SET`/`TIMEZONE_CHANGED` (مؤكد بالفحص: 0 نتائج).
- مستقبِل واحد يعيد حساب `TimeTriggerCalculator` لجميع المهام عند تغيّر
  المنطقة الزمنية أو الوقت، + `ACTION_TIMEZONE_OFFSET_CHANGED` (Android 17).
- الجدولة نفسها سليمة (ZonedDateTime) — ينقص فقط إعادة الجدولة عند التغيّر.
- اختبارات Robolectric تحاكي spring-forward/fall-back عبر `TimeZone.setDefault`.

### P1-6: تدقيق المكوّنات المصدّرة
- 6 مكوّنات exported في المانيفست الرئيسي.
- تحقق صارم من `action` أولاً، قراءة extras بطرق آمنة (try-catch، نوع-check)،
  وجعل المستقبلات الداخلية `exported=false` (PendingIntent داخلي لا يحتاج export).
- **المصدر**: Android Intent Redirection (Ostorlab 2026 / Google FAQ).

---

## 🟡 P2 — ميزات تنافسية وجودة

| البند | الوصف |
|---|---|
| P2-1 | إجراء NOTIFICATION_INPUT بحقل RemoteInput يعيد النص إلى %var |
| P2-2 | مشغّلات SensorManager (proximity/shake/light/step) |
| P2-3 | MediaController play/pause/next بدون صلاحيات |
| P2-4 | خادم HTTP محلي كـ trigger (منافس Tasker webhook) |
| P2-5 | nexaflow://run-task + ShortcutManagerCompat dynamic/pinned |
| P2-6 | فحص api.github.com/releases/latest + SHA-256 + PackageInstaller |
| P2-7 | DynamicColorScheme (API 31+) مع خيار التراجع لثيم سامسونغ |
| P2-8 | libs.versions.toml + configuration-cache + build-cache + Detekt |
| P2-9 | notificationTimeout 250-500ms (حالياً 100ms) |
| P2-10 | kotlinx.serialization بدل Gson في BackupManager |
| P2-11 | rememberSaveable لمسودة المحرر |

---

## ملاحظات إيجابية مؤكدة (لا تلمسها)
- الجدولة مقاومة لـ DST: `TimeTriggerCalculator`/`CronSchedule` تستخدم
  `java.time.ZonedDateTime` مع `ZoneId.systemDefault()`.
- `AppTriggerAccessibilityService` مفلترة بـ `typeWindowStateChanged` +
  `canRetrieveWindowContent=false` (صديقة البطارية).
- Shizuku UserService (AIDL) + تراجع `newProcess` منفذان.
- SMS User Consent/Retriever لـ Android 17 منفذ.
- `SavedStateHandle` في ViewModels التفاصيل + `rememberSaveable` في شاشة الأذونات.
- OemCompat بروابط autostart عميقة موحّدة مع مدير الأذونات.
- 4 بلاطات Quick Settings + AppWidgets + R8 + edge-to-edge منفذة.

---

## سجل التنفيذ — الدفعة الثانية (P2 كاملة + فحوصات P1)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| P1-2 | تحقق: لا يوجد أي `announceForAccessibility` في المشروع — لا حاجة لاستبدال | — |
| P1-3 | تحقق: Scaffold خارجي واحد يستهلك الـ insets لكل الشاشات (edge-to-edge آمن) | `NexaFlowApp.kt` |
| P2-1 | أزرار ردّ نصية (RemoteInput): حقل `replyVariable` في `NotificationActionButton` + `EXTRA_REPLY_VARIABLE` + `REMOTE_INPUT_REPLY_KEY` + المستلم يكتب الرد في `%var` (إنشاء/تحديث) + محرر رد في `NotificationButtonsEditor` | `NotificationActionButtons.kt` · `AutomationIntents.kt` · `NotificationActionReceiver.kt` · `ActionConfigEditor.kt` |
| P2-2 | مشغّل `SENSOR` (قرب/اهتزاز/ضوء/خطوات): `SensorTriggerMatcher` خالص + `SensorMonitor` يسجّل المستشعرات عند الطلب (بطارية) + واجهة رقائق + `ACTIVITY_RECOGNITION` | `SensorTriggerMatcher.kt` · `SensorMonitor.kt` · `TriggerEditorCard.kt` |
| P2-3 | تحقق: `SYSTEM_MEDIA_*` + `MediaActionsHandler` + `SystemController.mediaControl` موجودة مسبقاً | — |
| P2-4 | ويب هوك محلي: `TriggerType.WEBHOOK` + `WebhookServer` (ServerSocket loopback على 8765، token اختياري، يعمل عند الطلب) + `WebhookTriggerMatcher` خالص | `WebhookServer.kt` · `WebhookTriggerMatcher.kt` |
| P2-5 | روابط عميقة `nexaflow://run-task/{id}` (intent-filter + `handleDeepLink` في MainActivity) + زر «إضافة اختصار» (pushDynamicShortcut + requestPinShortcut) | `AndroidManifest.xml` · `MainActivity.kt` · `AutomationDetailsScreen.kt` |
| P2-6 | فاحص تحديثات داخل التطبيق: `UpdateChecker` (GitHub API + SHA-256 + FileProvider/ACTION_INSTALL_PACKAGE) + قسم «التحديثات» في الإعدادات + `REQUEST_INSTALL_PACKAGES` | `UpdateChecker.kt` · `UpdateViewModel.kt` · `SettingsScreen.kt` |
| P2-7 | Dynamic Color (Material You) لمفتاح في إعدادات المظهر على API 31+ مع تراجع لثيم سامسونغ | `Theme.kt` · `ThemePreferences.kt` · `ThemeScreen.kt` |
| P2-8 | Version Catalog كامل (`gradle/libs.versions.toml` — 26 ملف بناء) + `org.gradle.configuration-cache` + `org.gradle.caching` + Detekt (إعداد مقتضب + مهمة تجميع + خطوة CI) | `libs.versions.toml` · `gradle.properties` · `build.gradle.kts` · `config/detekt/detekt.yml` |
| P2-10 | `@Serializable` على نماذج الدومين + `BackupManager` و`ExecutionRecordMapper` على kotlinx.serialization (بدل Gson الانعكاسي — R8-safe) + إزالة الكود الميت | `BackupManager.kt` · `ExecutionRecordMapper.kt` · نماذج الدومين |
| P2-11 | مسودة المحرر عبر `rememberSaveable` (Savers مخصصة للمشغّلات/القيود/الإجراءات/الإعدادات) تنجو من التدوير وقتل العملية | `BuilderStateSavers.kt` · `AutomationBuilderScreen.kt` |

### إصلاحات حقيقية من بوابة Detekt الجديدة
- كود ميت: ثوابت `ALL_MINUTE/ALL_HOUR/ALL_MONTH` في CronSchedule، `worker` في TaskManager، معامل `context` في SmsCapabilityViewModel، معامل `automation` في RoutineMetaLine، معامل `attempt` في runAttempt.
- حلقة `processQueue` في TaskManager أعيد بناؤها (pollOrWait + processEnvelope).

## المرجع
- Android Developers: [Alarms](https://developer.android.com/develop/background-work/services/alarms),
  [FGS timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout),
  [Compose stability](https://developer.android.com/develop/ui/compose/performance/stability),
  [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile),
  [Android 17 behavior](https://developer.android.com/about/versions/17/behavior-changes-17),
  [Intent security](https://developer.android.com/agents/skills/security/android-intent-security/SKILL)
- [Gson Troubleshooting](http://google.github.io/gson/Troubleshooting.html) ·
  [R8 FAQ](https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md)
- [Tasker Plugin Migration](https://tasker.joaoapps.com/pluginsservicesmigration.html) ·
  [twofortyfouram SDK](https://github.com/twofortyfouram/android-plugin-client-sdk-for-locale)
- [Ostorlab — Intent Redirection](https://blog.ostorlab.co/android-intent-redirection.html) ·
  [ProAndroidDev — EncryptedSharedPreferences 2026](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a)

---

## سجل التنفيذ — الدفعة الأولى (P0-1/2/3/4 + P1-1/4/5 + P2-9)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| P0-1 | `MaintenanceWorker` (HiltWorker) ينظّف سجل التنفيذ كل 12 ساعة عبر `enqueueUniquePeriodicWork(KEEP)` | `app/work/MaintenanceWorker.kt` · `NexaFlowApplication.kt` · `app/build.gradle.kts` |
| P0-2 | Sentry برصد ANR (5s) وتفعيل اختياري عبر `PrivacyPreferences` (إعدادات → الخصوصية)، DSN من env فقط | `SentryReporter.kt` · `PrivacyPreferences.kt` · `PrivacyViewModel.kt` · `SettingsScreen.kt` |
| P0-3 | موديول `:baseline-profile` (مولّد CUJ) + ملف `app/src/main/baseline-prof.txt` ثابت + ProfileInstaller | `baseline-profile/` · `app/src/main/baseline-prof.txt` |
| P0-4 | تشفير المتغيرات الحساسة Keystore AES-GCM (`KeystoreSecureStorage`) + عمود `sensitive` + هجرة v10→v11 + مفتاح «حساس» في واجهة المتغيرات | `KeystoreSecureStorage.kt` · `GlobalVariableEntity.kt` · `Migrations.kt` · `VariableRepositoryImpl.kt` · `VariablesScreen.kt` |
| P0-5 | تحقق: قواعد R8 لنماذج Gson موجودة في `app/proguard-rules.pro` — لا حاجة لإضافة | — |
| P1-1 | `ACCESS_LOCAL_NETWORK` في المانيفست + `runtimePermissionsFor(SYSTEM_HTTP_REQUEST)` + شاشة شرح سامسونغ + 11 لغة | `PermissionCatalog.kt` · `PermissionExplainDialog.kt` · `AndroidManifest.xml` |
| P1-4/5 | `AutomationAlarmReceiver` يستمع `TIME_SET` + `TIMEZONE_CHANGED` + `TIMEZONE_OFFSET_CHANGED` ويعيد جدولة المنبّهات؛ `TimeTriggerCalculator` يقبل `zone` قابلة للحقن + 4 اختبارات DST (spring-forward/fall-back/فجوة 02:30/Sunday) | `AutomationAlarmReceiver.kt` · `TimeTriggerCalculator.kt` · `TimeTriggerCalculatorTest.kt` · `AutomationAlarmReceiverTimeChangeTest.kt` |
| P1-6 | تحقق: كل المستقبلات الداخلية `exported=false`، والمصدّرة مبررة (SMS/Consent/الودجات) | — |
| P2-9 | `notificationTimeout` من 100 → 300ms لخدمة إمكانية الوصول | `app_trigger_accessibility_service.xml` |

### إعادة توليد Baseline Profile
```bash
# على جهاز (روت أو API 33+):
./gradlew :baseline-profile:connectedAndroidTest
# انسخ الملف المُولَّد فوق app/src/main/baseline-prof.txt ثم التزمه
```

## سجل التنفيذ — الدفعة الثانية (P2-8b: Paging + R8 kotlinx + أمن/أداء)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| P2-Paging | شاشة السجل عبر **Paging 3.4.1**: `PagingSource` من Room (`room-paging` + فهرس `executedAt` من الدفعة السابقة) + `MappedPagingSource` (مهايئ ترقيم قُطعت فيه `PagingSource.map` في 3.4) + `Pager.cachedIn` في ViewModel + حالات تحميل/خطأ/إعادة محاولة/فارغ في الشاشة (10 لغات) | `ExecutionDao.getExecutionsPaged()` · `MappedPagingSource.kt` · `HistoryViewModel` · `HistoryScreen` · 3 اختبارات `MappedPagingSourceTest` |
| P2-R8 | قواعد **kotlinx.serialization** في `proguard-rules.pro` (كانت ناقصة بعد هجرة P2-10 — التصدير/الاستيراد كان سيُكسر في release مع R8) + تصحيح تعليق خاطئ في `data/build.gradle.kts` كان يدّعي أن kotlinx «آمن بدون قواعد» | `app/proguard-rules.pro` · `data/build.gradle.kts` |
| P2-Back | تفعيل **التنبؤ بالعودة** `android:enableOnBackInvokedCallback="true"` (API 33–35؛ إجباري في 36+) | `app/src/main/AndroidManifest.xml` |

> ملاحظة إصدارات: Paging 3.4 أزال `PagingSource.map` و`PagingSource.from` واستبدل `items(LazyPagingItems)` بـ `itemKey()`/`itemContentType()` — رُوعي في التنفيذ (شاهده في `HistoryScreen` و`TestPagingSources.kt`).

## سجل التنفيذ — الدفعة الثالثة (توافق 16KB + موثوقية DataStore + سرعة البناء)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| 16KB | **بوابة CI لفحص محاذاة 16KB** لكل `.so` داخل APK (مطلوب Google Play للتطبيقات التي تستهدف Android 15+) — تحقّق من `p_align >= 16384` لكل PT_LOAD في ELF32/64؛ كل المكتبات الحالية (graphics.path + datastore) متوافقة، والبوابة تمنع انحداراً مستقبلياً (مثل إعادة إضافة Sentry NDK) | `scripts/check_16kb.py` · خطوة جديدة في `.github/workflows/android-ci.yml` |
| DataStore | **معالج تلف `ReplaceFileCorruptionHandler`** للملفات الأربعة (notifications/privacy/sms/theme) — تعطّل القراءة بعد انقطاع أثناء الكتابة كان سيكرّش التطبيق عند كل إقلاع؛ الآن يُعاد ضبطه للافتراضيات + اختباران يثبتان السلوك (فشل بدون معالج / افتراضيات معه) | `NotificationPreferences.kt` · `PrivacyPreferences.kt` · `SmsPreferences.kt` · `ThemePreferences.kt` · `DataStoreCorruptionHandlerTest.kt` |
| سرعة البناء | `org.gradle.parallel=true` + رفع heap الجذر إلى 4GB + `kotlin.daemon.jvmargs=3GB` — البوابة الكاملة: **45m → 27m** | `gradle.properties` |

> ملاحظة منصة: اختبار المعالج فشل أولاً على Windows لأن `File.renameTo` لا يستبدل ملفاً موجوداً (بينما يعمل على Linux/أجهزة Android) — عولج بحذف الملف التالف داخل المعالج لمحاكاة الاستبدال عبر المنصات.

## سجل التنفيذ — الدفعة الرابعة (إزالة Gson نهائياً + تحصين سلسلة التوريد)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| Gson→kotlinx | **محوّلات Room** (أعمدة triggers/actions/constraints) تحوّلت من Gson العاكس إلى kotlinx.serialization — آخر استخدام انعكاسي لـ Gson في كود الإنتاج زال؛ Gson بقي اعتماداً اختبارياً فقط + **4 اختبارات توافق** تثبت أن صفوف Gson القديمة المخزنة تُقرأ بدون هجرة قاعدة بيانات (الصيغتان متطابقتان لهذه النماذج) | `core/database/.../Converters.kt` · `ConvertersCompatTest.kt` · `core/database/build.gradle.kts` |
| R8 | **حذف قسم Gson من proguard-rules.pro** (keep كامل للنماذج + قواعد enum العاكسة) — يبقى فقط قسم kotlinx + Shizuku + Hilt؛ R8 أصبح حراً في تحسين نماذج الدومين، والـ release يُبنى ويمر | `app/proguard-rules.pro` |
| سلسلة التوريد | **Gradle Dependency Verification** — `verification-metadata.xml` (6198 سطراً، SHA-256 لكل JAR/AAR/POM/بلاغ بما فيها مسارات الاختبارات)؛ التحقق يُفعَّل تلقائياً بوجود الملف ويمنع توريد تبعية معدّلة أو مخترَقة | `gradle/verification-metadata.xml` · `settings.gradle.kts` (تعليق توثيقي) |

> إعادة التوليد بعد إضافة/ترقية أي اعتماد: `./gradlew --write-verification-metadata sha256 :app:assembleDebug`

## سجل التنفيذ — الدفعة الخامسة (إصلاح شبكة على الخيط الرئيسي + StrictMode)

| البند | ما نُفّذ | الملفات الرئيسية |
|---|---|---|
| ANR | **إصلاح شبكة على الخيط الرئيسي**: `UpdateViewModel` كان يستدعي `HttpURLConnection` الحاجب (فحص GitHub API + تنزيل APK متعدد MB) داخل `viewModelScope.launch` الافتراضي (Main) — خطر ANR وتجميد الواجهة. الآن كل الشبكة داخل `withContext(Dispatchers.IO)` | `feature/settings/.../UpdateViewModel.kt` |
| StrictMode | **StrictMode للبناء التجريبي فقط**: كشف قراءة/كتابة قرص وشبكة على الخيط الرئيسي + تسريبات Activity/Closable/Registration مع `penaltyDeath` — كان سيكشف الخطأ أعلاه فوراً، ويعمل كشبكة أمان لكل التطوير القادم | `app/.../NexaFlowApplication.kt` |

> تحقّق أيضاً من: لا GlobalScope/runBlocking/Thread.sleep في الإنتاج، كل المستقبلات تستخدم `goAsync`+scope خلفي (`ApplicationScope` = Dispatchers.Default)، WebhookServer على IO، منح URI الصريح للتثبيت موجود، الـ composables كلها skippable.
