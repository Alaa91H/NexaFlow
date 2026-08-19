# عقد طبقة تنفيذ القدرات في NexaFlow

**الحالة:** تصميم مُعتمد قبل التنفيذ.  
**المبدأ:** تحوّل Action إلى `CapabilityRequest` فقط عندما تكون العملية مدعومة بطبقة قدرات. أما الإجراءات الأخرى فتبقى في سجل المعالجات الحالي إلى أن تُغلف بصورة آمنة.

## الحدود والمسؤوليات

| الطبقة | لا تعرف | تعرف وتنفذ |
|---|---|---|
| Workflow / Action | Root أو Shizuku أو أمر shell أو تفاصيل PackageInstaller | نية العملية وترتيبها ونتيجة العقدة. |
| Capability request | تفاصيل جهاز Android مباشرة | القدرة والمعطيات المقيدة وسياسة التنفيذ والتحقق. |
| Resolver | نصوص واجهة المستخدم أو سير DAG | descriptor، policy، توافق الجهاز، التوفر الصحي للخلفيات. |
| Backend | كيف بُني Workflow أو تخزن شاشته | تنفيذ capability واحدة ضمن صلاحياته وإعادة نتيجة منظمة. |
| Verification | اختيار root أو Shizuku | الحالة قبل/بعد والشرط الذي يجعل النتيجة موثقة. |
| History / UI | secrets أو arguments حساسة | ملخص آمن: القدرة والخلفية والحالة والسبب والمدة والتحقق. |

## عقود النطاق

تُعرّف في `domain/.../capability` ولا تستورد Android. تُستخدم `@Serializable` حيث تقتضي الحاجة التخزين أو تمرير الإعدادات.

| العقد | الحقول الجوهرية | القواعد |
|---|---|---|
| `CapabilityId` | `PACKAGE_READ`, `PACKAGE_INSTALL`, `PACKAGE_UNINSTALL`, `PACKAGE_FORCE_STOP`, `PACKAGE_SET_ENABLED`, `PACKAGE_CLEAR_DATA`, `UPDATE_APPS`, `INTENT_LAUNCH`, `NETWORK_HTTP_REQUEST` | تضاف القيم عند توفر Backend حقيقي، لا كوعود واجهة. |
| `BackendId` | `ANDROID_API`, `INTENT`, `PACKAGE_MANAGER`, `PACKAGE_INSTALLER`, `SHIZUKU`, `ROOT`, `ACCESSIBILITY`, `NETWORK`, `OEM`, `ADB`, `NATIVE` | identifier قابل للتسلسل لا يمثل ضمان دعم. |
| `PrivilegeLevel` | `NORMAL`, `SYSTEM`, `ACCESSIBILITY`, `SHIZUKU`, `ROOT`, `NETWORK`, `NATIVE` | يحكم السماح قبل الحل والتنفيذ. |
| `CapabilityDescriptor` | id، نسخة Android الدنيا، privilege، risk، الخلفيات المعروفة | مصدر حقيقة قابلية capability، لا source لقرار جهاز حي. |
| `CapabilityRequest` | id، parameters، policy، verification، actionId | parameters محكومة schema في mapper/backend ولا تقبل shell raw. |
| `ExecutionPolicy` | ترتيب مفضل، allowedBackends، wifi/battery/charging/screen/thermal، timeout، retry | القيم الافتراضية آمنة: لا privileged escalation خفي ولا retry أخطاء دائمة. |
| `CapabilityResult` | status، errorCode، backend، message، duration، verification، metadata | الرسالة ملخص آمن وerrorCode آلة-مقروء. |

## الحالة والنتيجة

| `CapabilityStatus` | المعنى |
|---|---|
| `SUCCESS` | نجح التنفيذ والتحقق المطلوب. |
| `PARTIAL` | نُفّذ جزء قابل للملاحظة، ولا يدّعى الاكتمال. |
| `PENDING_USER_ACTION` | Android يطلب تأكيد المستخدم أو Intent؛ لا تعد نجاحاً. |
| `UNSUPPORTED` | لا خلفية مدعومة ومسموح بها على الجهاز أو السياسة. |
| `FAILED` | فشل التنفيذ أو التحقق. |
| `CANCELLED` | ألغيت coroutine أو أوقف backend عمليةً جارية. |

| `CapabilityErrorCode` | قابل لإعادة المحاولة افتراضياً |
|---|---|
| `UNSUPPORTED_CAPABILITY`, `INVALID_CONFIGURATION`, `PERMISSION_DENIED`, `SECURITY_EXCEPTION`, `ANDROID_VERSION_UNSUPPORTED`, `ROOT_UNAVAILABLE`, `SHIZUKU_UNAVAILABLE`, `ACCESSIBILITY_UNAVAILABLE`, `ADB_UNAVAILABLE` | لا |
| `TIMEOUT`, `NETWORK_ERROR`, `BACKEND_UNAVAILABLE`, `UPDATE_NOT_FOUND` | تحدده policy وcontext؛ الافتراضي لا يعيد الخطأ غير المؤكد بلا حد. |
| `INSTALL_FAILED`, `UPDATE_FAILED`, `VERIFICATION_FAILED`, `UNKNOWN_ERROR` | لا يعاد إلا إذا صنفته backend transient صراحةً. |

## خوارزمية الحل

1. يتحقق mapper من إعداد Action ويصنع request محدوداً بالـ schema.
2. يفحص `ExecutionPolicyEvaluator` لقطة جهاز واحدة ويحجب الطلب برمز واضح إذا خالف Wi‑Fi أو البطارية أو الشحن أو الشاشة أو الحرارة.
3. يجلب `CapabilityResolver` descriptor ثم backends المسجلة التي تعلن دعم capability.
4. يُستبعد backend إذا كان Android API غير مناسب أو الصلاحية غير موجودة أو جهازه غير صحي أو لم تسمح به policy.
5. يرتب الباقي وفق التفضيل الآمن: Android public API ثم Intent/PackageManager/PackageInstaller ثم الخلفيات الاختيارية التي تسمح بها policy. لا يساوي وجود Root أولوية مطلقة.
6. ينفذ backend واحداً. لا ينتقل fallback بعد فشل حالة تنفيذ حقيقية ما لم يعرّف backend/result أن الفشل هو `BACKEND_UNAVAILABLE` قبل تنفيذ العملية؛ هذا يمنع تكرار عملية حساسة.
7. يجري verification إن طلبه descriptor أو request، ثم ينتج `CapabilityResult` ويحوله adapter إلى `SystemControlResult` المتوافق مع السجل القديم.

> **قاعدة عدم التكرار:** لا يجوز تفعيل fallback خلفية أخرى بعد نتيجة غير مؤكدة لعملية تغير الحالة، مثل تثبيت APK أو مسح بيانات. يستخدم fallback فقط عندما لم يبدأ التنفيذ أو عندما تصرح النتيجة أنها غير قابلة للتنفيذ على الخلفية الأولى.

## حد الأمان

تعد Root وShizuku وADB حدود تنفيذ داخلية. لا تقبل أي من هذه الخلفيات `command` مصدره المستخدم. تدخلها هو نموذج operation مصدق مثل `ForceStopPackage(packageName)`، ويتولى backend توليد command مع `SafeCommandBuilder` واقتباس argument. ينظم `CapabilityDescriptor` مستوى الامتياز والمخاطر، ويمنع runtime الطلب إذا لم تكن الخلفية ضمن policy أو الصلاحية غير مؤكدة.

لا تُحفظ كلمات مرور أو رموز OAuth أو محتوى request سري أو command حساس في `CapabilityResult.metadata`. وتعرض النتائج العامة packageName/نسخة فقط بعد ضبط الطول، مع رسائل error ثابتة قابلة للترجمة.

## التوافق والهجرة

تستمر `ActionRegistry` و`SystemControlResult` خلال المرحلة الأولى. يضيف `CapabilityActionHandler` الأنواع الجديدة ولا يغير معنى ActionType قديم. يملك `ActionExecutionContext` runtime اختياريّاً كي لا تنكسر الاختبارات أو مسار workflow compatibility. تُسجل metadata المنظمة أولاً ضمن action results الحالية؛ ولا يضاف جدول جديد أو migration إلا عندما يستقر العقد وتوجد بيانات فعلية تستحق الاستعلام.

## خطة الاختبار للعقد

تختبر وحدة النطاق enum/status/policy وقرار retries. وتختبر `core:execution` ترتيب resolver، حجب policy، رفض escalation، عدم fallback بعد عملية بدأت، وتحويل result. وتختبر `core:rom-integration` quoting للـ package names، availability لـ Root/Shizuku، وفروع PackageManager/PackageInstaller القابلة لـ Robolectric. أما Root/Shizuku الحقيقيان فيبقيان اختبارات تكامل اختيارية تحمل سبب التخطي بوضوح.
