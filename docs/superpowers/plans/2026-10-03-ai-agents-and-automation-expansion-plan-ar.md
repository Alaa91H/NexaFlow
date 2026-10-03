# خطة التنفيذ: منصة الوكلاء الذكية وتوسعة الأتمتة

**الهدف:** إنشاء وكلاء داخليين مستقلين وتوسيع المحفزات والإجراءات مع محررات متخصصة وتوافق كامل مع البيانات القائمة.

**المعمارية:** نغلق موفّرات الفرع الحالية، نبني مسار canonical typed، ثم نضيف إدارة الوكلاء والتخزين والتشغيل، ثم موجات قدرات مستقلة. يمر كل فعل عبر AutomationCommandService وCapabilityRouter.

**المواصفات:** docs/superpowers/specs/2026-10-03-ai-agents-automation-expansion-design-ar.md

**المكدس:** Kotlin, Android, Room, Compose, Hilt, Gradle, JUnit.

## حدود إلزامية

- حفظ 57 محفزًا و176 إجراءً legacy.
- لا سر خام في الوكيل أو السجل أو النسخ الاحتياطي.
- التدقيق والقدرة والتحقق يسبقان كل أثر.
- لا side effect متكرر بعد موت العملية قبل المصالحة.
- unknown capability يبقى مقفلا مع تفسير.
- قياس Android/OEM الحقيقي شرط قبل وصف دعم عام.
- لا تنفذ واجهة شكلية لميزة بلا runtime حقيقي.

## المشروع 0: إصلاح فرع overhaul

- [ ] T20: اختبر ترتيب موفّرات البيانات حسب الصحة والقدرات وسبب الرفض في AiGatewayRoutingTest وAiProviderRoutingTest. أصلح fallback وتسرب الجلسة فقط مع اختبار.
- [ ] T21: اختبر إضافة/التحقق/اكتشاف model/الحفظ/التعطيل/إزالة secret في AgentSettingsScreen وAiProviderDraftAdapterFactory. حل dialect المخزن ولا تحول native providers إلى Chat Completions.
- [ ] T22: قس حجم Kotlin وأزمنة catalog/editor/migration على CI Linux. لا تجعل اختلاف line endings في Windows يخالف البوابة.
- [ ] T23: سجل اختبار أجهزة Android للـpermissions والخلفية والمحاكي وتكوين الموفّرات، وافصل skipped عن passed.
- [ ] T24: تحقق workflow CI وكل خطوة في GitHub Actions من head النهائي.
- [ ] T25: fault-inject فشل الشبكة/الموفر/قاعدة البيانات/العملية وإعادة إرسال الحدث والموافقة.
- [ ] T26: شغل JVM/lint/build/coverage/full regression.
- [ ] T27: حدث docs/inventory من الـCI الحقيقي.
- [ ] T28: أغلق التدقيق بناء على artifacts لا على مربعات الخطة.
- [ ] T29: merge بعد code review وCI ناجح؛ لا تصدر tag.

## المشروع 1: إدارة وكلاء AI داخل التطبيق

1. اكتب core:agent-runtime بعقود AgentDefinition, AgentPolicy, AgentBudget, AgentRun, AgentApproval, AgentMemoryEntry واختبارات invariants مستقلة عن Android وCompose.
2. أضف core/database entities/DAO ومخطط Room جديدًا. زد schema version من قاعدة الفرع. اختبر ترحيل قاعدة الإصدار الحالي وإيقاف العملية واستعادة النسخة من دون إسقاط بيانات workflow.
3. أضف repositories/CAS/update revisions عبر data وservice. لكل run مفتاح idempotency وأحداث terminal مرة واحدة.
4. طبّق policy-filtered tool executor وapproval coordinator. اربط الموافقة بالمراجعات والفعل والجهاز والمنقضي مرة واحدة. مرر التعديلات عبر AutomationCommandService.
5. أضف AgentRunCoordinator/budget/timeout/cancel/recovery. اختبر حدود الوقت والحجم والتكلفة المجهولة والفعل قبل وبعد process kill.
6. اربط AiAgentTraceSink ب run ID واحذف prompt/private data/secret/raw continuation من السجل. أضف ذاكرة اختيارية منضبطة النطاق والعمر وقابلة للمحو والتصدير.
7. أنشئ feature:agents: قائمة/محرر/تفاصيل/تشغيل/سجل/ذاكرة عبر Compose وHilt، مع صلاحيات وتكاليف وقدرات مترجمة وعربية RTL.
8. اربط AI chat الحالي بتعريف محفوظ؛ اختبر migration للجلسات المؤقتة وعزل وكيلين وإيقاف عملية.
9. اختبر MCP/REST/Binder والمحادثة تحت نفس المنفّذ والصلاحية. احتفظ بعقد Full access الخارجي كما هو.

## المشروع 2: محررات عامة وتوسيع العقد القانونية

1. راجع AutomationNodeCatalog وCanonicalIdentityRegistry وNodeSchema وTriggerNodeSchemas وActionNodeSchemas. فرّق identity counts عن features فعلية.
2. طور CanonicalSchemaFieldEditor وNodeConfiguratorState لكل نوع حقل: رقم/مدة/وقت ومنطقة زمنية/موقع/حزمة/URI/enum ديناميكي/قائمة/JSON/expression/secret reference. احتفظ بمحررات الوقت والمودم المتخصصة لحين ثبوت parity.
3. اربط label وhelp وunit وdefault وvalidation وvisibility predicates والعناصر المحلية بـstable field ID. أظهر أخطاء parse بدلا من إسقاطها.
4. افصل target/operation typed عن تعداد المصدر القديم في CanonicalWorkflowDocumentV3 وCanonicalWorkflowV3ReadMapper، بإصدار schema جديد وترحيل وexport/import قابلين للاختبار.
5. أضف typed TriggerSourceRegistry لمصدر الحدث/الحالة/العتبة/الجدول. اختبر إبطال الصلاحية، unknown، إزالة التكرار، reboot وDST. لا تغير ANY/ALL الحالية لإضافة تسلسل زمني.
6. أضف OperationRegistry جديدًا مع مخطط/مدخل/مخرج/قدرة/idempotency/compensation. اختبر إجراء canonical جديد read-only من المحرر إلى الحفظ ثم planner وruntime بعد process restart.
7. لا تعرض عقودا جديدة حتى يطابق schema/catalog/compiler/handler/device capability.

## المشروع 3: موجات توسيع المحفزات والإجراءات

كل إضافة تسجل family/ID/schema/editor/runtime owner/permission/provider/units/output/privacy/retry/end action/tests/evidence.

- [ ] Wave A: الوقت والتقويم والاستثناءات، إشعارات متعددة المرشحات، دخول وخروج التطبيق، المتغيرات والعمليات والبيانات.
- [ ] Wave B: شبكات مقاسة وآمنة، BLE/NFC/USB بتصفية وأذونات، ملفات عبر URI يمنحه المستخدم، calendar/email/call/SMS composer مقابل send.
- [ ] Wave C: خدمات المرافق، Wear/desktop، home/HTTP/plugin providers ببروتوكول موثق ومصادقة وتكرار ومهل وإلغاء.
- [ ] Wave D: Shizuku/Root/OEM/accessibility/Device Owner فقط مع صحة القدرة وقراءة الحالة وقياس أجهزة فعلية.
- [ ] Wave E: AI extraction/classification/memory بمدخل/مخرج schema ووقت وتكلفة وحدود وapproval gates.
- [ ] كل نوع جديد ينجح عند data malformed, unknown permission, unavailable hardware, timeout, cancellation, duplicate event, process death والحدود الخاصة بعقدته.

## المشروع 4: بوابات الإصدار

1. اختبر كل migration وعقود الموفّر والأداة والمحادثة والثقة.
2. شغل check_canonical_final_audit.py وcheck_canonical_legacy_mappings.py وcheck_canonical_release_readiness.py، وفرق بين audit inventory واختبارات runtime.
3. شغل detekt/lintDebug/coverageGate/testDebugUnitTest/assembleDebug وCI على commit النهائي.
4. اختبر RTL/TalkBack/font scale/process restart على الأجهزة الملائمة.
5. سجل proof لكل capability كواحدة من VERIFIED_LOCAL, IMPLEMENTED_UNVERIFIED, PUBLIC_API_PERMISSION_REQUIRED, COMPANION_REQUIRED, PRIVILEGED_BACKEND_REQUIRED, UNAVAILABLE_CURRENT_DEVICE, UNSUPPORTED.
6. حدث وثائق CAPABILITY_CATALOG وMATRIX. لا تساوي المحاكي بإثبات هاتف فعلي ولا release asset بنجاح التنفيذ على الجهاز.

## سجل التنفيذ المتواصل

هذا السجل يفصل ما نُفذ عن بقية الخطة؛ وجود تعريفات أو محرر لا يعني تشغيل وكيل أو تغطية كل المحفزات والإجراءات.

- [x] R1: إنشاء `core:agent-runtime` بعقود تعريف/سياسة/ميزانية ومنفذ أدوات يطبق قائمة السماح والموافقة قبل التنفيذ؛ اختبارات الوحدة تغطي رفض الأداة، تثبيت لقطة الأدوات للتشغيل، وحدود الزمن/الحجم/التكلفة.
- [x] R2: ترقية Room إلى schema 25 وإضافة migrations تحفظ البيانات السابقة وجداول `agent_definitions` و`agent_runs` و`agent_run_events` و`agent_approvals`؛ DAO/مستودعات CAS ومفاتيح منع التكرار وموافقة أحادية الاستعمال؛ اجتياز اختبارات الترحيل والمستودع.
- [x] R3: إضافة شاشة إدارة داخلية منفصلة عن قائمة الوكلاء الخارجيين المقترنين، مع إنشاء/تحرير السياسة والميزانية وربط ملف موفر محفوظ؛ ترجمة كاملة واجتياز parity وtranslation gates.
- [x] R4: حفظ دورات التشغيل والموافقات بمفاتيح منع التكرار، انتقالات terminal أحادية، استعادة التشغيل غير المحسوم إلى `INTERRUPTED`، وإبطال الموافقات؛ ربط `AiConversationEngine` بكل تعريف ولائحة أدوات وميزانية وملف مزود محدد. الذاكرة الاختيارية، وأثر المحرك المنقح، ومداخل MCP/REST/Binder ضمن الأعمال المتبقية.
- [x] R5: اختبار تكامل فعلي من التعريف المحفوظ إلى المزود والأداة والموافقة وسجل التشغيل؛ يثبت الاختبار حجب بيانات الاعتماد من المعاينة وعدم حفظ المحفّز أو مدخل الأداة. اختبارات المستودع تغطي منع التكرار/الموافقة/الاستعادة، مع نجاح `assembleDebug`.
- [ ] R6: كل Wave للمحفزات والإجراءات تمر بعقد typed مخصص، محرر، صلاحية، تخزين/ترحيل، planner/runtime، اختبار malformed/revoke/duplicate/crash، ثم دليل جهاز عند الحاجة.
