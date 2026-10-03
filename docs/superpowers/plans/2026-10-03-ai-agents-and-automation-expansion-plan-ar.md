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
- [ ] R4: حفظ دورات التشغيل والموافقات بمفاتيح منع التكرار، بصمات HMAC بمفتاح من التخزين المشفّر، انتقالات terminal أحادية، استعادة التشغيل غير المحسوم إلى `INTERRUPTED`، وإبطال الموافقات. يعاد فحص تفعيل/مراجعة/قائمة سماح الوكيل قبل وبعد الموافقة وقبل الأثر؛ أدوات الكتابة/المجهولة تتطلب التأكيد افتراضياً؛ ويُنهي استنفاد حد الأدوات التشغيل كفشل. أضيفت ذاكرة اختيارية مشفّرة يدوية لكل وكيل، بمدة انتهاء ومحو وتصدير JSON صريح للمستخدم، مع سياق تشغيل محدود وغير موثوق. يبقى أثر المحرك المنقح ومداخل MCP/REST/Binder والتكامل مع AI chat ضمن الأعمال.
- [x] R5: اختبارات تكامل من التعريف المحفوظ إلى المزود والأداة والموافقة وسجل التشغيل؛ تثبت حجب بيانات الاعتماد من المعاينة وعدم حفظ المحفّز أو مدخل الأداة، الرفض المغلق عند سقف التكلفة المجهولة، وفشل التشغيل عند استنفاد الأدوات. اختبارات المستودع تغطي منع التكرار/خصوصية البصمة/الموافقة/الاستعادة، مع نجاح سابق لـ`assembleDebug`.
- [ ] R6: كل Wave للمحفزات والإجراءات تمر بعقد typed مخصص، محرر، صلاحية، تخزين/ترحيل، planner/runtime، اختبار malformed/revoke/duplicate/crash، ثم دليل جهاز عند الحاجة.

### استكمال R4/R6 — مراجعة الموافقة والتحرير المخصص

### تقدم 2026-10-03 — أول مسار Canonical Native عامل

- [x] إضافة `canonicalNodes` إلى نموذج الأتمتة وكتابة schema v4، مع قبول v3 للقراءة؛ والتحقق من الهوية الثابتة ونوع المخطط والحقول والقيم وتسلسل العقد قبل التخزين.
- [x] تمرير العقد عبر `CanonicalWorkflowV3ReadMapper` و`AgentTaskDraftV1` و`AgentTaskMapper` وبصمة التغيير؛ اختبارات تثبت حفظ Room وتحديث الوكيل عند تضمين العقد وتغير البصمة عند تعديلها.
- [x] إضافة planner/registry/dispatcher typed، ورفض العقود أو handlers أو القدرات غير المسجلة قبل أي أثر، مع ميزانية تنفيذ وcheckpoint durable داخل `ExecutionEngine`.
- [x] تسجيل إجراء Canonical مستقل حقيقي `core.workflow.delay`؛ يسمح من 0 إلى 5 دقائق، ويعمل حتى كـAST root. واجهة builder تعرضه فقط بوجود contract وhandler، وتسمح بإضافته وتغيير مدته وترتيبه وحذفه وتحفظ القائمة عند تحديث المهمة.
- [x] تمكين secret-reference في محرر المخطط: إعداد المعرف opaque فقط، وإدخال السر transient ثم حفظه في `SecretVault` المشفر، وعدم إدخال القيمة الخام في config/AST/JSON.
- [x] اجتياز اختبارات Canonical document وRoom mapper وAgentTask mapper/fingerprint وdispatcher وExecutionEngine checkpoint وbuilder field/secret-reference؛ كما نجح `:app:kspDebugKotlin` و`:app:compileDebugKotlin`.
- [x] أضيف أول مصدر إنتاجي Canonical: عتبة البطارية بمخطط typed (0–100، أعلى/أدنى، نوع الشاحن، وحالة الشحن)، وربط handler بقراءة حالة Android الحية ومراقب البطارية الحالي وANY/ALL وexit lifecycle. يعرض builder المصدر فقط إذا كان العقد والـhandler مسجلين معاً، ويمنع خلطه بالمحفزات legacy؛ اختبارات البطارية تتحقق من الفلترة ومن رفض الحقول غير الصالحة.
- [ ] لا تزال بقية مصادر Canonical للأحداث والجداول، إزالة تكرار أحداث reboot/الجدولة وقياس الأجهزة، وwaves A–E غير منفذة بالكامل.
- [ ] الإجراءات الأصلية الحالية لا تتداخل في قائمة ترتيب واحدة مع الإجراءات legacy؛ تعرض كقسم مستقل بعد الإجراءات legacy، وتُرتب داخل القسم. يجب إضافة نموذج ترتيب موحد أو توضيح ذلك بالعقد قبل توسيع الكتالوج.
- [ ] إكمال MCP/REST/Binder/AI chat، وتغطية خصائص الموافقة والذاكرة المتبقية، موجات A-E، CI النهائي، اختبارات RTL/TalkBack والأجهزة الفعلية، مراجعة branch ثم الدمج المحلي إلى `main`.

- [x] اجعل موافقة أدوات الكتابة والأنواع المجهولة مطلوبة تلقائياً حتى لو غابت عن سياسة وكيل قديمة؛ لا يسمح بتجاوز الافتراضي إلا عبر اختيار opt-out صريح محفوظ في `approvalOptionalToolNames`.
- [x] أظهر أخطاء فك أنواع الحقول canonical في محرر البناء بدلاً من إسقاط القيمة غير الصالحة بصمت، مع ترجمة الرسالة لكل اللغات المشحونة.
- [x] أضف منتقي وقت وتاريخ لمحرر المخطط العام مع بقاء حقل ISO مرئياً وقابلاً للتحرير.
- [ ] وسّع العناصر المتخصصة إلى المدة والمنطقة الزمنية والحزمة وURI والإحداثيات والقوائم وJSON والسر المرجعي/اختيار الملف، واختبر حفظ/إعادة تحميل كل نوع.
- [x] أضف منتقي المنطقة الزمنية وعناصر JSON/الإحداثيات والقوائم typed، واختبر round-trip لوقت/تاريخ/منطقة/حزمة/URI/إحداثيات/JSON/قائمة عبر حد التخزين الحالي.
- [x] أضف اختيار تطبيق قابل للبحث لحقول الحزمة، واختيار مستند SAF لحقل URI مع الاحتفاظ بإذن القراءة الدائم ورفض حفظ URI إذا لم يمنح المصدر إذناً قابلاً للاحتفاظ.
- [ ] أضف instrumented/device coverage لاختيار مستند SAF وإعادة فتح الأتمتة بعد إعادة تشغيل العملية؛ لا يوجد إثبات جهاز فعلي في هذه المرحلة.
- [ ] أضف اختيار secret reference عبر المخزن الحامي؛ لا تقبل نص سر خام.
- [x] أضف محرر مدة بوحدات مفهومة مع حفظ القيمة بالميلي ثانية والتحقق من overflow؛ اختبار التحويل الدقيق والفيض في `CanonicalBuilderSchemaBridgeTest`.
- [x] سجل عقد البطارية وhandler في Hilt بمقياس التطبيق، واستخدم سجل العقود/المعالجات نفسه لاكتشاف محفزات builder؛ اختبار شامل يغطي مزيج فلاتر البطارية ورُفض القيم غير المطابقة للمخطط.
- [x] اجعل تقييم المحفزات canonical يبدأ بلقطة قدرة مغلقة افتراضياً؛ لا تعتبر قدرة قراءة حالة الجهاز متاحة ما لم يثبتها runtime.
- [x] أضف `CanonicalNativeNodeSchemaRegistry` كحد ثقة للحفظ والقراءة: العقد الواردة من الوكيل أو JSON لا تحدد مخططها الموثوق بنفسها؛ رفض schema غير المسجل/المزور يسقط كتابة canonical إلى legacy-only degraded بلا payload، واختبار يثبت عدم حفظ نص سري مزعوم في عقدة مخترعة.
- [x] أصلح `CanonicalTriggerDispatcher` لتمرير كل عقدة حاليّة إلى handler عند تكرار definitionId، مع اختبار محفزين بطاريتين بعتبتين مختلفتين يثبت نتائج مستقلة.
- [x] ارفض `endBehavior` غير المدعوم على كل عقدة canonical موثوقة؛ اختبار يثبت أن تكوينًا خامًا داخل end behavior يخفض الكتابة إلى degraded بلا JSON محفوظ.
- [x] ثبّت انتظار اختبار مصالحة التقويم على شرط `expectedEndAt` المثبت من المزود بدلاً من مهلة ثابتة قصيرة.
- [x] أضف واجهة ذاكرة يدوية لكل وكيل بملاحظات قابلة للتحرير والحذف والانتهاء والتصدير الصريح إلى ملف يختاره المستخدم؛ خزّن كتلة JSON لكل وكيل في `SecureStorage` المشفّر، وضع ملاحظاته النشطة المحدودة كبيانات JSON غير موثوقة بدور المستخدم فقط؛ اختبارات المستودع وتكامل التشغيل تثبت العزل والانتهاء والحذف والحدود وعدم تسرب سياق وكيل آخر.
- [x] أضف حقول `targetId` و`semanticId` الثابتة إلى كتابة V3 مع nullable defaults وقراءة متوافقة لسجلات V3 القديمة؛ اختبارات domain mapper تثبت الكتابة والقراءة.
- [ ] افصل هويات العقد عن أنواع legacy في نموذج الأتمتة والقراءة والتخطيط والتنفيذ؛ لا تفتح عائلة نوع جديدة حتى يكتمل مسارها end-to-end.
