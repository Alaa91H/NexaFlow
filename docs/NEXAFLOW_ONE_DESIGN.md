# NexaFlow One — نظام التصميم

> **الحالة:** نشط — يستبدل `docs/GOOGLE_2026_DESIGN.md` (أُبقي كسجل تاريخي لل provenance).
> **النطاق:** نظام التصميم البصري للتطبيق — التوكنز، مكتبة المكوّنات، شاشات المظهر، البوابات.
> **تاريخ:** 2026-10-24.

---

## 1. الهدف

جعل NexaFlow يبدو **مصمَّماً** لا مُجمَّعاً. النظام القديم (Google 2026 / M3 Expressive) منضبط تقنياً لكنه يقرأ كتطبيق Google عام: أسطح محايدة، أزرق Google، dynamic color افتراضي، وبلا شخصية.

**NexaFlow One** يحقق أربع صفات:

1. **قمّة داكنة مع هواء بصري** — خلفية شبه سوداء + أشكال "blob" ضوئية ضخمة خلف المحتوى.
2. **كروت تدرّج ملوّنة** — كل عنصر يحمل تدرّجه الخاص (ترميز بصري سريع القراءة).
3. **شريط علوي بصري** — علامة + أزرار أيقونية دائرية.
4. **حبة تنقّل عائمة** — منفصلة عن الحواف بدل `NavigationBar` ملتصق.

---

## 2. الحدود القانونية والعلامة

المرجع البصري هو `morpheapp/morphe-manager`. **`morphe-manager` مرخّص GPLv3** (مشتق من Universal-ReVanced-Manager)، ومستودع `morpheapp/morphe-branding` ينصّ صراحةً:

> *"Morphe branding is protected by copyright and is **not** covered under any open-source license… The **Morphe logo**, The **Morphe name**… you must first obtain **explicit written permission**."*

**القرار المعتمد:**

| يُؤخذ (بنية / تقنية) | لا يُؤخذ |
|---|---|
| فكرة القمّة الداكنة + الكرات الضوئية | شعار Morphe / wordmark / أي SVG من `morphe-branding` |
| بنية `ThemeTraits` + `ThemeTraitsDefaults` | اسم "Morphe" |
| كروت التدرّج لكل عنصر | تدرّج `#1E5AA8 → #00AFAE` بحروفه |
| حبة التنقّل العائمة | خطوط Morphe المخصّصة |
| بنية `AppearanceSettings` كمخطط | نسخ الشيفرة (يُلزم GPL على MIT) |
| Material Symbols Rounded (Apache 2.0) | لقطات الشاشة |

> **قاعدة التنفيذ:** كل قاعدة في هذا النظام **مُعادة التنفيذ من الصفر** بناءً على الوصف. المرجع هنا للبنية والتصميم، لا للكود.

**الهوية:** `Indigo Flow → Violet → Aqua Flow` — تدرّج أصلي. `check_vendor_neutrality.py` يجب أن يبقى أخضر: تُذكر Morphe في هذا المستند كمرجع بحثي، لا كمنتج مدعوم.

---

## 3. المبادئ الخمسة

1. **القمّة أولاً** — كل شاشة تبدأ من قمّة مخصّصة + خلفية بيئية، لا من سطح M3 مسطّح.
2. **اللون كترميز** — العنصر يحمل تدرّجه الخاص؛ التدرّج ترميز بصري لا زينة.
3. **الطبقات قبل الحدود** — الفصل بالـ surface tier، والحدود hairline خيار ثانٍ.
4. **التخصيص كوّن** — المظهر مركّب من traits، لا قائمة خيارات مسطّحة.
5. **الأداء جزء من التصميم** — لا حركة تتجاوز 400ms، ولا رسوم بلا fallback.

---

## 4. التوكنز

### 4.1 مكان الألوان

| الملف | الدور |
|---|---|
| `core/ui-components/.../ui/theme/NexaFlowPalette.kt` | **اللوحة الكاملة** + البذور + بناء الـ `ColorScheme` |
| `core/ui-components/.../ui/theme/NexaFlowTraits.kt` | `NexaFlowTraits` + `NexaFlowTraitsDefaults` + `NexaFlowDensity` + `NexaFlowBackground` |
| `core/ui-components/.../ui/theme/NexaFlowShapes.kt` | مقياس الأشكال + `NexaFlowRadii` |
| `core/ui-components/.../ui/theme/NexaFlowTypography.kt` | مقياس الطباعة (الخطوط في `app`) |
| `core/ui-components/.../ui/theme/NexaFlowGradients.kt` | تدرّجات العلامة + تدرّجات العناصر |
| `core/ui-components/.../ui/theme/NexaFlowColors.kt` | الأدوار الدلالية (success / warning / brand) |
| `core/ui-components/.../ui/theme/LocalNexaFlowTokens.kt` | حامل التوكنز الموحّد |
| `app/.../ui/theme/Color.kt` | **إعادة تصدير فقط** — لا لون هنا |
| `app/.../ui/theme/Theme.kt` | بناء الثيم من التوكنز + الـ traits |

> `app` يملك **الأصول** (خطوط، seed مخصّص)، و`core/ui-components` يملك **الاستهلاك**. `feature/*` و`wear` يستهلكون `core` فقط.

### 4.2 الألوان

**التدرّج الأساسي:** `Flow Start #3B5BFF` → `Flow Violet #7A5AF8` → `Flow End #00D6B4`
(نسخة داكنة: `#8AA0FF` → `#B9A3FF` → `#4FE3C8`)

**الأدوار الدلالية:**

| الدور | Light | Dark |
|---|---|---|
| `background` | `#FBFBFD` | `#0A0B10` |
| `surface` | `#FFFFFF` | `#101219` |
| `surfaceContainerLow` | `#F5F6FA` | `#14161E` |
| `surfaceContainer` | `#EFF1F6` | `#191C25` |
| `surfaceContainerHigh` | `#E8EBF2` | `#20242E` |
| `surfaceContainerHighest` | `#E1E5EE` | `#282D39` |
| `onSurface` | `#101319` | `#EDEFF5` |
| `onSurfaceVariant` | `#5A6172` | `#9AA2B5` |
| `outline` | `#8A91A2` | `#6D7486` |
| `outlineVariant` | `#D3D8E3` | `#282D39` |
| `primary` | `#3B5BFF` | `#8AA0FF` |
| `primaryContainer` | `#E2E7FF` | `#1E2A6B` |
| `secondary` | `#00A38F` | `#4FE3C8` |
| `tertiary` | `#7A5AF8` | `#B9A3FF` |
| `error` | `#E5484D` | `#FF8A8E` |
| `success` | `#1D8A5B` | `#5BD6A0` |
| `warning` | `#9A5B00` | `#FBBF24` |

**البذور الستّة:** `indigo` (افتراضي) · `teal` · `violet` · `emerald` · `amber` · `rose`

### 4.3 الأشكال

`extraSmall 8dp` · `small 12dp` · `medium 18dp` · `large 26dp` · `extraLarge 32dp` · `giant 40dp` · `pill 999dp`

### 4.4 الطباعة

Roboto Flex (لاتيني) + Noto Sans Arabic (عربي). مقياس M3 مع أدوار العرض والعناوين بوزن **Bold**، والنصوص بأوزان عادية.

**قاعدة العربية:** الوزن 700 مسموح في العناوين فقط (‏≥ 18sp). لا يُستخدم تحت 14sp — Noto Sans Arabic يتدهور عند Extra Bold. مفحوصة بـ `ArabicLegibilityTest`.

### 4.5 الحركة

`expressive` (افتراضي) · `emphasized` · `spatial` · `blob` (18s, translate ±3%) · `press` (scale 0.97، 120ms) · `reveal` (fade + translateY 12dp، stagger 30ms)

كل شيء ≤ 400ms عدا `blob`. يُعطَّل كل شيء عند `ANIMATOR_DURATION_SCALE == 0`.

---

## 5. نظام السمات (Traits)

```kotlin
data class NexaFlowTraits(
    val monochrome: Boolean = false,        // تسطيح كل الألوان
    val colorAccents: Boolean = true,       // ألوان العناصر أم لون الثيم؟
    val outlines: Boolean = true,           // حدود hairline
    val blur: Boolean = true,               // أشرطة شفافة
    val ambientBackground: Boolean = true,  // أشكال الخلفية
    val density: NexaFlowDensity = COMFORTABLE,
    val background: NexaFlowBackground = AMBIENT
)
```

**‏2⁷ = 128 تركيبة** — كلها مدعومة. `NexaFlowTraitsDefaults` هو نقطة التكيّف الوحيدة: المكوّنات لا تفرّع على `LocalNexaFlowTraits` مباشرة.

**مثال:**

```kotlin
val hairline = NexaFlowTraitsDefaults.outline(
    color = NexaFlowTraitsDefaults.borderColor(MaterialTheme.colorScheme.outlineVariant),
    width = 1.dp
)
```

---

## 6. مكتبة المكوّنات

### 6.1 جديدة

| المكوّن | الدور |
|---|---|
| `AmbientCanvas` | القمّة + الأشكال الضوئية (رسم واحد، بلا recomposition) |
| `NexaFlowBrandBar` | الشريط العلوي: علامة + أزرار دائرية، أو زر رجوع + عنوان |
| `NexaFlowBrandMark` | الشعار + الكلمة |
| `NexaFlowRoundIconButton` | الزر الدائري المعبّأ |
| `NexaFlowNavPill` | حبة التنقّل العائمة |
| `NexaFlowGradientCard` | الكرت الملوّن |
| `NexaFlowGradientIcon` | الأيقونة الدائرية المتدرّجة |
| `NexaFlowSearchField` | حقل البحث الدائري |
| `NexaFlowSheetHost` | الغلاف الموحّد للأوراق السفلية |
| `NexaFlowStatTile` | بلاطة الإحصاء |
| `NexaFlowGradientFab` | الزر العائم على التدرّج |

### 6.2 معدَّلة

`NexaFlowCard` (حد hairline) · `NexaFlowTopBar` (شفاف + عنوان Bold) · `SectionHeader` (أيقونة) · `IconBadge` (تدرّج) · `StatusPill` (traits) · `SettingRow` (حد + أيقونة) · `StatCard` · `EmptyState` · `NexaFlowFloatingActionButton` · `NexaFlowNavigationBar` (مهملة → `NexaFlowNavPill`)

---

## 7. الخلفيات البيئية

`AmbientBackgrounds` — أربع حقول ثابتة (لا عشوائية):

| المعرّف | الوصف |
|---|---|
| `ambient` | الافتراضي — نيلي + فيروزي + بنفسجي |
| `ember` | دافئ — كهرماني + وردي |
| `quiet` | هادئ — حقلان واسعا |
| `none` | بدون — لأقل الأجهزة استهلاكاً |

في القمّة الفاتحة تُخفَّض الشفافية إلى 55% (نفس الألوان، شفافية أقل) — نفس هوية الخلفية عبر القمّتين.

**الأداء:** `Canvas` واحد + `drawBehind`، القراءة في طور الرسم فقط ⇒ لا recomposition. `blob` يعمل بـ `graphicsLayer`.

---

## 8. البوابات

| البوابة | ما تفرضه |
|---|---|
| `scripts/check_design_tokens.py` | **لا لون ولا نصف قطر خام خارج حزم الثيم** |
| `scripts/design_token_baseline.txt` | القوائم المسموحة (55 مدخلاً — يجب أن تنكمش فقط) |
| `scripts/check_strings_parity.py` | تطابق تام للنصوص بين كل اللغات |
| `scripts/check_hardcoded_text.py` | لا نص عربي في الكود |
| `scripts/check_vendor_neutrality.py` | حياد تجاري |
| `ColorContrastTest` | WCAG AA لكل تركيبة في النظامين |
| `ArabicLegibilityTest` | قواعد الوزن/الحجم للعربية |

**النطاق:** حزم الثيم المستثناة:
`core/ui-components/src/main/java/com/nexaflow/core/ui/theme/` و`app/src/main/java/com/nexaflow/app/ui/theme/`

---

## 9. الترحيل

| النسخة | السلوك |
|---|---|
| أول نسخة | النظام الجديد **معاينة** في المظهر، الافتراضي هو القديم |
| النسخة التالية | الجديد افتراضياً + زر "العودة للمظهر القديم" |
| majors | حذف النظام القديم |

---

## 10. قائمة الفحص لكل شاشة

- [ ] الخلفية البيئية تعمل ولا ترفع الكلفة > 3% من frame time
- [ ] حبة التنقّل لا تحجب محتوى
- [ ] كل نص يمرّ `stringResource`
- [ ] تباين AA في الوضعين
- [ ] RTL: الأيقونات تعكس، **التدرّج لا يعكس** (يبقى LTRClaims)
- [ ] كل اللغات: لا اقتطاع
- [ ] 48dp هدف لمس كحد أدنى
- [ ] TalkBack يقرأ البطاقات بترتيب منطقي
- [ ] بلا وميض عند التبديل بين الثيمين

---

*nexaflow-one — 2026-10-24*
