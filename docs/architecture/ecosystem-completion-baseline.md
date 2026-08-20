# خط أساس إكمال منظومة NexaFlow

**تاريخ التدقيق:** 19 أغسطس 2026
**نقطة الأساس:** `v3.32.0` مع شجرة عمل محلية تتضمن توسعة Plugin Action غير مرفوعة.

## القواعد غير القابلة للكسر

يبقى لكل من `WorkflowInterpreter` و`ExecutionEngine` و`TaskManager` و`NexaFlowEventBus` و`TriggerIndex` و`CapabilityResolver` و`CapabilityExecutionService` ومسار checkpoints/recovery نسخة واحدة فقط. يجب أن تكون كل إضافة adapter أو backend أو catalog entry يغذي هذه النسخ القائمة، ولا يجوز أن ينشئ scheduler أو interpreter أو event dispatcher موازياً.

## الوضع المثبت

| المجال | الحالة الحالية | الدليل | الأثر |
|---|---|---|---|
| Plugin action | موسع محلياً | `PluginDiscoveryRegistry` و`PluginCapabilityBackend` و`CapabilityActionMapper` | المسار الجديد يمر بالـ resolver والسياسة؛ legacy يبقى fallback |
| Plugin events | منفذ بحدود مقيدة | `PluginEventReceiver` → `PluginEventIngress` → `NexaFlowEventBus` → `TriggerIndex` → `PluginEventRouter` | Android 14+ فقط؛ يتحقق من sender package وcomponent/approval، payload primitive-only، dedup وrate limit؛ لا يستدعي receiver المحرك |
| Plugin conditions | منفذ بtyped tri-state | `PluginConditionBackend` و`AutomationConstraintGate` و`ConditionResult` | `UNKNOWN` و`UNAVAILABLE` لا يتحولان إلى `FALSE`؛ condition مرتبط بـ instance محفوظ وموافق عليه |
| EventBus/TriggerIndex | موجودان لكن الاستعمال غير كامل | `PackageMonitor` و`NotificationTriggerMonitor` و`AppTriggerAccessibilityService` تستدعي `ExecutionEngine` مباشرة | يجب توحيد المصادر تدريجياً قبل إضافة sources جديدة |
| Root/Shizuku | خلفيات typed مسجلة للعمليات المراجعة | `PrivilegedCapabilityCatalog`, `ShizukuCapabilityBackend`, `RootCapabilityBackend`, `PrivilegedOperation` | لا generic workflow shell؛ كل طلب يختار قناة واحدة صراحةً ويخضع لـ allowlist |
| Shizuku API | UserService AIDL فقط للعمليات الجديدة | `ShizukuShellBridge.executeOperation()` و`IUserShellService.executeOperation()` | أزيل `Shizuku.newProcess` وreflection؛ عدم اتصال binder يعيد حالة غير متاحة بلا fallback صامت |
| ADB | backend تشخيصي فقط غير مسجل إنتاجياً | `AdbCapabilityBackend` | يعيد `ADB_UNAVAILABLE` في تطبيق Android عادي ولا يعلن قناة وهمية |
| Accessibility | backend مقيّد مسجل مع جسر الخدمة | `AccessibilityCapabilityBackend` و`AccessibilityInteractionBridge` و`AppTriggerAccessibilityService` | يتطلب إفصاحاً محفوظاً واختيار backend صريحاً وحزمة نافذة نشطة وselectors محددة؛ trigger foreground القديم ما زال مساراً مباشراً يحتاج توحيداً لاحقاً |
| conditions | Boolean/minimal | `Constraint.kt` | يحتاج عقد result typed ثلاثي/خماسي منفصل |
| privileged outputs | coarse | `SystemControlResult(success,message)` | يظل compatibility boundary ولا يساوي CapabilityResult المنظم |

## المخاطر التي ثبتت ويجب علاجها

> `PrivilegedRunner.runShell(command)` و`UserShellService.exec(command)` يقبلان shell command عاماً ولو مر عبر validate. هذا لا يحقق متطلب منع generic shell من workflow، ولا يصلح كأساس لقدرات جديدة.

> أزيل fallback `Shizuku.newProcess` وreflection من `ShizukuShellBridge`. تستخدم الخلفيات الجديدة `UserService` AIDL لعمليات typed فقط؛ يبقى method legacy محصوراً لتوافق `SystemController` القائم ولا يمثل capability workflow.

> عدة مصادر trigger حالية، ومنها `AppTriggerAccessibilityService`، تتجاوز `NexaFlowEventBus` و`TriggerIndex` وتصل مباشرةً إلى `ExecutionEngine`. أي adapter event جديد لا يجوز أن يقلد هذا النمط؛ بدلاً من ذلك يجب أن يمر EventSource → EventAdapter → EventBus → TriggerIndex، ثم تنقل المصادر القديمة بالتدرج. Backend Accessibility الجديد لا يستخدم هذا المسار المباشر.

## حدود التنفيذ التالية

1. لا يضاف `PLUGIN_EVENT` أو `PLUGIN_CONDITION` إلى catalog أو UI قبل توثيق protocol public، ومصادقة source، وحدود payload، ونتيجة typed قابلة للاختبار. التنفيذ الحالي يقتصر على `PLUGIN_EVENT` الموثق في Android 14+ حيث تثبت هوية sender؛ لا يوجد قبول عام غير موثق على الإصدارات الأقدم.
2. لا يعرض Root أو Shizuku أو ADB generic command capability. فقط operation ids ومعاملات allowlisted وbuilder داخلي يمكن التحقق منه.
3. لا يمر `Bundle` أو `Parcelable` أو `Intent` أو Binder أو object خارجي إلى `RuntimeValue`.
4. كل backend مميز يعيد `UNAVAILABLE`/`DENIED`/`TIMEOUT`/`ERROR` منظمة ولا يعمل fallback صامت بين مستويات الامتياز.
5. تبقى actions وtriggers والإعدادات القديمة متوافقة؛ تتغير routes الجديدة فقط عندما تحمل metadata/approval صريحين أو عندما يتم migration additive مثبت.
