# NexaFlow — Architecture Overview

> Companion to the root `README.md`. This document maps the modules and traces the end-to-end
> data flow of a task from configuration to execution, so contributors can navigate the codebase
> quickly. Last verified: August 2026.

## 1. Module Map

NexaFlow is a layered, multi-module Android project. Dependencies point strictly inward:

```
┌────────────────────────────────────────────────────────────┐
│  app  (theme, DI graph, navigation, MainActivity)          │
├────────────────────────────────────────────────────────────┤
│  feature/*  (dashboard, automation-builder, automations,   │
│              history, icons, themes, widgets, settings)    │
├────────────────────────────────────────────────────────────┤
│  data  (repositories + mappers)        core/capability-manager │
├────────────────────────────────────────────────────────────┤
│  domain  (models, scheduling logic, use cases, interfaces)  │
├────────────────────────────────────────────────────────────┤
│  core/database · core/datastore · core/automation-engine    │
│  core/execution · core/rom-integration · core/ui-components │
└────────────────────────────────────────────────────────────┘
```

| Module | Responsibility |
|---|---|
| `core/database` | Room database: `AutomationEntity`, `ExecutionRecordEntity`, DAOs, **explicit migrations** (`Migrations.kt`, versions 1→5), exported schemas in `core/database/schemas/`. |
| `core/datastore` | `ThemePreferences`, `NotificationPreferences` via DataStore. |
| `core/automation-engine` | Background engine: `MonitoringService` (FGS), `AutomationScheduler` (exact alarms), `AutomationAlarmReceiver`, per-trigger monitors (battery, connectivity, device event, location, SMS, Bluetooth), `AppTriggerAccessibilityService`. |
| `core/execution` | `ExecutionEngine` — executes actions + records history. |
| `core/capability-manager` | Permission/capability center used by Settings. |
| `core/rom-integration` | Shizuku / Root hooks for advanced actions. |
| `core/ui-components` | Shared Samsung-style Compose components (`NexaFlowCard`, `StatCard`, `SettingRow`, `StatusPill`, `SectionHeader`, `IconBadge`, `ToggleRow`, `NexaFlowTopBar`, `EmptyState`) with design previews. |
| `domain` | Pure Kotlin: `Automation`/`Trigger`/`Action` models, `TimeTriggerCalculator` (single source of truth for schedule math), repository & history interfaces, use cases. |
| `data` | Repository implementations + `AutomationMapper` (domain ⇄ entity). |
| `feature/*` | Compose screens bound to the domain via ViewModels (Hilt). |

## 2. Core Data Model

```kotlin
Automation(
  id, name, description, icon, iconColor, backgroundColor, category, priority, enabled,
  triggers: List<Trigger>,          // WHEN — any of these fires the task
  actions: List<Action>,            // THEN — executed on trigger
  exitActions: List<Action>,        // when the condition ends (if not reverting)
  revertOnExit: Boolean,            // restore pre-run device state instead
  createdAt, updatedAt
)

Trigger(type: TriggerType, config: Map<String, String>)
Action(type: ActionType, config: Map<String, String>)
```

`TriggerType`: `TIME, BATTERY, APPLICATION, DEVICE, CONNECTIVITY, LOCATION, SMS,
BLUETOOTH_DEVICE, RINGER_MODE`.

`ActionType`: ~50 actions — brightness, volume/streams, DND, rotation, open app(s),
notifications, Wi-Fi/Bluetooth/mobile data/hotspot/NFC/airplane, flashlight, media controls,
open URL, clear notifications, status bar, screen timeout, stay awake, auto brightness, ringer,
power saver, animations, lock screen, set alarm, dark mode, recents/home, app settings,
ring volume, location, Play updates, Galaxy Store, send SMS, reminder, open settings, wait,
battery alerts, charging notifications, Shizuku/Root.

### Time trigger configuration

```kotlin
config = {
  "timeMode"   -> "SINGLE" | "RANGE",
  "time"       -> "HH:mm",          // SINGLE mode
  "rangeStart" -> "HH:mm",          // RANGE mode
  "rangeEnd"   -> "HH:mm",          // may cross midnight (22:00 → 06:00)
  "repeat"     -> "ONCE" | "DAILY" | "WEEKDAYS" | "WEEKENDS" | "SPECIFIC_DAYS"
              |  "MONTHLY" | "SPECIFIC_DATE" | "DATE_RANGE",
  "days"       -> "1,3,5",          // SPECIFIC_DAYS (1=Mon … 7=Sun)
  "monthDay"   -> "15",             // MONTHLY
  "date"       -> "yyyy-MM-dd",     // SPECIFIC_DATE
  "startDate" / "endDate" -> "yyyy-MM-dd",  // DATE_RANGE
}
```

## 3. End-to-End Flow: Trigger → Scheduler → Engine → Execution → History

```
                    ┌────────────────────────────────────────────────────────┐
   user edits task  │  feature/automation-builder  →  AutomationBuilderScreen │
   (name, triggers, │        ↓  saveAutomation(...)                          │
    actions, exit)  │  feature/automation-builder  →  AutomationBuilderViewModel
                    └────────────────────────────────────────────────────────┘
                                          │ persists via data/ repository (Room)
                                          ▼
                              ┌──────────────────────────┐
                              │  core/database (Room)    │
                              └────────────┬─────────────┘
                                           │ observe (Flow)
                                           ▼
                              ┌──────────────────────────┐
        dashboard UI ◄────────│  feature/dashboard       │  (live "next run" via
                              └──────────────────────────┘   TimeTriggerCalculator)
                                           │ enabled + TIME trigger
                                           ▼
                              ┌──────────────────────────┐
                              │  core/automation-engine  │
                              │  AutomationScheduler     │  setExactAndAllowWhileIdle
                              │  AutomationAlarmReceiver │  RUN + END alarms
                              └────────────┬─────────────┘
                                           │ fire / state change (monitors)
                                           ▼
                              ┌──────────────────────────┐
                              │  core/execution          │
                              │  ExecutionEngine         │  executes actions, records
                              └────────────┬─────────────┘   ExecutionRecordEntity
                                           ▼
                              ┌──────────────────────────┐
                              │  feature/history         │  execution history screen
                              └──────────────────────────┘
```

### Step-by-step

1. **Build (feature/automation-builder)** — the single-page editor collects the task name,
   icon, triggers, actions and exit behavior. `AutomationBuilderViewModel.saveAutomation`
   validates (at least one trigger and one action) and persists via the repository.
2. **Persist (data + core/database)** — `AutomationRepository` maps domain `Automation` to
   `AutomationEntity` (JSON-encoded triggers/actions) and writes to Room. `Migrations.kt`
   guarantees upgrades never destroy data.
3. **Observe (feature/dashboard)** — `DashboardViewModel` combines the automation stream with
   the latest execution timestamps and exposes `AutomationRow`s; the home screen shows a
   natural-language summary and a "next run" preview computed by `TimeTriggerCalculator`.
4. **Schedule (core/automation-engine)** — `AutomationScheduler` registers exact alarms via
   `setExactAndAllowWhileIdle` (falling back gracefully when exact alarms are unavailable).
   For a `RANGE` time trigger it schedules **two** alarms: a START alarm (runs the task) and an
   END alarm (`windowEndMillis`, which handles overnight ranges and zero-length windows) that
   triggers the exit behavior.
5. **Fire (core/automation-engine)** — `AutomationAlarmReceiver` (and per-trigger monitors for
   battery/connectivity/app/location/etc.) wake the engine and run the task.
6. **Execute (core/execution)** — `ExecutionEngine.runAutomation` applies the `Action`s
   (guarding each with its permission requirements) and appends an `ExecutionRecordEntity`.
7. **Review (feature/history)** — the history screen renders past runs (success/message/time).

## 4. Scheduling Math — TimeTriggerCalculator

`domain/.../schedule/TimeTriggerCalculator.kt` is the **single source of truth** for:
- `nextFireTime(config, fromMillis)` — next occurrence honoring repeat mode, specific days,
  monthly day, specific date and date-range bounds (returns `null` once a one-shot / range ends).
- `windowEndMillis(config, windowStartMillis)` — when a RANGE window ends (supports overnight
  ranges; end==start is treated as a full 24 h window).
- `matchesRepeat(repeat, config, day)` — weekday/weekend/monthly/specific-day matching.

It is used by both the alarm scheduler and the dashboard's "next run" preview, and is fully
covered by unit tests in `domain/src/test/.../TimeTriggerCalculatorTest.kt`.

## 5. Background Strategy

- `MonitoringService` runs as a **foreground service** (`specialUse`) with `START_STICKY` and
  restarts on `onTaskRemoved`.
- Exact alarms require `SCHEDULE_EXACT_ALARM`; the scheduler checks `canScheduleExactAlarms()`
  and falls back to inexact scheduling when permission is missing.
- `POST_NOTIFICATIONS` is requested at first launch; battery-optimization exemption is offered
  from the permission manager (justified by the automation use case).

## 6. Quality Gates

- **Unit tests:** `testDebugUnitTest` — scheduler math, mappers, and **Room migration tests**
  (`core/database/src/test/.../MigrationTest.kt`) validating every migration against exported
  schema JSONs via Robolectric.
- **Lint:** `lintDebug` runs as an independent CI job (`.github/workflows/android-ci.yml`) and
  uploads the report on failure.
- **Compose previews:** screens and `core/ui-components` components ship `@Preview`s (light,
  dark, and RTL where meaningful).
- **CI:** every push runs lint + unit tests + `assembleDebug assembleRelease`; pushing a `v*`
  tag additionally creates a GitHub Release with the APK.
