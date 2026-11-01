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
