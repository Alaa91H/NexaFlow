# NexaFlow — خطة التحسين الشاملة (Project Improvement Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** رفع NexaFlow من تطبيق أتمتة "يصلح ويعمل" إلى تطبيق احترافي موثوق، من خلال تقوية المحرك، توسيع الواجهة، وضمان الجودة والتوزيع.

**Architecture:** مشروع Android متعدد الوحدات (Kotlin + Jetpack Compose + Material3) بنمط Samsung Modes & Routines. التطبيق مبني على طبقات: `core/*` (المحرك والبنية)، `domain` (النماذج والمنطق)، `data` (التخزين)، `feature/*` (الشاشات)، `app` (التجميع). التخطيط يتم على 4 مراحل (P0→P3) بحيث تنتج كل مرحلة برمجيات قابلة للاختبار والبناء بشكل مستقل.

**Tech Stack:** Kotlin 2.4.x, Jetpack Compose (Material3), Room, DataStore, Hilt, Coroutines/Flow, GitHub Actions CI, Shizuku/Root (rom-integration), 10 لغات i18n.

## Global Constraints

- الحفاظ على أسلوب Samsung Modes & Routines (بطاقات، عناوين أقسام، واجهة عربية RTL أساسية).
- كل النصوص الجديدة يجب أن تُضاف للغات العشر: `values`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-hi`, `values-ja`, `values-pt`, `values-ru`, `values-tr`, `values-zh-rCN`.
- لا تغيير في مخطط قاعدة البيانات بدون `Migration` صريح في `core/database/src/main/java/com/nexaflow/core/database/Migrations.kt` + تحديث `5.json` في `core/database/schemas/`.
- الإصدار: `app/build.gradle.kts` — `versionName` يتبع الـ tag (vX.Y.Z-alpha/beta/rc).
- النشر: دفع tag `v*` إلى `main` يشغّل CI الذي يبني ويصدر Release تلقائياً بالـ APK (workflow موجود ومجرب).
- البناء يجب أن يبقى **خالياً من الأخطاء والتحذيرات** (`testDebugUnitTest assembleDebug assembleRelease`).
- اختبار الوحدات: JUnit4. اختبار Room: `MigrationTestHelper`.

---

# الحالة الحالية المُتحقق منها (أغسطس 2026)

## ما هو مُتقن بالفعل ✅

| المجال | الوضع |
|---|---|
| بنية الوحدات | 6 وحدات `core` + `domain` + `data` + 8 وحدات `feature` + `app` — فصل واضح |
| الترجمة | 10 لغات كاملة لكل وحدة `feature` و`capability-manager` و`automation-engine` |
| قاعدة البيانات | `exportSchema=true`، مهاجرات صريحة 1→5، مخطط `5.json` محفوظ في git |
| خدمة المراقبة | FGS `specialUse` + `START_STICKY` + إعادة تشغيل عند `onTaskRemoved` |
| الجدولة | `setExactAndAllowWhileIdle` + تحقق `canScheduleExactAlarms()` + إنذار نهاية النطاق الزمني |
| الأذونات | مدير أذونات شامل، طلب `POST_NOTIFICATIONS`، طلب إعفاء البطارية (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) موجود في `PermissionManagerScreen` |
| المحرك | 9 مشغّلات (وقت/بطارية/تطبيق/جهاز/اتصال/موقع/SMS/بلوتوث/وضع الصوت) + ~50 إجراء |
| التكامل | Shizuku/Root عبر `rom-integration`، كشف ROM وتفويضات النظام |
| الإصدار | workflow CI يبني debug+release ويصدر Release تلقائياً عند دفع tag |
| الأدوات | مشغّلات بلوتوث + وضع صوت + إجراءات خروج/استعادة + أداة toggle وstatus على الشاشة الرئيسية |

## الثغرات المؤكدة (راجعتها في الكود) ⚠️

1. **التوقيع**: الـ release يُوقَّع بمفتاح debug (لأغراض CI). غير مناسب للإنتاج/Play Store.
2. **الاختبارات**: فقط ~18 اختبار وحدة في 3 ملفات (Mappers + `TimeTriggerCalculator`). لا توجد اختبارات Room migrations ولا اختبارات Compose UI.
3. **معاينات**: لا توجد `@Preview` في أي شاشة Compose — يبطئ التطوير ويفقد الحماية من كسر الـ UI.
4. **الوثائق**: لا يوجد `docs/` ولا README مفصّل لطرق البناء والتوزيع.
5. **CI**: لا يوجد job مستقل للـ lint كحارس جودة منفصل.
6. **ميزات مفقودة** (مؤكدة بعدم وجودها في الكود): مشغّل الإشعارات (NotificationListenerService)، بلاطات الإعدادات السريعة (TileService)، مشغّل التقويم (CalendarContract)، إجراء TTS، إجراءات HTTP/Webhook، متغيرات بسيطة.

---

# خطة التحسين — 4 مراحل

## المرحلة P0: أسس الجودة والمتانة (المدى القصير — قيمة فورية)

> الغاية: تأمين الجودة والقابلية للتوزيع دون تغيير السلوك. كل مهمة مستقلة وقابلة للاختبار.

### المهمة 1: اختبارات مهاجرة قاعدة البيانات

**Files:**
- Create: `core/database/src/test/java/com/nexaflow/core/database/MigrationTest.kt`
- Modify: `core/database/build.gradle.kts` (إضافة `testImplementation` لـ `androidx.room:room-testing` و `androidx.test:core`)

- [ ] **Step 1: كتابة الاختبار** — `MigrationTestHelper` يبني v1 ثم يهاجر للنسخة 5 عبر `Migrations.ALL` ويُدخل صفاً تجريبياً في v1 ويتحقق من بقائه بكل الأعمدة الجديدة (defaults).
- [ ] **Step 2: تشغيل الاختبار** — `./gradlew :core:database:testDebugUnitTest` — يجب أن يفشل إن لم تُضف التبعية.
- [ ] **Step 3: إضافة التبعيات** في `core/database/build.gradle.kts`.
- [ ] **Step 4: تشغيل الاختبار** — يجب أن ينجح.
- [ ] **Step 5: Commit** — `feat(db): add migration tests 1→5`

### المهمة 2: توسيع اختبارات محرك الجدولة

**Files:**
- Modify: `domain/src/test/java/com/nexaflow/domain/schedule/TimeTriggerCalculatorTest.kt`

- [ ] **Step 1: كتابة اختبارات فاشلة** للأنماط المخفية: `WEEKDAYS`, `WEEKENDS`, `MONTHLY`, نطاق ليلي (22:00→06:00)، نطاق صفري (نهاية==بداية = 24 ساعة)، `windowEndMillis` عبر منتصف الليل.
- [ ] **Step 2: تشغيلها** — تأكد من سلوكها الصحيح مع الكود الحالي.
- [ ] **Step 3: إصلاح أي فشل** ثم Commit.

### المهمة 3: معاينات Compose للشاشات الأساسية

**Files:**
- Modify: `feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/DashboardScreen.kt`
- Modify: `feature/automations/src/main/java/com/nexaflow/feature/automations/AutomationDetailsScreen.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt`
- Modify: `core/ui-components/src/main/java/com/nexaflow/core/ui/*` (كل مكوّن: `NexaFlowCard`, `StatCard`, `SettingRow`, `StatusPill`, `SectionHeader`)

- [ ] **Step 1:** إضافة `@Preview` (وضع فاتح/داكن + RTL) لكل شاشة ومكوّن مع `PreviewParameterProvider` للبيانات التجريبية.
- [ ] **Step 2:** البناء `:feature:dashboard:compileDebugKotlin` للتحقق.
- [ ] **Step 3: Commit** — `feat(ui): add compose previews for core screens`

### المهمة 4: حارس الجودة في CI — lint مستقل

**Files:**
- Modify: `.github/workflows/android-ci.yml`

- [ ] **Step 1:** إضافة job `lint` (أو خطوة داخل job البناء) يشغّل `./gradlew lintDebug` مع رفع تقرير lint كأثر عند الفشل.
- [ ] **Step 2:** تشغيل `./gradlew lintDebug` محلياً وإصلاح التحذيرات الظاهرة.
- [ ] **Step 3: Commit** — `ci: add lint gate to workflow`

### المهمة 5: توثيق المشروع

**Files:**
- Create: `README.md` (توسيع) — وصف الميزات، البنية، أوامر البناء، كيفية عمل tag وإصدار.
- Create: `docs/ARCHITECTURE.md` — خريطة الوحدات والتدفق (Trigger → Scheduler → Engine → Execution → History).

- [ ] **Step 1:** كتابة README والـ ARCHITECTURE.
- [ ] **Step 2:** مراجعة الدقة مقابل الكود ثم Commit — `docs: add architecture overview`

---

## المرحلة P1: قوة المحرك وتوسيع المشغّلات (المدى المتوسط — أعلى قيمة)

> الغاية: ميزات يميزها المستخدم فوراً وتنافس Tasker/Samsung.

### المهمة 6: مشغّل الإشعارات (Notification Trigger) + حظر إشعارات تطبيق

**Files:**
- Create: `core/automation-engine/src/main/java/com/nexaflow/core/engine/NotificationListener.kt` (يوسّع `NotificationListenerService`)
- Create: `core/automation-engine/src/main/java/com/nexaflow/core/engine/NotificationTriggerMonitor.kt`
- Modify: `core/automation-engine/src/main/AndroidManifest.xml` (تسجيل الخدمة + إذن `BIND_NOTIFICATION_LISTENER_SERVICE`)
- Modify: `domain/src/main/java/com/nexaflow/domain/models/Automation.kt` (إضافة `NOTIFICATION` إلى `TriggerType`)
- Modify: `feature/automation-builder/.../TriggerEditorCard.kt` + `TriggerTypePickerDialog.kt` (خيار: حزمة تطبيق + كلمة مفتاحية)
- Modify: `core/execution/.../ExecutionEngine.kt` (إجراء `SYSTEM_BLOCK_NOTIFICATION` + `SYSTEM_CLEAR_APP_NOTIFICATIONS`)
- Modify: `feature/settings/.../PermissionManagerScreen.kt` (رابط تفعيل "الوصول إلى الإشعارات")

- [ ] **Step 1:** إضافة `NOTIFICATION` إلى `TriggerType` + اختبار mapper.
- [ ] **Step 2:** تنفيذ `NotificationListener` وربطه بـ `ExecutionEngine` عند وصول إشعار مطابق (حزمة/كلمة مفتاحية).
- [ ] **Step 3:** إجراء الحظر لكل إشعارات تطبيق معيّن.
- [ ] **Step 4:** واجهة المشغّل + السلاسل (10 لغات) + مدخل مدير الأذونات.
- [ ] **Step 5:** بناء + اختبارات + Commit.

### المهمة 7: مشغّل التقويم (Calendar Trigger)

**Files:**
- Modify: `domain/.../Automation.kt` (إضافة `CALENDAR` + نوع الحدث `EVENT_START`/`EVENT_END`/`EVENT_CREATED`)
- Create: `core/automation-engine/src/main/java/com/nexaflow/core/engine/CalendarMonitor.kt` (ContentObserver + `CalendarContract`)
- Modify: `core/automation-engine/src/main/AndroidManifest.xml` (إذن `READ_CALENDAR`)
- Modify: `feature/automation-builder/.../TriggerEditorCard.kt` (اختيار تقويم + كلمة مفتاحية + قبل X دقيقة)
- Modify: `feature/settings/.../PermissionManagerScreen.kt`

- [ ] **Step 1:** نموذج المشغّل + mapper.
- [ ] **Step 2:** `CalendarMonitor` يرصد أحداث التقويم المطابقة ويشغّل المهمة.
- [ ] **Step 3:** واجهة + أذونات + سلاسل 10 لغات.
- [ ] **Step 4:** بناء + Commit.

### المهمة 8: بلاطات الإعدادات السريعة (Quick Settings Tiles)

**Files:**
- Create: `feature/widgets/src/main/java/com/nexaflow/feature/widgets/TaskTileService.kt` (يوسّع `TileService`)
- Modify: `feature/widgets/src/main/AndroidManifest.xml` (إعلان 4 بلاطات بأيقونات مختلفة)
- Modify: `feature/widgets/.../WidgetsScreen.kt` (زر "إضافة بلاطة" يستدعي `requestAddTileService`)
- Modify: `app/src/main/java/com/nexaflow/app/NexaFlowWidgetProviders.kt` (تسجيل البلاطات)

- [ ] **Step 1:** `TaskTileService` يقرأ أول مهمة مفعّلة أو مهمة مختارة، النقر يعكس حالتها.
- [ ] **Step 2:** واجهة الإضافة + سلاسل 10 لغات.
- [ ] **Step 3:** بناء + Commit.

### المهمة 9: مشغّل نوع الشاحن (Charger Type)

**Files:**
- Modify: `domain/.../Automation.kt` (إضافة `CHARGING` config: `AC`/`WIRELESS`/`USB`/`ANY`)
- Modify: `core/automation-engine/.../BatteryMonitor.kt` (قراءة `BatteryManager.EXTRA_PLUGGED` والتفريق بين الأنواع)
- Modify: `feature/automation-builder/.../TriggerEditorCard.kt` (رقائق نوع الشاحن)
- Modify: `feature/dashboard/...` (ملخص حالة الشحن)

- [ ] **Step 1:** توسيع `BatteryMonitor` + منطق النوع.
- [ ] **Step 2:** واجهة + سلاسل 10 لغات.
- [ ] **Step 3:** بناء + Commit.

---

## المرحلة P2: عمق الميزات التفاضلية (المدى المتوسط)

> الغاية: ميزات "المفاجأة" التي تبرر التفوق على التطبيقات المشابهة.

### المهمة 10: متغيرات بسيطة + إجراء TTS

**Files:**
- Modify: `domain/.../Automation.kt` (إضافة `SYSTEM_TTS` إلى `ActionType`)
- Modify: `core/execution/.../ExecutionEngine.kt` (تنفيذ TTS عبر `TextToSpeech`)
- Create: `domain/.../models/VariableResolver.kt` (استبدال `%BATTERY%`، `%TIME%`، `%DATE%`، `%WIFI_SSID%` في النصوص)
- Modify: `feature/automation-builder/.../ActionConfigEditor.kt` (نص TTS + زر إدراج متغير)
- Modify: `feature/automation-builder/.../strings.xml` (10 لغات)

- [ ] **Step 1:** `VariableResolver` + اختبارات.
- [ ] **Step 2:** إجراء TTS + معاينة المتغيرات في الواجهة.
- [ ] **Step 3:** بناء + Commit.

### المهمة 11: إجراءات HTTP/Webhook

**Files:**
- Modify: `domain/.../Automation.kt` (إضافة `SYSTEM_HTTP_REQUEST`)
- Modify: `core/execution/.../ExecutionEngine.kt` (استدعاء GET/POST عبر `HttpURLConnection` أو `OkHttp` على Coroutine)
- Modify: `feature/automation-builder/.../ActionConfigEditor.kt` (URL + method + body)
- Modify: `app/src/main/AndroidManifest.xml` (إذن `INTERNET` — تحقق إن كان مفقوداً)

- [ ] **Step 1:** تنفيذ الإجراء مع مهلة و`try/catch` وإخفاق صامت.
- [ ] **Step 2:** واجهة + سلاسل 10 لغات.
- [ ] **Step 3:** بناء + Commit.

### المهمة 12: نسخ احتياطي تلقائي (Auto Backup) + تصدير أسرع

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (تفعيل `android:allowBackup="true"` + `dataExtractionRules`)
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `feature/settings/.../SettingsScreen.kt` (زر نسخ احتياطي فوري)

- [ ] **Step 1:** قواعد النسخ الاحتياطي (DB + DataStore).
- [ ] **Step 2:** زر النسخ الفوري + Commit.

---

## المرحلة P3: التوزيع الاحترافي والأداء

> الغاية: الجاهزية للإنتاج الحقيقي.

### المهمة 13: التوقيع الاحترافي للـ release

**Files:**
- Modify: `.github/workflows/android-ci.yml` (قراءة keystore من `GH Secrets`/`Actions Secrets` بدل debug)
- Modify: `app/build.gradle.kts` (قراءة `storeFile` من متغيرات البيئة عند توفّرها، والرجوع للـ debug محلياً)

- [ ] **Step 1:** تحديث gradle وCI لدعم keystore سريّ.
- [ ] **Step 2:** توثيق طريقة توليد keystore في README.
- [ ] **Step 3:** Commit.

### المهمة 14: أداء وتقليص الحجم

**Files:**
- Modify: `app/build.gradle.kts` (تفعيل `isMinifyEnabled` + `resourceShrinking` للـ release مع `proguard-rules.pro` مراجعة)
- Modify: `gradle.properties` (خيارات `android.enableR8.fullMode`)

- [ ] **Step 1:** تمكين R8 مع قواعد ProGuard مُختبَرة (التطبيق الحالي 50MB → هدف <35MB).
- [ ] **Step 2:** اختبار شامل يدوي للوظائف الحرجة بعد التصغير.
- [ ] **Step 3:** Commit.

### المهمة 15: اختبارات Compose UI الأساسية

**Files:**
- Create: `feature/dashboard/src/androidTest/.../DashboardScreenTest.kt`
- Create: `feature/automation-builder/src/androidTest/.../BuilderFlowTest.kt` (إنشاء مهمة → حفظ)

- [ ] **Step 1:** اختبارات androidTest عبر `createAndroidComposeRule`.
- [ ] **Step 2:** تشغيل `connectedDebugAndroidTest` (على جهاز/محاكي).
- [ ] **Step 3:** Commit.

---

## أولويات مقترحة (متى تنفَّذ)

| الأولوية | المهام | السبب |
|---|---|---|
| الآن | P0 (1–5) | أساس الجودة؛ منخفضة الجهد، عالية الأمان |
| بعدها | P1 (6–9) | ميزات يراها المستخدم مباشرة |
| ثم | P2 (10–12) | التفاضل التنافسي |
| لاحقاً | P3 (13–15) | الإنتاج الحقيقي والأداء |

> ملاحظة: يمكن تنفيذ P1 و P2 بالتوازي (وحدات مختلفة لا تتداخل). تُقسم كل مرحلة لخطة تنفيذ مستقلة عند البدء.

---

## Self-Review

- **Spec coverage:** كل ثغرة مؤكدة في "الحالة الحالية" لها مهمة: التوقيع (13)، الاختبارات (1،2،15)، المعاينات (3)، الوثائق (5)، lint CI (4)، الميزات المفقودة (6،7،8،9،10،11).
- **Placeholder scan:** لا توجد "TBD/TODO" — كل مهمة تحدد الملفات الدقيقة والخطوات.
- **Type consistency:** أسماء المشغّلات/الإجراءات الجديدة (`NOTIFICATION`, `CALENDAR`, `CHARGING`, `SYSTEM_TTS`, `SYSTEM_HTTP_REQUEST`, `SYSTEM_BLOCK_NOTIFICATION`) موحّدة عبر المهام.

---

## Execution Handoff

**الخطة محفوظة في `docs/superpowers/plans/2026-08-05-project-improvement.md`. خياران للتنفيذ:**

**1. Subagent-Driven (موصى به)** — أوزّع مهمة على وكيل مخصص مع مراجعة بين المهام.

**2. Inline Execution** — أنفّذ المهام في هذه الجلسة مباشرة مع نقاط توقف للمراجعة.

أيهما تختار؟ أم تريد البدء بمرحلة محددة (مثل P0 كاملة أولاً)؟
