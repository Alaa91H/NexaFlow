# تدقيق مستودع NexaFlow لطبقة الصيانة والأتمتة الإنتاجية

**تاريخ التدقيق:** 20 أغسطس 2026  
**الحالة:** مرحلة تدقيق فقط. لم يُعدل كود التشغيل في هذه المرحلة.

> **النتيجة المعمارية:** يملك NexaFlow بالفعل مجدولاً واحداً، ومحرك تنفيذ واحداً، ومدير مهام بطابور عامل، وسجل checkpoints دائم، وتعافياً عند بدء العملية، وسجل تاريخ Room، ومدير نسخ/استعادة. لذلك فإن إنشاء Runtime أو Scheduler أو EventBus أو Queue أو Persistence موازٍ سيكون تكراراً مخالفاً للمعمارية. التوسعة الصحيحة هي نماذج صيانة صغيرة وقرارات انتظار/استئناف فوق هذه المكونات.

## ملخص قابل لإعادة الاستخدام

| المكوّن | التنفيذ القائم والدليل | قابل لإعادة الاستخدام؟ | الفجوة الإنتاجية | أقل تعديل مطلوب | الخطر |
|---|---|---:|---|---|---|
| Scheduler | `AutomationScheduler` يعتمد `AlarmManager` ويعيد جدولة مهام `TIME` المفعلة | نعم | لا مفهوم Profile أو Window أو حالة انتظار دائمة | توسيع config/nodes لا Scheduler جديد | متوسط: حدود exact alarms وDoze |
| Daily/Weekly/Monthly | `TimeTriggerCalculator` يدعم `DAILY` و`SPECIFIC_DAYS` و`MONTHLY` و`MONTHLY_WEEKDAY` | نعم | لا قوالب صيانة أو سياسة missed window | تحويلها إلى تكوينات وقوالب فوق Trigger القائم | منخفض |
| DST/Timezone/Clock | حاسب الوقت يعمل بـ`ZonedDateTime`؛ receiver يعيد الجدولة عند تغيّر الوقت/المنطقة | نعم | لا اختبار قبول فعلي لكل OEM | إضافة اختبارات وحدات وحالة تشغيل فعلية | متوسط: سلوك OEM للأجهزة النائمة |
| Reboot recovery | `AutomationAlarmReceiver` يعيد تشغيل scheduler والمراقبة بعد boot/package replace | نعم | لا إثبات جهاز فعلي في هذه المرحلة | توسيع اختبارات Android والتحقق الواقعي | متوسط |
| Runtime | `ExecutionEngine.runAutomation` يمرر القيود قبل side effects، ويسجل history/checkpoint | نعم | لا حالات `WAITING_FOR_WINDOW` أو `WAITING_FOR_CONDITIONS` دائمة | إضافة نتائج/حالة انتظار إلى المسار القائم | متوسط |
| Workflow/DAG | `AutomationDagCompiler` و`WorkflowInterpreter` موجودان ويملكان sequence/parallel/retry/branch/loop | نعم | ربط اعتماديات automation-level وسياسة cycle/diagnostic موحدة | إعادة استعمال DAG compiler مع تعريفات اعتماد صغيرة | متوسط |
| Queue/Worker | `TaskManager` يملك priority queue وعاملاً واحداً وإلغاءً ومهلةً وdeadline وقفل موارد | نعم | لا حفظ queue بعد موت العملية ولا انتظار حدث مورد طويل | استعماله لعمل run قصير فقط؛ الانتظار يبقى لدى scheduler/monitor | عالٍ إذا استخدم كـ persistence |
| Retry | `TaskManager.RetryPolicy` و`RetryExecutor` يملكان backoff | نعم | لا تصنيف retryable بحسب capability/window ولا حد maxDelay موحد | سياسة retry typed فوق القائم | متوسط |
| Constraints | `AutomationConstraintGate` + `ConstraintEvaluator` تعمل AND وتفشل بشكل محافظ | نعم | لا OR/NOT متداخلة، ولا idle/thermal/storage/RAM/capabilities شاملة | توسيع النموذج والمقيّم وقارئ الحالة | متوسط |
| Device context | `ConstraintStateReader` يقرأ Wi-Fi والبطارية والقفل والسماعة وBluetooth وDND والطيران والشحن والموقع | جزئياً | لا screen-off، interactive/idle، thermal، storage، RAM، network type، Root/Shizuku كسياق typed | توسيع `ConstraintSnapshot` والقارئ فقط | متوسط |
| Event-driven resume | Battery و`DeviceStateMonitor28` ومراقبات trigger واسعة موجودة | نعم | لا معادلة pending-maintenance موحدة عند تغيّر مورد | ربط إعادة تقييم حالات الانتظار بالمراقبات القائمة | متوسط |
| Checkpoints/idempotency | `ActiveExecutionStore` و`DurableExecutionCheckpoint` يحفظان action index وidempotency keys؛ engine لا يعيد side effect مجهول | نعم | لا idempotencyKey على مستوى schedule/window/profile ولا خط idempotency ledger مستمر مستقل | اشتقاق key من automation/schedule/window داخل العقد القائم | عالٍ للعمليات المدمرة |
| Process-death recovery | `ExecutionRecoveryCoordinator` يصنف safe resume/verify/exit/manual diagnostics | نعم | لا workflow-aware resumer مكتمل ولا recovery action policy | توسيع coordinator/engine لا إنشاء recovery runtime | عالٍ |
| Failure classification | `ConditionResult` ورسائل TaskManager و`SystemControlResult` موجودة | جزئياً | لا `errorCode/stage/retryable/recoverable/attempt` موحدة محفوظة | إضافة نموذج failure typed إلى سجل التنفيذ | متوسط |
| History/retention | `ExecutionDao.insertWithRetention` يجري transaction مع 60 يوم/1000 سجل | نعم | الاحتفاظ ثابت وغير قابل للضبط ولا يستثني active runs صراحة | إعداد retention محفوظ واستعلام cleanup آمن | متوسط |
| Backup/restore | `BackupManager` يصدر automations ويعمل preflight وvalidation ويستورد disabled | نعم | لا variables/settings/profiles/schedules/checksum/device metadata ولا restore transaction شامل | ترقية schema version داخل backup الحالي | متوسط |
| Notifications | `NotificationPreferences` يحفظ master/execution/reminder/monitoring | نعم | لا Errors only / important / daily / weekly summary | توسيع enum السياسة والمجدول القائم للملخصات | منخفض |
| Dashboard/health | Dashboard يعرض routines وآخر تشغيل؛ History paging موجود | جزئياً | لا metrics حقيقية أو waiting/active/health summaries | queries aggregate فوق Room الحالية وViewModel/UI محدودة | متوسط |
| Reports | سجل النتائج والتاريخ موجودان | جزئياً | لا daily/weekly/monthly report abstraction | aggregation read-only فوق history | منخفض |
| Root/Shizuku | `DeviceProfileDetector` و`PrivilegedRunner` وbackends موجودة | نعم | لا يجوز اعتبارهما مصدراً لتحديثات Google Play أو نجاح privileged action | استعمال capability state فقط مع فشل typed | عالٍ أمني |
| Package operations | System installer وPackageManager/handlers موجودة | جزئياً | لا مصدر موثوق وتحقق archive/splits لتحديثات خارجية | إبقاء مسار Play capability-aware الحالي؛ لا تنزيل غير موثق | عالٍ أمني |
| Storage maintenance | Trigger storage منخفض موجود؛ لا cleanup profile مضبوط | جزئياً | لا أهداف cleanup صريحة ولا قائمة paths محمية ولا verify | أوامر cleanup قابلة للضبط ومدققة فقط | عالٍ أمني |

## أدلة المصدر الرئيسية

| المجال | نقاط الدخول الفعلية |
|---|---|
| الجدولة | `core/automation-engine/.../AutomationScheduler.kt` و`AutomationAlarmReceiver.kt` |
| الوقت وDST | `domain/.../schedule/TimeTriggerCalculator.kt` |
| التنفيذ | `core/execution/.../ExecutionEngine.kt` |
| القيود | `AutomationConstraintGate.kt` و`ConstraintStateReader.kt` و`domain/.../ConstraintEvaluator.kt` |
| الطابور وإعادة المحاولة | `core/execution/.../task/TaskManager.kt` و`domain/.../workflow/RetryExecutor.kt` |
| الاستعادة | `core/datastore/.../ActiveExecutionStore.kt` و`DurableExecutionModels.kt` و`ExecutionRecoveryCoordinator.kt` |
| السجل والاحتفاظ | `core/database/.../ExecutionDao.kt` و`data/.../HistoryRepositoryImpl.kt` |
| النسخ والاستعادة | `data/.../backup/BackupManager.kt` |
| الواجهة | `feature/dashboard` و`feature/history` و`feature/settings` |

## الحالة الفعلية حسب طلب الإنتاج

| القدرة | الحالة الحالية | مستوى الدليل | ملاحظة دقيقة |
|---|---|---|---|
| جدولة يومية | موجودة | UNIT_TESTED / CI_VERIFIED | تكوين `DAILY` في حاسب الوقت ومجدول المنبهات. |
| جدولة أسبوعية | موجودة | UNIT_TESTED / CI_VERIFIED | `SPECIFIC_DAYS` وweekday/weekend. |
| جدولة شهرية | موجودة | UNIT_TESTED / CI_VERIFIED | يوم الشهر أو weekday داخل الشهر. |
| استعادة بعد reboot | موجودة | ANDROID_INTEGRATION_TESTED | receiver يعيد الجدولة؛ لا يزال اختبار قبول جهاز فعلي مطلوباً. |
| DST/timezone | موجودة في الحساب | UNIT_TESTED / CI_VERIFIED | إعادة الجدولة عند broadcast الوقت والمنطقة. |
| شروط AND | موجودة | UNIT_TESTED | كل القيود المحلية يجب أن تتحقق. |
| شروط OR/NOT/nested | غير موجودة | UNSUPPORTED | يلزم توسيع نموذج التعبير؛ لا يصح الادعاء بدعمها. |
| Wi-Fi/battery/charging/lock/location | موجودة | UNIT_TESTED | من `ConstraintStateReader` الحالي. |
| idle/thermal/storage/RAM/screen-off | جزئية/غير مكتملة | PARTIAL | بعض triggers موجودة، لكنها ليست سياق maintenance window موحداً. |
| انتظار نافذة موارد | غير موجود | UNSUPPORTED | القيود الحالية تسجل `Skipped` بدلاً من حالة waiting دائمة. |
| retry/backoff | موجود داخل TaskManager | UNIT_TESTED | لا يزال الربط مع automation resource wait غير مكتمل. |
| cancellation/timeout/resources | موجود داخل TaskManager | UNIT_TESTED / ANDROID_INTEGRATION_TESTED جزئياً | in-process فقط، وليس recovery durable لطابور عام. |
| process-death checkpoint | موجود | ANDROID_INTEGRATION_TESTED | يمنع replay الأعمى للـ side effects المجهولة. |
| process-death resume كامل | جزئي | PARTIAL | coordinator يصنف؛ resumer الواعي بالworkflow غير مكتمل. |
| backup automations | موجود | UNIT_TESTED | import disabled-by-default بعد preflight. |
| backup variables/settings/profiles | غير موجود | UNSUPPORTED | خارج BackupFile v1. |
| retention | موجود ثابت | UNIT_TESTED | 60 يوماً و1000 سجل transactionally؛ ليس قابلاً للضبط. |
| health dashboard/reports | غير موجود | UNSUPPORTED | لا توجد aggregates أو واجهة صحية مخصصة. |
| Google Play app update الصامت | غير مدعوم هاتفياً | OFFICIAL_MANAGED_ONLY | Action v3.36.0 تسجل عدم التعرض بأمان؛ لا تدعي تحديثاً. |

## قرار المرحلة التالية

يجب أن تبدأ المرحلة التالية بتصميم **نماذج صيانة وقرار انتظار/استئناف** فوق scheduler وExecutionEngine وTaskManager وActiveExecutionStore الحالية، لا بإنشاء runtime موازٍ. يجب أن تبقى معالجة العمليات الحساسة (التثبيت، التنظيف، النسخ) خلف capability/security abstraction، مع رفض واضح إذا لم تتوافر مصادر أو امتيازات يمكن إثباتها.
