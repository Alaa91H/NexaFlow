# عقد توافق إضافات NexaFlow

## مسار التنفيذ الموحد

```text
External plugin metadata / callback
        ↓
Plugin discovery or protocol adapter
        ↓
PluginDescriptor + PluginInvocationPolicy
        ↓
CapabilityRequest(PLUGIN_ACTION | PLUGIN_CONDITION_READ)
        ↓
ExecutionPolicy → CapabilityResolver → PluginBackend
        ↓
Verification / checkpoint / redacted diagnostics
```

يتعمد هذا التصميم إبقاء `Bundle` و`Intent` و`Context` وclasses الخارجية داخل adapter Android فقط. لا يمر إلى طبقات domain أو data سوى metadata منظمة، وقيم `PluginValue` البدائية، ومراجع opaque، ونتائج منقحة.

## العقد العام

| العقد | المسؤولية | ما لا يقدمه |
|---|---|---|
| `PluginDescriptor` | هوية package/components والنوع والبروتوكول والتوافق والثقة وfeature metadata | لا يحمل Context أو binder أو secrets أو class loader |
| `PluginCapabilityDeclaration` | مخطط feature input/output وتعريف موافقة المستخدم | لا يمنح plugin امتياز أو backend |
| `PluginInvocationPolicy` | timeout/payload/dedup/variable bridge وموافقة المستخدم | لا يتجاوز سياسة capability أو أذونات Android |
| `PluginValue` | قيم primitive/list/map فقط عبر الجسر | لا يسمح Bundle/Parcelable/Serializable حر |
| `PluginInvocationResult` | correlation/result/outputs منقحة | لا يخزن raw response أو secret |
| `PLUGIN_ACTION` | side-effect action عبر backend صريح | لا يسمح action handler باستدعاء Intent مباشرة |
| `PLUGIN_CONDITION_READ` | query ثلاثي القيمة للحالة | لا يحول UNKNOWN إلى FALSE |

## الثقة والسياسة

كل إضافة مكتشفة تبدأ عند `UNTRUSTED`. لا يجوز أن يرفع الاكتشاف package إلى `USER_APPROVED` أو يتجاوز `requireUserApproval`. ولا تمنح الثقة وصولاً إلى Root أو Shizuku أو Accessibility أو secrets أو repositories؛ إنها فقط شرط إضافي قبل السماح للـ PluginBackend باستدعاء component مصرح به.

| القرار | المصدر | النتيجة |
|---|---|---|
| component غير enabled/exported أو pairing غير صالح | discovery validation | `UNAVAILABLE` مع compatibility status محدد |
| protocol غير مدعوم | registry | `UNSUPPORTED` ولا يظهر كمتحكم قابل للتشغيل |
| policy أو user approval غير متحقق | capability policy + plugin policy | لا ينفذ backend |
| payload غير primitive أو أكبر من الحد | `PluginValueAdapter` | `INVALID_CONFIGURATION` |
| repeated correlation ضمن نافذة dedup | invocation guard | `DUPLICATE` بلا side effect ثانٍ |
| receiver لا يجيب | adapter | `TIMED_OUT` وليس `SUCCESS` |
| plugin يعيد حالة لا يمكن تفسيرها | adapter | `UNKNOWN` مع diagnostics منقحة |

## التوافق الخلفي

يحافظ `PluginManifest` على `receiverClass` و`declaredActionIds` دون تغيير أو إزالة. أضيفت fields اختيارية للـ edit activity والنوع والبروتوكولات والقدرات، ولذلك لا تنكسر manifests أو exports السابقة. أما persisted instance الجديدة فيجب أن تحفظ package + type + edit activity + opaque config reference + blurb، ويعاد حل receiver عبر discovery قبل كل invocation حساس بدلاً من الثقة باسم receiver محفوظ وحده.

## حدود التنفيذ الأولى

1. توافق Locale base لـ setting وcondition هو الهدف الأول القابل للاختبار.
2. امتدادات Tasker مثل output variables وevent payload تعلن وتستخدم فقط بعد اكتشاف support صريح؛ لا يفترضها المضيف.
3. لا يعني ظهور plugin في PackageManager أنه متوافق أو مسموح؛ registry يعيد حالة compatibility مستقلة.
4. الأحداث لا تنفذ workflows من callback. يجري تحويلها إلى `NexaFlowEvent` ثم تمر بـ EventBus و`TriggerIndex` الحالي.
