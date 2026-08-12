# 🚀 تقرير معمارية تنفيذي متكامل — منصة أتمتة المهام (Automation & iPaaS)

> **نطاق التقرير:** تحليل معماري مجهري لمشروع NexaFlow («المهام المجدولة») — محرك أتمتة على الجهاز
> (On-Device Engine) اليوم، وتطوره نحو منصة iPaaS هجينة (جهاز + سحابة) بمعايير 2026.
> كل حكم تقني هنا مبني على البنية الفعلية في المستودع (وحدات `core/*`, `feature/*`,
> `compileSdk/targetSdk 37`, `minSdk 26`, Java 21 toolchain، +472 اختباراً) وليس تنظيراً.
> **Last verified against the working tree:** August 2026.

---

## 1. الهيكلية المعمارية لمنصة الأتمتة — مسار الحدث من Webhook حتى نهاية السلسلة

### 1.1 الحالة الحالية (On-Device Engine — ما يعمل اليوم فعلاً)

```
                              ┌────────────────────────────────────────────┐
                              │  app (Hilt graph, M3 theme, Navigation)    │
                              └───────────────┬────────────────────────────┘
                                              │
        ┌─────────────────────────────────────┼─────────────────────────────────────┐
        │            feature/* (UI)           │              core/* (المحرك)        │
        │  builder ── dashboard ── automations│                                       │
        │  settings ── themes ── widgets      │                                       │
        │  history ── icons ── capability-ctr │                                       │
        └─────────────────────────────────────┼─────────────────────────────────────┘
                                              ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                        core/automation-engine (MonitoringService — FGS)          │
│                                                                                  │
│   EVENT SOURCES (EventSource/TriggerSource) — كل مصدر يسجّل نفسه كقناة أحداث:     │
│   ┌─────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌─────────┐ ┌────────────┐ │
│   │Battery  │ │Device    │ │Connectivity│ │Location  │ │Bluetooth│ │RingerMode │ │
│   │Monitor  │ │EventMon  │ │Monitor    │ │Monitor   │ │Monitor  │ │Monitor     │ │
│   └────┬────┘ └────┬─────┘ └─────┬─────┘ └────┬─────┘ └────┬────┘ └─────┬──────┘ │
│   ┌─────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌─────────┐ ┌────────────┐ │
│   │Calendar │ │Sensor    │ │RomSetting │ │Webhook   │ │Cellular │ │SMS/App     │ │
│   │Monitor  │ │Monitor   │ │Monitor    │ │Server    │ │Monitor  │ │Listeners   │ │
│   └────┬────┘ └────┬─────┘ └─────┬─────┘ └────┬─────┘ └────┬────┘ └────────────┘ │
│        ▼           ▼             ▼            ▼            ▼                     │
│   ┌────────────────────────────────────────────────────────────────────────────┐ │
│   │           Event Dispatcher (كوروتين على ApplicationScope، مصفّاة)          │ │
│   │   automations.filter{enabled} → trigger match → constraints → cooldown     │ │
│   └───────────────────────────────────┬────────────────────────────────────────┘ │
└───────────────────────────────────────┼──────────────────────────────────────────┘
                                        ▼
                        ┌───────────────────────────────┐
                        │  core/execution/ExecutionEngine│
                        │  runAutomation(automation)     │
                        │  runExit(automation)           │
                        └───────┬───────────────┬────────┘
                                ▼               ▼
                   ┌──────────────────┐  ┌────────────────────┐
                   │ ACTION HANDLERS  │  │ EXIT BEHAVIOR      │
                   │ (صوت/رنين/حجم،    │  │ (عند انتهاء الشرط   │
                   │ شبكة 2G-5G،       │  │  → إجراءات منفصلة)  │
                   │ واي-فاي هوتسبوت،  │  └────────────────────┘
                   │ إشعار/تذكير،      │
                   │ إعدادات ROM،      │
                   │ NFC plugin…)      │
                   └────────┬─────────┘
                            ▼
              ┌───────────────────────────┐
              │ Persistence & History     │
              │ Room + Migrations         │
              │ DataStore (prefs)         │
              │ KeystoreSecureStorage     │
              └───────────────────────────┘
```

**مسار الحدث الحالي (مثال Webhook):**
`طلب HTTP → WebhookServer (embedded, core/automation-engine) → ربط sourceId=WEBHOOK → مصفّاة المشغّلات → ConstraintStateReader (بطارية/شحن/موقع) → cooldown → ExecutionEngine → handlers → سجل history`

**الخصائص التي تعمل اليوم (أساس التقرير):**
- محرك حدثي على الجهاز: كل مراقب = `EventSource` بقناة مستقلة + تجميع عبر الـ `ApplicationScope`.
- جداول زمنية ذرية قابلة للاختبار: `TimeTriggerCalculator`, `BatteryTriggerMatcher` (منطق نقي JVM).
- شبكة أمان دورية للبطارية كل 60 ثانية (zero-wakeup، ملتصقة بـ FGS، skip-if-unchanged).
- قيود (Constraints) تُقرأ قبل التنفيذ + سلوك «عند انتهاء الشرط» (`runExit`).
- مصادقة DSN اختيارية (SentryReporter) + اختبار MergedManifestNoSentryTest يضمن إقلاعاً بلا DSN.
- تكامل ROM: EvolutionXSettingsBridge, RomSettingMonitor, SystemAppInstaller, RomDetector.
- أمان: KeystoreSecureStorage + Shizuku للامتيازات، خصوصية إشعارات M3.

### 1.2 البنية المستهدفة (Hybrid On-Device + Cloud iPaaS — مرحلة التوسع)

```
Webhook / OAuth / Schedule ──► Cloud Gateway (K8s Ingress + API Gateway)
                                    │
        ┌───────────────────────────┼───────────────────────────────┐
        ▼                           ▼                               ▼
┌─────────────────┐      ┌────────────────────┐        ┌────────────────────┐
│ Event Ingest    │      │ Message Broker     │        │ Control Plane      │
│ (Webhook →      │      │ Kafka (مواضيع      │        │ (Workflow Registry │
│  DLQ فورية)     │      │  per-tenant +      │        │  DAGs, Secrets,    │
│                 │      │  Event Log)        │        │  Rate Limits)      │
└────────┬────────┘      └─────────┬──────────┘        └─────────┬──────────┘
         │                        │                            │
         ▼                        ▼                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        WORKFLOW RUNTIME (Go + Temporal)                  │
│  DAG Compiler → Decision Tasks → Activity Tasks → State (Payload/Context)│
│  Retry (exponential+backoff) ── Idempotency keys ── Saga/Compensation    │
│  ┌──────────────┐ ┌───────────────┐ ┌──────────────────┐                 │
│  │ Trigger Node │→│ Action Node   │→│ Custom Code Node │→ (branch/loop)  │
│  └──────────────┘ └───────────────┘ └──────────────────┘                 │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────────┐
        ▼                   ▼                       ▼
┌──────────────┐   ┌────────────────┐   ┌────────────────────────┐
│ Connectors   │   │ Sandbox        │   │ Observability          │
│ (OAuth2/     │   │ (WASM +        │   │ (OpenTelemetry traces, │
│  Webhooks/   │   │  Firecracker)  │   │  per-node input/output │
│  Polling)    │   └────────────────┘   │  logs, DLQ + Alerts)   │
└──────────────┘                        └────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │  AI Layer: NL2Flow (Text→Workflow),    │
        │  Semantic Field Mapping, Error-Agent   │
        └───────────────────────────────────────┘
```

**الفرق الجوهري:** اليوم المحرك يعيش داخل عملية Android (المراقبون يسجّلون في الذاكرة)،
أما الهدف فمحرك سير عمل مستقل عن الواجهة، والأحداث تدخل عبر ناقل رسائل موزّع،
وDAG هو منتج من الدرجة الأولى (يُجمَّع ويُتحقق منه قبل التنفيذ).

---

## 2. جدول أدوات المنظومة (Tech Stack Matrix)

| الطبقة | التقنية المختارة | البديل | سبب التفوق |
|---|---|---|---|
| **لغة المحرك الأساسية (On-Device)** | Kotlin + Coroutines (موجودة) | RxJava | مدمجة في النظام البيئي الحالي، تفاضلية النفقات، `@ApplicationScope` يوزّع العمل عبر `Dispatchers`، تكامل كامل مع Compose |
| **لغة محرك السحابة (Workflow Runtime)** | Go | Node.js / Python | تزامن خفيف (goroutines لكل عقدة)، بدء فوري، كتابة أخطاء أقل في وقت التشغيل، أفضل أداء/تكلفة لآلاف الـ activity tasks |
| **سير عمل طويلة العمر (Durable Execution)** | Temporal | BullMQ / Celery | مدمج فيه exactly-once + retry + Saga + وضع إيقاف مؤقت/استئناف للأعوام؛ BullMQ يتطلب بناء كل هذا يدوياً |
| **ناقل الرسائل** | **Kafka** | Redis Streams / RabbitMQ | RabbitMQ: راوتر AMQP ممتاز لكن سجل الأحداث وإعادة التشغيل من بداية الحدث يحتاج التخزين. Redis: ذاكرة أولاً — لا سجل دائم بمعيار موزّع. Kafka يعطي **Event Log** قابل لإعادة التشغيل (Replay) وهو العمود الفقري لكل من `Exactly-once` و`DLQ` و`Audit` |
| **قاعدة بيانات المهام (Metadata)** | PostgreSQL (موجودة كهيكل Room على الجهاز؛ سحابياً PG + pg_partman) | MySQL / MongoDB | معاملات ACID لسجل المهام، JSONB لتخزين config العقد، تمدد معماري نظيف من مخطط Room الحالي (Room ↔ PG/JPA تعيين حقل-بحقل) |
| **تخزين الأحداث/القياس** | ClickHouse | Elasticsearch | استعلامات تحليلية على سجلات التنفيذ بعمودية عالية؛ ES أفضل بحث نصي لكنه أثقل تكلفة لتدفق السجلات |
| **واجهة البناء المرئي** | React Flow (@xyflow/react) — على الويب؛ وعلى الجهاز تبقى Compose | Rete.js / Drawflow | React Flow: أقدم وأقوى منظومة عقد (نماذج مخصصة، تحكم برمجي كامل بـ viewport، تكامل مع Zustand/Redux)، ومجتمع ضخم لدعم DAG |
| **عزل كود المستخدم (Sandbox)** | **WebAssembly (Wasmtime/Wazero)** + قفص Firecracker microVM للأكواد الأثقل | Docker لكل خطوة | Docker: دورة حياة ثقيلة (ثوانٍ لتشغيل حاوية) وفاتورة أمان أعلى لكل تنفيذ. Wasm: بدء ~ms، حدود ذاكرة صارمة، بلا وصول للنظام إلا عبر imports صريحة — أنسب لـ «Custom Code Steps» عالية التردد |
| **تخزين الأسرار** | Vault (Hashicorp) + KMS (تشفير مزدوج، دورة مفاتيح) | أعمدة مشفرة في DB | فصل مسؤوليات: Vault يملك الأسرار، KMS يملك المفاتيح؛ إبطال/تدوير فوري بلا إعادة نشر؛ شهادة SOC2 جاهزة |
| **الترخيص/الربط الخارجي** | OAuth2.0 (Authorization Code + PKCE) كأساس لكل Connector، Webhooks للدفع، Polling احتياطي | API Keys فقط | OAuth2 يمنح إبطالاً مركزياً وتفويضاً جزئياً؛ Webhooks يقلل الحمل (حدث واحد بدل استطلاع)، Polling يبقى للمصادر بلا webhook |
| **المراقبة** | OpenTelemetry (traces + metrics + logs موحّدة) → Prometheus → Grafana | سجلات نصية | تتبع موزّع من Webhook حتى آخر عقدة بمعرّف تنفيذ واحد؛ السجلات النصية لا تربط الطلب عبر الخدمات |
| **الذكاء الاصطناعي** | LLM عبر وكيل محدد الأهداف (وظيفة واحدة: تحويل نص→DAG بصيغة JSON Schema صارمة) + فحوصات مخطط | LLM حر بلا مخطط | كل خروج LLM يُتحقق منه ضد Schema للـ DAG قبل القبول (validation-first) — يمنع هلاوس العقد غير الموجودة |
| **المنطق الزمني** | Temporal Schedules + Cron بالثواني | AlarmManager وحده | على الجهاز AlarmManager يبقى للجدول؛ في السحابة Temporal Schedules توفر دقة+استمرارية عبر إعادة التشغيل |

---

## 3. جدول تفكيك وتحسين المنطق (Logic & Algorithm Matrix)

| الخوارزمية/الآلية | المنطق الحالي (On-Device) | الحل المطوَّر (Hybrid iPaaS) |
|---|---|---|
| **مطابقة المشغّلات (Trigger Match)** | `automations.filter{enabled}` لكل حدث + مطابقة `TriggerType` — فحص خطي لكل مهمة | **فهرس نشر/اشتراك**: عند حفظ المهمة تُسجَّل في `TriggerIndex (sourceId → ids)`؛ الحدث يصل للفهرس فيقرأ فقط المهام المشتركة بالقناة (O(1) بدل O(N)) |
| **إعادة المحاولة (Retry)** | محاولة واحدة + تجاهل فوري عند الفشل | **Exponential Backoff + Jitter** (`min(2^n*base, cap) + random(0,jitter)`) مع **Idempotency Key** لكل محاولة — يمنع ازدواج تنفيذ الإجراء الحسّاس (مثل تبديل الشبكة) عند إعادة المحاولة |
| **منع الازدواج (Deduplication)** | `activeBatteryTriggers` + `cooldownMillis` في الذاكرة | عتبة الجلسة في ذاكرة موزّعة (Redis) + **عتبة دائمة** في قاعدة البيانات (صعود/هبوط مُسجَّل) — ينجو من إعادة تشغيل أي عقدة |
| **تتبع الحالة بين العقد (Payload Context)** | لا يوجد سياق بين الخطوات (كل إجراء مستقل) | **Payload JSON Path** عبر ممر ضيق: كل عقدة تُعلن `input: "$.paths[].lat"` وتكتب `output: "$.result"` — تخزين دلتا فقط (JSON Merge Patch) بدل نسخ كامل يقلل الذاكرة/الشبكة لسير العمل الضخم |
| **حدود المعدل (Rate Limiting)** | لا يوجد — تحكم يدوي | **خوارزمية Token Bucket لكل Connector/tenant** (Redis Lua atomic) + **Backoff احتراماً لرؤوس `Retry-After`** في الاستجابات؛ تعليق سلس (throttle) بدل فشل جماعي |
| **مطابقة الخروج (Exit Matching)** | `runExit` عند انتهاء الشرط | **تعبيرات حالة على مستوى المحرك**: `when(trigger_state).is_inactive_for(5m) → runExit` — نفس الآلية لكن بجدولة زمنية مؤكدة عبر Temporal |
| **المزامنة الرائعة (Backpressure)** | غير موجودة — المعالجة فورية | **Consumer lag كإشارة تحكم**: لو تأخر الـ consumer عن الموضوع تجاوز حداً → تفعيل windowed processing (تجميع الأحداث المتشابهة) وتأجيل غير الحساس |
| **الفشل في منتصف السلسلة (Partial Failure)** | الإجراءات متسلسلة؛ فشل واحد يوقف الباقي | **Saga/Compensation**: كل عقدة قابلة للعكس تُسجَّل `compensate`؛ عند فشل العقدة 3 تُنفَّذ تعويضات 2 و1 بالعكس — اتساق نهائي بلا مهمة «نصف منفذة» |
| **معالجة ملايين المهام (Throughput)** | جهاز واحد، تزامن كوروتين | **Sharding**: التقسيم حسب `tenantId % partitions` مع **Consumer Group** لكل عقدة — موازاة أفقية بلا ترتيب مكسور داخل السلسلة الواحدة |
| **ضمان التسليم (Delivery)** | بث النظام (قد يُفقد) + شبكة أمان دورية | **At-least-once + Idempotency** (وليس exactly-once الحرفي): التنفيذ قد يتكرر لكن أثره لا — هذا هو exactly-once العملي المعتمد في Temporal |

---

## 4. خارطة طريق التنفيذ المجهرية (Micro-Implementation Roadmap)

> القاعدة: **كل مرحلة تُسلم شيئاً قابلاً للاختبار والاستخدام**، والانتقال ينتظر اجتياز اختبارات المرحلة.
> الأولوية: تحصين ما يعمل → توسيع النموذج الذري → سحابة → ذكاء.

### المرحلة 0 — تحصين الأساس (0–2 أسابيع) ✅ جزئياً منجز
- [x] شبكة أمان البطارية (60s، zero-wakeup) — منجز
- [x] ضمان إقلاع بلا DSN (MergedManifestNoSentryTest) — منجز
- [x] Java 21 toolchain واختبارات SDK 37 — منجز
- [ ] **TriggerIndex**: تحويل `filter{enabled}` الخطي إلى فهرس `sourceId → ids` مع `Flow` من قاعدة البيانات (أساس المرحلة 1)
- [ ] **Context Payload**: نموذج `WorkflowRunContext` (معرّف التنفيذ، متغيرات، دلتا النتائج) يمر عبر الـ ExecutionEngine

### المرحلة 1 — محرك DAG حقيقي (2–6 أسابيع)
- [ ] نموذج `DagNode` في `domain` (id, type, inputSelector, outputPath, retryPolicy, compensate)
- [ ] محوّل المخطط الحالي (trigger→actions→exit) إلى **DAG مُتحقق منه** (كشف الدورات عبر Kahn's algorithm)
- [ ] `RetryPolicy` لكل إجراء: exponential backoff + jitter + idempotency key
- [ ] `PayloadContext` + JSON Path selectors (بدء بتطبيقين فعليين: الموقع والبيانات)
- [ ] اختبارات ذرية لكل خوارزمية (نمط `BatteryTriggerMatcher` الحالي)

### المرحلة 2 — باني مرئي حقيقي (6–10 أسابيع)
- [ ] Web: `React Flow` + `@xyflow/react` — لوحة عقد (سحب/إفلات) تصدّر `DAG JSON Schema`
- [ ] معاينة حية: محاكي تنفيذ يعرض مسار البيانات بين العقد (إبراز العقدة الجارية)
- [ ] تعيين الحقول رسومياً (Source field → Target field) مع تحقق النوع
- [ ] تصدير/استيراد JSON + إصدار السير (Versioning)

### المرحلة 3 — السحابة (10–16 أسبوعاً)
- [ ] **Gateway**: Webhook ingest → Kafka (موضوعات لكل tenant، ضغط، DLQ فوري)
- [ ] **Runtime Go + Temporal**: تشغيل نفس DAG JSON؛ كل عقدة Activity مؤكدة بـ idempotency key
- [ ] **Connectors v1**: OAuth2 (PKCE) + Webhook + Polling — Google، Slack، Telegram، Weather، HTTP عام
- [ ] **Vault + KMS** للأسرار، **Redis Token Bucket** للمعدلات
- [ ] **OpenTelemetry**: trace واحد من Webhook حتى آخر عقدة + سجل input/output لكل عقدة

### المرحلة 4 — الذكاء الاصطناعي (16–20 أسبوعاً)
- [ ] **NL2Flow**: LLM → JSON Schema صارم → فحص مخطط → عرض في الباني
- [ ] **Auto-mapping**: تضمينات دلالية لحقول المصدر/الهدف (semantic matching) مع اقتراحات قابلة للتأكيد
- [ ] **Error-Agent**: عند فشل سير عمل → اقتراح إصلاح بالسياق (ما العقدة، ما المدخلات، ما الخطأ) — لا تعديل ذاتي بدون موافقة

### المرحلة 5 — التوسع والأمان (20+ أسبوعاً)
- [ ] **Sandbox**: Wasm (Wazero/Wasmtime) لكود المستخدم + قفص Firecracker للخطوات الثقيلة
- [ ] Sharding + autoscaling (KEDA على lag الكافكا)
- [ ] اختبارات تحميل (ملايين الأحداث/اليوم) + Chaos (قتل عقدة في منتصف سلسلة → استئناف)
- [ ] ضبط الـ On-Device ليعمل كـ **Edge Node** (مزامنة مع السحابة عند الاتصال، تنفيذ محلي عند الانقطاع)

---

## 5. القرارات التقنية الحرجة (لماذا هذه دون غيرها)

1. **Kafka فوق Redis Streams**: التطبيق يحتاج إعادة تشغيل التاريخ (Replay) لكل تنفيذ مكتمل وإعادة معالجة بعد فشل عقدة؛ Kafka يسجل كل شيء ويسمح بـ `reset to beginning` لكل Consumer Group — Redis يخزّن في الذاكرة مع حدود ترحيل.
2. **Go فوق Node للـ Runtime**: كل عقدة سير عمل = إما goroutine خفيفة أو Task بحدود زمنية؛ Go يعطي كثافة عالية لكل GB مقارنة بـ Node (حمل ~50× أقل لكل طلب خامل) — حاسم عند آلاف الـ workflows المتزامنة.
3. **Wasm فوق Docker للساندبوكس**: حاويات Docker لخطوة كود واحدة = 1-3 ثواني إقلاع + سطح هجوم أكبر (نواة مشتركة)؛ Wasm معزول بذاكرة 32-bit صارمة ويقلع في أجزاء من المللي ثانية — لخطوات المستخدم الصغيرة هو الفارق بين «تجربة فورية» و«بانتظار».
4. **At-least-once + Idempotency فوق Exactly-once الحرفي**: الـ exactly-once الموزع الحقيقي (مثل تنسيق المعاملات الموزعة) مكلف ويزيد زمن الاستجابة بشكل كبير؛ التطبيق العملي المعتمد في Temporal هو at-least-once مع مفاتيح عدم تكرار — النتيجة للمستخدم واحدة تماماً.
5. **Temporal فوق بناء صفّ طوابير مخصص**: بناء خزنة حالة + scheduler + retry + saga يدوياً = 6-12 شهر عمل إضافي وأخطاء حدودية مستعصية؛ Temporal يحزمها كمنتج مختبَر مع نموذج ذهني موثّق.

---

## 6. خلاصة تنفيذية

- **ما تملكه اليوم فعلاً**: محرك أحداث كامل على الجهاز (11 مصدر حدث، إجراءات صوت/شبكة/إشعار/ROM/بلوتوث، قيود، سلوك خروج، تكامل ROM Evolution X، أمان Keystore، خصوصية M3) مع 472+ اختباراً وبنية وحدات نظيفة.
- **الفجوة الحرجة**: لا DAG (تسلسل فقط)، لا سياق بيانات بين الخطوات، لا retry، لا فهرسة مشغّلات، لا سحابة.
- **الأثر الأكبر بأقل جهد**: المرحلة 0 (TriggerIndex + PayloadContext) و1 (DAG + Retry) — ترفع المنصة من «أتمتة بسيطة» إلى «محرك سير عمل» دون أي إعادة بناء للواجهة.
- **الاتجاه المعماري الصحيح**: الهجين (جهاز + سحابة) لأن جوهر التطبيق خصوصيته **محلية/لحظية** (تحكم بالنظام، شبكة، موقع) بينما السحابة تضيف **الاتساق والتوسع والذكاء** — وهما لا يتنافسان بل يتكاملان عبر Edge sync.
