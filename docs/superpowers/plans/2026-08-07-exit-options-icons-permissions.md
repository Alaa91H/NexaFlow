# NexaFlow — خطة التوسع المتقدم: نهاية المهمة التكيفية، 100+ أيقونة، الموثوقية، ومدير الأذونات العدواني

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** رفع NexaFlow إلى مستوى Samsung Modes & Routines في ثلاثة محاور تفاضلية: (أ) خيارات نهاية المهمة التكيفية لكل إجراء على حدة، (ب) أكثر من 100 أيقونة لتخصيص المهام، (ج) موثوقية تنفيذ مضمونة، و(د) مدير أذونات "عدواني" يطلب الإذن فوراً بنافذة مباشرة.

**Basis — web research (أغسطس 2026):**
- **Samsung Modes & Routines / Tasker / MacroDroid**: كل إجراء داخل الروتين/المهمة يحمل سلوك نهاية خاصاً به. في Samsung، عند انتهاء الروتين **كل إعداد يعود تلقائياً إلى حالته السابقة** (revert per-setting) ما لم يحدد المستخدم خلاف ذلك. Tasker يستخدم "exit task" بإجراءات معكوسة، وMacroDroid يقدم إجراءات "On Exit". النمط الأفضل: **ثلاثية لكل إجراء**: "اتركه كما هو / أعد حالته الأصلية / اضبط قيمة محددة" مع محرر قيمة متكيف مع نوع الإجراء.
- **الأيقونات**: `material-icons-extended` لم تعد موصى بها (ميتة، +10–20MB)، الأنسب: تجميع ~150 VectorDrawable XML محلي + سجل Kotlin للبحث (الأسماء/الكلمات المفتاحية) + `LazyVerticalGrid` مع بحث وفئات. يضيف ~100–250KB فقط مع `shrinkResources`.
- **الموثوقية**: `AlarmManager.setExactAndAllowWhileIdle` + `BOOT_COMPLETED` (directBootAware) + FGS `specialUse` + WakeLock بمهلة + تفويض سريع إلى WorkManager للعمل القابل للتأجيل + تسجيل حالة تنفيذ في Room (PENDING/RUNNING/SUCCESS/FAILED) + retry مع exponential backoff + سجل محلي. Android 13+: `SCHEDULE_EXACT_ALARM` يحتاج `canScheduleExactAlarms()` وإعادة جدولة عند `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **الأذونات**: الأذونات الروتينية (dangerous) تُطلب **فوراً بنافذة النظام** عبر `ActivityResultContracts.RequestMultiplePermissions` — بلا الذهاب للإعدادات أول مرة. الإذن "الخاص" (WRITE_SETTINGS, DND, Notification Listener, Accessibility, Shizuku/Root) **لا يمكن منحه بنافذة** ويستلزم شاشة إعدادات — لكن يمكن تحويله إلى "شرح → زر → شاشة" بخطوة واحدة. الرفض الدائم يُكتشف بـ `shouldShowRequestPermissionRationale()==false` بعد رفض سابق → يعرض "الذهاب للإعدادات" مع تدهور رشيق.

**Architecture:** مشروع Android متعدد الوحدات (Kotlin + Jetpack Compose + Material3) بنمط Samsung Modes & Routines. طبقات: `core/*`, `domain`, `data`, `feature/*`, `app`. المخطط الحالي: `docs/superpowers/plans/2026-08-05-project-improvement.md` (المراحل P0–P3 اكتمل معظمها).

**Tech Stack:** Kotlin 2.4.x, Compose Material3, Room, DataStore, Hilt, Coroutines/Flow, GitHub Actions CI, Shizuku/Root, 10 لغات i18n.

## Global Constraints

- أسلوب Samsung Modes & Routines (بطاقات، عناوين أقسام، RTL عربي أساسي).
- كل النصوص الجديدة في **11 ملف**: `values`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-hi`, `values-ja`, `values-pt`, `values-ru`, `values-tr`, `values-zh-rCN`.
- تغيير قاعدة البيانات يتطلب `Migration` صريح في `core/database/.../Migrations.kt` + مخطط `X.json` جديد في `core/database/schemas/`.
- البناء خالٍ من الأخطاء والتحذيرات: `testDebugUnitTest assembleDebug lintDebug`.
- الإصدار عبر tag `v*` (workflow مجهز وينشئ Release بالـ APK تلقائياً).

---

# المحور أ: خيارات نهاية المهمة التكيفية (Adaptive Exit Behavior)

## الوضع الحالي (مُتحقق في الكود)
- `domain/.../Automation.kt`: `exitActions: List<Action>` + `revertOnExit: Boolean`.
- `ExecutionEngine.runExit` / `AutomationWorkflowRunner.runExit`: إما استعادة snapshot كامل (revertOnExit) أو تنفيذ exitActions — **لا يوجد خيار "اضبط قيمة محددة عند النهاية" ولا revert لكل إجراء على حدة**.
- `DeviceStateSnapshot` يلتقط: 8 أصوات + ringerMode + brightness + autoBrightness + screenTimeout + stayAwake + autoRotate + darkMode. **لا يلتقط**: Wi-Fi, Bluetooth, NFC, mobile data, hotspot, airplane, DND, flashlight, location, power saver, animations.
- `feature/automation-builder/.../AutomationBuilderScreen.kt` (قسم `section_exit_behavior`): مفتاح `revertOnExit` واحد + قائمة `selectedExitActions` عامة.

## المهمة A1: نموذج "سلوك النهاية لكل إجراء" (EndBehavior)

**Files:**
- Modify: `domain/src/main/java/com/nexaflow/domain/models/Automation.kt`
- Create: `domain/src/main/java/com/nexaflow/domain/models/EndBehavior.kt`

- [ ] **Step 1: تعريف النموذج** —
  ```kotlin
  enum class EndMode { LEAVE, REVERT, SET_VALUE }
  data class EndBehavior(
      val mode: EndMode = EndMode.LEAVE,
      val config: Map<String, String> = emptyMap() // تُستخدم فقط مع SET_VALUE
  )
  ```
  وأضف `val endBehavior: EndBehavior? = null` داخل `Action` (افتراضياً null = LEAVE — متوافق مع البيانات القديمة).
- [ ] **Step 2: مصفوفة التكيف لكل ActionType** — `object EndBehaviorCatalog` يصف لكل إجراء:
  - `toggleActions` (WIFI, BLUETOOTH, NFC, FLASHLIGHT, AIRPLANE, DND, MOBILE_DATA, HOTSPOT, LOCATION, POWER_SAVER, ANIMATIONS, STAY_AWAKE, AUTO_BRIGHTNESS, DARK_MODE): خيارات النهاية = **تشغيل / إيقاف / إعادة الحالة السابقة**.
  - `valueActions` (SYSTEM_VOLUME, SYSTEM_STREAM_VOLUME, SYSTEM_RING_VOLUME, SYSTEM_BRIGHTNESS, SYSTEM_SCREEN_TIMEOUT, SYSTEM_RINGER_MODE): خيارات النهاية = **قيمة محددة (محرر متكيف) / إعادة الحالة السابقة**.
  - البقية (SEND_NOTIFICATION, OPEN_APP, MEDIA, URL, LOCK_SCREEN, GO_HOME...): **اتركه كما هو فقط**.
- [ ] **Step 3: اختبارات النموذج** — `EndBehaviorCatalogTest` (كل ActionType مصنف صحيحاً، وكل toggle يدعم REVERT).
- [ ] **Step 4: Commit** — `feat(domain): per-action end behavior model + catalog`

## المهمة A2: هجرة قاعدة البيانات v7→v8 (endBehaviorJson)

**Files:**
- Modify: `core/database/src/main/java/com/nexaflow/core/database/Migrations.kt`
- Modify: `core/database/src/main/java/com/nexaflow/core/database/AutomationEntity.kt`
- Modify: `data/src/main/java/com/nexaflow/data/mapper/AutomationMapper.kt`
- Create: `core/database/schemas/8.json`
- Modify: `core/database/src/test/java/com/nexaflow/core/database/MigrationTest.kt`

- [ ] **Step 1:** `AutomationEntity.actionsJson` يبقى JSON لكن كل `Action` داخله يحمل `endBehavior` اختيارياً → لا حاجة لعمود جديد (Gson يتعامل مع الحقل الجديد تلقائياً مع default null). **تحقق**: هل يكفي؟ نعم لأن Action يُخزَّن JSON كاملاً — لا هجرة مطلوبة للتخزين.
- [ ] **Step 2:** إذا أردنا عموداً مستقلاً (اختياري): هجرة v7→v8 `ALTER TABLE automations ADD COLUMN endBehaviorsJson TEXT` — وثقها واختبرها في `MigrationTest` (v7→v8).
- [ ] **Step 3: Commit** — `feat(db): persist per-action end behavior`

## المهمة A3: التوسعة الإدراكية للـ DeviceStateSnapshot (التقاط per-setting)

**Files:**
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/DeviceStateSnapshot.kt`

- [ ] **Step 1: توسيع الالتقاط** — أضف: wifiEnabled, bluetoothEnabled, nfcEnabled, mobileDataEnabled, hotspotEnabled, airplaneMode, dndInterruptionFilter, flashlightOn, locationMode, powerSaver, animationsScale. قراءة آمنة عبر `runCatching` لكل بند (أجهزة مختلفة تقرأ بطرق مختلفة: `WifiManager.isWifiEnabled`, `NfcAdapter.isEnabled`, `Settings.Global/System`, `NotificationManager.getCurrentInterruptionFilter`).
- [ ] **Step 2: استعادة انتقائية** — `restore(context, keys: Set<String> = all)` تعيد فقط ما تريده: المفتاح `revertSetting(key)` يستعيد بنداً واحداً من الـ snapshot (هذا ما يستخدمه EXIT لكل إجراء).
- [ ] **Step 3: خريطة مفاتيح** — `keyOf(actionType)` يربط كل ActionType بمفتاح snapshot (SYSTEM_WIFI → "wifi", SYSTEM_VOLUME → "musicVolume"...).
- [ ] **Step 4: اختبارات** — اختبار `keyOf` لكل ActionType + اختبار أن restore الانتقائي يعيد القيمة الصحيحة فقط (عبر حَقن قيم).
- [ ] **Step 5: Commit** — `feat(engine): per-setting capture and selective restore`

## المهمة A4: محرك النهاية التكيفية (ExecutionEngine + WorkflowRunner)

**Files:**
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt`
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/compat/AutomationWorkflowRunner.kt`

- [ ] **Step 1: منطق runExit الجديد** — لكل `action`:
  - `endBehavior.mode == REVERT` → `snapshot.revertSetting(keyOf(action.type))` (يتطلب snapshot موجوداً؛ إن لم يكن → LEAVE).
  - `endBehavior.mode == SET_VALUE` → نفّذ الإجراء بمعكوس القيمة: لـ toggle عكوس (SET_VALUE with enabled=false يطفئ)، للقيم (VOLUME/BRIGHTNESS) يضبط القيمة، لـ RINGER_MODE يضبط الوضع.
  - `LEAVE` → تجاهل.
  - **التوافق الخلفي**: `revertOnExit == true` القديم → يعامل كأن كل إجراء REVERT (نفس السلوك الحالي). `exitActions` القديمة بدون endBehavior → LEAVE.
- [ ] **Step 2: الالتقاط المسبق** — `runAutomation` يلتقط snapshot دائماً عندما يوجد أي إجراء REVERT (وليس فقط revertOnExit).
- [ ] **Step 3: اختبارات** — `AutomationWorkflowRunnerTest`: حالة `revertOneAction_restoresOnlyThatSetting`، حالة `setValue_exit_executesInverse`, حالة `legacyRevertOnExit_stillRollsBackAll`.
- [ ] **Step 4: Commit** — `feat(engine): adaptive per-action exit execution`

## المهمة A5: واجهة نهاية المهمة التكيفية (المحرر)

**Files:**
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt` (قسم `section_exit_behavior`)
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/ActionConfigEditor.kt`
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderViewModel.kt`

- [ ] **Step 1: داخل كل بطاقة إجراء مختار** (وليس قسماً منفصلاً عاماً): صف "عند انتهاء المهمة" مع رقائق حسب `EndBehaviorCatalog`:
  - toggle: `اتركه` / `شغّل` / `أطفئ` / `أعد الحالة` (4 رقائق) — أو تبسيط: `اتركه` / `عكسه` / `أعد الحالة`.
  - value: `اتركه` / `اضبط: [محرر]` / `أعد الحالة`.
  - لا شيء: `اتركه` فقط (مخفي).
- [ ] **Step 2: إزالة مفتاح `revertOnExit` العام** — يُستبدل بالسلوك لكل إجراء (مع بقاء `revertOnExit` مقروءاً للتوافق عند تحميل مهمة قديمة: يحوَّل إلى REVERT لكل الإجراءات عند أول حفظ).
- [ ] **Step 3: الحفظ** — `saveAutomation` يكتب `endBehavior` لكل Action.
- [ ] **Step 4: السلاسل (11 ملف)** — `end_leave_as_is`, `end_revert`, `end_turn_on`, `end_turn_off`, `end_set_value`, `end_when_task_ends`.
- [ ] **Step 5: Commit** — `feat(builder): per-action adaptive end options UI`

## المهمة A6: شاشة التفاصيل + التوثيق

**Files:**
- Modify: `feature/automations/src/main/java/com/nexaflow/feature/automations/AutomationDetailsScreen.kt`
- Modify: `feature/automations/src/main/res/values*/strings.xml` (11 ملف)

- [ ] **Step 1:** عرض سلوك النهاية لكل إجراء في بطاقة الإجراءات: "عند الانتهاء: أعد الحالة" / "عند الانتهاء: شغّل" / إلخ.
- [ ] **Step 2: Commit** — `feat(automations): show per-action end behavior in details`

---

# المحور ب: أكثر من 100 أيقونة (Icon Expansion)

## الوضع الحالي (مُتحقق في الكود)
- `core/ui-components/.../NexaFlowIcons.kt`: 16 أيقونة فقط (`bolt, battery, sunny, dark, dnd, wifi, home, schedule, notifications, volume, flash, lock, palette, security, settings, star`).
- `feature/icons/.../IconPickerScreen.kt`: شبكة 4 أعمدة بدون بحث ولا فئات.
- أيقونات الإجراءات في `AutomationBuilderScreen.actionOptions` مستقلة (ImageVector مباشرة).

## المهمة B1: سجل الأيقونات الموسع (البنية)

**Files:**
- Create: `core/ui-components/src/main/java/com/nexaflow/core/ui/TaskIcon.kt` (نموذج: name, resId/vector, keywords, category)
- Modify: `core/ui-components/src/main/java/com/nexaflow/core/ui/NexaFlowIcons.kt`

- [ ] **Step 1: النموذج** —
  ```kotlin
  data class TaskIcon(
      val name: String,
      val vector: ImageVector,
      val keywords: List<String> = emptyList(),
      val category: IconCategory = IconCategory.GENERAL
  )
  enum class IconCategory { GENERAL, TIME, CONNECTIVITY, HOME, MEDIA, NOTIFICATIONS, SYSTEM, WORK, HEALTH, WEATHER }
  ```
- [ ] **Step 2: مصفوفة `TaskIcons.all`** — توسيع من 16 إلى **~120 أيقونة** من `material-icons-extended` **المحدد يدوياً** (وليس كل الحزمة). فئات: عام (bolt, star, check, heart, key...), وقت (alarm, schedule, timer, watch...), اتصال (wifi, bluetooth, nfc, signal...), منزل (home, chair, light...), إعلامات (notifications, dnd, email...), نظام (settings, lock, battery, flash...), عمل (briefcase, receipt...), صحة (fitness, water...), طقس (sunny, cloud, rain, snow...).
- [ ] **Step 3: التوافق الخلفي** — `iconVector(name)` يستمر بإرجاع الأيقونة بالاسم، و`NexaFlowIcons.all` يصبح `TaskIcons.all` (أو محولاً) حتى لا تكسر الواجهات الحالية (`selectedIconIndex`, `IconPickerScreen`, `NexaFlowIcons.all.indexOfFirst`).
- [ ] **Step 4: اختبار** — `TaskIconsTest`: لا تكرار أسماء، ≥100 أيقونة، كل أيقونة لها keywords.
- [ ] **Step 5: Commit** — `feat(icons): expand task icon registry to 120 icons`

> ملاحظة الأبعاد: `material-icons-extended` تضيف وزن build لكن مع `isMinifyEnabled` + `isShrinkResources` في release تُزال الأيقونات غير المستخدمة. الخيار الأنظف إن ظهر تضخم: توليد VectorDrawable XML من SVG (Tabler/Lucide) عبر سكربت — يضيف ~150KB فقط. **قرر أثناء التنفيذ:** ابدأ بـ `material-icons-extended` يدوياً؛ إن تجاوز APK حداً مقبولاً، تحوّل إلى XML vectors.

## المهمة B2: واجهة انتقاء الأيقونات المحسّنة

**Files:**
- Modify: `feature/icons/src/main/java/com/nexaflow/feature/icons/IconPickerScreen.kt`
- Modify: `feature/icons/src/main/res/values*/strings.xml` (11 ملف)

- [ ] **Step 1:** شريط بحث (TextField) يفلتر على `name + keywords` فورياً.
- [ ] **Step 2:** رقائق فئات (General/Time/Connectivity/...) أعلى الشبكة.
- [ ] **Step 3:** `LazyVerticalGrid(GridCells.Adaptive(72.dp))` مع معاينة تكبير عند الضغط المطول (اختياري).
- [ ] **Step 4:** إرجاع الاسم (`savedStateHandle.set("selected_icon", name)`) — يبقى نفس العقد مع `AutomationBuilderScreen`.
- [ ] **Step 5: Commit** — `feat(icons): searchable, categorized icon picker`

## المهمة B3: أيقونات الإجراءات الموحدة

**Files:**
- Modify: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AutomationBuilderScreen.kt` (actionOptions)

- [ ] **Step 1:** توحيد أيقونات الإجراءات عبر `TaskIcon` registry حيث أمكن (إبقاء ImageVector موجودة للتوافق مع ActionPresentation).
- [ ] **Step 2: Commit** — `refactor(icons): unify action and task icons`

---

# المحور ج: موثوقية التنفيذ المضمونة (Execution Reliability)

## الوضع الحالي (مُتحقق في الكود)
- `MonitoringService`: FGS `specialUse` + START_STICKY + إعادة جدولة عند `onTaskRemoved`.
- `AutomationScheduler`: `setExactAndAllowWhileIdle` + فحص `canScheduleExactAlarms()`.
- `AutomationAlarmReceiver`: يلتقط BOOT_COMPLETED + LOCKED_BOOT_COMPLETED + MY_PACKAGE_REPLACED.
- سجل تنفيذ: `HistoryRepository` + `ExecutionRecord` مع `actionResults` (مرحلة 9 اكتملت).
- **الثغرات**: لا WakeLock عند الاستيقاظ، لا إعادة جدولة عند تغيّر إذن exact alarm، COOLDOWN ثابت 10s (لا يوجد لكل مهمة)، لا retry تلقائي، لا كشف تعليق (PENDING عالق)، لا إشعار عند فشل التنفيذ.

## المهمة C1: WakeLock + تفويض سريع

**Files:**
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt`

- [ ] **Step 1:** في `onReceive`: `PowerManager.WakeLock` بمهلة 10s (`acquire(10_000)`), Release في `finally`.
- [ ] **Step 2:** تنفيذ المهمة داخل coroutine مع `withTimeout(10s)` لضمان عدم تعليق الاستقبال.
- [ ] **Step 3: Commit** — `fix(engine): wake lock around alarm execution`

## المهمة C2: إعادة الجدولة عند تغيّر إذن exact alarm

**Files:**
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml` (إضافة intent-filter `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`)

- [ ] **Step 1:** استقبال `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → إعادة جدولة كل مهام TIME/نطاقات.
- [ ] **Step 2: Commit** — `fix(engine): reschedule when exact-alarm permission changes`

## المهمة C3: فاصل زمني لكل مهمة (Cooldown)

**Files:**
- Modify: `domain/src/main/java/com/nexaflow/domain/models/Automation.kt` (إضافة `cooldownSeconds: Int = 10`)
- Modify: `core/automation-engine/.../AppTriggerAccessibilityService.kt`, `DeviceEventMonitor.kt`, `BatteryMonitor.kt`, `ConnectivityMonitor.kt` (استبدال COOLDOWN_MS الثابت بـ automation.cooldownSeconds)
- Modify: `feature/automation-builder/.../AutomationBuilderScreen.kt` (Slider 0–300 ثانية)
- Modify: `feature/automation-builder/src/main/res/values*/strings.xml` (11 ملف)
- Modify: `core/database/.../AutomationEntity.kt` + `Migrations.kt` (v8: `ADD COLUMN cooldownSeconds INTEGER NOT NULL DEFAULT 10`) + `8.json`

- [ ] **Step 1:** عمود DB + هجرة + Mapper.
- [ ] **Step 2:** استخدامه في كل المراقبات (تجميع `lastRunAt` بنفس المفتاح الحالي).
- [ ] **Step 3:** واجهة + سلاسل.
- [ ] **Step 4: Commit** — `feat(engine): per-automation cooldown`

## المهمة C4: التحقق من التنفيذ + retry + كشف التعليق

**Files:**
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/ExecutionEngine.kt`
- Modify: `core/execution/src/main/java/com/nexaflow/core/execution/compat/AutomationWorkflowRunner.kt`
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationScheduler.kt`

- [ ] **Step 1: حالة تنفيذ صريحة** — `ExecutionRecord` يحمل `status: PENDING/RUNNING/SUCCESS/FAILED` (تكتب RUNNING قبل التنفيذ، وتُحدَّث بعد).
- [ ] **Step 2: retry** — عند فشل إجراء (FAILED) و automation.retryCount>0 → إعادة جدولة retry مع backoff (60s × 2^n) عبر `AutomationScheduler`.
- [ ] **Step 3: كشف التعليق** — عند إعادة جدولة مهمة، إن وُجد record سابق `RUNNING` منتهي (أقدم من 5 دقائق) → علِّمه `FAILED` (self-healing).
- [ ] **Step 4: اختبارات** — `ExecutionEngineTest`: retry يجدول، والتعليق يُكتشف.
- [ ] **Step 5: Commit** — `feat(engine): execution verification, retry and stuck-run recovery`

## المهمة C5: سجل محلي مرن + إشعار فشل

**Files:**
- Create: `core/logging/.../RollingFileLogger.kt`
- Modify: `feature/settings/.../SettingsScreen.kt`

- [ ] **Step 1:** ملقّي ملفات دوري (سطر زمني لكل تنفيذ: trigger, wakelock, worker start, action results, error codes) — يُفعَّل في `NexaFlowApplication`.
- [ ] **Step 2:** إعداد "إشعار عند فشل مهمة" (افتراضي: مفعّل) → إرسال `SYSTEM_SEND_NOTIFICATION` عند FAILED.
- [ ] **Step 3: Commit** — `feat(reliability): rolling file logger + failure notifications`

---

# المحور د: مدير الأذونات العدواني (Aggressive Permission Manager)

## الوضع الحالي (مُتحقق في الكود)
- `feature/settings/.../PermissionManagerScreen.kt`: لوحة أذونات.
- `feature/automation-builder/.../PermissionExplainDialog.kt`: شرح Samsung-style قبل الطلب + `SpecialPermission` enum (WRITE_SETTINGS, DND_ACCESS, NOTIFICATION_ACCESS, ACCESSIBILITY, SHIZUKU, ROOT, ELEVATED, BLUETOOTH).
- `feature/automation-builder/.../BuilderComponents.kt`: `PermissionShortcuts` تفتح شاشات النظام.
- الفجوة: الطلب يتم "عند الحاجة" داخل المحرر فقط؛ لا يوجد **تدفق عدواني موحّد** يطلب كل الأذونات فوراً بنافذة، ولا فحص عند فتح التطبيق، ولا لوحة "منح الكل".

## المهمة D1: نموذج "الأذونات المطلوبة لكل مهمة"

**Files:**
- Create: `domain/src/main/java/com/nexaflow/domain/models/PermissionRequirement.kt`
- Create: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/PermissionCatalog.kt`

- [ ] **Step 1:** لكل TriggerType وActionType حدد: أذونات runtime (`Array<String>`), أذونات خاصة (`SpecialPermission`), حالة منح (`isGranted(context)`).
- [ ] **Step 2:** `requiredPermissions(triggers, actions, exitBehaviors): List<PermissionRequirement>` — دالة تجميع.
- [ ] **Step 3: اختبار** — `PermissionCatalogTest`: مهمة NFC+WiFi تتطلب الأذونات الصحيحة.
- [ ] **Step 4: Commit** — `feat(permissions): permission requirement catalog`

## المهمة D2: تدفق الطلب العدواني (Aggressive Request Flow)

**Files:**
- Create: `feature/automation-builder/src/main/java/com/nexaflow/feature/builder/AggressivePermissionFlow.kt`
- Modify: `feature/automation-builder/.../AutomationBuilderScreen.kt` (عند الضغط على "حفظ")

- [ ] **Step 1:** عند **حفظ المهمة** (أو فتح المحرر) مع أذونات ناقصة:
  1. فحص كل الأذونات (`checkSelfPermission` + `isGranted` الخاص).
  2. الأذونات runtime الناقصة → **نافذة النظام فوراً** عبر `RequestMultiplePermissions` (بلا إعدادات) — بعد شرح قصير من سطر واحد (Samsung style) إن كان `shouldShowRequestPermissionRationale()`.
  3. الأذونات الخاصة الناقصة → عرض قائمة "أذونات تحتاج تفعيلاً" مع زر لكل واحد يفتح شاشة النظام مباشرة (شرح → زر → شاشة في خطوة واحدة).
  4. بعد الرجوع من الإعدادات → إعادة فحص تلقائية (onResume) → إذا اكتمل كل شيء يُحفظ تلقائياً.
- [ ] **Step 2: زر "منح الكل الآن"** في لوحة الأذونات: يطلب runtime المتاحة بنافذة واحدة، ثم يعرض الخطوة التالية للخاصة.
- [ ] **Step 3: التعامل مع الرفض الدائم** — `shouldShowRequestPermissionRationale() == false` بعد رفض سابق → BottomSheet "الذهاب للإعدادات" بدل تكرار النافذة.
- [ ] **Step 4: Commit** — `feat(permissions): aggressive in-context permission request flow`

## المهمة D3: فحص عند فتح التطبيق + لوحة محسّنة

**Files:**
- Modify: `feature/settings/.../PermissionManagerScreen.kt`
- Modify: `app/src/main/java/com/nexaflow/app/NexaFlowApp.kt` (أو MainActivity)
- Modify: `feature/settings/src/main/res/values*/strings.xml` (11 ملف)

- [ ] **Step 1:** عند أول تشغيل: نافذة ترحيبية "نحتاج هذه الأذونات لتشغيل المهام" تعرض runtime (POST_NOTIFICATIONS, ومتى يطلب الباقي عند أول مهمة). لا تُزعج في كل تشغيل — مرة واحدة + عند إنشاء أول مهمة.
- [ ] **Step 2:** لوحة أذونات ملونة (أخضر=ممنوح، أصفر=اختياري، أحمر=ناقص) + سطر "يعمل بدون X: [قائمة الميزات المتأثرة]".
- [ ] **Step 3: Commit** — `feat(permissions): first-run prompt + color-coded dashboard`

---

## أولويات مقترحة (متى تنفَّذ)

| الأولوية | المهام | السبب |
|---|---|---|
| الآن | A1–A6 | الميزة الأكثر طلباً (نهاية المهمة التكيفية) — تتماسك مع المحرر الحالي |
| بعدها | D1–D3 | تجربة أذونات فورية — أثر فوري على التحويل والثقة |
| ثم | B1–B3 | أيقونات — تحسين تجميلي سريع، مستقل تماماً |
| لاحقاً | C1–C5 | موثوقية — قيمة عميقة لكنها لا تُرى فوراً |

> ملاحظة: A وB وD لا تتداخل وحداتياً مع بعضها (builder vs icons vs settings) — يمكن تشغيلها بالتوازي. C تعتمد على B لا شيء، لكنها تلمس المحرك؛ ننفذها بعد A لتجنب تصادمات في ExecutionEngine.

---

## Self-Review

- **Spec coverage:** المحور أ يعالج "عرض خيارات محددة عند انتهاء المهمة بحسب المهمة" (A5) + "العودة للحالة الأصلية" (A3/A4). المحور ب يعالج "100 أيقونة على الأقل" (B1: 120). المحور ج يعالج "التأكد من تنفيذ جميع المهام بشكل كامل واحترافي" (C1–C5). المحور د يعالج "مدير أذونات يطلب فوراً بنافذة" (D1–D3).
- **Placeholder scan:** لا TBD/TODO — كل مهمة تحدد الملفات والخطوات.
- **Type consistency:** `EndMode`, `EndBehavior`, `TaskIcon`, `IconCategory`, `PermissionRequirement` أسماء جديدة موحّدة عبر المهام.
- **Backward compat:** `revertOnExit` القديم يُحترم ويُحوَّل إلى REVERT لكل إجراء؛ `Action.endBehavior` افتراضياً null؛ `iconVector(name)` يحافظ على الأسماء القديمة.

---

## Execution Handoff

**الخطة محفوظة في `docs/superpowers/plans/2026-08-07-exit-options-icons-permissions.md`.**

**الخيارات:**
1. **Subagent-Driven (موصى به)** — ابدأ بالمحور أ (A1→A6) وكيلاً مخصصاً لكل مهمة مع مراجعة بينها.
2. **Inline Execution** — أنفّذ مباشرة في هذه الجلسة بنقاط توقف للمراجعة.
3. **بداية سريعة** — A1 + A3 + A4 أولاً (النموذج + المحرك) ثم A5 (الواجهة).

أي محور تريد أن نبدأ به؟ وهل تريد تنفيذه الآن؟
