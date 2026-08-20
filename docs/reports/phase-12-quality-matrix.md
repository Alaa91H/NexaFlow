# المرحلة 12 — مصفوفة الجودة والحدود

## بوابات ناجحة أثناء التنفيذ

| البوابة | النتيجة |
|---|---|
| `:core:plugin-sdk:testDebugUnitTest` | ناجحة |
| `:core:execution:testDebugUnitTest` | ناجحة؛ تتضمن 177 اختباراً بعد إضافة جسر PluginBackend واختبار ordered broadcast الحقيقي |
| `:data:compileDebugKotlin` | ناجحة قبل إضافات فهرس dependencies اللاحقة |
| `:core:automation-engine:compileDebugKotlin` | ناجحة |
| `:feature:automation-builder:compileDebugKotlin` | ناجحة قبل بطاقة الصحة اللاحقة |
| `:app:compileDebugKotlin` | ناجحة بعد تسجيل PluginDiscoveryRegistry وPluginBackend في Hilt، ونجحت مرة أخرى بعد توسعة settings capability |
| `git diff --check` | ناجحة في التدقيق النهائي الثابت |

## فحوص ثابتة لاحقة

بعد آخر بوابة تجميع، أضيفت اختبارات وقيم محدودة لـ workflow، adapter values، dependency scanner، وبطاقة صحة الإضافات. تم تدقيق مراجع Hilt وresources ومواضع القدرة والملفات غير المقصودة بـ `git diff --check` وعمليات جرد المصدر. لم يشغل هذا التسليم بناءً أو APK إضافياً بعد تلك الإضافات.

## تغطية السيناريوهات

| السيناريو | الاختبار أو الحارس |
|---|---|
| manifest بلا بروتوكول أو بقدرات مكررة | `PluginModelsTest` و`PluginManifestValidator` |
| pairing setting activity/receiver | `PluginDiscoveryRegistryTest` |
| action plugin موافق عليه ووصل receiver | `PluginCapabilityBackendTest` مع Robolectric ordered broadcast |
| instance mismatch | `PluginCapabilityBackendTest` يرفض قبل fire |
| Action legacy | `CapabilityActionMapperTest` يحافظ على handler التوافقي |
| JSON Plugin تالف | `parseJsonStrict` وPluginBackend يرفض قبل Bundle فارغ |
| page settings غير مسموحة | descriptor allowlist و`CapabilityRequestValidator` |
| قيمة plugin متداخلة أو غير صالحة | `PluginValueRuntimeAdapterTest` |
| loop ضخمة أو cancellation | `WorkflowInterpreterTest` |
| import dependency metadata مزيفة | `BackupManager.preflight` يعيد اشتقاق index من actions |

## حدود الإصدار

لا يوجد في هذا النطاق event plugin أو condition plugin adapter منتج؛ المعرفات التصميمية لا تسجل في catalog ولا تظهر في UI. ولا يوجد دعم Root أو shell أو Shizuku أو ADB ضمن طبقة الإضافات الجديدة. أما `PluginFireHandler` الموجود فهو compatibility fallback للإجراءات القديمة فقط؛ الإدخالات الجديدة ذات instance والموافقة تعبر `PluginCapabilityBackend`.
