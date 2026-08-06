# NexaFlow — Enterprise Automation Framework: Phase 1 Architectural Report

> **Phase 1 deliverable** — تحليل المشروع الحالي وإنشاء التقرير المعماري.
> وفقاً لقواعد المشروع: لا ننتقل إلى أي مرحلة تالية قبل اعتماد هذا التقرير واجتياز اختبارات المرحلة.
> Last verified against the working tree: August 2026.

---

## 1. Executive Summary

NexaFlow is already a **multi-module, Clean-Architecture automation app** with a working
trigger → schedule → execute → history pipeline, a Room database with explicit migrations,
a foreground-service monitoring engine, per-trigger monitors, a ROM-detection layer, and a
Shizuku/Root privileged runner. It is **not yet a framework**: triggers and actions are hard-coded
`when` dispatch, there is no condition/variable layer, no plugin SDK, and the engine is coupled to
the specific feature set.

This report:
1. Verifies the **current state** module-by-module (facts, not assumptions).
2. Maps the **target enterprise architecture** onto the existing modules (max reuse).
3. Produces a **gap analysis** per pillar (engine, events, conditions, actions, variables,
   scheduler, plugins, compatibility, providers, security, testing).
4. Defines the **Phase 2→9 migration plan** with concrete, testable milestones.
5. Records key **technical decisions and risks** (including Android 8 min-SDK gap).

**Headline facts discovered during the audit:**
- `minSdk = 29` (Android 10) — the brief requires **Android 8 (API 26)**. This is a compatibility gap.
- `core/execution/ConditionEvaluator.kt` is **referenced but does not exist** — conditions were
  removed from the domain in an earlier refactor. The enterprise spec reinstates them as modules.
- The engine supports **sequential** execution only; parallel/branching/loops/variables are absent.
- `core/rom-integration` is already a solid base: 13 ROM families detected, 6 integration levels,
  ~40 `SystemController` actions with graceful privileged fallback.
- 6 test suites exist; zero UI/integration/performance tests. CI runs lint + unit tests +
  debug/release builds + auto-release on tags.

---

## 2. Current State — Verified Module Map

```
app  (theme, Hilt graph, navigation, MainActivity)          [compileSdk 37 / minSdk 29 / Kotlin 2.4.x]
├── feature/dashboard        Home: cards, search, run-now, live "next run" preview
├── feature/automation-builder  Single-page editor: name/icon, triggers, actions, exit behavior
│     ├── TriggerEditorCard / ActionConfigEditor / AppPickerDialog / PermissionExplainDialog
│     └── TriggerDraft / TriggerTypePickerDialog / ActionPickerDialog / ExitActionPickerDialog
├── feature/automations      Task details screen
├── feature/history          Execution history
├── feature/icons|themes|widgets|settings  (Quick-Settings tiles, permission & notification managers)
├── data                     RepositoryImpl + mappers + BackupManager (JSON export/import)
├── domain                   Pure models (Automation/Trigger/Action), TimeTriggerCalculator,
│                            BatteryTriggerMatcher, use cases, repository interfaces
├── core/automation-engine   MonitoringService (FGS specialUse, START_STICKY, restart on task-removed)
│     ├── AutomationScheduler (AlarmManager exact + fallback) / AutomationAlarmReceiver
│     └── Monitors: Battery, Connectivity, DeviceEvent, Location, Bluetooth, RingerMode,
│                   Calendar, NotificationTrigger, SmsReceiver + AppTriggerAccessibilityService
├── core/execution           ExecutionEngine (sequential actions, revertOnExit snapshots,
│                            exit actions, history record) — **no condition evaluator**
├── core/rom-integration     RomDetector (13 families) · IntegrationLevel (6) · SystemController
│                            (~40 actions, privileged fallback) · PrivilegedRunner (Shizuku+Root,
│                            reflection-based newProcess) · RomCapabilityProvider · SystemPropertyProvider
├── core/capability-manager  CapabilityCenterScreen + CapabilityGrantHelper (deep-link grants)
├── core/database            Room: AutomationEntity, ExecutionRecordEntity, DAOs, Migrations 1→5
├── core/datastore           ThemePreferences, NotificationPreferences (DataStore)
└── core/ui-components       Samsung-style shared Compose components + previews
```

**Engine data model (today):** `Automation(triggers: List<Trigger>, actions: List<Action>,
exitActions, revertOnExit)` — `Trigger.type` and `Action.type` are enums with `Map<String,String>`
config. 11 trigger types, ~50 action types.

**Test inventory (verified):**
| Suite | Covers |
|---|---|
| `MigrationTest` | Room migrations 1→5 vs exported schema |
| `TimeTriggerCalculatorTest` | schedule math: once/daily/weekdays/days/monthly/date-range/overnight |
| `BatteryTriggerMatcherTest` | battery level + charger type |
| `AutomationMapperTest` / `ExecutionRecordMapperTest` | domain ⇄ entity |
| `TileTargetResolverTest` | Quick-Settings tile binding |

---

## 3. Target Enterprise Architecture (mapped onto existing code)

The target keeps every existing module and **adds** framework modules. Dependency direction
remains strictly inward. New modules are shaded █.

```
app
core
 ├── common █            (existing code moved here: Utils, Result wrappers, coroutine scopes)
 ├── security █          (encryption, secure storage, permission validation, command sanitizer)
 ├── logging █           (ExecutionTimeline, ErrorLogs, PerformanceMetrics, crash recovery)
 ├── utilities █
 ├── configuration █
automation-engine         (existing core/automation-engine + core/execution refactored into:)
 ├── workflow-engine █    (sequential/parallel/branch/loops/functions/nested — NEW)
 ├── executor █           (action dispatch via plugin registry — replaces the big `when`)
 ├── scheduler            (existing AutomationScheduler + NEW cron/interval/cooldown/debounce)
 ├── state-manager █      (NEW: global state + revert/rollback generalization of DeviceStateSnapshot)
 ├── task-manager █       (NEW: execution queue, priority, retry, timeout, cancellation)
events                     (NEW module; wraps existing monitors as EventSources)
 ├── system-events        (boot/shutdown/screen/battery/charging/USB/bt/wifi/network/nfc/sensors/headset/volume/time/date/calendar)
 ├── application-events   (installed/removed/opened/closed/foreground — extends AppTriggerAccessibilityService)
 ├── accessibility-events █ (window/text/click/gesture — NEW)
 ├── sensor-events █
 ├── custom-events █      (plugin-defined)
conditions █              (NEW module: condition primitives as independent modules)
actions                   (existing actionOptions/ExecutionEngine dispatch → pluginized)
variables █               (NEW: local/global/runtime/persistent, JSON/arrays/objects/maps)
plugins █                 (NEW: internal SDK for events/conditions/actions/UI/providers)
storage                    (existing core/database + core/datastore)
permissions                (existing core/capability-manager)
compatibility █           (NEW: Provider selector engine)
custom-rom                 (existing core/rom-integration + NEW ROM Profiles & SELinux/kernel layer)
accessibility              (existing AppTriggerAccessibilityService → module)
shizuku / adb █ / root / lsposed █   (existing PrivilegedRunner + NEW ADB/LSPosed providers)
ui                          (existing feature/* + NEW Visual Workflow Editor)
testing █
```

**Reuse-first rule:** every existing class is either *moved* (same behavior, new package) or
*wrapped* behind an interface. No existing class is deleted without an audit trail.

---

## 4. Gap Analysis (per pillar)

| Pillar | Current state | Gap to enterprise | Effort |
|---|---|---|---|
| **Engine core** | `ExecutionEngine` sequential `when` dispatch | Parallel, branching, loops, nested workflows, functions, retry, timeout, cancellation, rollback, priority queue | High |
| **Events** | 9 hard-wired monitors + SMS/boot receivers | EventSource abstraction, app-lifecycle events, accessibility events, sensor events, custom/plugin events | Medium |
| **Conditions** | **None** (`ConditionEvaluator` missing) | If/Else/Switch/Equals/Contains/Regex/Greater/Less/Boolean/Var-Exists/Time/Date/Battery/Network/BT/Location/AppState/FileState as modules | High (new) |
| **Actions** | ~50 hard-coded actions | Plugin registry + SDK; input automation (tap/swipe/type/gesture), file ops, HTTP/webhook | Medium-High |
| **Variables** | None | Local/global/runtime/persistent, JSON/arrays/objects/maps, `${var}` interpolation in action configs | High (new) |
| **Scheduler** | Exact alarms (time + range) | Cron, interval, delay, cooldown, debounce, sunrise/sunset | Medium |
| **Plugins** | None | Internal SDK: register events/conditions/actions/UI/providers without core edits | High (new) |
| **Providers** | Shizuku + Root (via `PrivilegedRunner`) | Provider-selector engine (Android/Accessibility/Shizuku/ADB/Root/LSPosed) with automatic best-provider choice | High |
| **ADB** | None | Wireless debugging, ADB commands, secure execution | Medium |
| **LSPosed** | None (optional) | Detection + hooks API, zero hard dependency | Low-Medium |
| **ROM profiles** | ROM *family* detection (13) | Per-ROM constraint profiles (Samsung/Xiaomi/OnePlus/Graphene/AOSP/custom), SELinux, kernel, OEM battery killers | Medium |
| **Security** | None dedicated | Encryption, secure storage, command sanitization, plugin isolation | Medium-High |
| **Logging** | None (only history) | Execution timeline, error logs, perf metrics, crash recovery | Medium |
| **Backup** | JSON export/import ✅ | ZIP backup, version migration (JSON exists — extend) | Low |
| **UI editor** | Form-based builder | Visual node editor: drag&drop, zoom, pan, undo/redo, search, templates, validation | Very High |
| **Min SDK** | 29 (Android 10) | **26 (Android 8)** required by brief | Medium (API guards) |
| **Testing** | 6 unit suites | Integration, UI, performance, compatibility matrix (API 26→37, stock/custom/root/shizuku/none) | High |

---

## 5. Migration Plan (Phases 2→9) — with acceptance gates

Every phase ends with a **buildable + fully tested** milestone. No phase starts before the
previous phase's tests pass (`./gradlew testDebugUnitTest assembleDebug lintDebug`).

### Phase 2 — Rebuild Core Architecture
- Introduce `core:common` (scopes, `Result`-style outcomes, dispatchers — pulled from existing
  `di/ApplicationScope.kt`, `CoroutinesModule.kt`).
- Introduce `core:logging` (ExecutionTimeline + ErrorLog + PerformanceMetrics as DataStore/Room).
- Introduce `core:security` (EncryptedSharedPreferences-compatible store behind an interface,
  `SafeCommandBuilder` that quotes/escapes every shell argument).
- Refactor `ExecutionEngine` → `executor` with a sealed `ActionHandler` interface and a
  `ActionRegistry`; each existing action becomes a handler (no behavior change, just dispatch).
- **Gate:** existing 51 unit tests pass unchanged; new tests for `SafeCommandBuilder` +
  `ActionRegistry` resolution.

### Phase 3 — Automation Engine
- `task-manager`: execution queue (FIFO + priority), retry policy, timeout, cancellation,
  suspension on doze via `WorkManager` fallback.
- `workflow-engine`: new domain model `Workflow(node[])` supporting sequential + parallel +
  conditional branches + loops; `WorkflowInterpreter` executes it via `ActionRegistry`.
- `state-manager`: generalize `DeviceStateSnapshot` into reversible `StateTransaction`
  (capture → apply → rollback) used by both revert-on-exit and new branching.
- `scheduler`: add cron/interval/cooldown/debounce primitives in `domain/schedule` with unit tests
  (extend the existing `TimeTriggerCalculator` pattern).
- **Gate:** `WorkflowInterpreterTest` (seq/parallel/branch/loop/rollback), `TaskManagerTest`
  (priority/retry/timeout), scheduler primitives tests. Keep current behavior for legacy tasks.

### Phase 4 — Migrate existing features onto the new engine
- Legacy `Automation` model is translated into a `Workflow` (triggers→entry events, actions→nodes,
  exitActions→exit branch) via a **compatibility mapper** — zero UI change.
- Existing monitors become `EventSource`s feeding the event bus.
- **Gate:** all existing UI flows keep working; e2e smoke test: create → run → history.

### Phase 5 — Compatibility Layer
- `compatibility` module: `ExecutionProvider` interface + `ProviderSelector` that scores
  Android/Accessibility/Shizuku/ADB/Root by availability + capability + ROM profile, picks the best
  automatically, falls back gracefully (this generalizes today's `tryPrivileged` fallback chain).
- **Gate:** `ProviderSelectorTest` over a simulated device matrix.

### Phase 6 — Root / Shizuku / ADB / LSPosed
- Extract `PrivilegedRunner` into per-provider runners behind `ExecutionProvider`.
- Add `adb` provider (wireless debugging detection, `adb shell` over localhost pair port, secure
  token storage), `lsposed` provider (opt-in detection only).
- **Gate:** provider unit tests with fakes; Shizuku/root existing behavior preserved.

### Phase 7 — Custom ROM Framework
- `custom-rom` (extends `core/rom-integration`): `RomProfile` data class (execution strategy,
  restrictions, background problems, perf tweaks) + built-in profiles (AOSP/Samsung/Xiaomi/OnePlus/
  GrapheneOS/unknown) + SELinux mode detection + kernel detection + OEM battery-killer guidance.
- **Gate:** `RomProfileResolverTest` (detection across simulated build props + SELinux states).

### Phase 8 — Variables + Plugins + Visual Editor
- `variables`: typed store (local/global/persistent), `ExpressionEngine` for `${...}` interpolation,
  JSON/array/object/map values.
- `plugins`: `PluginRegistry` + `AutomationPlugin` interface (events/conditions/actions/UI/providers)
  + sample plugin. **No core edits required to add a plugin.**
- `ui/workflow-editor`: Compose node canvas (drag&drop, zoom, pan, undo/redo, validation,
  error highlighting). Reuses `core/ui-components`.
- **Gate:** `ExpressionEngineTest`, `PluginRegistryTest`, editor instrumented test (basic).

### Phase 9 — Testing & Performance
- Full matrix: unit + integration + Compose UI tests + performance (battery-drain, wake-lock,
  cold-start) + compatibility test harness run on API 26/29/33/37.
- Battery optimization: Doze/App-Standby/OEM-killer strategies per ROM profile; low-power monitor
  batching (register only active triggers — today all monitors always run).
- **Gate:** zero lint errors/warnings, all suites green on the matrix, documented perf budget.

---

## 6. Key Technical Decisions & Risks

1. **Android 8 support (API 26) — must address in Phase 2.** Current `minSdk 29`. Required work:
   - `setExactAndAllowWhileIdle`/`canScheduleExactAlarms` already guarded ✅
   - Notification channels, foreground-service types (`specialUse` is API 34+) → add API guards
   - `java.time` requires API 26+ — OK exactly at 26 ✅ (desugaring if we ever go lower)
   - Compose/AGP min-SDK constraints — verify Material3/Core KTX support at 26
   - **Decision:** add `core:compatibility` API-guard utilities in Phase 2 and set `minSdk 26`
     early so the whole test matrix runs from the start.

2. **Conditions were removed historically** — the enterprise spec reinstates them. Reuse
   `BatteryTriggerMatcher` / `TimeTriggerCalculator` as the first condition primitives rather than
   re-implementing.

3. **Plugin isolation without classloaders:** load plugins from the same module at first (internal
   SDK), then optional dynamic loading behind a `PluginLoader` interface (no hard dependency).
   Security: plugins run with the app's permissions only; command actions go through
   `SafeCommandBuilder`.

4. **No cloud / no AI / offline-first** — all storage local (Room/DataStore/encrypted files);
   HTTP actions exist but are user-initiated actions, never telemetry.

5. **Performance:** today all monitors initialize unconditionally. Phase 3+ adds **active-registry**
   filtering (only enabled triggers' sources are registered) and batch event delivery — this is the
   single biggest battery win and matches the brief's low-power requirement.

6. **Risks:** Visual workflow editor is the largest single effort (estimate dominates Phase 8);
   provider matrix testing requires devices/emulators — CI emulator job needed in Phase 9.

---

## 7. Success Criteria (Definition of Done — final)

The project is successful only when it is: a real automation framework; compatible with Android 17;
works on stock ROM; works on custom ROM; works without root; works with root; works with Shizuku;
extensible via plugins; stable; fast; low-power; easy to use; maintainable for years.

**Phase 1 exit criteria (this report):**
- [x] Verified current-state analysis (modules, engine, execution, ROM layer, tests, manifest)
- [x] Target architecture mapped onto existing modules (max reuse)
- [x] Gap analysis per pillar
- [x] Phase 2→9 plan with acceptance gates
- [x] Risks and decisions recorded

> **Next:** on approval, Phase 2 begins (Core Architecture rebuild) with its own test gate.
