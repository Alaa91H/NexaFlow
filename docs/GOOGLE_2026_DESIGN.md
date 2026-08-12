# إعادة هيكلة التصميم — نمط جوجل 2026 (M3 Expressive)

> **Goal:** Make the whole app — every screen, list, option, and feature — look,
> feel, and behave like a 2026 Google app: Material 3 Expressive tokens,
> Google's 2026 gradient iconography, and Google-style simplified option
> presentation, unified through one design system.
>
> **Basis:** Research on Google I/O 2025–2026 (Material 3 Expressive launch and
> I/O 2026 updates), Google's 2026 app-icon redesign (14+ apps moved from flat
> solid colors to soft dynamic gradients), and the Android 16/17 Material
> surface/typography refinements. Sources: m3.material.io, blog.google
> (Material 3 Expressive), developer.android.com (Compose M3), pixso.net
> (2026 Google icon redesign analysis).

---

## 1. What "Google 2026" means concretely (research summary)

| Axis | 2025–2026 Google direction | What we adopt |
|---|---|---|
| **Shapes** | M3 Expressive: larger radii — cards 24dp+, sheets 28dp, pills everywhere | Expressive shape scale `8/12/16/24/28` |
| **Color** | Full-bleed color, tonal surface tiers, dynamic color (wallpaper seed), bright accents | Dynamic color (already on) + surfaceContainer tiering (already on) |
| **Typography** | Same M3 type scale, more expressive weights; hierarchy via size/weight not over-bolding | Keep M3 scale (already correct) |
| **Motion** | Spring-based physics, staggered entrances | Phase 3 (spring `MotionScheme`) |
| **Icons (2026 redesign)** | Flat → **soft dynamic gradients**, brighter tones, rounded symbols, white glyph on vivid gradient | Vivid blue→violet gradient launcher icon + white check/clock glyph |
| **Options/UX** | Settings-style list rows (icon-tile + title + subtitle + chevron), grouped sections, segmented buttons, bottom-sheet pickers, one-screen decisions | Already largely in place (`SettingRow`, `SectionHeader`); unify every screen on it |

---

## 2. Design token system (single source of truth)

All tokens live in `app/.../ui/theme/` and are consumed everywhere via
`MaterialTheme` — components never hard-code radii or colors.

### 2.1 Shapes — M3 Expressive scale (`Theme.kt`)
```
extraSmall  8dp   (chips, small controls)
small      12dp   (buttons, inputs)
medium      16dp  (lists, switches)
large       24dp  (cards, dialog surfaces)   ← was 16dp
extraLarge  28dp  (bottom sheets)
```
Every `NexaFlowCard` (uses `shapes.large`) and `NexaFlowTopBar`/dialogs inherit
this automatically — one-line change unifies the whole app.

### 2.2 Color (`Color.kt`)
- Keep dynamic color (Material You) as the default on Android 12+ — the exact
  Google-app behavior.
- Keep the seed-based fallback (Google Blue `#0B57D0` default) for pre-12 and
  the accent picker (blue/green/red/purple/amber/teal).
- Add the M3 Expressive **surface tint emphasis**: primary-tinted surface
  containers are already used by cards (`surfaceContainerLow`).
- No changes needed to the neutral families.

### 2.3 Typography (`Typography.kt`)
Keep the M3 scale (already spec-exact). No change.

### 2.4 Iconography
- **Launcher (adaptive):** vivid blue→violet gradient background (the
  Gemini/2026 family) with a **white** bold checkmark + clock glyph; rounded
  joins; `monochrome` layer = same silhouette for Material You theming.
  Plain `<shape>` gradient (no `aapt:attr`), solid-color vector paths — 100%
  launcher-safe.
- **Notification small icon:** existing monochrome `ic_stat_nexaflow` — verify
  it matches the new glyph.
- **In-app icons:** `material-icons-extended` filled set (already in use) —
  Google's filled icon language; no change needed.

---

## 3. Component unification (all screens, all options)

The shared library `core/ui-components` is the single source for every screen:

| Component | Google-2026 spec | Current | Action |
|---|---|---|---|
| `NexaFlowCard` | 24dp radius, tonal container, zero elevation | 16dp (`shapes.large`) | ✅ auto via shape token |
| `SettingRow` | icon tile (40dp circle, tonal) + title + subtitle + chevron/switch | ✅ matches | keep |
| `SectionHeader` | titleSmall onSurfaceVariant, optional trailing action | ✅ matches | keep |
| `NexaFlowTopBar` | surface tinted bar, titleLarge | ✅ matches | keep |
| `StatCard` / `StatusPill` / `IconBadge` / `EmptyState` | tonal, rounded | ✅ | verify radii inherit `shapes.*` only |
| Bottom sheets / dialogs | extraLarge (28dp), rounded top | ✅ | keep |

**Rule going forward:** screens must only consume `core/ui-components` +
`MaterialTheme` tokens; no local hard-coded `RoundedCornerShape`/colors. Any
new screen follows this checklist.

---

## 4. Option presentation — Google-style simplification

Google apps (Settings, Gmail, Tasks) present options as:

1. **List rows** — one decision per row: icon tile · title · subtitle · chevron
   or switch. Never inline multi-choice text.
2. **Segmented buttons** — mutually exclusive modes (e.g., network mode,
   theme mode) as one segmented control, not radio lists.
3. **Bottom-sheet pickers** — a value picker opens a sheet with the choices,
   "Cancel"/"Apply", never a separate screen.
4. **Grouped sections** — `SectionHeader` groups rows; one card per logical
   group (Google Settings style), not one card per row.
5. **Immediate feedback** — switches flip instantly; toggles never need a save
   button.

**Audit outcome (P4):** Dashboard, Builder, and Capability Center fully
conform (list rows, grouped cards, segmented chips, immediate switches). The
two remaining radio-button pickers — the location-check interval dialog in
Settings and the quick-tile binding dialog in Widgets — were unified to the
Google single-choice row (content + trailing checkmark) via the shared
`CheckableRow` component, which keeps radio semantics for screen readers.

---

## 5. Launcher icon — Google 2026 gradient (this pass)

New adaptive icon (drawable-only, launcher-safe):

- **Background** `ic_launcher_background.xml`: linear gradient 135°, vivid
  blue `#4E6EF2` → violet `#7A5CF7` → `#9D5FF5` (the Gemini/2026 family),
  replacing the washed-out pastel `#C9D4FF → #F5EDFF`.
- **Foreground** `ic_launcher_foreground.xml`: bold **white** checkmark
  (rounded joins, 11dp stroke) + small white clock ring/hands — glyph in
  white on the vivid gradient, exactly the 2026 recipe.
- **Monochrome** `ic_launcher_monochrome.xml`: same silhouette in white
  (system-tinted by Material You).
- **Fallback** `values/colors.xml ic_launcher_background`: update to the new
  vivid mid-tone so legacy launchers match.

---

## 6. Phased roadmap (each phase lands green + detekt-clean)

| Phase | Scope | Files | Acceptance |
|---|---|---|---|
| **P0 — Tokens & icon (this pass)** | Expressive shapes, 2026 gradient icon, fallback color | `Theme.kt`, `ic_launcher_*`, `colors.xml` | assembleDebug green; icon renders (Robolectric test) |
| **P1 — Component sweep** | Replace any remaining hard-coded radii/colors across screens with tokens | `feature/*` audit via grep `RoundedCornerShape\|Color(0x` | zero non-token radii in UI code |
| **P2 — Navigation & bars** | `NexaFlowTopBar` scroll-tint (surface over content), pill FAB, NavigationBar density | `NexaFlowTopBar.kt`, dashboard/root scaffold | top bar tints on scroll; FAB pill |
| **P3 — Motion** ✅ | Spring entrance on FAB + staggered Keep-style cascade on task cards; springs documented from M3 Expressive spec (400/800 stiffness, ~0.8 damping); reduced-motion respected (ANIMATOR_DURATION_SCALE=0 skips all motion) | `NexaFlowMotion.kt`, `NexaFlowFloatingActionButton.kt`, `DashboardScreen.kt` | assembleDebug green; detekt clean |

> **P3 note (library reality):** the Compose BOM this project resolves ships
> material3 **1.4.0**, where `MotionScheme`/`MotionSchemeKeyTokens` are
> `internal` (the expressive motion API only became public in 1.5.0). Rather
> than a risky material3 upgrade, `NexaFlowMotion.kt` exposes the documented
> M3 Expressive spring values directly (`NexaFlowSprings`), giving the same
> motion language. M3 dialogs/sheets keep their own system animation; when a
> future material3 bump exposes `MotionScheme`, switch `NexaFlowSprings` to
> read the theme scheme — the call sites don't change.
| **P4 — Simplification pass** ✅ | Audited Dashboard/Builder/Capability Center/Settings/Widgets against §4; unified the two remaining radio-button pickers (location interval, tile binding) to the Google checkmark-row pattern via shared `CheckableRow` | `CheckableRow.kt`, `SettingsScreen.kt`, `WidgetsScreen.kt` | assembleDebug green; detekt clean |
| **P5 — Notifications & widget** ✅ | Colorized M3 notifications on all four builders (setColorized(true), API 31+ ignored below); channel colors verified equal to the theme primary (#0B57D0 day / #A8C7FA night = GoogleBlue seed); widget = QS tiles: card radius 24dp via NexaFlowCard → shapes.medium, tile icon/label synced to the bound task at runtime | `MonitoringService.kt`, `OemAutostartNotifier.kt`, `ReminderAlarmReceiver.kt`, `SystemController.kt` | module tests + detekt green |

---

## 7. Non-goals / guardrails

- **No behavior change** — this is a design pass; task execution logic,
  triggers, permissions, and integrations are untouched.
- **No new dependencies** — tokens and components only; the Compose BOM
  (2026.06) already ships the material3 APIs we need.
- **Accessibility preserved** — dynamic color, contrast roles, focus, and
  reduced-motion stay intact; RTL verified on previews.
- Every phase ends with `testDebugUnitTest` (affected modules) + `detekt`
  green before the next begins.
