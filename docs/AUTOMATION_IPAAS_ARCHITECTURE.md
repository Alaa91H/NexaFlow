# 🚀 تقرير معمارية تنفيذي متكامل (نسخة مجهرية) — منصة أتمتة المهام (Automation & iPaaS)

> **النطاق:** تفكيك معماري مجهري لمشروع NexaFlow («المهام المجدولة») — محرك الأتمتة على الجهاز
> (On-Device Engine) كما يعمل اليوم، وتصميم تطوره إلى منصة iPaaS هجينة (جهاز + سحابة) بمعايير 2026.
> كل حكم تقني مبني على البنية الفعلية في المستودع (وحدات `core/*`, `feature/*`, compileSdk/targetSdk 37,
> minSdk 26, Java 21 toolchain, +550 اختباراً) — **تفصيل مباشر بلا تنظير، وكل اختيار مقارن ببدائله.
> Last verified against the working tree:** August 2026.

---

## 1. معمارية محرك الأتمتة — مسار الحدث مجهرياً (Event-Driven + Message Broker + DAG)

### 1.1 الحالة الحالية (On-Device Engine) — طبقة-بطبقة

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ app — Hilt Graph / M3 Theme / Navigation (feature/*: builder, dashboard,      │
│       automations, settings, themes, widgets, history, icons, capability-ctr) │
└──────────────────────────────────┬────────────────────────────────────────────┘
                                   ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ core/automation-engine — MonitoringService (FGS, SPECIAL_USE, START_STICKY)   │
│                                                                               │
│  EVENT SOURCES — كل مصدر يطبّق EventSource (sourceId + وصف + start/stop):     │
│  ┌─────────┐ ┌───────────┐ ┌────────────┐ ┌──────────┐ ┌─────────┐ ┌──────────┐│
│  │ Battery │ │ Device    │ │Connectivity│ │ Location │ │Bluetooth│ │RingerMode││
│  │ Monitor │ │EventMon   │ │Monitor     │ │Monitor   │ │ Monitor │ │ Monitor  ││
│  └────┬────┘ └─────┬─────┘ └─────┬──────┘ └────┬─────┘ └────┬────┘ └────┬─────┘│
│  ┌─────────┐ ┌─────────┐ ┌────────────┐ ┌───────────┐ ┌────────┐ ┌───────────┐│
│  │ Calendar│ │ Sensor  │ │RomSetting  │ │ Webhook   │ │Cellular │ │ SMS/App   ││
│  │ Monitor │ │ Monitor │ │Monitor     │ │ Server    │ │Monitor  │ │Listeners  ││
│  └────┬────┘ └────┬────┘ └─────┬──────┘ └─────┬─────┘ └───┬────┘ └─────┬─────┘│
│       ▼           ▼            ▼              ▼           ▼            ▼      │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ Event Dispatcher — Coroutine على ApplicationScope (SupervisorJob)      │   │
│  │ 1) filter{enabled} (يُستبدل بـ TriggerIndex في المرحلة 0)              │   │
│  │ 2) TriggerMatcher ذري (TimeTriggerCalculator, BatteryTriggerMatcher)   │   │
│  │ 3) ConstraintStateReader (بطارية/شحن/موقع داخل/خارج)                    │   │
│  │ 4) Cooldown (cooldownMillis لكل مهمة)                                   │   │
│  └──────────────────────────────────┬──────────────────────────────────────┘   │
└─────────────────────────────────────┼─────────────────────────────────────────┘
                                      ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ core/execution — ExecutionEngine                                              │
│   runAutomation(automation) → ActionHandlers (صوت/رنين/حجم، شبكة 2G-5G،        │
│                                هوتسبوت، إشعار، تذكير، ROM، NFC plugin…)        │
│   runExit(automation)      → ExitActions عند انتهاء شرط المشغّل                │
│   Constraints: قبل كل تنفيذ تُقرأ الحالة الفعلية (لا يُثق بالحدث وحده)         │
└───────────────────────────┬───────────────────────────────────────────────────┘
                            ▼
   Persistence: Room (+ Migrations) · DataStore · KeystoreSecureStorage
   History: سجل تنفيذ لكل مهمة (feature/history)
```

**مسار حدث Webhook مجهرياً:** `HTTP POST → WebhookServer (embedded, HttpServer على المنفذ المحلي) → تحقق الطلب → sourceId=WEBHOOK → مصفّاة المشغّلات → ConstraintStateReader → cooldown → ExecutionEngine → handlers → سطر history`.

**ملاحظة دقيقة عن «Exactly-once» على الجهاز:** بث `ACTION_BATTERY_CHANGED` قد يُفقد؛ لهذا وُجدت شبكة الأمان الدورية (60s، ملتصقة بـ FGS، `skip-if-unchanged`). هذا هو نمط **at-least-once مع إزالة الازدواج** (`activeBatteryTriggers` + `cooldownMillis`) — وليس ضماناً حرفياً، وهو ما سنبنيه عليه سحابياً.

### 1.2 البنية المستهدفة — عمق البروتوكولات (Event Streaming + Durable Workflows)

```
┌────────────────────────────  Cloud Gateway (K8s Ingress + API Gateway + WAF) ─┐
│  Webhook (توقيع HMAC-SHA256) · OAuth2 Callback · Schedule (Temporal)          │
└───────────┬───────────────────────────────────────────────────────────────────┘
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Event Ingest — Outbox Pattern (معاملاتي)                                      │
│  الطلب يكتب Order/Event في قاعدة البيانات **داخل نفس المعاملة** ← Outbox      │
│  Debezium CDC (WAL) يقرأ الـ outbox → Kafka — يمنع «فقدان حدث بين طلبين»      │
└───────────┬───────────────────────────────────────────────────────────────────┘
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Message Broker — Kafka (Redpanda بديل متوافق API)                             │
│  موضوعات: events.<tenant> (مقسّم على tenantId), dlq.<tenant>, audit           │
│  ترتيب مضمون per-partition (مفتاح = workflowId) → سلسلة واحدة لا تختلط        │
│  Consumer Groups لكل عقدة — موازاة أفقية داخل نفس السلسلة بأمان               │
└───────────┬───────────────────────────────────────────────────────────────────┘
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Control Plane — PostgreSQL (Workflow Registry, DAG JSON Schema, Secrets refs, │
│                            Rate-Limit quotas, Idempotency keys)               │
└───────────┬───────────────────────────────────────────────────────────────────┘
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│ Workflow Runtime — Go + Temporal                                              │
│  DAG Compiler: JSON → DAG (تحقق من الدورة عبر Kahn) → Decision Tasks          │
│  كل عقدة = Activity مؤكدة (Idempotency Key = hash(workflowId, nodeId, input)) │
│  الحالة = Event History (تخزين مؤقت دائم) — استئناف تام بعد موت أي عقدة       │
│  Saga: كل عقدة قابلة للعكس تُسجّل compensate → تراجع منسّق عند الفشل          │
└───────────┬───────────────────────────────────────────────────────────────────┘
            ▼
┌───────────────┬───────────────────┬───────────────────────┬──────────────────┐
│ Connectors    │ Sandbox           │ Observability         │ AI Layer         │
│ OAuth2(PKCE)  │ WASM (Wazero)     │ OTel traces/metrics/  │ NL2Flow →        │
│ Webhook+HMAC  │ + Firecracker     │ logs (معرّف تنفيذ واحد)│ Schema-validated │
│ Polling (تكيّف)│ لكود ثقيل         │ per-node in/out logs  │ Auto-mapping     │
│ REST/GraphQL  │                   │ DLQ + Alerts          │ Error-Agent      │
└───────────────┴───────────────────┴───────────────────────┴──────────────────┘
```

**لماذا هذا الشكل تحديداً:**
- **Outbox Pattern** يحل المشكلة الكلاسيكية «الطلب نجح لكن الحدث ضاع»: الكتابة في قاعدة البيانات والنشر في Kafka تتم بنفس المعاملة عبر CDC.
- **Kafka Event Log** هو العمود الفقري لكل من `Replay` (إعادة معالجة سلسلة فاشلة من بدايتها)، `Audit`، و`DLQ` — الثلاثة غير ممكنة بكفاءة على Redis Streams (ذاكرة أولاً) أو RabbitMQ (توجيه لا تخزين تاريخي).
- **Temporal** يملك Event History دائم لكل سير عمل — الموت المفاجئ لعقدة لا يضيع حالة؛ تُستأنف من آخر حدث معالَج.

---

## 2. التكامل والمكدس التكنولوجي (Integrations & API Strategy)

### 2.1 استراتيجية الـ Connectors (SDK داخلي موحّد)

| آلية الربط | متى | التفاصيل المجهرية |
|---|---|---|
| **OAuth2.0 (Authorization Code + PKCE)** | كل Connector لمستخدم نهائي | `code_verifier` عشوائي 43-128 حرفاً، `code_challenge = base64url(sha256(verifier))`؛ الـ refresh_token **مُدوَّر عند كل تجديد** ومخزّن في Vault (وليس في DB)؛ عند رفض refresh → إبطال الاتصال وإشعار المستخدم |
| **Webhooks (دفع)** | المصادر التي تدعمها | توقيع الطلب `HMAC-SHA256(secret, body)` + مقارنة `timing-safe`؛ إرجاع `202` فوراً و`4xx` للرسائل المكررة (Idempotency-Key) |
| **Polling (تكيّف)** | مصادر بلا Webhook | **Backoff تكيّفي**: يبدأ كل 60s؛ عند 429 يضاعف الفاصل حتى سقف (مثلاً 15 دقيقة)؛ عند نشاط متزايد يخفض — توازن بين الفورية والتكلفة |
| **REST vs GraphQL** | REST للـ Connectors الخارجيين (تخزين مؤقت بسيط)، GraphQL داخلياً لواجهة السير | GraphQL داخلياً يقلل الحقول الزائدة للـ Dashboard؛ خارجياً REST هو المعيار الأوسع للربط |

### 2.2 لغات وأطر المعالجة والبناء

| الوجهة | الاختيار | البديل | لماذا |
|---|---|---|---|
| On-Device | **Kotlin + Coroutines** (موجود) | RxJava | تفاضلية نفقات، تكامل Compose/Hilt، صفر هجرة |
| Runtime سحابي | **Go** | Node.js | goroutine لكل عقدة (~2KB بدل ~4MB لخيط JVM)، بدء ~ms، كثافة عالية لكل GB عند آلاف الـ workflows |
| Durable Workflows | **Temporal SDK (Go)** | BullMQ/Celery | Event History + Saga + retry مدمج |
| Builder ويب | **React + TypeScript + React Flow** | Rete.js/Drawflow | منظومة عقد أعمق، تحكم برمجي كامل، مجتمع ضخم |
| Builder جهاز | **Compose (موجود)** | — | استمرار، لا هجرة |

---

## 3. المنطق البرمجي والخوارزميات — تفكيك مجهري

### 3.1 إعادة المحاولة الذكية (Exponential Backoff + Jitter)

```
retry_delay(n) = min(cap, base * 2^(n-1)) + random(0, jitter*cap)
   base=1s, cap=60s, jitter=0.2
   n=1 → ~1s   n=2 → ~2s   n=3 → ~4s   …   n=7+ → ~60s
```
- **فئات الأخطاء:** أخطاء قابلة لإعادة المحاولة (5xx, timeouts, 429 مع `Retry-After`) تُعاد؛ أخطاء دائمة (4xx validation) تُفشل فوراً إلى DLQ — لا نحرق محاولات على خطأ لن ينجح.
- **Idempotency Key** لكل محاولة: `sha256(workflowId|nodeId|inputHash)` — التكرار لا يكرر الأثر (مهم جداً لإجراءات حساسة مثل تبديل وضع الشبكة أو إرسال رسالة).

### 3.2 إدارة الحالة بين العقد (Payload Context) بأقل ذاكرة

```
عقدة A: output → "$.weather.temp" = 21.4
عقدة B: input  ← "$.weather"    (JSONPath selector)
التخزين: JSON Merge Patch (دلتا فقط) — لا نسخة كاملة من السياق بعد كل عقدة
الحد الأعلى: 256KB لكل سياق تنفيذ (تنبيه عند الاقتراب) — يمنع انفجار الذاكرة
الوصول: قراءة-فقط داخل العقدة؛ الكتابة فقط عبر إعلان output صريح (لا أثر جانبي خفي)
```

### 3.3 حدود المعدل (Rate Limiting) الموزّعة

- **Token Bucket** في Redis بـ Lua atomic (معدل + سعة لكل Connector/tenant).
- احترام رؤوس `429` و`Retry-After` — تعليق السلسلة (throttle) بدل فشل جماعي.
- عند استنفاد الحصة: إعادة جدولة العقدة (Temporal) بدل إسقاط الحدث.

### 3.4 مطابقة المشغّلات — من O(N) إلى O(1)

- **اليوم:** `automations.filter{enabled}` لكل حدث → فحص خطي.
- **الهدف (المرحلة 0):** `TriggerIndex(sourceId → ids)` يُبنى من `Flow` قاعدة البيانات ويُحدَّث عند الحفظ؛ الحدث يصل للفهرس فيقرأ المشتركين فقط. مع `DAG` يصبح فحص `enabled + constraints` جزءاً من الـ Decision Task.

---

## 4. تكامل الذكاء الاصطناعي (AI-Powered Automation)

| القدرة | الخط أنابيب المجهري | الضمانات |
|---|---|---|
| **NL2Flow (نص → سير عمل)** | 1) LLM → **JSON Schema صارم** للـ DAG ← 2) **فحص مخطط** (عقد موجودة، حقول مطلوبة، أنواع) ← 3) عرض في الباني للمراجعة | أي مخرج لا يجتاز الفحص يُرفض — يمنع هلاوس العقد؛ LLM اقتراح وليس قرار |
| **Auto-mapping دلالي** | تضمينات (embeddings) لحقول المصدر/الهدف → تشابه جيب التمام ← إعادة ترتيب بـ LLM (إعادة ترتيب قصيرة) → اقتراحات قابلة للتأكيد | المستخدم يؤكد كل تعيين قبل الحفظ |
| **Error-Agent** | عند فشل سير عمل: RAG فوق سجلات التنفيذ (أي عقدة، أي input، أي خطأ) → اقتراح إصلاح محدد بالسياق | **لا تعديل ذاتي أبداً** — اقتراح فقط، التنفيذ بموافقة صريحة |

---

## 5. تجربة المستخدم وباني سير العمل (Visual Builder)

- **React Flow (ويب):** عقد مخصصة (Trigger/Action/Condition/Custom Code)، Handles موزّعة، Minimap، أدوات توازن تلقائي، Undo/Redo عبر Zustand.
- **الوضع الحي (Live Test Mode):** تنفيذ تجريبي ببيانات وهمية (Mock) داخل الباني → إبراز العقدة الجارية ومسار البيانات (Input→Output لكل عقدة) بلونين — رؤية فورية قبل النشر.
- **عرض مسار التنفيذ:** كل تنفيذ حقيقي يحمل `traceId` → في صفحة التاريخ يُعرض المسار الكامل: كل عقدة، زمنها، مدخلاتها/مخرجاتها (من OTel).
- **جهاز (Compose):** يبقى المحرر الحالي (بطاقات مشغّلات/قيود/إجراءات) — يُضاف لاحقاً عرض مسار التنفيذ في `feature/history`.

---

## 6. بيئة التنفيذ الآمنة (Sandboxing & Security)

| الطبقة | الاختيار | التفاصيل المجهرية |
|---|---|---|
| **كود المستخدم (خفيف، عالي التردد)** | **WebAssembly — Wazero/Wasmtime** | حدود ذاكرة صارمة (مثلاً 32MB)، **لا وصول للنظام إطلاقاً** إلا عبر imports صريحة (لا شبكة افتراضياً، لا FS، لا env)، بدء ~ms، حد CPU (نفقات مُقاسة) |
| **كود ثقيل (سيلينيوم/معالجة ملفات)** | **Firecracker microVM** | VM خفيفة (~125ms إقلاع، 5MB ذاكرة) بدل حاوية ثقيلة؛ عزل نواة كامل |
| **لماذا ليس Docker لكل خطوة** | — | حاوية لكل تنفيذ = 1-3 ثواني إقلاع + سطح هجوم نواة مشتركة — غير اقتصادية لملايين الخطوات |
| **الأسرار** | **Vault + KMS (تشفير مزدوج)** | Vault يملك الأسرار، KMS يملك المفاتيح؛ تدوير/إبطال فوري؛ كل تشفير AES-256-GCM؛ لا أسرار في السجلات أو الـ payload (references فقط) |
| **الجهاز** | **KeystoreSecureStorage (موجود)** | مفاتيح في Keystore Android (Hardware-backed حيثما توفّر) — تبقى حتى بعد تشفير السحابة |

---

## 7. المراقبة والسجلات والشفافية (Observability & Execution Logs)

- **OpenTelemetry موحّد:** `traceId` واحد من Webhook حتى آخر عقدة؛ كل عقدة تسجّل **input/output** (مقنّعاً للأسرار) + الزمن + الحالة.
- **DLQ (Dead Letter Queue):** رسالة فشلت نهائياً (بعد استنفاد الـ retries) → `dlq.<tenant>` مع سبب الفشل + عدد المحاولات → تنبيه بلا ضجيج (threshold + تجميع) + واجهة «إعادة تشغيل من DLQ».
- **التنبيهات:** تجميع حسب النمط (نفس الخطأ × N في نافذة زمنية) — تنبيه واحد بدل آلاف.
- **سجل التنفيذ للمستخدم:** صفحة history تعرض per-node in/out — شفافية كاملة («أي بيانات دخلت وأي خرجت»).

---

## 8. المرونة والاستمرارية (Resiliency & Fault Tolerance)

| التهديد | الحل المجهري |
|---|---|
| موت عقدة في منتصف السلسلة | Temporal Event History → استئناف من آخر حدث معالَج (لا إعادة من البداية) |
| ازدواج التنفيذ بعد إعادة المحاولة | **At-least-once + Idempotency** (وليس exactly-once الحرفي المكلف): الأثر لا يتكرر حتى لو تكرر الطلب |
| «حدث نجح والطلب لم يُسجَّل» | **Outbox Pattern** (كتابة + نشر بنفس المعاملة عبر CDC) |
| فقدان ترتيب السلسلة عند التوسع | مفتاح القسمة = workflowId → partition واحد → ترتيب مضمون |
| فشل قواعد البيانات | PostgreSQL HA (Patroni/etcd) + فشل تلقائي |
| الكوارث | **Chaos Testing** أسبوعي: قتل عقدة/خادم/شبكة في منتصف تنفيذ → تحقق من الاستئناف (اختبار آلي في CI للبنية السحابية) |
| انقطاع الشبكة على الجهاز | الجهاز كـ **Edge Node**: تنفيذ محلي فوري، مزامنة النتائج عند عودة الاتصال (إزالة ازدواج عبر Idempotency) |

---

## 9. جدول أدوات المنظومة (Tech Stack Matrix — موسّع)

| الطبقة | المختار | البديل | سبب التفوق (المقارنة) |
|---|---|---|---|
| محرك الجهاز | Kotlin + Coroutines | RxJava | تفاضلية، تكامل Hilt/Compose، لا هجرة |
| Runtime سحابي | Go | Node/Python | goroutine ~2KB، بدء ms، كثافة ×50 للطلبات الخاملة |
| Durable Workflows | Temporal | BullMQ/Celery | Event History + Saga + retry مدمج؛ BullMQ يبنيها يدوياً |
| ناقل الرسائل | **Kafka** | Redis Streams / RabbitMQ | سجل دائم قابل لإعادة التشغيل (Replay) → DLQ/Audit/Exactly-once؛ Redis بلا سجل موزّع دائم، RabbitMQ موجّه لا تاريخي |
| CDC للـ Outbox | Debezium (PostgreSQL WAL) | استطلاع يدوي للـ outbox | قراءة WAL بلا عبء على قاعدة الإنتاج، زمن تأخير ~ms |
| بيانات المهام | PostgreSQL (+ JSONB) | MySQL/Mongo | ACID + JSONB؛ تعيين حقل-بحقل نظيف من مخطط Room |
| قياس السجلات | ClickHouse | Elasticsearch | تحليلات عمودية بثمن أقل لتدفق السجلات |
| Builder | React Flow (ويب) + Compose (جهاز) | Rete.js/Drawflow | أعمق منظومة عقد، مجتمع ضخم، تحكم كامل |
| Sandbox | WASM (Wazero) + Firecracker | Docker لكل خطوة | ms بدل ثوانٍ؛ ذاكرة صارمة؛ عزل كامل بدون نواة مشتركة |
| الأسرار | Vault + KMS | أعمدة مشفرة | تدوير/إبطال فوري، فصل مسؤوليات، SOC2 |
| الربط | OAuth2(PKCE) + Webhook + Polling | API Keys | إبطال مركزي، تفويض جزئي، حمل أقل |
| المراقبة | OpenTelemetry → Prometheus → Grafana | سجلات نصية | trace موزّع بمعرّف واحد |
| الذكاء | LLM + JSON Schema صارم | LLM حر | فحص المخطط قبل القبول — صفر هلاوس |
| Cache/معدلات | Redis (Lua atomic) | MySQL locks | ذرية موزّعة بالملي ثانية |

---

## 10. خارطة الطريق المجهرية (Micro-Implementation Roadmap)

> القاعدة: كل مرحلة تُسلم شيئاً يعمل ويُختبر؛ لا ننتقل قبل اجتياز اختبارات المرحلة. الأرقام تقديرية.

### المرحلة 0 — تحصين الأساس — ✅ منجز
- [x] شبكة أمان البطارية 60s (zero-wakeup) · [x] إقلاع بلا DSN (MergedManifestNoSentryTest) · [x] Java 21 toolchain
- [x] **TriggerIndex**: `core/automation-engine/.../engine/TriggerIndex.kt` — فهرس `sourceId → ids` يُبنى من `Flow` القاعدة ويُحدَّث عند الحفظ، `bySource` بـ O(1)، مزوَّد عبر Hilt (`EngineModule`) ويُشغَّل في `MonitoringService` (12 اختباراً ذرياً)
- [x] **PayloadContext**: `core/execution/.../execution/WorkflowRunContext.kt` — دلتا JSON Merge Patch بحد 256KB + JSONPath، يمر عبر `ExecutionEngine.runAutomation` إلى المعالجات (21 اختباراً)

### المرحلة 1 — محرك DAG — ✅ منجز
- [x] `DagNode` + `DagGraph` + `RetryPolicy` في `domain/workflow/DagNode.kt` (id, type, input, outputPath, retry, compensate)
- [x] محوّل `Automation → DAG مُتحقق` في `domain/workflow/DagCompiler.kt` — كشف الدورة عبر `kahnTopologicalSort` (ترتيب طوبولوجي أو null للرفض قبل التنفيذ) — 19 اختباراً
- [x] `RetryExecutor` في `domain/workflow/RetryExecutor.kt` — backoff كامل + jitter + idempotency key + تصنيف قابل/دائم (10 اختبارات)
- [x] JSONPath selectors على PayloadContext — قراءة/كتابة `$`/`$.a.b`/`$.a[0].b`/`$[0]` في `WorkflowRunContext`
- [x] **تطبيق retry على الشبكة**: `HttpRequestHandler` يعيد المحاولة على 5xx/429/فشل اتصال بتراجع أُسّي، يرفض 4xx فوراً، ويحمل `Idempotency-Key` ثابتة عبر المحاولات — 11 اختباراً
- [x] اختبارات ذرية لكل خوارزمية (نمط BatteryTriggerMatcher)

### المرحلة 2 — الباني المرئي (6–10 أسبوع)
- [ ] Web: React Flow — لوحة عقد (سحب/إفلات) تصدّر DAG JSON Schema
- [ ] Live Test Mode: محاكي ببيانات وهمية + إبراز العقدة الجارية + مسار البيانات
- [ ] تعيين حقول رسومي (Source→Target) مع تحقق النوع
- [ ] Versioning + استيراد/تصدير JSON

### المرحلة 3 — السحابة (10–16 أسبوع)
- [ ] Gateway + Kafka (مواضيع لكل tenant) + Outbox عبر Debezium
- [ ] Runtime Go + Temporal (تشغيل نفس DAG JSON؛ كل عقدة Activity مؤكدة)
- [ ] Connectors v1: Google، Slack، Telegram، Weather، HTTP عام (OAuth2 + Webhook + Polling)
- [ ] Vault + KMS + Token Bucket (Redis)
- [ ] OTel: trace واحد + per-node input/output logs

### المرحلة 4 — الذكاء (16–20 أسبوع)
- [ ] NL2Flow: LLM → JSON Schema → فحص → عرض في الباني
- [ ] Auto-mapping دلالي (تضمينات + إعادة ترتيب LLM) باقتراحات مؤكَّدة
- [ ] Error-Agent (RAG على السجلات، اقتراحات بموافقة)

### المرحلة 5 — التوسع والأمان (20+ أسبوع)
- [ ] Sandbox: WASM (Wazero) + Firecracker
- [ ] Sharding + autoscaling (KEDA على lag الكافكا)
- [ ] Chaos tests + اختبارات تحميل (ملايين الأحداث/اليوم)
- [ ] الجهاز كـ **Edge Node** (مزامنة + إزالة ازدواج عبر Idempotency)

---

## 11. القرارات الحرجة — الموجز

1. **Kafka فوق Redis**: إعادة تشغيل التاريخ لكل تنفيذ يحتاج سجلاً دائماً موزّعاً — Redis تخزين ذاكرة أولاً.
2. **Go فوق Node**: حمل ~50× أقل لكل طلب خامل — حاسم عند آلاف الـ workflows.
3. **WASM فوق Docker**: «تجربة فورية» مقابل «بانتظار» لخطوات المستخدم عالية التردد.
4. **At-least-once + Idempotency فوق Exactly-once الحرفي**: المعاملات الموزعة الحقيقية مكلفة وبطيئة؛ مفتاح النجاح عدم تكرار الأثر — والنتيجة للمستخدم واحدة.
5. **Temporal فوق صفّ مخصص**: 6–12 شهر عمل إضافي وأخطاء حدودية — Temporal يحزمها كمنتج مختبَر.
6. **Outbox فوق النشر المباشر**: القناة الوحيدة التي تضمن «طلب ناجح = حدث موجود» عند الفشل المفاجئ.

---

## 12. الخلاصة التنفيذية

- **تملك اليوم:** محرك أحداث كامل على الجهاز (11 مصدر، إجراءات صوت/شبكة/ROM/بلوتوث، قيود، سلوك خروج، تكامل Evolution X، أمان Keystore، +550 اختباراً) — أساس صلب لا يُهدم.
- **محرك سير العمل (المرحلة 0+1) مكتمل فعلياً:** TriggerIndex (O(1)) · WorkflowRunContext (دلتا + حد 256KB + JSONPath) يمر عبر المحرك · DagNode + محوّل Kahn مُتحقق · RetryExecutor مطبّق على الشبكة مع Idempotency-Key.
- **الفجوات الحرجة المتبقية:** ربط مخرجات العقد (outputPath → context) في المعالجات · JSONPath selectors في المحرر · سجلات per-node · الباني المرئي (React Flow) · السحابة.
- **أعلى عائد تالٍ:** المرحلة 2 (الباني المرئي + تعيين الحقول + per-node logs) ثم المرحلة 3 (Gateway + Kafka + Temporal) — التراكيب المحلية جاهزة للتصدير كـ DAG JSON Schema.
- **الاتجاه المعماري:** الهجين — خصوصية الجهاز (تحكم بالنظام/شبكة/موقع) + اتساق السحابة وتوسعها وذكائها، عبر Edge sync مع إزالة الازدواج.

---

# الملحق أ — عقود التنفيذ الجاهزة (Implementation Contracts)

> هذه النسخة الثالثة من التقرير تُضيف **عقوداً قابلة للتنفيذ مباشرة** — كل بند يقابل ملفاً/واجهة في المستودع الحالي، بمعرّفات مطابقة لاتفاقيات المشروع (`domain`, `core/automation-engine`, `core/execution`). الهدف: أن يبدأ التنفيذ من «التصميم» إلى «الشفرة» دون قرارات مفتوحة.

## أ.1 مخطط DAG — JSON Schema (إصدار v1)

```jsonc
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "NexaFlowWorkflow",
  "type": "object",
  "required": ["schemaVersion", "id", "name", "trigger", "nodes"],
  "properties": {
    "schemaVersion": { "const": "1" },
    "id": { "type": "string", "pattern": "^[a-z0-9-]{8,64}$" },
    "name": { "type": "string", "maxLength": 80 },
    "trigger": {
      "type": "object",
      "required": ["type", "config"],
      "properties": {
        "type": { "enum": ["TIME","BATTERY","CONNECTIVITY","LOCATION","CALENDAR","SENSOR","BLUETOOTH","RINGER","ROM_SETTING","WEBHOOK","CELLULAR","SMS_APP"] },
        "config": { "type": "object" }   // يُتحقق منه بمخطط فرعي per-type
      }
    },
    "constraints": {
      "type": "array",
      "items": { "$ref": "#/definitions/nodeRef" }   // كل قيد = عقدة شرط في DAG
    },
    "nodes": {
      "type": "array",
      "items": { "$ref": "#/definitions/node" },
      "minItems": 1
    },
    "exit": { "$ref": "#/definitions/node" }          // سلوك «عند انتهاء الشرط»
  },
  "definitions": {
    "node": {
      "type": "object",
      "required": ["id", "type", "input"],
      "properties": {
        "id": { "type": "string", "pattern": "^[a-z0-9-]{1,48}$" },
        "type": { "enum": ["ACTION_SOUND","ACTION_RINGER","ACTION_VOLUME","ACTION_NETWORK","ACTION_HOTSPOT","ACTION_NOTIFY","ACTION_REMINDER","ACTION_ROM","ACTION_NFC","CONDITION","CUSTOM_CODE"] },
        "input": { "type": "object", "additionalProperties": true },
        "outputPath": { "type": "string" },           // JSONPath إلى PayloadContext
        "retry": {
          "type": "object",
          "properties": {
            "maxAttempts": { "type": "integer", "minimum": 1, "default": 3 },
            "baseDelayMs": { "type": "integer", "default": 1000 },
            "capMs": { "type": "integer", "default": 60000 },
            "jitter": { "type": "number", "minimum": 0, "maximum": 1, "default": 0.2 }
          }
        },
        "compensate": { "$ref": "#/definitions/node" } // إجراء عكسي (Saga)
      }
    },
    "nodeRef": { "type": "string", "description": "مرجع بمعرّف عقدة في nodes[]" }
  }
}
```

## أ.2 المرحلة 0 — عقود Kotlin (منفّذة — انظر الملفات الفعلية في أ.4)

> **ملاحظة التنفيذ:** العقود أدناه نفِّذت فعلياً مع تحسينين مقصودين عن النص الحرفي: (1) دلتا `WorkflowRunContext` شجرة متداخلة بدل خريطة مسطّحة — وإلا لكان `$.weather.temp` يطمس `$.weather.humidity`؛ (2) `require()` بدل `throw IllegalArgumentException` لإرضاء detekt `UseRequire`. البقاء مع الملفات الفعلية المذكورة في أ.4 هو المرجع.

### TriggerIndex — `core/automation-engine/.../engine/TriggerIndex.kt`

```kotlin
/** فهرس sourceId → معرفات المهام — يلغي filter{enabled} الخطي في Event Dispatcher. */
class TriggerIndex(
    private val automationsFlow: Flow<List<Automation>>,   // Flow قاعدة البيانات القائمة
) {
    private val index = ConcurrentHashMap<String, MutableSet<String>>()  // sourceId → ids
    private val all = ConcurrentHashMap<String, Automation>()            // id → snapshot

    /** يُشغَّل مرة واحدة في ApplicationScope؛ يعيد بناء الفهرس عند أي تغيير في القاعدة. */
    suspend fun start() {
        automationsFlow.collect { list ->
            index.clear(); all.clear()
            list.filter { it.enabled }.forEach { a ->
                a.triggers.forEach { t -> index.computeIfAbsent(t.sourceId) { HashSet() }.add(a.id) }
                all[a.id] = a
            }
        }
    }

    /** O(1): يعيد المهام المشتركة في مصدر حدث معيّن — بدل فحص كل المهام. */
    fun bySource(sourceId: String): List<Automation> =
        index[sourceId]?.mapNotNull { all[it] } ?: emptyList()

    fun snapshot(id: String): Automation? = all[id]
}
```

### WorkflowRunContext — `core/execution/.../execution/WorkflowRunContext.kt`

```kotlin
/** سياق تنفيذ واحد يمر عبر العقد — دلتا فقط (JSON Merge Patch) بحد أعلى للذاكرة. */
class WorkflowRunContext(
    val runId: String,                 // UUID لكل تنفيذ — يطبع في history و OTel
    val automationId: String,
    val triggeredAt: Long,
) {
    private val patch = mutableMapOf<String, Any?>()   // مسار JSONPath → قيمة (دلتا)
    private var bytesEstimate = 0

    companion object { const val MAX_BYTES = 256 * 1024 }

    /** يكتب عبر مسار صريح فقط — لا أثر جانبي خفي. */
    fun put(path: String, value: Any?) {
        val delta = estimate(value)
        check(bytesEstimate + delta <= MAX_BYTES) { "PayloadContext تجاوز حد 256KB" }
        patch[path] = value; bytesEstimate += delta
    }

    /** JSONPath selector: $.weather.temp → يقرأ من الدلتا (ويتوسع لاحقاً لتخزين خارجي). */
    @Suppress("UNCHECKED_CAST")
    fun get(path: String): Any? {
        if (!path.startsWith("$.")) return null
        val segments = path.removePrefix("$.").split(".")
        var node: Any? = patch[segments.firstOrNull() ?: return null]
        for (seg in segments.drop(1)) {
            node = when (node) {
                is Map<*, *> -> node[seg]
                else -> null
            }
            if (node == null) return null
        }
        return node
    }

    private fun estimate(v: Any?): Int = when (v) {
        null -> 4
        is String -> v.length + 16
        is Number -> 16
        is Boolean -> 4
        is Map<*, *> -> v.entries.sumOf { estimate(it.key) + estimate(it.value) }
        is Iterable<*> -> v.sumOf { estimate(it) }
        else -> 64
    }
}
```

### RetryPolicy — `core/execution/.../execution/RetryPolicy.kt`

```kotlin
/** Exponential backoff + jitter — مطابق للعقد retry في مخطط DAG. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000,
    val capMs: Long = 60000,
    val jitter: Double = 0.2,
)

class RetryExecutor(private val rng: Random = Random.Default) {
    /** يعيد delay للمحاولة n (1-based) — min(cap, base·2^(n-1)) + jitter. */
    fun delayMs(attempt: Int, policy: RetryPolicy): Long {
        val exp = policy.baseDelayMs * (1L shl (attempt - 1).coerceAtMost(6))
        val capped = exp.coerceAtMost(policy.capMs)
        val jitterAmt = (capped * policy.jitter).toLong()
        return capped + if (jitterAmt > 0) rng.nextLong(-jitterAmt, jitterAmt + 1) else 0L
    }

    /** Idempotency key — التكرار لا يكرر الأثر. */
    fun idempotencyKey(workflowId: String, nodeId: String, inputHash: String): String =
        sha256("$workflowId|$nodeId|$inputHash")
}
```

## أ.3 خوارزمية Kahn — كشف الدورة في المحوّل (المرحلة 1)

```kotlin
/** يعيد ترتيباً طوبولوجياً أو null إذا وُجدت دورة — يُستخدم في محوّل المخطط → DAG. */
fun kahnTopologicalSort(edges: Map<String, Set<String>>): List<String>? {
    val indegree = HashMap<String, Int>().also { m ->
        edges.keys.forEach { m[it] = 0 }
        edges.forEach { (_, deps) -> deps.forEach { d -> m[d] = (m[d] ?: 0) + 1 } }
    }
    val queue = ArrayDeque<String>().apply { indegree.filterValues { it == 0 }.keys.forEach { add(it) } }
    val order = ArrayList<String>(indegree.size)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        order.add(node)
        edges[node]?.forEach { dep ->
            val d = indegree[dep]!! - 1; indegree[dep] = d
            if (d == 0) queue.addLast(dep)
        }
    }
    return if (order.size == indegree.size) order else null   // null = دورة → رفض المخطط
}

// اختبار ذري (نمط BatteryTriggerMatcher):
//   edges {A→[B], B→[C], C→[A]} → null   (دورة)
//   edges {A→[B], B→[C]}        → [A, B, C]
```

## أ.4 تسلسل التنفيذ — ✅ الخطوات الثلاث منجزة (الحالة الفعلية)

| # | الخطوة | الملف الفعلي | الحالة والاختبارات |
|---|---|---|---|
| 1 | `TriggerIndex` + `start()` في ApplicationScope | `core/automation-engine/.../engine/TriggerIndex.kt` (+ `di/EngineModule.kt`، حقن في `MonitoringService`) | ✅ 12 اختباراً ذرياً — فهرس يُبنى من Flow ويُحدَّث عند الحفظ؛ `bySource` يعيد المشتركين فقط (O(1)) |
| 2 | `WorkflowRunContext` يمر عبر `ExecutionEngine.runAutomation` | `core/execution/.../execution/WorkflowRunContext.kt` (+ حقل `runContext` في `ActionExecutionContext`) | ✅ 21 اختباراً — دلتا JSON Merge Patch + حد 256KB (رفض قبل التحوير) + JSONPath قراءة/كتابة + مرور نفس المثيل للمعالجات |
| 3 | `RetryPolicy` + `RetryExecutor` على إجراءات الشبكة | `domain/.../workflow/RetryExecutor.kt` + `RetryPolicy` في `DagNode.kt`، مطبّق في `core/execution/.../handler/HttpRequestHandler.kt` | ✅ 21 اختباراً (10 للرياضيات + 11 للتنفيذ) — backoff مصداق + jitter ضمن النطاق + idempotency ثابت + 4xx دائم بلا محاولة + `Idempotency-Key` على الشبكة |

> **الاتفاقية:** كل خطوة تُسلَّم مع اختبارات ذرية خضراء وdetekt نظيف — نفس معيار الإصدارات السابقة (مثل `BatteryTriggerMatcher` و`MergedManifestNoSentryTest`). إجمالي الاختبارات بعد الخطوات الثلاث: **+550** (18 وحدة).

### أ.4.1 الخطوات التالية (من حيث توقف التنفيذ)

| # | الخطوة | الملف المتوقع | الاختبار المتوقع |
|---|---|---|---|
| 4 | ربط مخرجات العقد: `outputPath → context.put(...)` في المعالجات (يبدأ بـ `HttpRequestHandler` كتابة `status`/`body`) | `core/execution/.../handler/*.kt` | اختبار: عقدة B تقرأ عبر `get(outputPath)` ما كتبته عقدة A |
| 5 | JSONPath selectors في المحرر: حقل مرجع `%` يقرأ من سياق التشغيل | `feature/automation-builder` | اختبار ذري لقرار `%variable` من `WorkflowRunContext` |
| 6 | سجل per-node (input/output) في `ExecutionRecord`/الخط الزمني | `core/execution` + `feature/history` | اختبار: كل عقدة لها مدخل/مخرج مقنّع |
