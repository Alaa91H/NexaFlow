# تصميم عقود توسعة منظومة NexaFlow

**الحالة:** قرار هندسي معتمد للمرحلة 3
**النطاق:** إضافات Locale/Tasker، نتائج الشروط، والخلفيات ذات الامتياز.
**غير داخل النطاق:** تشغيل event plugin كـ workflow trigger قبل إثبات مصدر event، واستخراج/تنفيذ shell command عام.

## القرار الأساسي

يحافظ NexaFlow على مسار تنفيذ واحد: `CapabilityRequest → CapabilityResolver → CapabilityExecutionService → CapabilityBackend`. ولا يصبح أي protocol خارجي قدرة منتجية بمجرد نجاح الاكتشاف. يجب أن يمر كل request في validator والسياسة والـ timeout الموجودين، وأن يعيد نتيجة منظمة قابلة للتسجيل.

| العقد | القرار | ضمان التوافق |
|---|---|---|
| `ConditionResult` | sealed domain result بخمس حالات: `Satisfied`، `Unsatisfied`، `Unknown`، `Unavailable`، و`Error(reason)` | يبقى `ConstraintEvaluator` boolean للشروط المحلية القديمة؛ يتلقى adapter جديد للـ plugin ولا يحل محله. |
| `CapabilityId.PLUGIN_CONDITION_READ` | قدرة قراءة فقط بمثيل plugin محفوظ وموثق، وليس generic component invocation | المعرّف موجود محلياً ويُستكمل له descriptor/backend additive. |
| أخطاء الامتياز | `SHIZUKU_DENIED` و`ROOT_DENIED` تفصلان الرفض عن عدم توفر الخدمة؛ تبقى الأخطاء العامة للتوافق | لا تتغير دلالات الأخطاء الموجودة. |
| `PrivilegeLevel` | تضاف دلالات `NONE` و`ADB_SHELL` صراحة؛ تبقى الدلالات الحالية الموروثة حتى تمر migration كاملة في descriptors والسياسات | لا تكسر workflows أو fixtures التي تستخدم `NORMAL` و`SYSTEM`. |
| ثقة الإضافة | `UNTRUSTED` و`LOCALE_COMPATIBLE` و`TASKER_EXTENDED` تصف مستوى تفاوض protocol فقط؛ الموافقة تبقى property مستقلة في policy/config | لا تمنح discovery أو enum مرتفع امتياز تشغيل أو وصول outputs. |
| `PluginInvocationPolicy` | تضيف aliases واضحة `allowOutput` و`requireApproval` مع الإبقاء على الحقول القديمة لمدة migration | policy تظل متوافقة مع serialised/default callers الحاليين. |

## semantics الشروط

يملك `PluginConditionBackend` العقد التالي عند التنفيذ اللاحق:

| نتيجة ordered broadcast | `ConditionResult` | أثر gate مستقبلي |
|---|---|---|
| `RESULT_CONDITION_SATISFIED` | `Satisfied` | يمر هذا الشرط فقط. |
| `RESULT_CONDITION_UNSATISFIED` | `Unsatisfied` | لا يمر هذا الشرط فقط. |
| `RESULT_CONDITION_UNKNOWN` أو timeout | `Unknown` | لا يتحول إلى `false`؛ تسجل الحالة ويقرر caller السياسة. |
| component/permission غير متاح | `Unavailable` | لا تعالج على أنها نتيجة condition. |
| configuration/protocol غير صحيح | `Error` | failure قابل للتشخيص لا boolean مخفي. |

تعتمد هذه الدلالات على أن Locale يستخدم `ACTION_QUERY_CONDITION` كبث ordered وأن Tasker يعامل `Unknown` بأنه لا يسبب event ولا يغير state.[6] [7]

## trust وevent delivery

> لا توفر رسالة `ACTION_REQUEST_QUERY` في Locale الأساسي مصادقة sender موحدة عبر جميع إصدارات Android. تتوفر `BroadcastReceiver.getSentFromPackage()` و`getSentFromUid()` من API 34 فقط.[10]

لذلك يتوقف الإعلان عن event delivery على كل الشروط التالية معاً: plugin instance محفوظ وموافق عليه، receiver مسجل بنطاق lifecycle محدود، permission/signature contract قابل للتحقق أو sender identity API 34+، payload primitive-only محدود، deduplication، ثم النشر إلى EventBus. لا يجوز استدعاء `ExecutionEngine` من receiver.

## حدود الخلفيات المميزة

| backend | ما يمكن تصميمه الآن | ما هو محظور |
|---|---|---|
| Shizuku | availability/denial/lifecycle typed؛ UserService عبر AIDL وoperation id محدد | reflection إلى `newProcess` و`sh -c` أو command نصي من workflow.[1] |
| Root | typed operation catalog و`SafeCommandBuilder` داخلي مقيد | `executeArbitraryShell` أو إعادة تصدير raw command في capability request. |
| ADB | descriptor/diagnostic فقط إن لم يكن channel متاحاً فعلاً داخل التطبيق | إظهاره كتفعيل حقيقي في تطبيق Android عادي. |
| Accessibility | selectors غير حساسة، package allowlist، stale-node retry وحدود زمنية بعد consent | الاحتفاظ بعقد node/Intent في runtime أو تخطيط/قرار ذاتي غير rule-based.[11] [13] |

## المراجع

[1]: https://github.com/RikkaApps/Shizuku-API "Shizuku API developer guide"
[6]: https://www.twofortyfouram.com/developer "Locale Developer API"
[7]: https://tasker.joaoapps.com/pluginslibrary.html "Tasker Plugin Library"
[10]: https://developer.android.com/reference/android/content/BroadcastReceiver "BroadcastReceiver API"
[11]: https://developer.android.com/guide/topics/ui/accessibility/service "Create an accessibility service"
[13]: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en "Google Play AccessibilityService policy"
