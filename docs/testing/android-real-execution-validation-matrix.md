# NexaFlow Android Real-Execution Validation Matrix

**خط الأساس:** `v3.33.0` (`ae478fd`)
**الهدف:** تحويل الادعاءات المدعومة باختبارات JVM/Robolectric إلى أدلة Android متصلة حيث يكون ذلك آمناً وحتمياً، وإبقاء المسارات التي تحتاج جهازاً مادياً أو امتيازاً خارجياً مصنفة بوضوح حتى تتوفر بيئتها.
**حدود المرحلة:** لا إعادة تصميم، ولا Runtime/EventBus/Queue جديد، ولا capability تخميني، ولا نجاح مزيف عند غياب البيئة.

> لا يمنح هذا الملف أي تصنيف تحقق. يمنح التصنيف فقط بعد تسجيل اسم اختبار منفذ فعلاً، الجهاز، API، المتطلبات المسبقة، النتيجة، والسجل المرفق. وجود مصدر اختبار AndroidJUnit4 وحده لا يكفي.

## قواعد الأدلة

| النتيجة | شرط تسجيلها |
|---|---|
| `ANDROID_INTEGRATION_TESTED` | نجح `connectedAndroidTest` على جهاز/محاكي متصل موثق، واستدعى المسار المنتج الحقيقي وحقق الأثر الخارجي أو read-back المناسب. |
| `REAL_DEVICE_VERIFIED` | نجح مسار يحتاج بيئة مادية/امتيازاً لا يحاكيه المحاكي بأمان، مع توثيق الجهاز والحالة والنتيجة. |
| `REAL_DEVICE_REQUIRED` | القدرة لا يجوز ترقيتها في غياب جهاز يحقق متطلبها، مثل Root/Shizuku/Accessibility/Plugin خارجي/OEM. هذا حقل **حاجة تحقق** وليس تصنيف القدرة النهائي. |
| `ENVIRONMENT_UNAVAILABLE` | لا يوجد جهاز أو إضافة أو امتياز أو API لازم. لا يعني ذلك فشل التنفيذ. |
| `IMPLEMENTATION_FAILURE` | توفرت المتطلبات المسبقة، وشغّل الاختبار المسار الحقيقي، ثم أخفق الأثر المتوقع أو verification. |

## مصفوفة التنفيذ

| المجال | المسار المنتج المراد إثباته | اختبار متصل آمن مقترح | الأثر أو verification المطلوب | المتطلبات المسبقة | الحالة قبل التشغيل |
|---|---|---|---|---|---|
| Workflow execution | `ExecutionEngine.runAutomation` → handlers/capabilities → history/checkpoint | Automation داخلي يستخدم actions غير مميزة وآمنة | `ExecutionRecord` + history + checkpoint cleanup | جهاز API 26+، app debug مثبت | مرشح Android integration |
| Native actions | `ActionRegistry` → handler → Android public API | clipboard, notification/reminder, URL intent، public settings عند توفرها | read-back أو observable system state | صلاحية كل فعل | مزيج integration/real-device-required |
| Native triggers | source → adapter → EventBus/TriggerIndex حيث ينطبق → workflow | screen/battery/charging/network/package/timer فقط إن كانت deterministic | timeline/history + dedup/exit | broadcast/device state قابل للتحكم | مرشح جزئي |
| Conditions | `AutomationConstraintGate` → local snapshot أو capability | شرط محلي deterministic + blocked run | gate result + عدم تشغيل action | صلاحية القراءة | مرشح Android integration |
| Plugin action | saved action → `PluginCapabilityBackend` → external receiver | fixture Plugin APK فقط | discovery/config/invocation/result/verification | APK fixture مثبت ومرئي | REAL_DEVICE_REQUIRED |
| Plugin event | fixture event → `PluginEventReceiver` → ingress → bus/index/router | fixture plugin على API 34+ | event + matched workflow + dedup | API 34+، sender identity، trigger موافق عليه | REAL_DEVICE_REQUIRED |
| Plugin condition | fixture condition → `PluginConditionBackend` → gate | fixture Plugin APK | S/U/Unknown + workflow gate | APK fixture مثبت | REAL_DEVICE_REQUIRED |
| Shizuku | capability request → resolver → UserService → typed operation | operation غير مدمرة على package fixture أو setting allowlisted | external read-back + service lifecycle states | Shizuku running/granted/UserService | REAL_DEVICE_REQUIRED |
| Root | capability request → Root backend → typed operation | operation محصورة على package fixture/file sandbox | package/file read-back + deny/timeout | جهاز rooted و`su` صالح | REAL_DEVICE_REQUIRED |
| ADB | `AdbCapabilityBackend` | لا يوجد test نجاح؛ يثبت structured unavailability فقط | `ADB_UNAVAILABLE` | لا قناة ADB in-app | UNSUPPORTED by design |
| Accessibility | workflow → capability backend → service → test-app UI node | fixture UI deterministic | UI node/value/scroll/read-back | service enabled + consent + Play-safe fixture | REAL_DEVICE_REQUIRED |
| Package operations | typed Root/Shizuku backend | package fixture enable/disable/force-stop فقط | PackageManager/read-back | Root أو Shizuku | REAL_DEVICE_REQUIRED |
| Notifications | listener/reminder/action receiver | reminder notification + controlled notification fixture | posted notification/action result | `POST_NOTIFICATIONS`; listener access حيث يلزم | مرشح جزئي |
| Network | `HttpRequestHandler` → production transport | loopback/local deterministic HTTP server في androidTest | status/body/idempotency/retry observation | `INTERNET`، server fixture | مرشح Android integration |
| Files | public app-scoped files أو typed privileged copy | app-scoped file create/hash/copy/read | path/hash/read-back | storage policy؛ Root/Shizuku للtyped copy | مزيج integration/real-device-required |
| Location | `LocationMonitor` → actual location callback | emulator/device mock location فقط إن كان provider قابل للضبط | workflow record + enter/exit | location grant/location enabled | REAL_DEVICE_REQUIRED لتأكيد الخلفية |
| Sensors | `SensorMonitor` → SensorManager callback | فقط جهاز يملك sensor مناسباً | trigger/exit record | hardware sensor | REAL_DEVICE_REQUIRED |
| Scheduling | AlarmManager/reminder/task scheduling | future alarm + cancellation + duplicate/checkpoint behavior | receiver/history + cancel read-back | exact-alarm access عند required | مرشح جزئي |
| Flow control | `WorkflowInterpreter` | internal action executor harness داخل androidTest | retry/timeout/race/while outcomes | لا صلاحية | مرشح Android integration، لكن لا يثبت actions الخارجية |
| Typed data | `RuntimeValue`/`ScopedDataRuntime`/`WorkflowRunContext` | serialization + execution-scoped handoff | typed values/context output | لا صلاحية | مرشح Android integration |
| Verification | `CapabilityExecutionService.verify` أو read-back policy | capability-specific only | external state، لا return value فقط | حسب capability | مزيج |
| Checkpoints | `ActiveExecutionStore` | durable write/reload across test process boundary حيث ممكن | checkpoint status/cursor/idempotency | DataStore | مرشح Android integration |
| Recovery | `ExecutionRecoveryCoordinator` | controlled persisted checkpoints، لا blind replay | disposition + required recovery state | DataStore | مرشح Android integration؛ kill process الحقيقي لاحقاً |
| Cancellation | `TaskManager`/workflow/handler | queued and running cancellation of deterministic work | terminal state + no leaked lease | لا صلاحية | مرشح Android integration |
| Retry | `TaskManager`/HTTP retry | deterministic local failure-then-success server/task | attempts + stable idempotency + terminal state | server fixture | مرشح Android integration |
| Resource limits | monitor stop/unsubscribe, queue timeout/deadline | service/monitor lifecycle where deterministic | registrations/jobs released | relevant grants | مرشح جزئي |
| Performance | macrobenchmark/baseline profile | startup only، ثم dedicated workflow benchmarks عند توفر الجهاز | published timings/memory methodology | connected device | CONTRACT_ONLY حتى تنفيذ القياس |
| Battery/background | monitors/alarms/FGS | soak/device telemetry | wakeups, lifecycle, consumption methodology | physical AOSP+OEM devices | REAL_DEVICE_REQUIRED |

## سجل الجهاز المطلوب لكل تنفيذ

| الحقل | قيمة مطلوبة |
|---|---|
| Device ID | ADB serial غير حساس أو اسم مستعار ثابت |
| Manufacturer/model | الشركة والطراز |
| Android/API/ABI | نسخة النظام وAPI والبنية |
| Environment | emulator / AOSP-like physical / OEM physical |
| Root/Shizuku/Accessibility | available/granted/enabled مع وقت الالتقاط |
| Test command | أمر الاختبار المنفذ فعلاً |
| Artifact | XML، logcat مقصوص، وبيان أثر خارجي خالٍ من الأسرار |
| Classification decision | النتيجة، والسبب، وأي limitation |

## بوابة الترقية

لا تنتقل أي قدرة من `UNIT_TESTED_ONLY` أو `PARTIAL` إلى `ANDROID_INTEGRATION_TESTED` إلا بعد تحقق الآتي مجتمعة:

1. اختبار AndroidJUnit4 أو connected test منفذ فعلاً، لا مجرد source.
2. استدعاء المسار الإنتاجي، لا fake backend أو fake transport يخفي نقطة التكامل المقصودة.
3. تحقق من الأثر الخارجي/read-back عندما تكون العملية ذات side effect.
4. حفظ artifact يوضح البيئة والنتيجة.
5. فصل عدم توفر البيئة عن فشل التنفيذ.

القدرات التي تعتمد Root أو Shizuku أو Accessibility أو Plugin خارجي أو OEM behavior لا تُرقى في محاكي عام، بل تبقى موثقة بـ `REAL_DEVICE_REQUIRED` حتى تتوفر البيئة المطابقة.
