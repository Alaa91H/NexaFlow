# NexaFlow Design System — Ink Glass

Ink Glass is the single visual language of NexaFlow. It replaces the earlier
Google-style borderless cards, the alternating grey surfaces and the stacked
FABs with one calm, dark-first system built from a small set of tokens.

This document is normative. If code and this file disagree, one of them is a bug.

Principles:

1. **Ink base.** Near-black blue background, tonal cards, 1dp hairline borders.
   No elevation shadows on content.
2. **Glass only for floating chrome.** Exactly one blurred element per screen:
   the bottom Dock. Content, sheets and dialogs are solid.
3. **Colour is meaning.** Category hues tint icon tiles; the primary colour is
   reserved for actions, selection and live state.
4. **Show the logic.** WHEN → THEN is visible as structured rows, not a sentence.
5. **Calm density.** One card style (24dp radius), 12dp gaps, 16dp gutters.

---

## 1. Where the tokens live

| Layer | File | Contents |
| --- | --- | --- |
| Colour scheme | `app/src/main/java/com/nexaflow/app/ui/theme/Color.kt` | Ink seed, light/dark neutral families |
| Semantic roles | `core/ui-components/.../theme/NexaFlowColors.kt` | `success`, `warning`, `accentLabel`, category palette, alpha tokens |
| Type scale | `core/ui-components/.../theme/NexaFlowType.kt` | `NexaFlowType`, per-script helpers |
| Dimensions | `core/ui-components/.../Dimens.kt` | spacing grid, radii, component metrics |
| Theme wiring | `app/src/main/java/com/nexaflow/app/ui/theme/Theme.kt` | locals, glow host, per-locale font family |

Feature code must not contain literal colours or raw dp values. Use
`MaterialTheme.colorScheme`, `NexaFlowTheme.colors`, `NexaFlowTheme.categories`,
`NexaFlowTheme.type` and `Dimens`.

`DesignTokensTest`, `NavigationStyleTest` and `ScriptTypographyTest` pin the
values in this document.

---

## 2. Colour roles

### 2.1 Ink dark scheme (default seed `ink`)

| Token | Ink dark | Material role |
| --- | --- | --- |
| Screen background | `#0A0D14` | `surface` / `background` |
| Card, search pill, picker group | `#141926` | `surfaceContainerLow` |
| Container (interpolated) | `#171D2D` | `surfaceContainer` |
| Dock fill base | `#1B2234` | `surfaceContainerHigh` |
| Toggle off track | `#2A3144` | `surfaceContainerHighest` |
| Primary (fills, selected, Add) | `#6D8CFF` | `primary` |
| On primary | `#0A1033` | `onPrimary` |
| Eyebrow / WHEN-THEN label | `#8B9AFF` | `NexaFlowColors.accentLabel` (dynamic: `primary`) |
| Primary text | `#EEF1F8` | `onSurface` |
| Secondary text / meta | `#9AA4B8` | `onSurfaceVariant` |
| Section label | `#B8C1D6` | `onSurfaceVariant` |
| Ask pill text | `#C9D3FF` (light: `#2E3E8F`) | `NexaFlowColors.askPillLabel` (dynamic: `primary`) |
| Unselected checkbox border | `#5A6580` | `outline` |
| Toggle thumb off | `#8E99B0` (light: `#6E778C`) | `NexaFlowColors.switchThumbOff` |
| Running state | `#7FE0A6` | `NexaFlowColors.success` (`#78DAA0` in the semantic palette) |

Ink is the default scheme. Dynamic colour stays available but is opt-in
(§9.1); accent seeds keep their hue and adopt the Ink neutrals.

### 2.2 Alpha tokens (`NexaFlowAlphas`)

All derived from `onSurface` so they follow any scheme, including dynamic
colour.

| Token | Alpha | Used by |
| --- | --- | --- |
| `HairlineBorder` | 0.07 | cards |
| `StrongBorder` | 0.12 | header icon buttons |
| `ChipBorder` | 0.17 | category chips |
| `GlassBorder` | 0.15 | Dock |
| `Divider` | 0.05 | grouped-list separators |
| `FlowChipFill` | 0.07 | WHEN / THEN chips |
| `AskPillFill` | 0.06 | Ask pill |
| `IconButtonFill` | 0.05 | circular icon buttons |
| `SelectedRowTint` | 0.12 | selected picker row |
| `SuccessPillFill` | 0.17 | running pill |
| `CategoryTileFill` | 0.16 | routine icon tile |
| `CategoryTileFillPicker` | 0.14 | picker row tile |
| `LoadingShimmer` | 0.06 | skeletons |
| `EmptyStateTile` | 0.14 | empty-state tile |

### 2.3 Category palette (`NexaFlowCategoryColors`)

A routine takes the colour of its **first trigger's category**, falling back to
its **first action's category**. Tiles use the hue at 16% (14% in pickers) and
the icon at full strength.

| Category | Dark | Light (tone 40) | Covers |
| --- | --- | --- | --- |
| `TIME` | `#B9A4FF` | `#6A4FA8` | time/schedule, display, sleep/DND |
| `LOCATION` | `#8AB4FF` | `#2C5CAD` | location, device, GPS |
| `POWER` | `#7FE0A6` | `#176B45` | battery, power |
| `CONNECTIVITY` | `#6FD6F0` | `#00626F` | bluetooth, NFC, Wi-Fi |
| `NOTIFICATIONS` | `#FFB86C` | `#8A4B00` | notifications, apps, SMS |
| `SOUND` | `#FF9EC4` | `#8A3A5C` | sound, media, ringer |
| `ADVANCED` | `#B8C1D6` | `#4E5668` | advanced, other |

Mapping lives in `feature/automation-builder/.../InkCategoryMapping.kt`
(triggers and actions → `NexaFlowCategory`). Light hues are checked ≥ 4.5:1 for
the icon on its own tinted tile over `surfaceContainerLow`.

---

## 3. Shape and dimension

| Token | Value |
| --- | --- |
| Card / bento / flow container | `Dimens.CardRadius` 24dp, 1dp hairline |
| Grouped list surface | `Dimens.GroupRadius` 28dp, clipped, 1dp hairline |
| Routine icon tile | `Dimens.RoutineTile` 48dp, radius 16dp |
| Picker row icon tile | `Dimens.PickerTile` 44dp, radius 14dp |
| Circular icon button | `Dimens.IconButton` 44dp visual, 48dp touch |
| Search pill | `Dimens.SearchHeight` 52dp, fully rounded |
| Category chip | `Dimens.ChipHeight` 40dp, 48dp touch via `minimumInteractiveComponentSize` |
| Multi-check | `Dimens.CheckSize` 28dp circle |
| Dock | height 68dp (min, grows with font scale), radius 34dp, margins 16 / 28dp, padding 8dp, button 52dp, max width 560dp |
| Dialog / bottom sheet | 28dp |

Spacing: 4dp grid (`Space1…Space14`), screen gutter 16dp (24dp expanded),
card-to-card 12dp (`CardGap`), tile-to-text 14dp (`TileTextGap`), flow chip
radius 12dp, flow label column 44dp.

No content shadows. The Dock is the only shadowed element: y-offset 12dp,
blur 32dp, black 40%.

---

## 4. Typography

Families are chosen **per locale** in `Theme.kt`:

| Locale | Family |
| --- | --- |
| `ar` | IBM Plex Sans Arabic |
| `ru`, `hi`, `ja`, `zh-rCN` | Roboto Flex / system (Plus Jakarta Sans has no Cyrillic, Devanagari or CJK) |
| everything else | Plus Jakarta Sans |

The scale (`NexaFlowType`, read through `NexaFlowTheme.type`):

| Field | Size / weight | Notes |
| --- | --- | --- |
| `screenTitle` | 30sp / 800 | −0.02em tracking, line height 36 |
| `subScreenTitle` | 22sp / 800 | |
| `bentoValue` | 28sp / 800 | |
| `cardTitle` | 16sp / 700 | |
| `pickerRowTitle` | 15.5sp / 700 | |
| `meta` | 12.5sp / 500 | secondary colour, line height 16 |
| `sectionLabel` | 13sp / 700 | |
| `button` / `chip` | 15sp / 800 · 14sp / 600 | |
| `askPill` | 15sp / 600 | |
| `statusPill` | 11.5sp / 700 | |
| `eyebrow` | 11sp / 800 | caps + 0.08em tracking, **Latin only** |
| `flowLabel` | 10.5sp / 800 | WHEN / THEN / EXIT |
| `flowChip` | 12.5sp / 600 | |

### Script and bidi rules

* **Arabic never receives tracking or uppercasing** — tracking breaks letter
  joining. Gate on the string's own script, not the app locale: use
  `TextStyle.forText(text)` (drops letter-spacing) and `eyebrowText(text)`
  (uppercases Latin only). `containsArabicScript` uses the `\p{IsArabic}`
  Unicode script property.
* **User-authored titles are bidi-safe**: routine, trigger and action names go
  through `TextStyle.forUserText(text)`, which drops tracking for Arabic and sets
  `textDirection = TextDirection.Content` with `TextAlign.Start`, so “تشغيل GPS”
  and “O2 2GB” both render correctly inside one list.

---

## 5. Background glow

`Modifier.screenGlow(variant)` draws two static radial gradients behind screen
content with `drawBehind` (transparent at 70% radius, no animation). They also
give the Dock something to refract.

| Variant | Glow A | Glow B |
| --- | --- | --- |
| `Dashboard` | 340dp, offset (−90, −80) from top-start, primary `#3C5BFF` @33% | 360dp, end −120 / top 430, tertiary `#8B5CF6` @33% |
| `SubScreen` | — | 360dp, end −120 / top −90, primary @33% |

Light theme: alpha 12%.

---

## 6. Glass

`GlassSurface` is the only blurred element in the app. Screens register one blur
source with `Modifier.glassSource()` on their scrolling root; the Dock consumes
it. Glass is never applied per list item.

* Blur mode: `surfaceContainerHigh` at 65%, Haze blur radius 22dp, saturation
  stand-in tint 10%, 1dp border at 15% `onSurface`, shadow per §3.
* **Fallback (solid):** API < 31, Battery Saver on, or preference `Off` →
  `surfaceContainerHigh` at 94% with the same border, shadow and size. Layout is
  identical in both modes, so switching never reflows.

`resolveGlassMode(sdkInt, batterySaver, preference)` is pure and unit-tested
(`GlassModeTest`).

| Preference | Behaviour |
| --- | --- |
| `AUTO` (default) | blur on API 31+, solid in Battery Saver or below 31 |
| `ON` | blur wherever the API allows it |
| `OFF` | always solid |

---

## 7. Components (`core/ui-components`)

| Component | Contract |
| --- | --- |
| `NexaFlowCard` | 24dp radius, `surfaceContainerLow`, 1dp hairline, 14dp vertical / 16dp horizontal padding, no elevation, no `alternatingIndex`. |
| `IconTile` | Squircle (`size` 48/44, `radius` 16/14) filled with a category hue at 16% (14% in pickers); 22dp icon in the same hue. Replaces `IconBadge` inside lists. |
| `NexaFlowSwitch` | Material 3 `Switch` on the Ink colour set: `primary` track with a white thumb when on, `surfaceContainerHighest` track with `switchThumbOff` when off. The single switch everywhere in the app, so the off thumb never falls back to `outline` (which measures 2.2:1 on the dark off track). |
| `StatusPill` | 6dp dot + label, fully rounded, 3dp × 9dp padding, label 11.5sp/700, 6dp gap. Variants: Running (success), Needs permission (error container), Error. Never used for “idle”. |
| `BentoStatCard` | 24dp card, eyebrow, 28sp/800 value, 12.5sp caption (wraps to two lines so nothing truncates at font scale 200%); the pair is measured with `IntrinsicSize.Max` so both cards match. The `Today` variant adds a six-bar sparkline (4dp gaps, 22dp max height, 3dp radius bars, one per 4-hour bucket, minimum 2dp, empty bars at 12%). |
| `SearchPill` | 52dp, fully rounded, `surfaceContainerLow`, hairline, 18dp horizontal padding, 22dp icon, 12dp gap, 15sp hint in secondary colour. |
| `CategoryChip` / `CategoryChipRow` | 40dp tall, 16dp horizontal padding, 8dp icon gap, 14sp/600, border 17%; selected fills primary with `onPrimary` content and a leading check. In the builder picker the chips **wrap** (8dp gaps, natural widths) so every category is visible and no label is clipped; `CategoryChipRow` keeps the single-line scroll for places that hold one row. |
| `PickerGroup` / `PickerRow` / `MultiCheck` | 28dp clipped group; rows 13dp × 16dp padding, 14dp gap, 1dp 5% divider above every row but the first. Row = `IconTile`(44) + title 15.5/700 + 12.5sp description (≤2 lines) + trailing `MultiCheck` (28dp circle, 2dp `outline` when off, primary + `onPrimary` check when on). `toggleable` with `Role.Checkbox` merged into the row. `CheckableRow` remains for single-choice dialogs. |
| `Dock` | Glass bar (§6) with a leading slot and a trailing action: `DockAskPill`, `DockPrimaryButton`, `DockIconButton`, `DockTextAction`, `AddNodeButton`. |
| `ScreenHeader` | 44dp circular `HeaderIconButton` (hairline 12%, fill 5%) plus title block at the other end (or after a 14dp gap when it is a back button). |
| `FlowStepRow` / `FlowChip` | One 44dp eyebrow label column + wrapping chips; the single component behind WHEN, THEN and EXIT everywhere. |
| `EmptyState` / `LoadingState` | Empty icon in a 56dp / 20dp-radius tile at 14% primary; skeletons reuse the card shape with a 6% shimmer. |
| `SettingsGroup` / `SettingRow` / `SectionHeader` | Grouped surfaces and rows with the same metrics as picker rows. |

Every shared component ships previews in `InkComponentPreviews.kt`: light, dark,
RTL (`locale = "ar"`), font scale 2.0, and phone/tablet widths for the chrome.

`AlternatingSurface`, `SelectChip` and the `BuilderCardAccent` palettes are
removed; `IconBadge` survives only where a non-category circle is still right.

Contrast is gated, not eyeballed: `InkContrastTest` builds the real schemes
(`googleColorScheme(ink, dark)`) and asserts every text pair at ≥ 4.5:1 and
every non-text boundary at ≥ 3:1 — primary/meta/eyebrow text, the Dock in both
blur (65%) and fallback (94%) modes, the Ask pill, the Add button, all seven
category hues on their 16% and 14% tiles in both schemes, and the running and
needs-permission pills. Editing a palette token to something dimmer fails the
build.

---

## 8. Screen recipes

### 8.1 Dashboard

Header (`Routines` + “{total} tasks · {active} active” + settings icon) →
`SearchPill` → bento row (`Next run`, `Today`) → section label “Scheduled tasks”
→ `RoutineCard` list (exactly one expanded at a time) → Dock with the Ask pill
and a 52dp primary plus button (extended with its label on medium+ widths).

`RoutineCard` row: `IconTile`(48) + title 16/700 + one-line meta + optional
`StatusPill`, trailing 52dp `Switch`. The whole card toggles expansion (no
chevron). Expanded block, indented 62dp: `FlowStepRow` WHEN, `FlowStepRow`
THEN, footnote (“Reverts automatically at {time}” or “Runs {n} exit actions”),
then the diagnostics the card has always exposed — readiness, live lifecycle,
execution health, trigger/constraint/action state, end behaviour and
customisations. Bottom content padding = Dock height + margin + 16dp + nav
inset.

### 8.2 Picker (add triggers / add actions)

`ScreenHeader` back + “Add triggers” (22sp/800) + “{n} selected” →
`SearchPill` → `CategoryChipRow` → `PickerGroup` → Dock with “{n} selected” and
`DockPrimaryButton` (disabled at 0 selections at 38% alpha).

### 8.3 Builder, details, history, settings, themes

Builder: **one page**, top to bottom — a header card (icon + name), then WHEN,
THEN, WHEN THIS ENDS and CONSTRAINTS as 24dp `NexaFlowCard`s with their own
section headers, one card per node tinted by the node's category.

There is no step wizard: no `PrimaryTabRow`, no progress bar and no per-step
Continue button. The trigger and action catalogues open from an `AddNodeButton`
pill inside their own station (`Add trigger` / `Add action`) and close again on
confirm, so the page never shows two open pickers at once and an existing task
opens on its own flow. The single action lives in the Dock (`BuilderDock`): the
leading slot states what the routine already contains
(“{triggers} triggers · {actions} actions”) and the trailing `DockPrimaryButton`
saves (`Create task`, or `Save` when editing). The builder still has no Run-now
entry point because an unsaved routine cannot be executed.

Details: `ScreenHeader` with `HeaderIconButton` actions (shortcut, edit, delete)
over the existing detail sections; the deep-link and per-node diagnostics are
unchanged.

History: `ScreenHeader`, outcome filters as `CategoryChip`s, 13sp/700 day labels,
and one grouped surface per day — rows are a 44dp tinted tile, the task name and
meta, and a trailing status pill, with 5% dividers between rows and 28dp corners
on the first and last of each day.

Settings: `SettingsGroup`/`SettingRow` grouped surfaces with 44dp tiles.

Themes: 48dp accent circles with a 2dp primary ring when selected, dark-mode
selector, **Glass effects** (Auto / On / Off) and **Dynamic colour** switch, both
persisted in `ThemePreferences`.

Widgets: same tokens, 24dp radius, no blur.

Dialogs and sheets: solid `surfaceContainerHigh`, 28dp radius (top corners for
sheets), hairline top border, 60% scrim, no blur.

### 8.4 Node configuration depth

A trigger or action editor exposes only the fields that define that option: what
it watches, where, and which value. Tuning layers that duplicated the engine's
own defaults were removed from **every** trigger and action, because they turned
a two-field choice into a form of millisecond durations.

* **Trigger filters are gone.** Debounce, rate limit (count over window), minimum
  interval, cooldown, stability window and hysteresis — the whole
  `trigger_temporal_filters_title` block — no longer render, together with the
  `CanonicalBuilderSchemaBridge.temporalFiltersBindingForTrigger` helper that fed
  them. The fields remain part of the stored configuration schema and the engine
  still reads them, so a task saved earlier keeps its tuned values; a new task
  runs on the catalog defaults.
* **The advanced trigger-expression editor is gone.** ALL / ANY / NOT / SEQUENCE
  / COUNT nodes, nested groups, the sample preview and the seconds window are
  no longer shown; simple matching stays in the combine selector. A stored
  expression keeps running untouched, and one that no longer validates is
  dropped on save instead of blocking every save with a rule the UI can no longer
  open.
* **HTTP request actions keep URL, method, headers and body**; timeout, retries,
  retry backoff and the response output path were removed, so the handler
  defaults apply.

Rationale: an option that needs a paragraph of explanation is a failure of the
option, not of the user. Everything removed here was optional tuning, never the
identity of the option, and no engine capability was deleted.

### 8.5 Node option controls

The renderer that draws every canonical trigger/action field follows four rules,
so no option ever falls back to a form:

| Field | Control |
| --- | --- |
| `ENUM_TOKEN` | `CategoryChip` row with translated labels. A single-choice node prints no header — the chips are their own label. |
| `INTEGER` / `DECIMAL` / `PERCENTAGE` | `SteppedValueRow`: `−` / `+` buttons around a slider, value line formatted with the field's unit (percent, °C, MB/GB, bars). |
| `DURATION_MS` | ROM preset chips when the ROM defines the values (screen timeout), otherwise the same stepper in minutes. |
| `PACKAGE_ID` / package collection | Installed-app sheet; chosen apps become removable chips, never package-name text. |

Vocabulary lives in `SchemaFieldPresentation` (labels, token names, units,
bounds, step sizes) so the renderer never prints a storage key or an upper-case
constant. Tokens without a translated resource are humanised (`FIRST_DAY` →
"First Day") rather than shown raw. The one text box that remains is genuinely
free text: a webhook path, an SSID, a search phrase.

Trigger groups, the duplicate audit and the per-trigger control matrix are in
[`docs/ink-glass/TRIGGER_INVENTORY.md`](ink-glass/TRIGGER_INVENTORY.md).

---

## 9. Defaults and recorded decisions

1. **Colour mode.** Ink is the default scheme; dynamic colour is opt-in and
   available from Themes. Acceptance screenshots use Ink.
2. **Fonts.** Plus Jakarta Sans + IBM Plex Sans Arabic are bundled in
   `res/font` under the SIL OFL (`docs/licenses`, `licenses` metadata).
3. **Alternating grey surfaces removed.** Category-coloured tiles and one
   uniform card replace the `alternatingIndex` design.
4. **Extension values** (notifications/sound/advanced hues, the Needs-permission
   pill, sparkline bucketing, the light theme) are specified here rather than
   in the visual reference and should get a visual sign-off.
5. **Glass default** is `AUTO`: blur on API 31+, solid in Battery Saver.

---

## 10. Accessibility, RTL, large screens

* Layouts use start/end only. Directional icons use `AutoMirrored` variants.
  In RTL the Dock's Ask pill sits at the start and the plus button at the end.
* Contrast ≥ 4.5:1 for text (3:1 large); touch targets ≥ 48dp; every control
  reports a role, state and `stateDescription` (`RoutineCard` announces name,
  state, trigger summary and expanded/collapsed; picker rows announce checked
  state; the sparkline has the text equivalent “{n} runs today”).
* Font scale 200%: titles wrap to two lines, the Dock grows past 68dp (min
  height, not fixed), chips stay on one line and scroll.
* Expanded widths: content capped at `Dimens.MaxContentWidth` (840dp) and
  centred; the Dock is capped at 560dp and centred; the navigation rail
  replaces the bar.
* Reduce motion: every `NexaFlowMotion` helper collapses to an instant state
  change, and haptics still fire.

## 11. Motion and haptics

`NexaFlowMotion` (`nexaFlowSpatialSpec`, `nexaFlowEffectsSpec`,
`nexaFlowEntrance`) remains the only source of animation specs. Card expansion
uses the spatial spec with an effects-spec content fade; switch and checkbox use
the effects spec; list entrance staggers 30ms for the first six items. Haptics:
`ToggleOn`/`ToggleOff` on switches and checkboxes, `ContextClick` on Run now.
