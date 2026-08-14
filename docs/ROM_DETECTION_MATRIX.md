# ROM Detection Matrix — Reference

Maintenance reference for the ROM-family detection and settings-integration layer in
`core/rom-integration`. Every rule below is **the live source of truth** mirrored from:

| File | Role |
|---|---|
| `core/rom-integration/.../rom/RomDetectionMatrix.kt` | Detection table + two-pass resolver |
| `core/rom-integration/.../rom/model/RomFamily.kt` | The family enum (names + descriptions) |
| `core/rom-integration/.../rom/RomSettingSchema.kt` | Settings-key prefixes, namespaces, lineage/OEM classification |
| `core/rom-integration/.../rom/RomDetector.kt` | Live `Build.*`/`ro.*` snapshot that feeds the matrix |
| `core/rom-integration/.../rom/RomCapabilityProvider.kt` | Capability map per family (see that file for detail) |

**When editing detection:** keep this document in sync with `RomDetectionMatrix.kt` and
`RomSettingSchema.kt`. Both files are pure JVM (no `android.*` imports) so their logic is
atomically unit-testable — see the existing tests in
`core/rom-integration/src/test/` and extend them when you add a rule.

---

## 1. Detection algorithm (two passes)

`RomDetectionMatrix.detectFamily(props, brand, manufacturer)` resolves a family in two passes
over the ordered `RULES` list.

### Pass 1 — property rules (most specific first)

Walk `RULES` in order; the **first** rule that satisfies **both**:

1. **Property hit** — at least one of `rule.properties` exists in the snapshot with a
   non-blank value (`props[key].orEmpty().isNotBlank()`).
2. **Brand admit** — if `rule.brands` is non-empty, `Build.BRAND` (lowercased) must be in it.
   Rules with an **empty** `brands` list have no brand constraint and win on any hardware
   (this is what lets custom ROMs be detected on any device).

wins the family. Later rules are never consulted.

### Pass 2 — manufacturer fallback

Only reached when **no** property rule hit. Walk `RULES` in order; the first rule whose
`rule.manufacturers` contains `Build.MANUFACTURER` (case-insensitive) wins.

If neither pass matches → `RomFamily.OTHER`.

### Why this order (branching priorities)

| Priority | Rule | Reason |
|---|---|---|
| 1 | **Custom ROMs before OEM skins** | A LineageOS build flashed on a Samsung/Xiaomi device must classify as the custom ROM, never as the stock skin. Custom-ROM rules carry no brand constraint, so they win regardless of hardware. |
| 2 | **Specific forks before their bases** | Evolution X sets both `ro.evolution.version` *and* the inherited `ro.lineage.version`; the fork rule comes first so the build is Evolution X, not generic LineageOS. Same for PixelOS/crDroid/etc. vs LineageOS. |
| 3 | **HarmonyOS before EMUI** | HarmonyOS builds also set EMUI markers; the HarmonyOS rule precedes EMUI. |
| 4 | **Realme UI / OxygenOS before ColorOS** | These share `ro.oplus.version` with OPPO ColorOS. Brand constraints (`realme`, `oneplus`) resolve the family — ColorOS is only admitted on brand `oppo`. |
| 5 | **Manufacturer fallback last** | Stock builds whose version property is missing/renamed (Motorola, Sony, Pixel, …) fall back to the manufacturer table. |

---

## 2. The full rule table

### 2.1 Custom ROMs — property rules, no brand constraint (run on any hardware)

| # | Family | Properties (any non-blank admits) |
|---|---|---|
| 1 | `EVOLUTION_X` | `ro.evolution.version` |
| 2 | `CR_DROID` | `ro.crdroid.version` |
| 3 | `PIXEL_EXPERIENCE` | `ro.pixelexperience.version` |
| 4 | `PARANOID_ANDROID` | `ro.pa.version` |
| 5 | `ARROW_OS` | `ro.arrow.version` |
| 6 | `PIXEL_OS` | `ro.pixelos.version` |
| 7 | `PROJECT_ELIXIR` | `ro.elixir.version` |
| 8 | `DERPFEST` | `ro.derp.version` |
| 9 | `SUPERIOR_OS` | `ro.superior.version` |
| 10 | `LINEAGE_OS` | `ro.lineage.version` |
| 11 | `GRAPHENE_OS` | `ro.grapheneos.build_type`, `ro.grapheneos.version` |

> Every custom ROM of the LineageOS family publishes its own `ro.<name>.version` via its
> vendor overlay — that is the detection contract for the whole family.

### 2.2 OEM skins — property rules, brand-constrained

| # | Family | Properties | Brands (lowercase) |
|---|---|---|---|
| 12 | `HARMONY_OS` | `ro.build.version.harmonyos`, `hw_sc.build.platform.version` | `huawei`, `honor` |
| 13 | `EMUI` | `ro.build.version.emui`, `ro.build.hw_emui_api_level` | `huawei`, `honor` |
| 14 | `HYPER_OS` | `ro.mi.os.version.name` | `xiaomi`, `redmi`, `poco` |
| 15 | `MIUI` | `ro.miui.ui.version.name` | `xiaomi`, `redmi`, `poco` |
| 16 | `REALME_UI` | `ro.build.version.realme`, `ro.realme.version` | `realme` |
| 17 | `VIVO_ORIGIN_OS` | `ro.vivo.os.build.display.id`, `ro.vivo.os.build.display.version` | `vivo`, `iqoo` |
| 18 | `COLOR_OS` | `ro.oplus.version`, `ro.build.version.oplusrom`, `ro.build.version.oplus` | `oppo` |
| 19 | `OXYGEN_OS` | `ro.oxygen.version`, `ro.build.version.oxygen` | `oneplus` |
| 20 | `ONE_UI` | `ro.build.version.oneui` | `samsung` |
| 21 | `ASUS_ZEN_UI` | `ro.build.asus.version`, `ro.asus.version` | `asus` |
| 22 | `NOTHING_OS` | `ro.nothing.version`, `ro.nothing.build.version` | *(none — any hardware)* |

> `NOTHING_OS` intentionally has no brand constraint (its property is unique enough).

### 2.3 Manufacturer fallback — stock builds without a version property

| # | Family | Manufacturers (case-insensitive) |
|---|---|---|
| 23 | `PIXEL` | `google` |
| 24 | `ONE_UI` | `samsung` |
| 25 | `MIUI` | `xiaomi`, `redmi`, `poco` |
| 26 | `OXYGEN_OS` | `oneplus` |
| 27 | `REALME_UI` | `realme` |
| 28 | `COLOR_OS` | `oppo` |
| 29 | `VIVO_ORIGIN_OS` | `vivo`, `iqoo` |
| 30 | `EMUI` | `huawei`, `honor` |
| 31 | `ASUS_ZEN_UI` | `asus` |
| 32 | `MOTOROLA` | `motorola` |
| 33 | `SONY_XPERIA` | `sony`, `semc` |
| 34 | `NOTHING_OS` | `nothing` |

> The `AOSP` family is never *detected* by the matrix (no rule maps to it) — it represents a
> known-clean AOSP build and is reserved. `OTHER` is the catch-all for undetected builds.

---

## 3. Property snapshot

- `RomDetectionMatrix.ALL_PROPERTIES` = the union of every property key any rule reads —
  `RomDetector` snapshots exactly that set **once** per detection (plus
  `ro.evolution.buildtype`, a metadata key carried into `RomBuildInfo`, not used for
  classification).
- Properties are read via `SystemPropertyProvider.get(key)`; blank values are filtered out
  before classification.

## 4. Build info output

`RomDetectionMatrix.detect(...)` (full signature) produces a `RomBuildInfo` with:
`family, brand, manufacturer, device, model, androidVersion, securityPatch, buildId,
buildDisplay, androidSdk` **plus** `evolutionVersion` (`ro.evolution.version`),
`lineageVersion` (`ro.lineage.version`), and `evolutionBuildType`
(`ro.evolution.buildtype`) for the Evolution X deep-integration path.

`RomDetector.detect()` wires this to live `Build.*` values. Its `internal var buildValues`
is the test seam — pure-JVM tests inject values instead of the real `Build`.

---

## 5. Settings schema per family (`RomSettingSchema`)

The `settings` providers (system / secure / global) are shared by every Android 12–17 build;
what differs between ROMs is the **key prefix** the ROM's own settings app reads and the
**namespace** the keys conventionally live in.

### 5.1 Key prefixes

| Family(ies) | Prefixes |
|---|---|
| `EVOLUTION_X` | `evo_`, `evolution_`, `dex_`, `lineage_`, `sysui_`, `qs_`, `lockscreen_`, `status_bar_`, `notification_` |
| LineageOS-derived (`LINEAGE_OS`, `CR_DROID`, `ARROW_OS`, `PIXEL_OS`, `PROJECT_ELIXIR`, `DERPFEST`, `SUPERIOR_OS`, `PIXEL_EXPERIENCE`, `PARANOID_ANDROID`) | `lineage_`, `sysui_`, `qs_`, `lockscreen_`, `status_bar_`, `notification_`, `arrow_`, `pixelos_`, `elixir_`, `derp_`, `superior_`, `pa_`, `pe_` |
| `MIUI`, `HYPER_OS` | `miui_`, `hyper_` |
| `ONE_UI` | `sec_`, `oneui_` |
| `COLOR_OS`, `OXYGEN_OS`, `REALME_UI` | `oplus_`, `oppo_`, `oneplus_`, `realme_` |
| `VIVO_ORIGIN_OS` | `vivo_`, `funtouch_` |
| `EMUI`, `HARMONY_OS` | `hw_`, `emui_` |
| `ASUS_ZEN_UI` | `asus_` |
| `NOTHING_OS` | `nothing_` |
| everything else (`PIXEL`, `AOSP`, `MOTOROLA`, `SONY_XPERIA`, `OTHER`) | *(empty — bridge can't enumerate)* |

### 5.2 Default namespace

| Namespace | Families |
|---|---|
| `secure` | `EVOLUTION_X`, all LineageOS-derived forks, `NOTHING_OS` |
| `system` | all OEM skins (`MIUI`, `HYPER_OS`, `ONE_UI`, ColorOS-family, `VIVO_ORIGIN_OS`, `EMUI`, `HARMONY_OS`, `ASUS_ZEN_UI`) and the fallback `else` |
| *(empty)* | undetected families |

### 5.3 Classifiers

- `isLineageDerived(family)` — true for `LINEAGE_OS` + its direct forks (`EVOLUTION_X`,
  `CR_DROID`, `ARROW_OS`, `PIXEL_OS`, `PROJECT_ELIXIR`, `DERPFEST`, `SUPERIOR_OS`,
  `PIXEL_EXPERIENCE`, `PARANOID_ANDROID`). These share the LineageOS privileged SDK/HALs.
- `isOemSkin(family)` — true for all OEM skins (`ONE_UI`, `MIUI`, `HYPER_OS`, `COLOR_OS`,
  `OXYGEN_OS`, `REALME_UI`, `VIVO_ORIGIN_OS`, `EMUI`, `HARMONY_OS`, `ASUS_ZEN_UI`,
  `NOTHING_OS`).
- `isSupported(family)` — `prefixes(family).isNotEmpty()`; the settings bridge can only
  read/write custom keys on supported families.

---

## 6. Maintenance guide

### Adding a new custom ROM (LineageOS-family)

1. Add the family to `RomFamily.kt`.
2. Add a property rule **above** the `LINEAGE_OS` rule (forks precede the base), no brand
   constraint — e.g. `Rule(RomFamily.MY_ROM, properties = listOf("ro.myrom.version"))`.
3. Add its fork prefix to `RomSettingSchema.prefixes(...)` **and** to the LineageOS-derived
   group in `prefixes()`, `defaultNamespaceName()` (`secure`), and `isLineageDerived()`.
4. Add/extend unit tests in `core/rom-integration/src/test/` — including the precedence
   case (fork property present together with `ro.lineage.version` → fork wins).

### Adding a new OEM skin

1. Add the family to `RomFamily.kt`.
2. Add a **brand-constrained** property rule (brands = the vendor's brands, lowercase) in the
   OEM section — and a manufacturer-fallback rule in section 2.3 if the vendor ships stock
   builds without a version property.
3. Mind shared properties: if the vendor shares a property with a sibling skin (e.g.
   `ro.oplus.version`), the brand constraint is what disambiguates — never rely on rule order
   alone for shared keys.
4. Add prefixes/namespace/`isOemSkin` entries in `RomSettingSchema`.

### Golden rules

- **Order is semantics.** The first matching rule wins; never reorder rules without
  re-running the precedence tests.
- **Forks before bases, custom before OEM, fallback last.**
- **Shared properties need brand constraints.**
- Keep both files and this document in sync; the code is pure JVM and fully unit-tested —
  a new rule without a test is a regression waiting to happen.
