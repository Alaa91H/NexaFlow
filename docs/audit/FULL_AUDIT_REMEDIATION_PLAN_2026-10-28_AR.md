# NexaFlow — تدقيق شامل وخطة إصلاح تنفيذية

| البند | القيمة |
|---|---|
| تاريخ التدقيق | 2026-10-28 |
| الفرع | `main` |
| التزام التدقيق | `5464d21fc` (`origin/main` — `feat(triggers): harden notification and communication filters (#244)`) |
| إعادة تحقق | `e260493c` (2026-11-01) — بوابات المصدر الآلية وحالة الخطة فقط؛ أرقام §3 و§18.2 و§18.3 تبقى عند `5464d21fc` |
| نطاق المصدر | 36 مشروع Gradle، 1,165 ملف Kotlin (699 إنتاجي + 452 اختبار + 14 جهاز)، 134,357 سطر Kotlin إنتاجي، 68,753 سطر اختبارات |
| المسار المحلي | `/mnt/sdb-disk/NexaFlow` |
| نوع التقرير | تدقيق مصدر + بوابات آلية + قياسات بناء. **لا يشمل اعتمادًا على جهاز فعلي.** |

Audit-Status: active
Audit-Baseline: e260493cbf3aea16eef9180fd8c5f0e87d43fac9

> **دلالة `Audit-Baseline`:** يشير إلى آخر التزام أُعيد التحقق فيه من البوابات الآلية وحالة تنفيذ الخطة (انظر §18.4)، وليس إلى الالتزام الذي قيست عليه أرقام §3 و§18.2 و§18.3؛ تلك القياسات تبقى على `5464d21fc`. إعادة التحقق لا ترقّي أي بند جهاز أو OEM من `BLOCKED`، ولا تُستخدم دليلًا على سلوك فعلي على جهاز.

> **قاعدة الأدلة المطبقة في هذا التقرير:** لا تُعد أي ميزة أو حماية «متحققة» لمجرد وجود كود أو واجهة أو اختبار شكلي. كل نتيجة مصنفة إما **مؤكدة** (بدليل قابل لإعادة التشغيل هنا)، أو **فجوة موثقة** (مثبتة بعدم وجود الدليل)، أو **غير قابلة للتحقق محليًا** (تحتاج جهازًا أو خدمة خارجية). هذا يطابق سياسة المشروع نفسها في `docs/VALIDATION.md` و`docs/AUDIT.md`.

---

## 1. الملخص التنفيذي

المشروع في حالة **نضج هندسي مرتفع جدًا** مقارنة بمشاريع Android مماثلة الحجم، وليس في حالة «إصلاح عاجل بسبب عيوب أمنية جوهرية». الأدلة على ذلك:

- طبقة أمنية صلبة: مصادقة Bearer مع ربط هوية النقل، رفض `Origin`، سياسة `Host` صارمة، تثبيت DNS عبر `OkHttp` مع إعادة تحقق لكل تحويل، رفض HTTP وتشديد HTTPS، مقارنة ثابتة الزمن للتوكنات، تشفير AES-GCM عبر Android Keystore، واستيراد محدود الحجم قبل التحليل.
- استمرارية بيانات محسوبة: Room بمخطط صريح (v28، 26 لقطة مخطط)، 27 ترحيلًا تغطي 1→28 مع اختبارات خطوات وسلاسل تاريخية، DataStore مع معالج فساد، وسجلات استئناف/إعادة تشغيل.
- جدولة موثوقة: هوية «حدوث» ثابتة، جيل جدولة، نافذة تسليم محددة، إعادة تسليح محدودة، وتمييز صريح بين نتيجة غير معروفة وإعادة محاولة عمياء.
- بوابات آلية كثيفة: 129 سكربت Python، 57 ملف اختبار سكربتات، تحقق توقيع اعتماديات لـ1,184 مكونًا، Detekt + Lint + تعقيد/حجم، وفحص أسرار على كامل تاريخ Git.

على مستوى التحقق الآلي: الترجمة وDetekt وLint نجحت بالكامل على HEAD، بينما مجموعة JVM لم تكتمل على مضيف التدقيق (1,718 اختبارًا، فشلان مرتبطان بالمضيف، و7 وحدات تجاوزت مهلة 10 دقائق) — التفاصيل في NFA-17.

**الفجوة الحاسمة ليست في الكود، بل في الإثبات:** لا يوجد أي دليل جهاز فعلي (Android 26–37، Doze، إقلاع، Root/Shizuku، OEM)، ومسار الاختبارات على الجهاز غير إلزامي ويعمل على مستوى API واحد فقط (35) بينما التطبيق يستهدف 37. إضافة إلى ذلك، بوابة التغطية لا تشمل الوحدات الحرجة، وأهم واجهة REST (`AgentApiController`) بلا اختبار مباشر، وملف `mapping.txt` لإصدار R8 لا يُنشر — ما يجعل تقارير الأعطال الميدانية غير قابلة لفك التشويش.

**التقدير العام: 3.6/5 — جاهز للمصدر والتوزيع عبر GitHub، وغير جاهز لإعلان «جاهزية إنتاجية» أو للنشر في Google Play قبل معالجة NFA-01/NFA-02/NFA-10.**

### أهم عشر إجراءات (مرتبة بالأثر)

1. نشر `mapping.txt` وربطه بـSentry/الإصدار — حاليًا أي crash من APK مُصغّر بـR8 غير قابل للقراءة (NFA-03).
2. جعل اختبارات `connectedAndroidTest` إلزامية على الأقل لمستويي API 26 و37 (NFA-02).
3. تغطية `AgentApiController` باختبارات عقد ومصادقة سلبية (NFA-05).
4. توسيع بوابة التغطية لتشمل `core:execution` و`core:automation-engine` و`core:agent-api` و`core:rom-integration` (NFA-04).
5. تثبيت GitHub Actions بـSHA + توليد SBOM وفحص CVEs (NFA-08).
6. بناء مصفوفة أجهزة/OEM وتشغيل soak 72 ساعة مع قالب أدلة موحد (NFA-01).
7. تحديث سجل `findings.md` و`AUDIT.md` إلى HEAD الحالي وإزالة الادعاءات المتقادمة مثل `LOCKED_BOOT_COMPLETED` (NFA-11).
8. إضافة بوابة حدود وحدات (Konsist أو سكربت) تمنع `feature → data/database` و`core → engine` (NFA-07).
9. تشغيل `macrobenchmark` وفرض ميزانية بدء تشغيل في CI (NFA-12).
10. حسم قناة التوزيع (GitHub فقط أم Play) قبل أي عمل على الامتثال أو الـflavors (NFA-10).

---

## 2. نطاق التدقيق والمنهجية

### 2.1 ما شمله التدقيق

| المحور | ما تم فحصه |
|---|---|
| البنية | `settings.gradle.kts`، 36 مشروعًا، خريطة اعتماديات الوحدات، حدود الطبقات، حجم الملفات، ازدواج النموذج القديم/الكنوني. |
| الأمن | Manifest (42 صلاحية، 20 مكوّنًا، 9 مُصدَّرة)، `core/security`، `core/agent-security`، `core/agent-api`، Webhooks، HTTP action، الاستيراد/التصدير، Sentry، التوقيع. |
| البيانات | Room v28، 27 ترحيلًا، اختبارات الترحيل، DataStore، النسخ الاحتياطي، حدود الاستيراد. |
| الموثوقية | `AutomationAlarmReceiver`، الجدولة، هوية الحدوث، الإلغاء، الاستئناف، البوت، تغيير الوقت/المنطقة الزمنية. |
| الجودة | Detekt، Lint، التغطية، عدد الاختبارات، الاستثناءات المُسكَتة، TODOs، حجم الملفات. |
| البناء والتوريد | Gradle 9.6.1، AGP 9.3.1، Kotlin 2.4.10، R8، تحقق التبعيات، Actions، Dependabot، الإصدار. |
| التعريب والوصول | 10 لغات + الإنجليزية، تكافؤ المفاتيح، النص المضمّن، RTL، فحوص إمكانية الوصول. |
| التوثيق | ادعاءات README/catalog، سجلات التدقيق، اتساقها مع HEAD. |

### 2.2 الأساليب

1. **قراءة مصدرية موجهة**: بدءًا من الأسطح عالية المخاطر (المصادقة، الشبكة، التنفيذ، الترحيلات) لا من الوثائق.
2. **إعادة تشغيل البوابات**: تشغيل سكربتات التحقق الفعلية على HEAD بدل نقل نتائج قديمة.
3. **قياس**: إحصاء الملفات/الأسطر/الاختبارات/الصلاحيات/الاعتماديات آليًا.
4. **محاولة بناء نظيف**: `:app:compileDebugKotlin` على JDK 21 (انظر §19 لقيود البيئة).
5. **مقارنة الادعاءات بالواقع**: كل ادعاء في README/docs يُقابل بملف وسطر.

### 2.3 تصنيف النتائج

| التصنيف | المعنى |
|---|---|
| `CONFIRMED` | عيب أو نقص مثبت بملف/سطر أو بمخرَج أداة. |
| `DOCUMENTED_GAP` | الفجوة معترف بها في وثائق المشروع، لكنها ما زالت مفتوحة وتؤثر على القبول. |
| `UNVERIFIED` | لا يمكن إثباتها في هذه البيئة (جهاز/خدمة/حساب). تُعامل كغير منجزة. |

---

## 3. الحقائق المقاسة (Baseline)

| المقياس | القيمة | الدليل |
|---|---:|---|
| مشاريع Gradle | 36 | `settings.gradle.kts` |
| ملفات Kotlin (بلا مخرجات build) | 1,165 | `find` مع استثناء `build/` و`.t09-build-cache/` |
| منها إنتاجية / اختبار / جهاز | 699 / 452 / 14 | `src/main` / `src/test` / `src/androidTest` |
| أسطر Kotlin إنتاجية | 134,357 | `src/main` |
| ملفات اختبار JVM | 452 | `src/test` |
| اختبارات JVM (`@Test`) | 3,828 | عدّ نصي |
| أسطر اختبارات JVM | 68,753 | `src/test` |
| ملفات اختبار جهاز | 14 (22 اختبارًا) | `src/androidTest` |
| الصلاحيات المعلنة | 42 | `AndroidManifest.xml` |
| المكوّنات المعلنة | 20 (9 `exported=true`) | `AndroidManifest.xml` |
| مكتبات في كتالوج الإصدارات | 69 | `gradle/libs.versions.toml` |
| مكونات مثبتة بالتحقق | 1,184 | `gradle/verification-metadata.xml` |
| اللغات | الإنجليزية + 10 | `values-*`, `locales_config.xml` |
| المحفزات/الإجراءات | 57 (55 ظاهرة) / 180 | بوابة الكتالوج |
| `@Suppress` / `@SuppressLint` | 84 / 42 | بوابة الميزانية |
| `TODO/FIXME/HACK` في الإنتاج | 0 | فحص نصي |
| سكربتات Python | 129 + 57 ملف اختبار | `scripts/` |
| CI | 870 سطرًا، 7 وظائف | `.github/workflows/nexaflow-ci.yml` |

### 3.1 نتائج البوابات المعاد تشغيلها

| البوابة | النتيجة |
|---|---|
| `check_strings_parity.py` | ✅ `PARITY_PROBLEMS: 0` |
| `auto_fix.py --check` | ✅ لا موارد يتيمة/مكررة |
| `audit_translation_completeness.py` | ✅ `TRANSLATION_PROBLEMS: 0` |
| `check_hardcoded_text.py` | ✅ 703 ملفًا نظيفًا، مرساة واحدة مسموحة |
| `check_vendor_neutrality.py` | ✅ |
| `audit_catalog_and_releases.py catalog` | ✅ 57 محفزًا و180 إجراءً، تكافؤ كامل |
| `check_suppression_budget.py` | ✅ 84/42 |
| `check_readme_stale_counts.py` | ✅ |
| `check_resources.py` | ✅ |
| `check_persistence_safety.py` | ✅ 26 مخططًا، الأحدث 28 |
| `check_ai_secret_boundary.py` / `check_ai_provider_registry.py` | ✅ |
| `check_atomic_overhaul_baseline.py` / `check_atomic_architecture_fitness.py` | ✅ |
| `check_node_contracts.py` | ✅ 180/57 مفتاحًا مطابقًا للمخططات |
| `audit_findings.py` | ✅ 14 نتيجة قديمة سليمة البنية |
| `check_canonical_release_readiness.py` | ⚠️ فشل مبرر: «working tree is not clean» بسبب مجلد بناء غير متتبع — ليس عيبًا مصدريًا |
| `check_canonical_release_closure.py` | ⚠️ فشل تابع للبوابة السابقة |
| `audit_exported_components.py` | ⚠️ يحتاج `--merged <manifest>`؛ شُغّل بلا معامل خطأً |
| `./gradlew :app:compileDebugKotlin` (JDK 21) | ✅ `BUILD SUCCESSFUL in 13m 17s` — 306 مهمة، 0 أخطاء مع `allWarningsAsErrors` |
| `./gradlew detekt lintDebug --continue` | ✅ `BUILD SUCCESSFUL in 21m 45s` — 1,361 مهمة، Detekt وLint لكل الوحدات |
| `./gradlew testDebugUnitTest --continue` | ⚠️ `BUILD FAILED in 31m 08s` — 1,718 اختبارًا نُفّذت في 26 وحدة، 2 فشل، 0 أخطاء، **7 وحدات تجاوزت مهلة 10 دقائق** فلم تُنفّذ اختباراتها |
| إعادة تشغيل `:core:common:testDebugUnitTest` بمهلة 45 دقيقة | ✅ `BUILD SUCCESSFUL in 3m 37s` — يثبت أن المهلة سببها التزاحم لا التعليق |
| `:core:rom-integration:testDebugUnitTest --tests *RootGrantFlowTest*` | ⚠️ 14 اختبارًا، **2 فشل حتمي** بسبب وجود `/sbin/su` على مضيف Linux (NFA-17) |

> **ملاحظة بيئة:** استخدام ذاكرة Gradle المؤقتة القديمة (`.t09-build-cache/gradle-home`) أدى إلى فشل تحقق 5 ملفات POM لأن نسخها المخزنة كانت قد فقدت CRLF. تحققنا من أن التجزئات الأصلية على Maven Central تطابق `verification-metadata.xml`، ثم أُعيد البناء باستخدام `~/.gradle`. هذا عيب بيئة/كاش وليس عيبًا في المشروع (NFA-14).

### 3.2 نتائج Lint (كل الوحدات)

- **0 أخطاء، 41 تحذيرًا** (لا تفشل البناء حسب `lint.xml`).
- أعلى التحذيرات: `ObsoleteSdkInt` (17)، `UseRequiresApi` (5)، `SdCardPath` (3)، `ExportedContentProvider` (2)، `PrivateApi` (2)، `Typos` (2)، `DataExtractionRules` (2).
- تحذيران `ExportedContentProvider` يخصان مزودي Sentry المعطّلين صراحة (`enabled=false`)؛ يجب توثيقهما أو كتمهما بسبب مبرر بدل تركهما ضوضاء.
- تحذير `DataExtractionRules` في `wear/src/main/AndroidManifest.xml:12`: تطبيق الساعة لديه `allowBackup=false` لكن بلا `dataExtractionRules`، بينما تطبيق الهاتف يملكهما — تفاوت سياسة نسخ/نقل يستحق التوحيد.
- `PrivateApi` في `PrivilegedTelephonyBridge.kt:118,122` متوقع لمسارات الامتياز، لكنه يحتاج إلى اختبار جهاز عند تغيير API.

---

## 4. تقييم الأبعاد

| البعد | الدرجة | التبرير الموجز |
|---|:---:|---|
| الأمن التطبيقي | 4.0/5 | سياسات شبكة ومصادقة وتشفير ممتازة؛ ينقصها إثبات جهاز، ونشر mapping، وفحص CVEs. |
| سلامة البيانات | 4.5/5 | Room صريح + 27 ترحيلًا مختبَرًا + استيراد محدود + DataStore مع فساد محكوم. |
| موثوقية التنفيذ | 4.0/5 | هوية حدوث وجيل جدولة واستئناف ناضج؛ يبقى إثبات الطاقة/Doze/الإقلاع على الجهاز. |
| الاختبارات | 3.5/5 | 3,828 اختبار JVM مُعلن؛ 1,718 نُفّذت هنا، و22 اختبار جهاز فقط، وبوابة تغطية جزئية، وسطح REST الرئيسي بلا اختبار. |
| قابلية الصيانة | 2.5/5 | 13 ملفًا ضخمًا مجمّدًا، تعطيل قواعد التعقيد، وانحراف حدود الوحدات. |
| الأداء | 2.5/5 | Baseline profile وR8 وCompose metrics موجودة، لكن لا قياس في CI ولا ميزانيات. |
| سلسلة التوريد والإصدار | 3.5/5 | تحقق توقيع ممتاز وتوقيع إنتاجي إلزامي؛ Actions غير مثبتة بـSHA، لا SBOM، لا فحص ثغرات. |
| التعريب والوصول | 3.5/5 | تكافؤ لغوي ممتاز وبوابة نص مضمّن؛ لا أتمتة TalkBack/RTL. |
| التوثيق والامتثال | 3.0/5 | توثيق غني جدًا لكنه متقادم جزئيًا، وقرار توزيع/Play غير محسوم. |

---

## 5. البنية المعمارية

### 5.1 ما هو سليم

- فصل واضح بين `domain` (نموذج قابل للتسلسل، بلا Android)، و`data` (مستودعات واستيراد/تصدير)، و`core/*` (محرك، تنفيذ، أمان، ROM)، و`feature/*` (Compose)، و`app` (تركيب Hilt ونقاط الدخول).
- مسار تنفيذ واحد مهيمن: `ActionRegistry` ← معالجات ← `CapabilityRouter` للمهام الدلالية، مع تحقق لاحق للشرط بدل افتراض النجاح.
- ازدواج النموذج القديم/الكنوني مُدار بأدلة (T05–T44) وليس بمسار تنفيذ موازٍ.

### 5.2 الانحرافات المؤكدة

| الانحراف | الدليل | الأثر |
|---|---|---|
| `feature/dashboard` تنشئ `BackupManager` مباشرةً من طبقة البيانات | `feature/dashboard/.../DashboardViewModel.kt:15,89` | كسر عزل الطبقات، صعوبة الاختبار، وتسريب تفاصيل التنفيذ إلى الواجهة. |
| `feature/history` تعلن اعتمادًا على `:core:database` بلا أي استخدام | `feature/history/build.gradle.kts:74` + لا imports لـ`com.nexaflow.core.database` | اقتران بناء غير ضروري وإبطاء الترجمة. |
| `core:capability-manager` تعتمد على `core:automation-engine` | `capability-manager/.../SmsCapabilityViewModel.kt:6` | اتجاه اعتماد من طبقة قدرات إلى محرك، يعكس الاتجاه المقصود. |
| `core:datastore` تعتمد على `core:ai-runtime` | `core/datastore/.../AiProviderPreferences.kt:4-8` | طبقة التخزين تعرف أنواع وقت تشغيل AI؛ يصعّب إعادة الاستخدام. |
| لا بوابة تفرض الحدود | `scripts/check_atomic_architecture_fitness.py:9,27` يفحص الحجم والـCoroutineScope فقط | الانحرافات أعلاه ستتكرر بلا اعتراض. |
| اعتماد مكرر | `feature/automation-builder/build.gradle.kts:104-105` | تلوث وإرباك بسيط. |

### 5.3 حجم الملفات

13 ملفًا إنتاجيًا «مجمّدًا» عند خط أساس، أكبرها:

| الملف | الأسطر | البايتات |
|---|---:|---:|
| `feature/automation-builder/.../TriggerEditorCard.kt` | 2,388 | 187,609 |
| `feature/automation-builder/.../AutomationBuilderScreen.kt` | 2,013 | 161,621 |
| `core/rom-integration/.../SystemController.kt` | 1,935 | 99,683 |
| `feature/automation-builder/.../ActionConfigEditor.kt` | 1,831 | 128,671 |
| `core/execution/.../ExecutionEngine.kt` | 1,627 | 83,641 |

السياسة الحالية (`MAX_NEW_KOTLIN_BYTES = 45,000` مع تجميد القائمة) تمنع التدهور لكنها **لا تفرض تقليصًا**؛ لذلك يبقى الدين ثابتًا.

---

## 6. الأمن والخصوصية

### 6.1 ضوابط متحققة بالكود

| الضابط | الدليل | ملاحظة |
|---|---|---|
| منع طلبات المتصفح لسطح الوكيل | `AgentApiController.kt:58` (رفض `Origin`) | يقلل CSRF/DNS rebinding. |
| سياسة Host صارمة | `AgentApiHostPolicy.kt` | IPv4/IPv6 خاصة فقط عند تفعيل LAN، وهو معطّل حتى يتوفر TLS. |
| مصادقة Bearer + ربط هوية | `AgentApiController.kt:307-344` | التوكن مقسوم إلى id.secret، ويُخزن hash فقط (`AgentTokenCodec`). |
| مقارنة ثابتة الزمن | `AgentTokenCodec.matches` / `ExternalAccessPolicy.authorized` | مع سقف 4096 بايت. |
| تشفير الأسرار | `KeystoreSecureStorage` + `AesGcmEnvelopeCodec` | AES-256-GCM، AAD يربط المفتاح المنطقي، وترحيل V2 كسول. |
| منع SSRF وتثبيت DNS | `HttpUrlPolicy` + `SecureHttpTransport` | HTTPS فقط، لا redirects تلقائية، سقف 128KB، ومقاومة rebinding. |
| Webhooks محلية محصّنة | `WebhookRequestGuard` + `WebhookServer:126` | 127.0.0.1 فقط، توكن إلزامي، حدود رؤوس/مهلة. |
| استيراد محدود | `BackupLimits` + `BackupManager` | 4MiB، عمق 32، 500 مهمة، تعطيل المستورد وتجريد التوكنات. |
| تحقق مكونات مُصدَّرة | `scripts/audit_exported_components.py` + `ExportedComponentsAuditTest` | CI يفحص الـmanifest المدمج. |
| Sentry بلا PII | `SentryReporter.kt:77` | لقطات الشاشة وNDK معطّلان، والتفعيل اختياري. |

### 6.2 مخاطر قائمة

1. **42 صلاحية** بينها حساسة/مقيّدة: `SEND_SMS`, `RECEIVE_SMS`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, إضافة إلى خدمة إمكانية الوصول. هذا مقصود وظيفيًا لكنه يفرض عبئًا امتثاليًا كبيرًا لأي قناة غير GitHub.
2. **`QUERY_ALL_PACKAGES`** مبرر في `lint.xml` وظيفيًا، لكنه من أكثر الأسباب رفضًا في Play، ولا يوجد ملف تبرير/إفصاح جاهز.
3. **الأسرار في النسخ الاحتياطي**: `docs/SECURITY.md` يصرّح بأن «بيانات اعتماد الإجراءات الأخرى لا تُجرَّد تلقائيًا». هذا حدّ معروف، لكنه يستحق تصنيفًا صريحًا في واجهة التصدير.
4. **بدون mapping** لا يمكن تحليل أعطال الإصدار (NFA-03).
5. **لا فحص ثغرات للاعتماديات** (NFA-08).

---

## 7. سلامة البيانات والاستمرارية

- **Room**: `AppDatabase` v28، `exportSchema = true`، 26 لقطة مخطط، 27 ترحيلًا، و`MigrationTest.kt` (723 سطرًا) يغطي خطوات 1→28 وسلاسل تاريخية. لا يوجد `fallbackToDestructiveMigration` ولا `allowMainThreadQueries`.
- **DataStore**: معالج فساد + اختبارات. المشكلة الوحيدة المسجلة هي استبدال الملف على Windows (T02-001 / NFA-14).
- **النسخ الاحتياطي**: 4MiB، حدود شكلية صريحة، تعطيل الاستيراد، تجريد `deepLinkToken` وتوكن Webhook. اختبار `BackupPolicyManifestTest` يحمي `allowBackup=false`.
- **الاستئناف**: `ExitCoordinator` + سجل استئناف دائم + مفاتيح idempotency مجزّأة. لا إعادة تنفيذ ضمنية لتأثير جانبي غير محسوم — وهذا هو السلوك الصحيح.

**الفجوة المتبقية**: اختبارات انهيار حقيقية عبر حدود Room/DataStore/السجل (T02-004) ما زالت غير مثبتة على جهاز؛ الاختبارات الحالية تحاكي الفشل منطقيًا لا على نظام ملفات فعلي.

---

## 8. الموثوقية والجدولة

نقاط قوة مثبتة في `AutomationAlarmReceiver`:

- رفض أي إنذار بلا `occurrenceId` أو `generation` أو `windowStartAt`.
- تحقق `matchesIncomingOccurrence` قبل التنفيذ، وقتل «الحدوث المتقادم».
- نافذة سماح 15 دقيقة للحدث النقطي، ورفض بدء نافذة زمنية انتهت.
- wake lock bounded (30 ثانية) و`goAsync` مع `finish()` في `finally`.
- إعادة تسليح محدودة عند فشل المستقبل، مع تسجيل صريح لعدم توفر إعادة المحاولة.
- `BOOT_COMPLETED` و`MY_PACKAGE_REPLACED` يعيدان الجدولة، و`TIME_SET`/`TIMEZONE_CHANGED`/`TIMEZONE_OFFSET_CHANGED` تعيد الحساب.

**ما لا يمكن إثباته هنا**: سلوك Doze، تقييد Android 15+ لبدء FGS من BOOT_COMPLETED، استمرار 72 ساعة، وأثر قتل العملية من الشركة المصنعة. جميعها مصنفة `UNVERIFIED` ومرتبطة بـNFA-01.

> **تصحيح لسجل قديم:** `docs/audit/findings.md` (T02-002) يستشهد بوجود `LOCKED_BOOT_COMPLETED` في `AndroidManifest.xml:196`. هذا الادعاء **لم يعد صحيحًا** على HEAD الحالي: لا يوجد أي تطابق لـ`LOCKED_BOOT_COMPLETED` في الـmanifest. النتيجة القديمة يجب أرشفتها لا تكرارها.

---

## 9. الأداء والموارد

- R8 كامل + `shrinkResources` + baseline profile مُضمَّن (`profileinstaller`) + `baseline-prof.txt`.
- Compose Compiler metrics مُفعّلة (`app/build.gradle.kts`) لكن **لا تُقرأ ولا تُبوَّب**.
- وحدة `macrobenchmark` موجودة (`StartupBenchmarks`, `CanonicalPlannerBenchmarks`) لكن **لا وظيفة CI تشغّلها**.
- لا ميزانية لزمن البدء أو حجم APK أو استهلاك البطارية، ولا مقارنة انحدار بين الإصدارات.
- لا قياس Perfetto/ApplicationExitInfo مرفق بأي إصدار.

**الخلاصة:** الأداء «مُهيَّأ للقياس» لكنه «غير مُقاس»؛ لا يجوز وصفه بالتحسّن أو بعدم الانحدار.

---

## 10. الاختبارات والجودة

| المؤشر | القيمة | الحكم |
|---|---:|---|
| اختبارات JVM | 3,828 | قوي |
| اختبارات جهاز | 22 (13 ملفًا في `:app` + مولّد Baseline Profile لا يعمل في CI) | ضعيف جدًا مقابل 9 مكوّنات مُصدَّرة و3 مسارات امتياز |
| اختبارات JVM نُفّذت فعليًا في هذه الجلسة | 1,718 من 3,828 | 7 وحدات لم تُكمل بسبب مهلة 10 دقائق |
| تغطية مفروضة | 6 وحدات فقط | غير كافٍ للوحدات الحرجة |
| `@Ignore` | 0 | جيد |
| `Thread.sleep` في الاختبارات | 12 | مقبول مع مراقبة |
| استثناءات عامة مُسكَتة في الإنتاج | 127 | يحتاج مراجعة انتقائية |
| Detekt: قواعد التعقيد | معطّلة | ثغرة صيانة |
| Lint: UnusedResources/MissingTranslation/HardcodedText | أخطاء | ممتاز |

**الفجوة الأهم**: لا اختبار مباشر لـ`AgentApiController` — الملف 1,019 سطرًا ويحتوي مسارات pairing/session/CRUD/idempotency/approval. توجد اختبارات لـ`AgentA2AController` و`AgentMcpController` و`AgentAccessManager` و`AgentHttpRequestParser` فقط. هذا يعني أن أكبر سطح هجوم شبكي محلي غير مغطى باختبار وحدة مباشر.

---

## 11. البناء والتوريد والإصدار

| البند | التقييم | الدليل |
|---|---|---|
| تحقق توقيع الاعتماديات | ممتاز | `gradle/verification-metadata.xml` (1,184 مكونًا) |
| توقيع إنتاجي إلزامي للوسوم | ممتاز | `app/build.gradle.kts` + CI |
| منع debug signing افتراضيًا | ممتاز | نفس الملف |
| تثبيت Actions بـSHA | ضعيف | `@v7`, `@v5`, `@v4.0.4` قابلة للتحرك |
| SBOM / provenance | غائب | لا سكربت ولا حزمة |
| فحص CVEs للاعتماديات | غائب | التحقق يثبت السلامة لا الخلو من الثغرات |
| رفع `mapping.txt` | غائب | لا ذكر في CI أو Gradle |
| Dependabot | موجود (Gradle + Actions يوميًا) | `.github/dependabot.yml` |
| فحص الأسرار | ممتاز | Gitleaks 8.30.1 على كل التاريخ، مثبت بالتحقق |
| فحص 16KB / zipalign / توقيع APK | ممتاز | CI |

**مخاطرة مركّبة**: وظيفة `release` تملك `contents: write` وتعمل على وسم، ووظيفة `merge-dependabot-update` تستخدم `pull_request_target`. تثبيت Actions بـSHA إلزامي هنا لأن اختراق وسم Action متحرك يعني صلاحية كتابة على المستودع.

---

## 12. التعريب والوصول والاتساق

- 10 لغات + الإنجليزية، تكافؤ مفاتيح 0 مشاكل، وفحص اكتمال ترجمة 0 مشاكل، وبوابة تمنع النص العربي المضمّن.
- `supportsRtl=true`، و`localeConfig` متسق مع `localeFilters`.
- لا يوجد اختبار RTL آلي أو pseudo-locale، ولا TalkBack/AccessibilityChecks مبرمجة. توجد اختبارات نموذج عرض لإمكانية الوصول (`NodeConfiguratorAccessibilityTest`) لكنها لا تفحص الشجرة الدلالية على جهاز.
- الترجمة «مكافئة بالمفاتيح» لا «مراجَعة بشريًا»؛ لا يوجد سجل مراجعة لغوية لكل لغة.

---

## 13. التوثيق والامتثال

- README دقيق في الأرقام (57/180) ويحتوي تحذيرات صريحة ضد المبالغة — وهذا يُحسب للمشروع.
- لكن `docs/AUDIT.md` ما زال يعرض جدولًا لكل إجراء بحالة `PARTIAL/UNTESTED` مبنيًا على تدقيق 2026-10-04، و`docs/audit/findings.md` مثبت على `a9cf4e6` وليس HEAD.
- `docs/MASTER_PLAN_STATUS_2026-10-05.md` يصف v3.91.10 بينما HEAD الحالي بعده بعشرات الالتزامات.
- لا يوجد مؤشر «عمر الوثيقة» ولا بوابة تفشل عند تدقيق أقدم من N التزامات.

---

## 14. سجل النتائج

### NFA-01 — غياب دليل جهاز/OEM

| الحقل | القيمة |
|---|---|
| الخطورة | **P0** |
| التصنيف | `DOCUMENTED_GAP` |
| المجال | قبول الجهاز |
| الدليل | لا جهاز متصل (`adb devices` فارغ)؛ CI يشغّل محاكي API 35 فقط؛ `docs/OPEN_QUESTIONS.md` OQ-01 مفتوح؛ `docs/VALIDATION.md` يصرّح `NOT TESTED`. |
| الأثر | كل ادعاء حول Doze، الإقلاع، FGS، Root/Shizuku، OEM، والاستمرار 72 ساعة غير مثبت. لا يمكن إعلان جاهزية إنتاجية. |
| الإصلاح | مصفوفة أجهزة دنيا: جهاز مرجعي (API 26 أو 30) وجهازان من OEM مختلفين (API 34–37). سيناريوهات: إقلاع، force-stop، Doze، تغيير وقت/منطقة، إذن إنذار دقيق، Shizuku ممنوح/مرفوض، Root ممنوح/مرفوض، 72h soak. قالب أدلة موحد + رفع السجلات المنقّحة. |
| معيار القبول | ملف `docs/evidence/device/<date>/matrix.md` يحتوي كل خلية بحالة `PASS/FAIL/BLOCKED` + logcat + إصدار البناء + بصمة الجهاز، ويعيد إنتاج السيناريوهات بعد أي تغيير في الجدولة/الاستئناف. |

### NFA-02 — اختبارات الجهاز غير إلزامية وعلى API واحد

| الحقل | القيمة |
|---|---|
| الخطورة | **P0** |
| التصنيف | `CONFIRMED` |
| الدليل | `.github/workflows/nexaflow-ci.yml:384` (شرط التشغيل: schedule أو workflow_dispatch)، `:427` (`system-images;android-35`)، `:465` (`connectedDebugAndroidTest`). 22 اختبارًا فقط. |
| الأثر | انحدارات الصلاحيات/الخدمات/الأمان لن تُكتشف قبل الإصدار. |
| الإصلاح | وظيفة إلزامية على PR لاختبارات جهاز مختارة (smoke) على API 37 + وظيفة ليلية على 26/33/36/37. تصنيف الاختبارات `@Smoke`/`@Full`. |
| معيار القبول | PR لا يُدمج إذا فشل smoke؛ تقرير ليلي يحتوي 4 مستويات API على الأقل. |

### NFA-03 — عدم نشر `mapping.txt` مع R8

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `app/build.gradle.kts` (`isMinifyEnabled = true`) ولا أي إشارة إلى `mapping.txt` في CI أو Gradle؛ Sentry بلا Gradle plugin. |
| الأثر | أي crash أو ANR من إصدار مُصغّر يكون غير قابل للفك؛ قيمة Sentry شبه معدومة. |
| الإصلاح | إضافة `sentry-android-gradle-plugin` أو خطوة `upload-artifact` لـ`app/build/outputs/mapping/release/mapping.txt` + ربطه بالإصدار (نسخة مشفرة/مقيدة). |
| معيار القبول | كل GitHub Release يحتوي أصل mapping (أو رابط داخلي) + اختبار يدوي: crash متعمد في إصدار release يُفك إلى symbol حقيقي. |

### NFA-04 — تغطية مفروضة على 6 وحدات فقط

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `build.gradle.kts:139-146`: `:data`, `:domain`, `:core:compatibility`, `:core:logging`, `:core:security`, `:core:wear-protocol`. |
| الأثر | `core:execution` (23k سطرًا) و`core:automation-engine` (13.5k) و`core:agent-api` و`core:rom-integration` بلا حد أدنى. |
| الإصلاح | تدرّج: (أ) قياس الحالي لكل وحدة؛ (ب) فرض 60% للوحدات الأربع ثم 70% خلال دورتين؛ (ج) إضافة "diff coverage ≥80%" للملفات المعدلة. |
| معيار القبول | `./gradlew coverageGate` يفشل عند النزول تحت العتبة لكل وحدة مدرجة، ويطبع النسبة في السجل. |

### NFA-05 — `AgentApiController` بلا اختبار مباشر

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `core/agent-api/.../AgentApiController.kt` (1,019 سطرًا)؛ لا ملف اختبار يشير إليه (`AgentA2AControllerTest`, `AgentMcpControllerTest`, `AgentAccessManagerTest` فقط). |
| الأثر | مسارات pairing/session/task CRUD/idempotency/approval غير محمية بانحدار. |
| الإصلاح | `AgentApiControllerTest`: توكن مفقود/تالف/منتهي/مُلغى، Origin، Host، content-length سالب، JSON زائد الحقول، idempotency مكرر، موافقة مفقودة، صلاحية ناقصة، حدود المعدل. |
| معيار القبول | ≥25 حالة اختبار، منها 10 سلبية للمصادقة/الحدود، وتمريرها في CI. |

### NFA-06 — دين التعقيد والحجم مجمّد لا ينقص

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `config/detekt/detekt.yml:32-51` يعطّل `CyclomaticComplexMethod`, `CognitiveComplexMethod`, `LongMethod`, `NestedBlockDepth`, `ComplexCondition`؛ `check_atomic_architecture_fitness.py:27` يجمّد 13 ملفًا حتى 187KB. |
| الأثر | معدل عيوب أعلى، مراجعة أصعب، واختبار أبطأ. |
| الإصلاح | تفعيل `LongMethod` و`NestedBlockDepth` بقيم متساهلة أولًا مع baseline مولّد، ثم تقليص تدريجي. تقسيم أعلى 5 ملفات إلى composables/helpers/handlers. |
| معيار القبول | لا ملف > 45KB جديد، وانخفاض مجموع البايتات المجمّدة 20% لكل دورة، وعدم زيادة baseline. |

### NFA-07 — انحراف حدود الوحدات بلا بوابة

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `DashboardViewModel.kt:15,89` (data inside feature)، `feature/history/build.gradle.kts:74` (اعتماد غير مستخدم)، `SmsCapabilityViewModel.kt:6` (core→engine)، `AiProviderPreferences.kt:4-8` (datastore→ai-runtime). |
| الأثر | تقويض قابلية الاختبار والتقسيم، ودورات بناء أطول. |
| الإصلاح | قاعدة معمارية (Konsist أو سكربت): `feature/*` لا تعتمد `:data` أو `:core:database`؛ `core/*` لا تعتمد `feature/*`؛ `core:datastore` لا تعتمد `core:ai-runtime` (نقل النماذج إلى `:domain` أو `:core:common`). |
| معيار القبول | البوابة تفشل على الاعتماد المخالف، وتمر على الشجرة الحالية بعد الإصلاح. |

### NFA-08 — سلسلة التوريد: Actions متحركة، لا SBOM، لا CVEs

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `uses: actions/checkout@v7`, `setup-java@v5`, `upload-artifact@v7`, `android-actions/setup-android@v4.0.4`؛ وظيفة `release` بـ`contents: write`؛ لا سكربت SBOM/CVE بين 129 سكربتًا. |
| الأثر | Action مخترق = تنفيذ كود بصلاحية كتابة/نشر. وغياب SBOM يمنع الاستجابة السريعة لثغرة مكتبة. |
| الإصلاح | تثبيت كل `uses` بـcommit SHA + تعليق الإصدار؛ `CycloneDX`/`syft` لتوليد SBOM لكل إصدار؛ `osv-scanner` أو Trivy في CI؛ توقيع provenance (SLSA) لـAPK. |
| معيار القبول | صفر `uses` بوسم متحرك؛ SBOM مرفق بالإصدار؛ فحص CVEs يفشل عند `HIGH/CRITICAL` بلا استثناء موثق. |

### NFA-09 — غياب خط تشخيص ميداني

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `DOCUMENTED_GAP` |
| الدليل | `SentryReporter.kt` يتطلب تفعيلًا صريحًا + DSN مضمّنًا وقت البناء؛ لا اختبار اتصال حقيقي؛ لا لوحة/تنبيهات؛ لا تصدير تشخيص محلي. |
| الأثر | أعطال المستخدمين غير مرئية، خصوصًا في مسارات الخلفية. |
| الإصلاح | تصدير حزمة تشخيص محلية (سجل منقّح + حالة جدولة + آخر 50 تنفيذًا) بزر في الإعدادات؛ اختياريًا telemetry مجهولة مع معدل أخذ عينات؛ لوحة ANR/crash. |
| معيار القبول | مستخدم يستطيع تصدير حزمة تشخيص من جهاز حقيقي، وتحتوي الحقول المطلوبة دون أسرار. |

### NFA-10 — قرار التوزيع والامتثال غير محسوم

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `DOCUMENTED_GAP` |
| الدليل | `docs/OPEN_QUESTIONS.md` OQ-02 وOQ-05 مفتوحان؛ 42 صلاحية بينها SMS/Accessibility/QUERY_ALL_PACKAGES. |
| الأثر | أي عمل على Play سيتغير جذريًا حسب القرار؛ والبناء الحالي لا يملك flavors. |
| الإصلاح | قرار مالك: GitHub فقط (الوضع الحالي) أو Play + GitHub. عند Play: `play` flavor بلا `QUERY_ALL_PACKAGES`/`REQUEST_INSTALL_PACKAGES` حيث أمكن، إقرارات SMS/Accessibility، Data Safety، وسياسة خصوصية منشورة. |
| معيار القبول | ADR موقّع + قائمة صلاحيات/إفصاح لكل قناة + بناء flavor يمر `lint` و`bundleRelease`. |

### NFA-11 — تقادم سجلات التدقيق

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `CONFIRMED` |
| الدليل | `docs/audit/findings.md:3` مثبت على `a9cf4e6`؛ T02-002 يذكر `LOCKED_BOOT_COMPLETED` الغائب حاليًا؛ `docs/AUDIT.md` جدول إجراءات بحالة `UNTESTED` من 2026-10-04. |
| الأثر | قرارات مبنية على واقع قديم؛ إهدار جهد في «إصلاح» ما أُصلح. |
| الإصلاح | إعادة توليد السجل على HEAD أو وسمه `historical`؛ بوابة `docs freshness` تفشل إذا تجاوز تدقيق نشط N التزامات. |
| معيار القبول | كل وثيقة تدقيق تحمل HEAD أو وسم تاريخي صريح؛ لا ادعاء مصدر متقادم في وثيقة «حالية». |

### NFA-12 — لا قياس أداء في CI

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `CONFIRMED` |
| الدليل | `macrobenchmark/` يحتوي اختبارين ولا وظيفة CI؛ مولّد `baseline-profile` يعمل يدويًا فقط؛ لا ميزانيات بدء/حجم/بطارية، ولا بوابة تتحقق من حداثة `baseline-prof.txt`. |
| الأثر | لا يمكن الدفاع عن أي ادعاء تحسّن أو كشف انحدار. |
| الإصلاح | وظيفة `benchmark` ليلية على جهاز/محاكي مع `StartupTimingMetric`، حد مبدئي p95، ونشر النتائج في `docs/evidence/benchmarks/`. |
| معيار القبول | سلسلة زمنية من 10 تشغيلات على الأقل + فشل عند تجاوز الميزانية. |

### NFA-13 — مقاييس Compose لا تُفحص

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `CONFIRMED` |
| الدليل | `composeCompiler { metricsDestination ... }` في `app/build.gradle.kts`؛ لا سكربت يقرأ `build/compose-metrics`. |
| الأثر | عدم استقرار (`unstable`) يتسبب في إعادة تركيب صامتة. |
| الإصلاح | سكربت يفشل عند زيادة عدد الـcomposables غير القابلة للتخطي أو الفئات غير المستقرة فوق خط أساس. |
| معيار القبول | تقرير يُنشر لكل PR مع فروق واضحة. |

### NFA-14 — قابلية إعادة البناء عبر المنصات/الكاش

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `CONFIRMED` (تاريخيًا) |
| الدليل | T02-001 (5/46 فشل DataStore على Windows)؛ في هذه الجلسة فشل تحقق 5 POMs بسبب كاش CRLF، بينما التجزئات الأصلية صحيحة. |
| الأثر | «يعمل على جهازي»؛ CI Linux لا يكشف مشاكل Windows. |
| الإصلاح | وظيفة CI ثانية على Windows (اختبارات `core:datastore` و`core:database` فقط)؛ بناء داخل حاوية مثبتة؛ منع إعادة استخدام كاش Gradle عبر المنصات. |
| معيار القبول | مجموعة فرعية حرجة تمر على Windows وLinux على نفس الالتزام. |

### NFA-15 — لا أتمتة وصول/RTL

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `DOCUMENTED_GAP` |
| الدليل | T02-009؛ لا `AccessibilityChecks`، ولا اختبار pseudo-locale، ولا لقطات RTL. |
| الأثر | تجربة عربية/TalkBack قد تنكسر دون اكتشاف. |
| الإصلاح | `AccessibilityChecks.enable()` في smoke tests؛ `ar` + pseudo-locale في Robolectric/Compose tests؛ لقطات مرجعية ar/en. |
| معيار القبول | صفر انتهاك خطير في الشاشات الرئيسية + مراجعة يدوية TalkBack موثقة. |

### NFA-16 — ديون تنظيف صغيرة

| الحقل | القيمة |
|---|---|
| الخطورة | **P3** |
| التصنيف | `CONFIRMED` |
| الدليل | `feature/history/build.gradle.kts:74` اعتماد غير مستخدم؛ `feature/automation-builder/build.gradle.kts:104-105` تكرار؛ `AgentApiServer.kt:254` ثابت `ALL_INTERFACES` غير مستخدم؛ `wear/src/main/AndroidManifest.xml:12` بلا `dataExtractionRules` بينما الهاتف يملكها. |
| الأثر | إرباك وإبطاء بناء بسيط. |
| الإصلاح | إزالة الاعتماد غير المستخدم والمكرر والثابت الميت؛ إضافة `dataExtractionRules` لتطبيق الساعة؛ تفعيل `UnusedPrivateProperty` في Detekt لمنع تكرار الثابت الميت. |
| معيار القبول | `./gradlew detekt` يمر بعد الإزالة. |

### NFA-17 — اختبارات غير معزولة عن المضيف + مهلة اختبار عدوانية

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `CONFIRMED` |
| الدليل | `RootGrantFlowTest.kt:65,202` يحقن `pathResolution = { false }` ثم يتوقع `isSuBinaryAvailable()==false`، لكن `SystemAppStatusDetector.kt:156-166,190-191` يفحص المسارات الثابتة (`/sbin/su` …) **قبل** استخدام الـseam. على مضيف Fedora حيث `/sbin/su` موجود، فشل الاختباران حتميًا (14 اختبارًا/2 فشل في 10s). إضافةً إلى ذلك `build.gradle.kts:38` يفرض مهلة 10 دقائق لكل وحدة، وقد أبطلت 7 وحدات على هذا المضيف رغم نجاح `:core:common` منفردًا في 3m37s. |
| الأثر | مجموعة الاختبارات ليست خضراء على كل منصة تطوير؛ والمهلة القصيرة تُنتج فشلًا كاذبًا وتُلغي تقارير الوحدة بالكامل بدل تسجيل ما اكتمل. |
| الإصلاح | (أ) جعل المسارات الثابتة قابلة للحقن (seam للـpaths وسلوك `File.exists`) أو التحقق أولًا من `pathResolution` في `isSuBinaryAvailable`؛ (ب) رفع المهلة إلى 25–30 دقيقة مع `maxParallelForks` محدود للوحدات الثقيلة، أو استخدام `forkEvery` + مهلة لكل اختبار بدل مهلة للوحدة؛ (ج) ضمان كتابة تقرير جزئي عند الإيقاف. |
| معيار القبول | `testDebugUnitTest` يمر على Ubuntu وFedora/Windows؛ لا وحدة تُقطع بسبب التزاحم عند `--max-workers=4`؛ تقرير XML موجود حتى عند الفشل. |

### NFA-18 — أسلوب Gradle DSL مضلّل

| الحقل | القيمة |
|---|---|
| الخطورة | **P3** |
| التصنيف | `CONFIRMED` |
| الدليل | `app/build.gradle.kts:95-97` و`:98` — `buildFeatures` و`testOptions` مكتوبان داخل `defaultConfig` نحويًا، ويعملان فقط لأن Kotlin يحلّهما على المستقبِل الخارجي (`android`). |
| الأثر | قارئ جديد قد يظن أنهما خاصان بـ`defaultConfig`؛ خطر عند إعادة الهيكلة. |
| الإصلاح | إعادة المسافة البادئة/الأقواس إلى المستوى الصحيح. |
| معيار القبول | `./gradlew help` و`lintDebug` يمران بلا تغيير سلوكي. |

### NFA-19 — سطح عرض قديم غير قابل للوصول في `TriggerEditorCard`

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `RESOLVED` (2026-10-30) |
| الدليل | `TriggerEditorCard.kt` يرسم فرع `when (draft.type)` فقط عندما يعيد `CanonicalBuilderSchemaBridge.editingBindingForTrigger` القيمة `null`. القياس الفعلي (اختبار `TriggerEditorRoutingTest` المنفذ على HEAD): **37** محفزًا من 57 يمر عبر محرر المخطط الكنوني، و**20** فقط تصل إلى `when`. |
| الأثر | مئات الأسطر من واجهة قديمة لا يمكن تنفيذها إطلاقًا (HEADPHONE, CHARGER, AIRPLANE_MODE, DARK_MODE, CALL_STATE, MEDIA_PLAYING, عائلة ON/OFF بأكملها, DEVICE_LOCKED, LOCATION_STATE, SCREEN_ROTATION_STATE, TIMEZONE_CHANGED, BOOT_COMPLETED, ALARM_SET_CHANGED, CONNECTIVITY, WIFI_CONNECTED, MOBILE_DATA_CONNECTED, HOTSPOT). تزيد حجم الملف والمراجعة، وتوهم بأن هناك مسار رسم ثانٍ فعّال. |
| الإصلاح | (أ) حذف الفروع غير الوصولة وأي دوال/نصوص صارت يتيمة (مع تشغيل `check_resources.py` و`lintDebug` لكشف النصوص اليتيمة في 11 لغة)؛ (ب) تثبيت الخريطة الحالية باختبار العقد المذكور حتى لا يتحول محفز إلى لوحة فارغة بصمت. |
| معيار القبول | `TriggerEditorRoutingTest` يبقى أخضر؛ لا فرع `when` لنوع موجود في قائمة العرض الكنوني؛ لا نصوص يتيمة في `values-*`. |
| الإغلاق (2026-10-30) | حُذفت الفروع الـ25 غير الوصولة؛ والملفات `CalendarPickerDialog.kt` و`BluetoothDevicePickerDialog.kt` ومعها `onPickCalendar`/`onPickBluetooth`؛ و**46** مفتاح نصي من 33 ملف موارد (auto_fix.py)؛ و**68** استيرادًا. أُضيف فحص `cardHasNoArmForSchemaRenderedTypes` داخل `TriggerEditorRoutingTest` (ثبت فشله عند إدخال فرع ميت تجريبيًا)، وتُحقّق: compile + detekt + 115 اختبار وحدة + lint للوحدة + address parity/resources/hardcoded/boundaries/suppression/fitness. |

### NFA-20 — سطح عرض قديم غير قابل للوصول في `ActionConfigEditor`

| الحقل | القيمة |
|---|---|
| الخطورة | **P2** |
| التصنيف | `RESOLVED` (2026-10-30) |
| الدليل | نفس نمط NFA-19 على الإجراءات: `BuilderActionPresentation.kt:492` يستدعي `ActionConfigEditor` فقط عندما يعيد `CanonicalBuilderSchemaBridge.editingBindingForAction` القيمة `null`. القياس: **127** إجراءً من 180 يرسمه المخطط الكنوني، و**53** فقط تصل إلى المحرر اليدوي (44 فرعًا لبعضها عدة تسميات). |
| الأثر | 126 فرعًا غير قابل للتنفيذ داخل ملف 1,831 سطرًا، مع استيرادات ومساعدات لا تُستعمل. |
| الإغلاق (2026-10-30) | حُذفت الـ126 فرعًا وأُبقي 44؛ الملف 1,831 → 912 سطرًا و101,260 → 55,371 بايت (−45.3%)؛ حُذف `VariableTextField` و`RunsImmediatelyHint` و`BluetoothEnabledHint` و`ON_OFF_TRIGGER_LABELS` (لا مراجع)، و35 استيرادًا، و**62** مفتاحًا نصيًا إضافيًا عبر auto_fix؛ أُضيف فحص `cardHasNoActionArmForSchemaRenderedActions` (ثبت فشله عند إدخال فرع ميت)، والخريطة الجديدة مثبتة في `TriggerEditorRoutingTest`؛ إضافةً إلى `TriggerStateConfigEditorsTest` (5 اختبارات عرض Robolectric تنقر الشرائح وتتحقق من المسودة الناتجة). |
| معيار القبول | لا فرع لمحرّر إجراء يملك ربط مخطط؛ لا مساعدات يتيمة؛ `lintDebug` والاختبارات والبوابات خضراء. |

### NFA-21 — حقول إجراءات معلنة لا يقرأها المنفّذ (T02-005)

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `RESOLVED` (2026-10-30) |
| الدليل | سجل T02-005 في `docs/audit/findings.md` رصد أربعة حقول معلنة في `ActionNodeSchemas` لا يقرأها منفّذها: `BATTERY_ALERTS.below`، `APPLICATION_LAUNCH_APP.packages`، `SYSTEM_OPEN_PLAY_STORE_APP.package`، `SYSTEM_REBOOT.mode`. إعادة الفحص على HEAD أثبتت أن الأول يُقرأ فعلًا خارج معالج الإجراء: `BatteryMonitor` يستخدمه كعتبة (`alertAction.config["below"]`) لتفعيل التنبيه مرة واحدة عند الهبوط تحت العتبة، فالنتيجة كانت إيجابية كاذبة لتحليل «نفس الفرع». الثلاثة الأخرى كانت عيوبًا حقيقية. |
| الأثر | اختيار «RECOVERY» في هدف إعادة التشغيل كان ينفّذ `reboot` عاديًا ويُسجّل النجاح؛ واختيار تطبيق في «فتح متجر Play» كان يفتح الصفحة الرئيسية للمتجر لا صفحة التطبيق؛ و`packages` القديم في «فتح تطبيق» كان يُحفظ ولا يُنفّذ؛ وكان محرر الإجراءات يطلب `namespace/key/value` لهذه الإجراءات لأنها لا تملك ربط مخطط. |
| الإصلاح | (أ) عقد `RebootMode` مغلق (`NORMAL`/`RECOVERY`/`BOOTLOADER`) يبني argv الصحيح، وأي قيمة أخرى تُفشل الطلب قبل تنفيذ أي أمر مرتفع؛ (ب) `openPlayStoreApp(package)` يفتح `market://details?id=` ثم صفحة الويب عند غياب عميل المتجر، ويرفض ما ليس اسم حزمة صالحًا؛ (ج) `APPLICATION_LAUNCH_APP` يقرأ `packages` القديم عند غياب `package`؛ (د) محرر الإجراءات يعرض هدف إعادة التشغيل وحزمة المتجر بدل حقول الإعدادات. |
| معيار القبول | لكل حقل اختبار يفشل قبل الإصلاح: `SystemRebootAndStoreIntentTest` (9 حالات، منها التقاط الأمر الفعلي `'reboot' 'recovery'`)، `DeclaredActionFieldParityTest` (3 حالات على المعالجات الحقيقية)، و`ActionConfigEditorDeclaredFieldsTest` (3 حالات عرض). ثبت عدم كونها شكلية بإرجاع السلوك القديم مؤقتًا (`launchApp(config["package"] ?: "")` وخريطة تتجاهل الهدف): فشلت 4 حالات إعادة تشغيل وحالة `packages` القديم، ثم عادت خضراء بعد الاسترجاع. |
| الإغلاق (2026-10-30) | `RebootMode.kt` جديد في `:core:rom-integration`؛ `SystemController.rebootDevice(mode)` و`openPlayStoreApp(package)`؛ تمرير الحقلين من `SystemActionsHandler`؛ محرر الإجراءات يفصل النمط المعلن عن حقول `SYSTEM_SET_SETTING`؛ مفتاح نصي جديد `reboot_mode` مترجم في 11 لغة. |
| المتبقي | إغلاق T02-005 في السجل التاريخي غير مطبّق: `findings.md` وثيقة تاريخية مثبتة على SHA، فالمرجع الحالي هو هذا القسم. `PLUGIN_FIRE.blurb` ما زال غير مقروء في وقت التشغيل (وصف واجهة فقط) ويحتاج قرارًا بشأن إبقائه. |

### NFA-22 — المنطقة الزمنية للجدولة لا يمكن اختيارها ولا تُقرأ (T02-006)

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `RESOLVED` (2026-10-30) |
| الدليل | `TriggerNodeSchemas` يعرّف `zonePolicy`/`zoneId` و`TimeTriggerCalculator.resolveZone` يقرأهما، لكن محرر TIME المتخصص (`TriggerTimeEditor`) لم يحتوِ أي عنصر تحكم بهما، و`FamilyPhase24TimeLocation.scheduleSchema()` يكتب الحقل الكنوني `timezone`، و`TimeTriggerCalculator` لم يكن يقرأه إطلاقًا. النتيجة: اختيار منطقة IANA كان يُحفظ ولا يؤثر، أو لا يمكن اختياره أساسًا. |
| الأثر | جدولة الساعة 08:00 كانت دائمًا بتوقيت الجهاز حتى لمن فعّل منطقة ثابتة من وكلاء/الكنوني، ومسارات «السفر/تغيير المنطقة» تنتج مواعيد غير متوقعة. |
| الإصلاح | (أ) `resolveZone` يحترم `timezone` الكنوني عند غياب سياسة صريحة، والسياسة الصريحة تتقدم عليه، و`FIXED_IANA` يقبل المفتاحين ويفشل مغلقًا للمنطقة غير الصالحة؛ (ب) قسم «المنطقة الزمنية» في محرر TIME يقترح الجهاز/منطقة محددة، ويكتب `zonePolicy`+`zoneId`+`timezone` معًا، ولا يفعّل `FIXED_IANA` في التخزين حتى تُفسّر المنطقة، فلا يمكن حفظ منطقة تُعطّل التنفيذ بصمت. |
| معيار القبول | 3 حالات جديدة في `TimeTriggerCalculatorTest`، و4 حالات عرض في `TimeZoneSectionTest` تنقر الشرائح وتتحقق من المسودة الناتجة، بما فيها حالة الكتابة غير المكتملة. |
| الإغلاق (2026-10-30) | نُقل قسم TIME بالكامل من `TriggerEditorCard` إلى `TimeTriggerEditor` (`TimeTriggerConfig`) فانخفض الكارد 59,905 → 54,348 بايت؛ وأُضيفت 5 مفاتيح نصية في 11 لغة (`time_zone_*`)، وحُذفت 77 استيرادًا ميتًا من `TriggerTimeEditor`. |

### NFA-23 — «5G فقط» يُخرج الشريحة من الخدمة، وبوابة قراءة القدرات تُفشل التبديل 5G/4G

| الحقل | القيمة |
|---|---|
| الخطورة | **P1** |
| التصنيف | `RESOLVED` (2026-10-30) |
| الدليل | جهاز اختبار فعلي (واجهة ROM مخصّصة، SDK 37، KernelSU): مهمة «نقطة اتصال WiFi» تحفظ `network_mask=524288` (NR وحده) و`endBehavior` بـ`266240` (LTE). سجل التنفيذ: `Network capabilities for the selected SIM are no longer readable` ثم `Network mode not applied: sub1 via cmd phone (set-allowed-network-types-for-users failed)`. الفحص المباشر: بعد محاولة NR رجعت `cmd phone get-allowed-network-types-for-users` بـ`UNKNOWN` و`set` بـ`No valid subscription found`، بينما `allowed_network_types_for_reasons=user=266240,power=54151,enable_2g=621492` و`isNrAvailable=false` — أي أن NR وحده غير قابل للتطبيق على هذه الشبكة (NSA بلا شبكة SA، وسبب `power` لا يسمح بـNR). |
| الأثر | اختيار «5G» في المنشئ (الملف NR وحده) يُخرج الشريحة من الخدمة، وبعدها تتعذّر قراءة القدرات فيفشل أيضًا إجراء النهاية الذي يُعيد LTE — أي يبقى الهاتف بلا خدمة حتى تصحيح يدوي. وبوابة ما قبل الكتابة كانت تُفشل التنفيذ عند أي تعذّر لحظي في قراءة القدرات أو عند اعتبار قناع المستخدم قدرة عتاد. |
| الإصلاح | (أ) `NetworkModePolicy.isNrOnly` و`optionsFor` لم يعد يعرض ملف 5G بلا جيل احتياطي، فلا يمكن إنشاء هذا الوضع من المنشئ؛ (ب) `NetworkModeController.setNetworkMode` يرفض كتلة NR-only قبل لمس الراديو برسالة تشرح السبب وتطلب إعادة اختيار ملف 5G مع LTE؛ (ج) أُزيلت بوابة إعادة قراءة القدرات قبل الكتابة، ويبقى التحقق الحقيقي عبر سلم الكتابة المرتبط بالاشتراك والقراءة العكسية. |
| معيار القبول | `NetworkModeControllerTest` (3 حالات: رفض NR وحده برسالة صريحة، قبول NR\|LTE، وعدم عودة رسالتي «no longer readable/supported») و`NetworkModePolicyTest` (لا خيار NR وحده، ودلالة `isNrOnly`). |
| الإغلاق (2026-10-30) | الاختبارات + detekt للوحدتين خضراء؛ بوابات `auto_fix` والنصوص والحدود وميزانية الكتمان واللياقة المعمارية والتدقيق ونضارة الوثائق تمر؛ وأُعيد بناء APK التصحيح وتثبيته على الجهاز نفسه. |
| المتبقي | المهمة المحفوظة على الجهاز ما زالت تحمل القناع القديم: يجب إعادة اختيار ملف «NR / LTE» في محرر «وضع الشبكة» لتعمل من جديد؛ لا تُرحَّل الكتلة القديمة تلقائيًا كي لا يُعاد كتابة إعداد راديو بتخمين. |

---

## 15. خطة الإصلاح

الخطة مقسمة إلى 5 مراحل. المدد تقديرية لفريق من مطور واحد + مراجع بدوام جزئي، وتُقاس بـ«أيام عمل».

### المرحلة 0 — إغلاق الفجوات الرخيصة عالية الأثر (أسبوع واحد، 5–7 أيام)

| # | المهمة | يعالج | معيار القبول |
|---|---|---|---|
| 0.1 | نشر `mapping.txt` وربطه بالإصدار/Sentry | NFA-03 | أصل mapping في كل إصدار + crash تجريبي مفكوك |
| 0.2 | تثبيت Actions بـSHA | NFA-08 | `grep uses:` بلا وسوم متحركة |
| 0.3 | SBOM + فحص CVEs | NFA-08 | CycloneDX مرفق + فشل عند HIGH/CRITICAL |
| 0.4 | تحديث سجلات التدقيق/وسمها تاريخية | NFA-11 | لا ادعاء متقادم في وثيقة حالية |
| 0.5 | إزالة الاعتماد المكرر/غير المستخدم والثابت الميت | NFA-16 | Detekt/بناء يمر |
| 0.6 | تصحيح بنية DSL في `app/build.gradle.kts` | NFA-18 | لا تغيير في المهام/المخرجات |

**تحقق المرحلة:** `./gradlew detekt lintDebug :app:assembleRelease -PallowDebugSigning=true` + بوابة SBOM + مراجعة يدوية لمخرجات الإصدار.

### المرحلة 1 — ثقة الاختبار (3 أسابيع، 12–16 يومًا)

| # | المهمة | يعالج |
|---|---|---|
| 1.1 | `AgentApiControllerTest` شامل (مصادقة، حدود، idempotency، موافقة) | NFA-05 |
| 1.2 | تصنيف الاختبارات `@Smoke` وتشغيلها إلزاميًا على PR (API 37) | NFA-02 |
| 1.3 | مصفوفة ليلية API 26/33/36/37 | NFA-02 |
| 1.4 | توسيع `coverageGate` للوحدات الحرجة + diff coverage | NFA-04 |
| 1.5 | حزمة تشخيص محلية قابلة للتصدير | NFA-09 |
| 1.6 | عزل اختبارات ROOT عن مسارات المضيف + ضبط المهلة/التوازي | NFA-17 |

**تحقق المرحلة:** `./gradlew testDebugUnitTest coverageGate detekt lintDebug` + PR تجريبي يُفشل smoke عمدًا + جهاز/محاكي API 37.

### المرحلة 2 — الصيانة والحدود (5 أسابيع، 18–22 يومًا)

| # | المهمة | يعالج |
|---|---|---|
| 2.1 | بوابة حدود وحدات (Konsist/سكربت) + إصلاح الانحرافات | NFA-07 |
| 2.2 | تفعيل قواعد التعقيد تدريجيًا مع baseline | NFA-06 |
| 2.3 | تقسيم أعلى 5 ملفات (Builder، SystemController، ExecutionEngine) | NFA-06 |
| 2.4 | بوابة مقاييس Compose | NFA-13 |

**تحقق المرحلة:** البوابة الجديدة تفشل على اعتماد مخالف مُدخل عمدًا؛ مجموع بايتات الملفات المجمّدة ينخفض ≥20%.

### المرحلة 3 — الأداء والقبول على الأجهزة (4 أسابيع، 15–20 يومًا)

| # | المهمة | يعالج |
|---|---|---|
| 3.1 | وظيفة macrobenchmark ليلية + ميزانية بدء | NFA-12 |
| 3.2 | مصفوفة أجهزة/OEM + سيناريوهات البوت/Doze/FGS | NFA-01 |
| 3.3 | Soak 72 ساعة مع مراقبة البطارية/الذاكرة | NFA-01 |
| 3.4 | أتمتة وصول/RTL + لقطات | NFA-15 |
| 3.5 | CI على Windows للمجموعة الحرجة | NFA-14 |

**تحقق المرحلة:** تقرير مصفوفة مكتمل، 10 نقاط قياس أداء، وتقرير soak موقّع.

### المرحلة 4 — الامتثال والتوزيع (قرار + 3 أسابيع)

| # | المهمة | يعالج |
|---|---|---|
| 4.1 | ADR لقناة التوزيع | NFA-10 |
| 4.2 | flavors + إفصاحات صلاحيات + Data Safety | NFA-10 |
| 4.3 | مراجعة MASVS/ASVS مختصرة + اختبار اختراق داخلي للـAgent API | مكمّل للأمن |
| 4.4 | توقيع provenance (SLSA) + نشر بصمات SHA-256 لكل أصل | مكمّل للتوريد |

---

## 16. بوابات إضافية مطلوبة في CI

```yaml
# مقترح — تُضاف إلى .github/workflows/nexaflow-ci.yml
- name: Dependency vulnerabilities
  run: osv-scanner --lockfile=gradle/verification-metadata.xml .
- name: SBOM
  run: syft dir:. -o cyclonedx-json > sbom.json
- name: Module boundaries
  run: python3 scripts/check_module_boundaries.py
- name: Release mapping artifact
  run: test -s app/build/outputs/mapping/release/mapping.txt
- name: Compose stability budget
  run: python3 scripts/check_compose_stability.py app/build/compose-metrics
- name: Docs freshness
  run: python3 scripts/check_docs_freshness.py --max-commits 25
```

**سياسة الدمج المقترحة:** لا يُدمج PR إذا فشل أي من: smoke على جهاز، حدود الوحدات، CVEs، ميزانية Compose، حداثة الوثائق.

---

## 17. مؤشرات القياس (KPIs)

| المؤشر | الحالي | الهدف (3 أشهر) |
|---|---|---|
| نتائج P0 مفتوحة | 2 | 0 |
| نتائج P1 مفتوحة | 9 | ≤3 |
| مستويات API في CI | 1 (35) | 4 (26/33/36/37) |
| اختبارات جهاز | 22 | ≥80 |
| تغطية مفروضة | 6 وحدات | ≥18 وحدة + diff 80% |
| أكبر ملف Kotlin | 187KB | ≤90KB |
| Actions بوسم متحرك | 22 | 0 |
| SBOM لكل إصدار | لا | نعم |
| mapping منشور | لا | نعم |
| زمن بدء p95 | غير مقاس | ≤1200ms على جهاز مرجعي |
| crash-free sessions | غير مقاس | ≥99.5% بعد تفعيل القياس |
| مجموعة JVM خضراء على Ubuntu + Fedora/Windows | لا (2 فشل على Fedora) | نعم |

---

## 18. المخاطر والقرارات المطلوبة

| المخاطرة | الاحتمال | الأثر | التخفيف |
|---|---|---|---|
| عدم توفر أجهزة/OEM | مرتفع | حجب NFA-01 | بدء بجهاز مرجعي + جهاز OEM واحد، واستخدام Firebase Test Lab للمستويات الأخرى |
| تفعيل قواعد التعقيد يكسر البناء | متوسط | تأخير | baseline مولّد + ratchet لا انفجار |
| تقسيم الملفات الضخمة يسبب انحدارات | متوسط | عالٍ | تقسيم ميكانيكي + اختبارات قبل/بعد + مراجعة |
| Play يرفض الصلاحيات | مرتفع إن اختير Play | حجب توزيع | flavor مقيّد + إقرارات مسبقة |
| تكلفة/خصوصية telemetry | متوسط | قانوني | افتراضيًا معطّل + أخذ عينات + توثيق |
| اعتماد على مشرف واحد | مرتفع | استمرارية | توثيق قرارات (ADR) + مراجعة خارجية دورية |

---

## 18.1 حالة تنفيذ الخطة (2026-10-29)

| المرحلة | الحالة | ما نُفّذ فعليًا | المتبقي / الحاجز |
|---|---|---|---|
| 0 | ✅ مكتملة | mapping كأصل إصدار + التحقق منه في CI؛ تثبيت كل `uses` بـSHA؛ SBOM CycloneDX + فحص OSV مع allowlist؛ تحديث سجلات التدقيق ووسمها؛ إزالة الاعتماد غير المستخدم/المكرر والثابت الميت؛ إصلاح بنية DSL في `app/build.gradle.kts`؛ `dataExtractionRules` لساعة Wear | — |
| 1 | ✅ مكتملة (عدا بند واحد) | `AgentApiControllerTest` (40 حالة)؛ وسم `@Smoke` + وظيفة PR على API 37؛ مصفوفة ليلية 26/33/36/37 عبر workflow قابل لإعادة الاستخدام؛ `coverageGate` للوحدات الأربع + `check_diff_coverage.py` بحد 80%؛ حزمة تشخيص محلية قابلة للمشاركة مع تنقيح الأسرار؛ عزل اختبارات ROOT عن مسارات المضيف ورفع المهلة إلى 30 دقيقة | تقرير XML جزئي عند إيقاف Gradle بالمهلة غير مضمون (سلوك Gradle)؛ الاختبارات العادية تُنتج XML حتى عند الفشل |
| 2 | ⚠️ جزئية | بوابة حدود الوحدات + allowlist موثّق (3 ديون)؛ تفعيل `LongMethod` و`NestedBlockDepth` مع baselines؛ بوابة ميزانية استقرار Compose؛ فصل `SystemInputController` عن `SystemController` | **هدف تقليص 20% غير محقق**: القياس 13.96% بعد ثلاث دورات في 2026-10-30 (كان 0.37%)؛ حُذف NFA-19 وNFA-20 وأُغلق NFA-21 مع تحويل إعادة التشغيل والمتجر إلى وحدتين مستخرجتين. أكبر أربعة ملفات ما زالت تحتاج جلسات تقسيم مخصصة (انظر 18.2) |
| 3 | ⚠️ جزئية | وظيفة macrobenchmark ليلية + بوابة ميزانية بدء؛ CI على Windows للوحدات الحرجة؛ اختبار Robolectric عربي/RTL؛ قوالب مصفوفة الأجهزة وSoak | **BLOCKED**: لا جهاز/محاكي محلي ولا أجهزة OEM ولا سلسلة 10 قياسات؛ فحوص TalkBack اليدوية غير منفذة |
| 4 | ⚠️ جزئية | ADR لقناة التوزيع (مقترح)؛ وثائق الصلاحيات وData Safety؛ مراجعة MASVS مبنية على الكود؛ SHA-256 لكل أصل + SLSA provenance وربط الصلاحيات | **BLOCKED**: قرار المالك (OQ-02/OQ-05) قبل flavors؛ إصدار موقّع فعلي لإنتاج أول attestation؛ اختبار اختراق خارجي |

### 18.2 قياسات التقسيم (Phase 2.3)

| الملف | HEAD (بايت) | الحالي (بايت) | الفرق |
|---|---|---|---|
| `SystemController.kt` | 91,244 | 86,767 | −4,477 (−4.9%) |
| `TriggerEditorCard.kt` | 141,446 | 54,348 | −87,098 (−61.6%) |
| `ActionConfigEditor.kt` | 101,260 | 54,710 | −46,550 (−46.0%) |
| بقية الملفات الأحد عشر | — | — | دون تغيير |
| **الإجمالي (13 ملفًا)** | **964,407** | **824,222** | **−14.54%** |
| ملفات جديدة مقابلة | — | 27,879 (6 ملفات) | (خارج القائمة المجمّدة) |
| **صافي البايتات المحذوفة فعليًا** | — | — | **−112,306 (−11.65%)** |

### 18.2.1 تقسيم `TriggerEditorCard` (دورة 2026-10-30)

نُقلت أقسام المحفزات ذات العقد البسيط (حالة واحدة، حدث واحد، أو عتبة رقمية)
من جسم الدالة 2,250 سطرًا إلى `TriggerStateConfigEditors.kt` (18,188 بايت، أي
تحت سقف 45,000)، ثم حُذفت الفروع غير الوصولة (NFA-19)، فأصبح الكارد مُوزّعًا
مبسّا يقوم فقط على 20 نوعًا تصل فعلًا إلى المسار القديم:

| الخطوة | قبل | بعد |
|---|---|---|
| فروع `when (draft.type)` | 45 فرعًا لـ57 نوعًا | 20 فرعًا للنواع الـ20 الوصولة فقط |
| `ActionConfigEditor` (بعد استخراج محرري الحزمة والهدف) | 55,371 | 54,710 |
| أقسام مركّبة (HEADPHONE/CHARGER، AIRPLANE/DARK، WIFI/CELL، ON/OFF×14) | 18 جسمًا متطابقًا | 5 دوال مشتركة |
| جسم الدالة | 2,388 سطرًا / 141,446 بايت | 1,025 سطرًا / 59,905 بايت |
| `TriggerStateConfigEditors.kt` | — | 426 سطرًا / 18,188 بايت |
| مُنتقيات تقويم/بلوتوث | `CalendarPickerDialog` + `BluetoothDevicePickerDialog` (554 سطرًا) | **حُذفت**: لا مدخل يمكن الوصول إليه |
| نصوص مساعدة | — | **46 مفتاحًا** حُذفت من 33 ملف موارد (11 لغة) |
| استيرادات | — | 68 استيرادًا غير مستخدم حُذف |

**القياس النهائي بعد دورة 2026-10-30 الثالثة:** الكارد 141,446 → 54,348 بايت (−61.6%)
بعد نقل قسم TIME وسلوكه إلى `TriggerTimeEditor`، ومحرر الإجراءات 101,260 → 54,710 (−46.0%)،
والواجهة الجذرية للتحكم بالنظام 91,244 → 86,767 (−4.9%)، ومجموع الملفات المجمّدة
964,407 → 824,222 (−14.54%). وبعد طرح حجم الملفات الستة الجديدة (27,879) يكون
صافي المحذوف فعليًا **112,306 بايت (−11.65%)**، لا مجرد نقل كود. تم تحديث
`BASELINE_HOTSPOTS` إلى 54,348 و54,710 و86,767 لتثبيت المكسب (ratchet).

> ما زال الهدف ≥20% على مجموع الملفات المجمّدة **غير محقق** (القياس 13.58%)
> والملفات التالية في الأولوية هي `AutomationBuilderScreen.kt`
> (114,819) و`SystemController.kt` (87,694) و`ExecutionEngine.kt` (83,428)
> و`AutomationDetailsScreen.kt` (71,326) و`LegacyMappingTable.kt` (63,167).

الهدف في الخطة (≥20%) **لم يتحقق**. في دورة 2026-10-30 نُفّذ تقسيم
`TriggerEditorCard` وحذف فروعه غير الوصولة (NFA-19)، ولم تُلمس
`AutomationBuilderScreen` و`ActionConfigEditor` (Compose) و`ExecutionEngine`
بعد؛ كل منها يحتاج جلسة مخصصة مع إعادة تشغيل اختبارات الوحدة بعد كل خطوة.
بوابات `check_atomic_architecture_fitness.py` تمنع أي زيادة وتُلزم بتقليص تدريجي.

### 18.3 قياس التغطية بعد التوسيع

| الوحدة | السطور المغطاة | العتبة المفروضة | الحالة |
|---|---|---|---|
| `:core:agent-api` | 61.3% (1,428/2,328) | 60% | ✅ |
| `:core:execution` | 68.8% (8,575/12,464) | 68% | ✅ |
| `:core:automation-engine` | 44.5% (3,102/6,967) | 43% | ✅ |
| `:core:rom-integration` | 24.7% (1,000/4,048) | 22% | ✅ |
| `:data` | 84.2% (1,202/1,428) | 80% | ✅ |
| `:core:compatibility` | 90.9% (199/219) | 80% | ✅ |
| `:core:logging` | 89.3% (250/280) | 80% | ✅ |
| `:core:security` | 82.9% (68/82) | 80% | ✅ |
| `:core:wear-protocol` | 94.8% (165/174) | 80% | ✅ |

العتبات هي ratchet من القياس الحالي وترتفع نحو هدف 60% للوحدات الأربع في
الدورة التالية؛ لا يجوز إنزالها.

### 18.4 سجل إعادة التحقق

| التاريخ | الالتزام | ما أُعيد فحصه | النتيجة |
|---|---|---|---|
| 2026-10-31 | `3d9afd7ac` | بوابات المصدر الآلية في وظيفة `lint` (حداثة الوثائق، المخزون الذري، الترجمة والتكافؤ، النص المضمّن، حياد المورّد، حدود الوحدات، استقرار Compose، بوابات T01–T44، `auto_fix --check`، `check_node_contracts`) + `./gradlew detekt` + بناء نسختَي `benchmark` لـ`:app` و`:macrobenchmark` | ✅ كل البوابات تمر وDetekt ناجح. بنود الجهاز/OEM وSoak وTalkBack وNFA-10 ما زالت `BLOCKED` كما هي؛ أرقام التغطية والتقسيم لم تُعَد قياسها |
| 2026-11-01 | `e260493c` | بوابات وظيفة `lint` كاملةً على فرع إعادة التصميم: `auto_fix --check`، تكافؤ النصوص، اكتمال الترجمة، النص المضمّن، حياد المورّد، المخزون الذري (أُعيد توليده)، بوابات T01–T44، `check_docs_freshness`، استقرار Compose، حدود الوحدات، `./gradlew detekt` و`./gradlew lintDebug` | ✅ البوابات تمر بعد إعادة توليد مخزون `field-runtime-parity.csv` واستعادة حالة «لا تطبيقات» في منتقي التطبيقات. الالتزام الجديد هو قمة الفرع المُعاد تصميمه قبل الدمج؛ يُرفع `Audit-Baseline` مرة أخرى إلى التزام الدمج على `main` وفق البند 6. بنود الجهاز/OEM وSoak وTalkBack وNFA-10 ما زالت `BLOCKED`، ولم تُعَد قياسات التغطية ولا أرقام §3 |

**بروتوكول إعادة التحقق** (يُطبَّق قبل كل رفع لـ`Audit-Baseline` — البوابة
`scripts/check_docs_freshness.py --max-commits 25` تفشل بعد 25 التزامًا):

1. شغّل بوابات المصدر الآلية كما في `.github/workflows/nexaflow-ci.yml` (وظيفة `lint`) و`./gradlew detekt` على الالتزام الجديد.
2. شغّل `python3 scripts/check_docs_freshness.py --max-commits 25` وتأكد من نجاحه بعد التحديث.
3. أضف صفًا مؤرخًا هنا يذكر ما أُعيد فحصه فعلًا وما لم يُعَد (القياسات، الأجهزة، Soak).
4. ارفع `Audit-Baseline` إلى الالتزام الذي فُحص فقط، واترك `التزام التدقيق` وقياسات §3 و§18.2 و§18.3 كما هي حتى إعادة تدقيق فعلية.
5. لا تُحوّل أي بند `BLOCKED` إلى ✅ اعتمادًا على إعادة تحقق آلية؛ ذلك يحتاج دليل جهاز كما تنص قاعدة الأدلة في أعلى التقرير.
6. اربط `Audit-Baseline` بالتزام **موجود في تاريخ `main`** (مثل التزام دمج squash)، لا بالتزام داخل فرع سيُحذف بعد الدمج: بوابة الحداثة تستدعي `git rev-list <baseline>..HEAD`، والالتزام غير المُشار إليه من أي مرجع بعد حذف الفرع يفشل في نسخة CI النظيفة.

---

## 19. حالة التحقق في هذه الجلسة

| البند | النتيجة | الملاحظة |
|---|---|---|
| `:core:agent-api:testDebugUnitTest --tests AgentApiControllerTest` | ✅ 40 اختبارًا | يغطي المصادقة/Origin/Host/الحدود/idempotency/الموافقة/إصدارات المهمة |
| `testDebugUnitTest --continue` بعد حذف كل تقارير XML القديمة | ✅ 3,004 اختبارًا، 0 فشل/خطأ/تخطٍّ، 33 وحدة | كل XML من تشغيل اليوم؛ إعادة تشغيل الوحدتين المعدّلتين بعد الإصلاح |
| `:app:compileDebugAndroidTestKotlin` | ✅ | يثبت تجميع اختبارات الجهاز بعد وسم `@Smoke` |
| `:app:assembleRelease -PallowDebugSigning=true` | ✅ BUILD SUCCESSFUL (28m07s) | R8 يعمل و`mapping.txt` بحجم 106.5MB مُنتَج |
| `:core:rom-integration:coverageGate` بعد التقسيم | ✅ 24.7% (1,000/4,048) | ارتفعت بعد `SystemInputControllerTest`؛ لا انحدار بعد فصل `SystemInputController` |
| بوابات المصدر (vendor neutrality، hardcoded text، parity، resources، catalog، contracts) | ✅ جميعها 0 مشكلة | 10 بوابات نُفّذت في هذه الجلسة |
| `./gradlew coverageGate` | ✅ BUILD SUCCESSFUL (1m17s بعد تشغيل اختبارات نظيف) | كل الوحدات المدرجة فوق عتباتها، والبقية report-only |
| `./gradlew detekt --no-build-cache` | ✅ BUILD SUCCESSFUL (1m28s، تحليل فعلي) | القواعد الجديدة مفعّلة وbaselines محدّثة |
| `./gradlew lintDebug --no-build-cache` | ✅ BUILD SUCCESSFUL (14m59s، 1,326 مهمة) | لا أخطاء موارد/ترجمة/صلاحيات |
| `:feature:history:testDebugUnitTest` | ✅ | يشمل `DiagnosticBundleTest` (3) و`HistoryRtlTest` (2) |
| `:feature:dashboard:testDebugUnitTest` + `:core:rom-integration:testDebugUnitTest` | ✅ | بعد نقل منفذ التصدير وفصل وحدة الإدخال |
| اختبارات Python الجديدة | ✅ 34 اختبارًا | supply-chain، diff-coverage، module-boundaries، compose-stability، benchmark-budget |
| `SystemRebootAndStoreIntentTest` | ✅ 9 اختبارات | تثبت الأمر الفعلي لكل هدف (`'reboot'`، `'reboot' 'recovery'`، `'reboot' 'bootloader'`) ورفض الهدف غير المدعوم دون تنفيذ، ومسارَي المتجر (market ثم الويب) ورفض الاسم غير الصالح |
| `DeclaredActionFieldParityTest` | ✅ 3 اختبارات | تمرير `package`/`packages` إلى `launchApp` و`package` إلى صفحة المتجر عبر المعالجات الحقيقية |
| `ActionConfigEditorDeclaredFieldsTest` | ✅ 3 اختبارات | محرر إعادة التشغيل يعرض الهدف ويحفظه، ولا يعرض حقول الإعدادات؛ محرر المتجر يعرض الحزمة فقط |
| `TimeTriggerCalculatorTest` (حالات جديدة) | ✅ 3 اختبارات | `timezone` الكنوني يُحترم، والسياسة الصريحة تتقدم عليه، والمنطقة التالفة ترجع لتوقيت الجهاز |
| `TimeZoneSectionTest` | ✅ 4 اختبارات | الجهاز افتراضيًا، اختيار منطقة ثابتة يبدأ بمنطقة صالحة؛ الاختيار السريع «Europe/Berlin» يُحفظ ويُفسّر به؛ الكتابة غير المكتملة تبقى DEVICE_LOCAL مع رسالة خطأ |
| مجموعات الوحدات المتأثرة | ✅ 1,029 اختبارًا، 0 فشل | `:feature:automation-builder` 128، `:core:rom-integration` 169، `:core:execution` 732، و`:domain` كاملة |
| detekt + lintDebug للموحدتين المعدّلتين | ✅ | `:feature:automation-builder` (بناء + detekt + lint + كل اختباراته) و`:core:rom-integration`، و`:app:compileDebugKotlin` |
| فحص YAML لكل workflows | ✅ 3 ملفات | تثبيت SHA + مهام قابلة لإعادة الاستخدام |
| `scripts/check_module_boundaries.py` | ✅ 0 انتهاك (3 ديون مسموحة موثّقة) | يفشل على أي اعتماد جديد مخالف |
| `scripts/check_compose_stability.py` | ✅ app: unstable 21/21، non-skippable 20/20 | يفشل عند تجاوز الميزانية |
| `scripts/check_diff_coverage.py` | ✅ 9 اختبارات وحدة + 100% (209/209) مقاسًا بـ`--base` | يفشل عند تغطية <80% للسطور المعدّلة |
| Gitleaks | ⛔ لم يُشغّل محليًا | الأداة غير مثبتة؛ سجل CI الموثق يبقى المرجع |
| اختبارات جهاز/محاكي | ⛔ لم تُشغّل | لا جهاز متصل؛ وظائف CI جاهزة لكن الدليل الفيزيائي غير موجود |
| Sentry/مزود LLM/Shizuku/Root | ⛔ لم تُختبر | تحتاج حسابات وأجهزة |

> لا يُعلن أي ادعاء إنتاجي قبل إغلاق NFA-01 (أجهزة) وOQ-02 (قناة التوزيع)
> وإنتاج أول إصدار موقّع مع attestation.

## 20. الملاحق

### 20.1 خريطة سريعة للملفات الحاكمة

| الملف | الدور |
|---|---|
| `app/build.gradle.kts` | التوقيع، R8، التغطية، إعدادات الاختبار |
| `build.gradle.kts` | Detekt/Lint/التغطية على كل الوحدات |
| `.github/workflows/nexaflow-ci.yml` | كل بوابات CI والإصدار |
| `app/src/main/AndroidManifest.xml` | الصلاحيات والمكوّنات المُصدَّرة |
| `core/agent-api/AgentApiController.kt` | سطح REST المحلي والمصادقة |
| `core/agent-security/*` | الخزنة، التوكنات، الأذونات |
| `core/execution/*` | المحرك، المعالجات، سياسة الشبكة |
| `core/automation-engine/AutomationAlarmReceiver.kt` | الجدولة والاستئناف |
| `core/database/Migrations.kt` | 27 ترحيلًا |
| `scripts/check_atomic_architecture_fitness.py` | حدود الحجم |
| `docs/audit/findings.md` | سجل T02 (بحاجة لتحديث) |

### 20.2 أوامر إعادة التحقق

```bash
# بوابات المصدر
python3 scripts/check_strings_parity.py
python3 scripts/auto_fix.py --check
python3 scripts/audit_translation_completeness.py
python3 scripts/check_hardcoded_text.py
python3 scripts/check_vendor_neutrality.py
python3 scripts/audit_catalog_and_releases.py catalog
python3 scripts/check_suppression_budget.py
python3 scripts/check_resources.py
python3 scripts/check_persistence_safety.py
python3 scripts/check_atomic_architecture_fitness.py
python3 scripts/check_node_contracts.py
python3 scripts/audit_findings.py

# Gradle (JDK 17 أو 21)
./gradlew detekt lintDebug testDebugUnitTest
./gradlew coverageGate
./gradlew :app:assembleRelease -PallowDebugSigning=true
```

### 20.3 الربط بسجل T02

| T02 | الحالة في هذا التدقيق |
|---|---|
| T02-001 (DataStore/Windows) | مؤكدة تاريخيًا → NFA-14 |
| T02-002 (Locked boot) | **متقادمة**: لا `LOCKED_BOOT_COMPLETED` على HEAD → NFA-11 |
| T02-003 (هوية الحدث) | مفتوحة كما هي؛ تحتاج اختبارات مصدر-مصدر |
| T02-004 (استئناف بعد انهيار) | مفتوحة؛ تحتاج fault injection على جهاز |
| T02-005 (حقول إجراء بلا قراءة) | مفتوحة |
| T02-006 (منطقة زمنية ثابتة) | مفتوحة |
| T02-007 (تحقق Plugin) | منفذة وغير متحققة على جهاز |
| T02-008 (حدود الاستيراد) | **متحققة مصدريًا** (`BackupLimits`/`BackupManager`) |
| T02-009 (الوصول) | مفتوحة → NFA-15 |
| T02-010 (جودة الترجمة) | التكافؤ الآلي يمر؛ المراجعة البشرية غائبة |
| T02-011/012 (أداء/أجهزة) | مفتوحة → NFA-01/NFA-12 |
| T02-013 (DST) | منفذة وغير متحققة على جهاز |
| T02-014 (إلغاء دائم) | مفتوحة |

---

**الخلاصة:** NexaFlow يمتلك أساسًا هندسيًا وأمنيًا أقوى من متوسط السوق، لكنه لا يملك بعد **دليل الجاهزية**: لا جهاز، لا تغطية مفروضة لوحدات التنفيذ، لا رؤية ميدانية للأعطال، ولا ربط بين الإصدار وmapping. تنفيذ المرحلتين 0 و1 وحدهما يرفع الثقة من «كود جيد» إلى «منتج قابل للدفاع عنه»، ويجب ألا يُعلن أي ادعاء إنتاجي قبل إغلاق NFA-01 وNFA-02 وNFA-03.
