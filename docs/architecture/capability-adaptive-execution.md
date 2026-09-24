# Capability-Adaptive Execution — Operation Registry & Router

> **الحالة:** تصميم مُنفَّذ (Phase A–C)؛ Phase C يضيف التخطيط الدلالي المسبق الموحّد مع مسار التنفيذ.
> الوثيقة تشرح البنية كما هي في الكود فعليًا، لا كما هي مرغوبة.

## 1. القرار المعماري

NexaFlow يمتلك **مسار قرار تنفيذ واحد** لكل عملية دلالية:

```
Action (serialized config)
   ↓ LegacyActionAdapter
TypedOperationRequest (operation + typed parameters)
   ↓ CapabilityRouter
OperationSpec (registry lookup)
   ↓ CandidateStrategyResolver (per-strategy candidates with reasons)
   ↓ least-privilege + evidence/health ranking
Strategy execution (typed backend call — never a workflow shell string)
   ↓ Postcondition verification
OperationOutcome (SUCCESS / PARTIAL / PENDING_USER_ACTION / UNSUPPORTED / FAILED / CANCELLED / UNKNOWN)
   ↓ Evidence + Health recording
Safe fallback / reconcile when outcome is UNKNOWN or transport-level
```

**ما الذي قرره التدقيق:**
- `ExecutionEngine` و`WorkflowInterpreter` و`ActionRegistry` تبقى كما هي (لا محرك موازٍ).
- `CapabilityResolver`/`CapabilityExecutionService` تبقى — الراوتر الجديد يبنى **فوقها** ويستخدم نفس الـ backends.
- `ProviderSelector` يُحصر في تزويد diagnostics/compatibility engine بقراءة قناة عرض فقط؛ لم يعد جزءًا من قرار تنفيذ العملية الجديدة.
- `SystemController` يبقى facade للعمليات غير المُرحّلة؛ لا يُستخدم للعمليات المُرحّلة.
- `PrivilegedRunner` يصبح transport detail خلف typed backends (كما هو اليوم) — لا يستقبل نصًا من workflow.

## 2. الطبقات الجديدة (كلها في `core/execution/capability/semantic`)

| الطبقة | الملف | المسؤولية الوحيدة |
|---|---|---|
| `SemanticOperationId` | `SemanticOperation.kt` | معرّف دلالي دقيق (WIFI_GET_STATE, WIFI_SET_STATE, …) |
| `OperationParameterSchema` | `OperationParameters.kt` | عقد typed للمدخلات: required/optional، نوع، مجال أرقام، قيم مسموحة؛ **رفض غياب قيمة مطلوبة بدل default خطير** |
| `OperationSpec` | `OperationSpec.kt` | العقد الكامل: min/max API، hardware features، risk، idempotency، retry safety، side effect، verification، compensation، strategies، consent |
| `OperationRegistry` | `OperationRegistry.kt` | المصدر الوحيد لتعريفات العمليات؛ يفشل على التكرار |
| `CapabilityStrategy` | `CapabilityStrategy.kt` | تنفيذ عملية واحدة بطريقة واحدة + availability + verify |
| `OperationStrategyResolver` | `OperationStrategyResolver.kt` | ينتج `StrategyCandidate` لكل استراتيجية مسجلة مع سبب/ثقة/دليل — لا scoring opaque |
| `CapabilityRouter` | `CapabilityRouter.kt` | **المصدر المركزي لقرار "كيف أنفذ هذه العملية الآن"** — يرشّح حسب compatibility ثم policy ثم availability ثم evidence/health ثم ثقة |
| `OperationOutcome` | `OperationOutcome.kt` | نتيجة موحدة تشمل `UNKNOWN` (transport timeout/بعد side effect محتمل) |
| `CapabilityEvidenceStore` | `CapabilityEvidenceStore.kt` | أدلة نجاح/فشل لكل (operation, strategy, fingerprint) — بلا أسرار أو أوامر خام |
| `StrategyHealthTracker` | `StrategyHealthTracker.kt` | UNKNOWN/HEALTHY/DEGRADED/FAILED/TEMPORARILY_UNAVAILABLE/DISABLED + cooldown |
| `DeviceFingerprint` | `DeviceFingerprint.kt` | manufacturer/model/API/securityPatch/ROM family + hardware flags |
| `EnvironmentEventBus` | `EnvironmentEvents.kt` | أحداث بيئة (shizuku death, root grant, accessibility…) تُلغي أدلة الاستراتيجيات المتأثرة **فقط** |

## 3. قواعد الراوتر (مرتّبة، قابلة للتفسير)

1. **Compatibility**: العملية غير مدعومة على API/جهاز ⇒ `UNSUPPORTED` قبل أي تنفيذ.
2. **Security/policy**: استراتيجيات privileged تُستبعد إلا مع explicit opt-in (نفس قاعدة `allowPrivilegedBackends` الحالية).
3. **User policy**: preferred/allowed backends من الطلب.
4. **Availability الحي**: availability لكل استراتيجية مُسأل مباشرة.
5. **Evidence**: استراتيجية نجحت وverified على نفس الـ fingerprint تأخذ ثقة أعلى.
6. **Health**: استراتيجية في cooldown/degraded تُرشّح أخيرًا أو تُستبعد.
7. **Least privilege**: الترتيب الافتراضي when-everything-equal: Android API → WRITE_SETTINGS → Accessibility → Shizuku → Root. Root لا يُختار لمجرد توفره.
8. **الثقة**: candidate يعيد `confidence` + `reason` (نص تفسيري للدوائر الداخلية/diagnostics).

## 4. Fallback وUNKNOWN (التعاقد)

- فشل **transport-level** (binder died, server not running, process spawn failed) ⇒ الانتقال للاستراتيجية التالية **مسموح**.
- نتيجة **UNKNOWN** (timeout بعد إمكانية حدوث side effect) ⇒ **لا fallback أبدًا**؛ يُنفَّذ reconcile: قراءة الحالة الفعلية عبر استراتيجية GET ⇒ إما `SUCCESS (verified)` أو `FAILED`.
- فشل **postcondition** (نفّذ والنتيجة مخالفة) ⇒ `FAILED`/`PARTIAL` مع verification metadata؛ fallback فقط إذا أعلن الـ spec العملية retry-safe.
- العملية non-idempotent لا تُعاد إطلاقًا بدون دليل (نفس قاعدة `CapabilityRetrySafety.UNSAFE` الحالية).

## 5. عقد Verification

- كل `OperationSpec` تعلن `verification: REQUIRED | BEST_EFFORT | NONE`.
- `REQUIRED` بلا verifier في استراتيجية ⇒ فشل وقت التسجيل (parity test)، لا وقت التشغيل.
- `EXECUTION_SUCCESS != VERIFIED_SUCCESS` — النتيجة النهائية تعكس الـ verification.
- لا verification وهمية: إن لم توجد قراءة موثوقة، يكون `attempted=false` والوضع يبقى غير مُثبَت.

## 6. الترحيل (Migration)

- **المرحلة A**: البنية أعلاه + عمليات Wi-Fi/Bluetooth/Rotation/Brightness/ScreenTimeout/Location/NFC/Hotspot/AirplaneMode/DND.
- **المرحلة B**: `CapabilityActionMapper` يرحّل الأنواع المُصنّفة للراوتر عبر `SemanticActionRouter` — بقية الأنواع تستمر عبر مساراتها الحالية بلا تغيير. مكتمل أيضًا: **`ShizukuTypedStrategy`** (مسار Shizuku typed عبر `PrivilegedRunner.runShizukuOperation` بـ argv مغلق عبر UserService AIDL — الصلاحية وحدها لا تكفي، الحصول يتطلب UserService bound)، وربط `EnvironmentEventWiring` الحقيقي: كل انتقال فعلي في دورة حياة Shizuku (binder received/dead، UserService connected/disconnected) ينشر `ShizukuStateChanged` فيسبطل إبطالًا مستهدفًا لدليل استراتيجية Shizuku فقط.
- **المرحلة C**: `CapabilityRouter.plan()` يستخدم نفس التحقق والترشيح والترتيب الحي من دون أي side effect، ثم `SemanticWorkflowPlanner` يجمع الخطة لكل main action و`SET_VALUE` end behavior وexit action. المسار الذي لا يملك سوى `SETTINGS_USER_ACTION` يصبح `PENDING_USER_ACTION` ولا يُعتبر صالحًا للتنفيذ الآلي. Builder وDry Run و`ExecutionEngine` يستهلكون الخطة نفسها، بينما التنفيذ يعيد التخطيط مباشرة قبل الـ side effect ولا يثق بنتيجة preflight كتصريح مخزّن.
- **لا يُحذف أي مسار قديم** قبل أن يغطيه الراوتر بعمليات equivalent ومثبتة (parity tests).
- `DeviceStateSnapshot` وEndBehavior واستعادة الحالة تبقى كما هي؛ الراوتر يُستخدم فيها عبر نفس mapper لاحقًا.

## 7. الأمان (غير قابل للتفاوض)

- الراوتر لا يقبل ولا يبني نص shell. كل استراتيجية تبني argv/typed call بنفسها.
- `PrivilegedOperation` هو الشكل الوحيد الذي يعبر حدود Root/Shizuku.
- الـ evidence لا تحفظ: أسرار، أوامر خام، قيم parameters الحساسة (SSID/كلمات مرور). تحفظ: operation/strategy/fingerprint/result/latency/verification/عدادات.
- plugin لا يصل للراوتر إلا عبر `PLUGIN_ACTION` الموجود — لا backend خام جديد.

## 8. الاختبار

- **Unit pure**: resolver/ranker/health/evidence/UNKNOWN-reconcile (JVM).
- **Robolectric**: قراءة الحالة الحقيقية عبر Settings/WifiManager/LocationManager على الأجهزة الافتراضية.
- **Parity gates**: عملية مسجلة بلا استراتيجية = فشل؛ strategy بلا verifier لكن verification REQUIRED = فشل.
- **الجهاز الحقيقي**: يبقى خارج نطاق CI؛ يُصادق يدويًا ويُسجل NOT TESTED حيث لم يتم.
- **Preflight parity**: اختبار الخطة يثبت أن اختيار الاستراتيجية لا ينفّذها، وأن Settings-only يمنع التشغيل الآلي قبل أول side effect، وأن Dry Run وBuilder يحصلان على نفس قرار الراوتر.
