# NexaFlow — Atomic UI/UX Audit Report & Migration Roadmap (2026)

> Phase 1 deliverable. Every judgment is based on the actual repository state
> (Compose BOM 2026-06-01, material3 1.4.0, navigation-compose 2.9.8, SDK 37,
> minSdk 26, targetSdk 37, edge-to-edge, dynamic color, Roboto Flex).

---

## 1. Current state summary

The app is already on the M3/M3-Expressive track after prior redesign rounds.
**Verified good** (keep, do not churn):

| Area | State | Verdict |
|---|---|---|
| Color | `Color.kt`: full M3 role set (primary/secondary/tertiary + containers, surface container 0→highest, inverse, outline, scrim) per accent seed; 6 Google seeds (blue/green/red/purple/amber/teal); `dynamicColor` (Android 12+, wallpaper-sourced) with `googleColorScheme` fallback; light+dark | ✅ Keep |
| Typography | `Typography.kt`: Google 2026 Roboto Flex variable font + Noto Sans Arabic fallback, exact M3 type scale (display 57→label 11) with correct weights (normal/medium), letter spacing, RTL-capable | ✅ Keep |
| Shapes | `Theme.kt`: M3 Expressive shape scale 8/12/16/24/28 dp via `MaterialTheme.shapes` | ✅ Keep |
| Motion | `NexaFlowMotion.kt`: spatial/effects springs (M3 Expressive), `NexaFlowAnimatedVisibility`, dialog enter, `isSystemReduceMotionEnabled()` | ✅ Keep |
| Navigation motion | `NexaFlowApp.kt`: directional spring NavHost transitions, reduce-motion fallback to crossfade | ✅ Keep |
| Edge-to-edge | `MainActivity`: `enableEdgeToEdge()` + transparent bars, icon appearance follows theme; Scaffolds handle insets | ✅ Keep |
| Predictive back | Manifest `android:enableOnBackInvokedCallback="true"`, nav-compose 2.9.8 (predictive-back-aware) | ✅ Keep |
| Components | `core/ui-components`: NexaFlowCard, TopBar, NavigationBar, FAB, EmptyState, IconBadge, StatusPill, SectionHeader, SettingRow, StatCard, CheckableRow | ✅ Keep (see migration) |
| RTL | Compose RTL-native (start/end, AutoMirrored icons used) | ✅ Keep |
| Deep links | `nexaflow://run-task/{id}` | ✅ Keep |

---

## 2. Findings (what is old / inconsistent / debt)

### F1 — No central spacing/dimension token system (HIGH)
Magic numbers everywhere in components: `12.dp`/`16.dp` paddings, `40.dp` row
icons, `56.dp` empty-state icon, `4.dp`/`8.dp` gaps. No `Dimens` object, no
`GridSize`. Violates M3 spacing token guidance (4dp grid).
**Action:** create `core/ui-components/…/Dimens.kt` with named tokens and
migrate the core components.

### F2 — No adaptive layout (Window Size Classes) (HIGH)
`NexaFlowApp` is phone-first: bottom `NavigationBar`, single-pane NavHost,
no `WindowSizeClass`, no NavigationRail, no list-detail/supporting-pane for
medium/expanded widths. Violates M3 adaptive guidance (compact/medium/expanded
by window size, not device).
**Action:** `calculateWindowSizeClass`, NavigationRail at expanded width,
content-width discipline; keep bottom bar on compact.

### F3 — Hard-coded brand colors inside components (MEDIUM)
`IconBadge` hard-codes `Color(0xFF0B57D0)` etc. in its **preview only** — the
component itself takes colors as params (good), but `StatusPill` preview and a
few screens (`ExecutionDetailsScreen`, `HistoryScreen`, `WidgetsScreen`,
`CapabilityCenterScreen`) embed raw `Color(0x…)` values that should route
through `colorScheme` roles or the accent palette. `ThemeScreen`/`IconPicker`
hex values are **intentional** (accent swatches / icon palette — keep).
**Action:** replace raw hex in status/execution screens with semantic roles
(success/error via container colors), keep genuine palette pickers.

### F4 — Inconsistent surface usage on non-card screens (MEDIUM)
Some screens place content directly on `background` while others use
`surfaceContainerLow`; section headers/rows aren't always wrapped in
`NexaFlowCard` with the same padding rhythm.
**Action:** standardize screen-level surfaces (background for scrolled
content, surfaceContainer for grouped settings blocks) in the components
layer so screens consume tokens instead of re-deciding.

### F5 — State layers not centralized (LOW)
No central hover/press/focus overlay helper — most interactions rely on M3
defaults (good) but custom rows (CheckableRow, SettingRow) re-derive
`clickable` + ripple manually.
**Action:** add a `Modifier.nexaFlowStateLayer()` (ripple + hover overlay)
in core/ui-components and use it in the rows.

### F6 — Loading/empty/error states partially ad hoc (MEDIUM)
`EmptyState` is central and good; but several screens hand-roll loading
(`if (loading) … else`) with no shared skeleton/error/retry component.
**Action:** add `LoadingState` + `ErrorState` (retry) primitives to
core/ui-components and adopt them where screens currently hand-roll.

### F7 — Tests: no UI verification for the new tokens (MEDIUM)
Components compile and unit tests pass, but no Robolectric/Compose-UI test
pins the token values or the adaptive layout (bottom bar vs rail switch).
**Action:** add compose-ui tests for Dimens tokens + rail/bottom-bar switch.

---

## 3. Target architecture

```
Foundation        Color.kt · Typography.kt · Theme.kt · Dimens.kt · MotionTokens
Primitives        IconBadge · StatusPill · SectionHeader · Spacer tokens
Components        NexaFlowCard · TopBar · NavigationBar/Rail · FAB · EmptyState
                  LoadingState · ErrorState · SettingRow · StatCard · CheckableRow
Patterns          ListDetail · SupportingPane · SettingsGroup
Screens           dashboard · builder · details · settings · … (consume tokens only)
```

**Token rule:** no component may hard-code a color, dp, or type role that
already has a token. Screens may only use `MaterialTheme.{colorScheme,
typography, shapes}` + `Dimens.*`.

---

## 4. Migration roadmap

| # | Legacy | New | Reason | Priority |
|---|---|---|---|---|
| 1 | Magic dp in core components | `Dimens.*` tokens (4dp grid) | One place to retune density; consistency | High |
| 2 | Phone-only bottom bar | `WindowSizeClass` + NavigationRail (expanded) | M3 adaptive; tablet/foldable/desktop | High |
| 3 | Raw hex in status/execution screens | `colorScheme` roles (success/error containers) | Dark/dynamic-color correctness | Medium |
| 4 | Hand-rolled loading/error | `LoadingState`/`ErrorState` primitives | Consistent UX + a11y (live regions) | Medium |
| 5 | Manual ripple in rows | `Modifier.nexaFlowStateLayer()` | Centralized state layers | Low |
| 6 | Ad hoc screen surfaces | `LazyListScope.settingsGroup()` (header+card) — DONE: main Settings screen | Visual hierarchy discipline | Medium |

**Do NOT change (functional reasons):** accent swatch hexes (palette picker
data), icon palette colors, engine/domain code, notification channel colors
already themed, widget RemoteViews (system-limited), deep-link behavior,
the FGS/monitoring service internals.

**Risks:** (1) token migration must keep every screen byte-equivalent where
the token equals today's literal value — verify by diff of rendered dp;
(2) rail/bottom-bar switch must preserve back-stack state (NavHost must not
recompose a new graph when the window class changes mid-session);
(3) RTL parity — all layout shifts use `start/end`.

**Verification per phase:** `assembleDebug` + `testDebugUnitTest` +
module detekt + string parity + compose-ui tests for new primitives and the
adaptive switch; manual pass on the connected device (LTR + RTL, light +
dark, compact + expanded).
