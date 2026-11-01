# Ink Glass — device pass

Run on a real handset, not an emulator: Xiaomi 23049PCD8G (Redmi Note 12 Turbo),
**Android 17 / API 37**, 1080 × 2400 at 440 dpi ≈ **393dp** wide — the reference
artboard is 390dp, so spacing and type land on the same grid.

```bash
adb install -r app/build/outputs/apk/release/app-release.apk   # debug-signed
```

Screenshots in `docs/ink-glass/screenshots/`.

| # | Shot | What it shows |
| --- | --- | --- |
| 01 | `01-launch.png` | Ink dashboard, Arabic RTL, glass Dock |
| 02 | `02-builder.png` | Builder, conditions picker (Arabic) |
| 03 | `03-picker-selected.png` | Picker with a selected row + count |
| 04 | `04-settings.png` | Settings |
| 05 | `05-themes.png` | Themes: modes, Dynamic colour, **Glass effects** |
| 06 | `06-fontscale200.png` | Font scale 2.0 |
| 07 | `07-dynamic-scheme.png` | Material You scheme (dynamic colour on) |
| 08 | `08-dashboard-ink-ar.png` | Ink dashboard after the fixes, Arabic |
| 09 | `09-dashboard-ink-en.png` | Ink dashboard, English LTR |
| 10 | `10-picker-wrapped-chips.png` | Picker opening on search, all six category chips wrapped |
| 11 | `11-battery-below-options.png` | Battery threshold moved below the catalogue |

## Verified on the device

* **Ink scheme renders as specified**: `#0A0D14` background, `#141926` cards with
  the 1dp hairline, `#6D8CFF` primary, `#8B9AFF` eyebrows (sampled from the PNG:
  the plus button measured exactly `#6D8CFF`, the dock `#262D3E`).
* **Exactly one glass element per screen** — the Dock. Glass sits on the halo of
  the two background glows, so the blur has something to refract.
* **RTL is first-class**: title/start alignment, settings at the end, Dock's Ask
  pill at the start (right) and the add button at the end (left), category chips
  and picker rows mirrored, numerals and time strings untouched.
* **Bento row is now level** — `IntrinsicSize.Max` keeps the sparkline card and
  the next-run card the same height (they differed before the fix).
* **Glass effects selector** (Auto / On / Off) and **Dynamic colour** switch are
  present and persisted; Auto is the default and was the selected chip.
* **Dynamic colour actually re-themes the Ink alphas** — the dashboard turned
  neutral grey (`#212124`) with a wallpaper `primary` (`#AEC6FF`), i.e. the
  "dynamic → M3 roles, keep the alpha tokens" rule from §5.1.
* **Font scale 200%**: titles wrap, cards grow, the Dock keeps its layout. Two
  defects were found here and fixed (see below).
* **Semantics tree** (uiautomator, the same nodes TalkBack reads): the settings
  button is "الإعدادات", the add button "مهمة جديدة", and the sparkline exposes
  "0 تشغيلات اليوم" — the text equivalent from §9.

## Defects the device found, fixed in this branch

1. **Bento caption truncated at 200%** — "لا توجد تشغيلات قادمة" collapsed to
   "لا توجد" because the caption was `maxLines = 1`. Now wraps to two lines.
2. **Unequal bento heights** — the next-run card was shorter than the sparkline
   card; the row now measures both cards together.
3. **Ask pill unreadable under a dynamic scheme** — the label used
   `onPrimaryContainer`, and the wallpaper palette handed back a *darker* tone
   (`#002457`) on the 6% pill, dropping it to near-invisible. Added the
   `askPillLabel` role (`#C9D3FF` in Ink, `primary` under dynamic colour) and
   gated it in `InkContrastTest`.
4. **Off switch thumb 2.2:1** — Material's default `outline` on the Ink off
   track; replaced by the shared `NexaFlowSwitch` on `switchThumbOff`.

## Ordering and option-clarity pass (requested after the first device run)

* **The list starts at the search field.** The battery threshold trigger was
  rendered above the catalogue, so the card opened on a one-off trigger and
  pushed search, the chips and the rows down. It now renders as its own section
  *below* all options (`BatteryThresholdSection`), and the picker no longer
  prints its title twice — the section header is the only heading, so search is
  the first control.
* **All category chips fit.** The strip scrolled sideways with a trailing fade
  and clipped the last categories at 390dp; it now wraps onto a second line with
  natural widths, so الجدولة / الجهاز / الاتصال / الموقع / التطبيقات / التواصل
  are all visible and comparable. Same for the eight action categories.
* **Battery arguments are explicit choices.** The old controls cycled a value on
  each tap (`Charger: ANY` → AC → USB → WIRELESS → ANY), hiding the option set
  and punishing a mis-tap. Direction, charger type and charging state are now
  labelled chip rows — one tap selects, every option visible, identical stored
  tokens (`BELOW`/`ABOVE`, `ANY`/`AC`/`USB`/`WIRELESS`,
  `ANY`/`CHARGING`/`NOT_CHARGING`), so existing tasks and the engine are
  unaffected.

## Option-simplification pass (requested after the ordering pass)

* **Why.** A trigger or action editor was showing tuning layers on top of the
  choice itself — millisecond debounce/rate-limit/cooldown/stability/hysteresis
  fields on most triggers, a five-mode expression builder with nested groups and
  second windows on the WHEN station, and timeout/retry/backoff/output-path knobs
  on HTTP requests. None of them are needed to say *when* something happens or
  *what* should run, and they made a two-field choice read as an unexplained
  form.
* **What was removed.** The `trigger_temporal_filters_title` block from both the
  canonical and the specialised trigger editors, the schema helper that built it,
  `TriggerExpressionEditorCard` and its preview component, and the HTTP advanced
  tail. 27 string keys per locale (270 lines) were deleted across the 10 locales,
  so the strings cannot reappear from a stale resource.
* **What was deliberately kept.** The configuration schema still accepts those
  fields and the engine still reads them: a task saved earlier keeps its tuned
  values, its stored expression still evaluates, and nothing in `domain/`,
  `core/execution` or the Room schema changed. A stored expression that no longer
  validates is dropped on save (the builder could otherwise block forever on a
  rule it can no longer open).

### Verification for this pass

| Check | Result |
| --- | --- |
| `:feature:automation-builder:testDebugUnitTest` | PASS (includes the rewritten `TriggerExpressionReferenceRepairTest`, which now asserts the stored-runtime contract without the deleted preview) |
| `:app:assembleDebug` | PASS |
| `:app:lintDebug` | PASS — 0 errors, 5 pre-existing warnings; the 11 locale files lint clean after the deletions |
| Removed resources in the shipped APK | PASS — `aapt2 dump resources app-debug.apk` contains no `trigger_temporal_filters_title`, `trigger_expression_*` or `http_retry*` entries |
| Device (b3a3beb3) | The new debug APK was installed and the app relaunched into the Arabic dashboard; the trigger sheet itself was **not** opened on device this pass, because the builder still sits behind the three-step wizard and blind `adb input` taps through the config sheets are not a reliable way to reach it. The APK resource dump above is the check that the section cannot render. |

## Still open

* **The builder is not on §7.3 yet.** "New task" opens the earlier three-step
  wizard: a `LinearWavyProgressIndicator` over a `PrimaryTabRow` (WHEN / THEN /
  WHEN THIS ENDS), the Add button floating above the list instead of in the
  Dock, and the tab row clipping as the screen scrolls. The condition/action
  catalog *inside* it is already Ink (SearchPill, `CategoryChip`,
  `PickerGroup` with 44dp tinted tiles and 28dp multi-check), but the chrome
  around it is not. §7.3 itself asks for "Compose previews for review before
  merging", so this stays out of the branch until that review happens.
* **TalkBack speech** was not listened to. The semantics tree was inspected
  instead; the announcement wording still wants a human ear.
* **Macrobenchmark scroll (50 routines)** is not written or measured — the
  `:macrobenchmark` module only carries startup and planner benchmarks, and the
  P90 budget is a measurement, so no number is claimed here.
* **Routine card / details / history** were not captured on the device: the
  builder needs a wired trigger-action pair first, and blind `adb input` taps
  through the config sheets are not a reliable way to create one.
