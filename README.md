# NexaFlow

**Advanced Context-Aware Automation Engine for Android** — Samsung Modes & Routines style.

NexaFlow is a professional automation platform inspired by Samsung Modes & Routines and Tasker,
designed to provide a more powerful, customizable, and extensible automation experience.

## Features

- **Context-Aware Automations:** Create intelligent device automations using WHEN → THEN logic
  with 9 trigger types (time, battery, app, device event, connectivity, location, SMS,
  Bluetooth device, ringer mode) and ~50 system actions (sound, display, connectivity, media,
  notifications, apps, battery, advanced).
- **Time triggers with full scheduling:** single time, time range (incl. overnight 22:00→06:00),
  repeat options (once / daily / weekdays / weekends / specific days / monthly / specific date /
  date range), and an end-of-range alarm that fires exit/revert behavior automatically.
- **Exit behavior:** per-task "when this ends" section with either a revert-to-original-state
  switch or custom exit actions (mutually exclusive).
- **Two-way editing:** tasks are fully editable — the builder loads an existing automation and
  pre-fills name, icon, triggers, actions and exit behavior.
- **Searchable multi-select pickers:** add several triggers/actions at once from a searchable
  dialog; reorder actions with arrows.
- **Safe data migrations:** every schema change ships an explicit Room `Migration` (1→5) with
  exported schemas — upgrades never wipe user data.
- **Modular Architecture:** clean multi-module structure (core/domain/data/feature/app).
- **Modern UI:** Jetpack Compose + Material 3 in a Samsung One UI style, 10-language i18n,
  Compose design previews for screens and components.
- **Background reliability:** `specialUse` foreground service, `setExactAndAllowWhileIdle`
  scheduling with `SCHEDULE_EXACT_ALARM`, battery-optimization exemption request.
- **Monitoring & controls:** home-screen toggle/status widgets, run-now button, live "next run"
  preview, execution history.
- **Themes:** accent colors + dark mode persisted via DataStore.
- **Capabilities:** permission manager, Shizuku/Root integration via `rom-integration`.

## Technology Stack

- **Language:** Kotlin 2.4.x
- **UI:** Jetpack Compose + Material 3 (Samsung-style)
- **Architecture:** Clean Architecture, MVVM, Repository Pattern
- **Dependency Injection:** Hilt
- **Database:** Room (with exported schemas + explicit migrations)
- **Storage:** DataStore
- **Asynchronous:** Kotlin Coroutines + Flow
- **Background:** Foreground Services, AlarmManager (`setExactAndAllowWhileIdle`)
- **Navigation:** Navigation Compose
- **Minimum SDK:** Android 10 (API 29)

## Project Structure

```
.github/workflows/       CI: lint gate, unit tests, debug+release build, auto-release on tags
app/                     Application module (theme, DI, navigation)
core/
  database/              Room DB + entities + explicit migrations + schema exports
  datastore/             Theme & notification preferences (DataStore)
  automation-engine/     MonitoringService, scheduler, monitors, alarm receiver
  execution/             ExecutionEngine that runs triggers/actions
  capability-manager/    Permission/capability center
  rom-integration/       Shizuku / Root integrations
  ui-components/         Shared Samsung-style components (cards, rows, pills…)
domain/                  Pure models, scheduling logic (TimeTriggerCalculator), use cases
data/                    Repository implementation + mappers
feature/
  dashboard/             Home screen with routine cards + search + run now
  automation-builder/    Single-page task editor (name, triggers, actions, exit behavior)
  automations/           Task details screen
  history/               Execution history
  icons/ themes/ widgets/ settings/
```

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/Alaa91H/NexaFlow.git
   ```
2. Open the project in Android Studio (recent version with AGP 9.x / JDK 17).
3. Build and run on an Android 10+ device or emulator.

### Build commands

```bash
# Unit tests (includes Room migration tests + scheduler tests)
./gradlew testDebugUnitTest

# Assemble both variants
./gradlew assembleDebug assembleRelease

# Android Lint (quality gate)
./gradlew lintDebug

# Full local verification used before every release
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

## Releasing

Releases are fully automated via Git tags. Pushing a `v*` tag to `main` triggers GitHub Actions
which builds, runs tests/lint and creates a GitHub Release with the release APK attached.

```bash
git tag v3.2.0-alpha
git push origin v3.2.0-alpha
```

Pre-release tags (`alpha` / `beta` / `rc`) are published as pre-releases automatically.

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module map and the
  Trigger → Scheduler → Engine → Execution → History data flow.
- [`docs/PLUGIN_SDK.md`](docs/PLUGIN_SDK.md) — the Locale plugin protocol (EDIT_SETTING /
  FIRE_SETTING + Bundle JSON + Blurb) and the experimental `core/plugin-sdk` blueprint for
  third-party plugin developers.

## Contributing

We welcome contributions! Please see `CONTRIBUTING.md` for details.

## License

This project is licensed under the MIT License — see the `LICENSE` file for details.
