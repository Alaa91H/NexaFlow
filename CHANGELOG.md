# Changelog

## [Unreleased]

## [v3.82.0] - 2026-09-21

### Fixed

- **Wear OS sync no longer stays on "Connecting".** Two root causes closed:
  the phone now declares a Data Layer **capability** (`nexaflow.sync`) and
  re-pushes the automation snapshot whenever a wearable node **connects**
  (previously a single fire-and-forget `DataItem` push at process start was
  silently lost if the watch was not reachable at that moment); and the watch
  now **actively requests a sync** via the MessageClient pull-request protocol
  when the app is opened or resumed, instead of waiting for a push that may
  never come. Together the watch recovers in every order of events — watch
  opens first, phone restarts while watch is away, or a transient GMS failure.

### Added

- **ALL/ANY trigger matching (community request).** Automations with multiple
  triggers previously always fired when *any* trigger fired (implicit OR).
  A new per-automation `triggerMatch` policy — exposed in the builder as a
  selector above the trigger list — lets users require **ALL conditions to
  hold simultaneously**: e.g. enable DND only when *charging **AND** between
  22:00–07:00*. In ALL mode the engine verifies the remaining triggers' live
  state (via `TriggerStateEvaluator`) after the initiating trigger fires;
  event-only trigger types (notification, screen-off, package install/uninstall,
  boot, …) cannot be confirmed after the fact; when such a condition cannot be
  verified, ALL mode fails closed and the run is skipped rather than pretending
  a past event is current state. Serialized as an optional field — existing
  automations and backups keep
  their historical ANY behavior unchanged.

## [v3.81.0] - 2026-09-21

### Added

- **Package operations migrated to the semantic router (Phase B).** Force-stop
  (`APPLICATION_CLOSE_APP`, `SYSTEM_FORCE_STOP_APP`), clear-data
  (`SYSTEM_CLEAR_APP_DATA`), and package enable/disable (`SYSTEM_ENABLE_APP`,
  `SYSTEM_DISABLE_APP`) now execute as typed semantic operations
  (`PACKAGE_FORCE_STOP`, `PACKAGE_CLEAR_DATA`, `PACKAGE_SET_ENABLED_STATE`)
  through the Shizuku and Root typed strategies — closed `pm`/`am` argv over
  the UserService AIDL, never workflow-supplied shell text.
- **Real package-state read-back.** A new bounded `ReadPackageEnabledState`
  privileged operation (`pm list packages -d`, one deterministic output line)
  gives verification and UNKNOWN-reconciliation an actual post-condition read;
  the public-API strategy contributes `PackageManager` enabled-setting reads,
  so a Shizuku/Root-originated UNKNOWN can be settled through the Android API
  when available. Unexpected output shapes stay honest-null, never guesses.
- **Honest failure semantics.** A package dispatch that may have landed before
  a transport drop surfaces as UNKNOWN and reconciles by reading state instead
  of blind re-execution; `PACKAGE_CLEAR_DATA` is registered with
  `UNSUPPORTED` compensation (data destruction is irreversible) and HIGH risk.
- **Legacy-config compatibility.** The historical `package`/`packageName`
  config aliases resolve in the mapper; `SYSTEM_ENABLE_APP`/`SYSTEM_DISABLE_APP`
  carry their intent in the action type for pre-configVersion automations;
  unparseable explicit flags are rejected rather than defaulted.

### Changed

- `OperationRegistry` grows to 32 registered operations (28 state pairs plus
  the four package operations); parity gates extended with a package
  counterpart test, an honest-compensation assertion for clear-data, and the
  documented privileged-only exception for package writes.

## [v3.80.0] - 2026-09-21

### Added

- **Shizuku typed strategy — Phase B of the capability-adaptive migration.**
  `ShizukuTypedStrategy` routes semantic operations through the closed
  `PrivilegedOperation` algebra via `PrivilegedRunner.runShizukuOperation`
  (UserService AIDL, direct argv, never `sh -c`). Implemented operations:
  Wi-Fi / Bluetooth / airplane mode / NFC / mobile data / hotspot / DND state
  writes, plus bounded read-back for reconciliation and verification through
  the reviewed settings-read allowlist. The strategy is the only place a new
  Shizuku argv shape may be added — workflow input can never become a shell
  expression through this path.
- **Honest Shizuku readiness.** Availability distinguishes three states:
  not granted (permission required), granted but UserService not bound
  (GRANTED_NOT_BOUND — reported unavailable with a reconnect path, never
  executable), and ready. A granted permission alone is no longer treated as
  readiness for typed operations.
- **Real environment-event wiring.** `EnvironmentEventWiring` connects the
  semantic-layer `EnvironmentEventBus` to the actual Shizuku lifecycle
  listener: binder received, binder dead, and UserService
  connected/disconnected transitions publish targeted
  `ShizukuStateChanged` events. The `EnvironmentInvalidator` erases only
  Shizuku-backed evidence and health — root evidence survives a Shizuku
  binder death (targeted invalidation, never a full capability rescan).
  Wiring is idempotent and injected through a listener-registration seam so
  it is unit-testable without the Shizuku server.

### Changed

- Production DI now ships four semantic strategies (public Android API,
  Shizuku typed, root typed, Settings hand-off); the registry-parity gate
  updated accordingly. Router fallback and UNKNOWN-reconciliation semantics
  are unchanged and now exercised for the Shizuku path: a radio toggle that
  may have landed before a transport drop surfaces as `UNKNOWN` and
  reconciles by reading the actual state, never re-executes.

## [v3.79.0] - 2026-09-21

### Fixed

- **Task runs silently skipped — four root causes eliminated.** User-reported:
  many automations did not execute on a connected device. Static analysis and
  device logcat review traced every skip path to the admission layer, not the
  actions:
  - **Save-time snapshot race** — `saveAutomation` read the capability snapshot
    synchronously while the refresh triggered on screen entry was still in    flight, so the pre-scan answer classified runnable tasks as inadmissible    and saved them **disabled**. Admission is now decided on
    `CapabilityStateStore.freshSnapshot()`: request a refresh, wait (bounded,
    4 s budget) for an observation made at or after the request. Inside the
    store's 30 s minimum-refresh backoff the recent snapshot is returned
    immediately, so the save flow can never hang.
  - **Stale snapshot blocking runs** — the whole-run capability gate blocked
    any task whose snapshot was inadmissible, including snapshots observed
    hours earlier while the process sat in the background. A snapshot older
    than 60 s is now treated as not evidence about the device: the gate
    admits and every action path re-verifies the concrete capability live
    before its first side effect (diagnostic timeline entry
    `CAPABILITY_BLOCKED_STALE_SNAPSHOT`).
  - **Fresh-block learning** — a block on a genuinely fresh, observed
    unavailability now schedules a targeted capability refresh
    (`capabilitySnapshotInvalidator`), so a grant that lands right after a
    blocked run is seen by the next run instead of re-blocking on the same
    evidence forever.
  - **Grant visibility after failure** — when an action failed with "No
    elevated runtime", the engine only invalidated the root-probe cache,
    which the 5 s storm-spacing guard then silently swallowed; a grant that
    landed a second earlier stayed hidden through every "refresh".
    `SystemAppStatusDetector.refreshAndProbe()` now bypasses the spacing
    guard deliberately (documented: one extra `su` spawn is the price of
    never hiding a fresh grant), and the engine retries the action exactly
    once when the re-probe flips to granted — safe, because the previous run
    never reached the elevated runtime and no side effect can have started.

### Tests

- `CapabilityGateFreshnessTest` (4 tests): stale snapshot admits, startup
  race admits, fresh block records and schedules a refresh, fresh admissible
  runs.
- `SaveAdmissionFreshnessTest` (2 tests): `freshSnapshot` returns a
  post-refresh observation; respects the backoff window without hanging.
- `RefreshAndProbeTest` (3 tests): forced refresh bypasses the spacing guard,
  result is cached for ordinary callers, invalidate-only can never observe a
  grant. All tests seed a deterministic baseline so none depends on leftover
  probe state or wall-clock distance.
- Full suite: 816 unit tests across `core/execution`, `core/rom-integration`
  and `domain` — 0 failures, 0 skipped.

## [v3.78.0] - 2026-09-21

### Changed

- **Product-neutral codebase (vendor decoupling)** — NexaFlow no longer names
  commercial ROMs, OEMs or devices anywhere in its code, resources, UI or docs.
  The engine reasons about *capability tiers*, never products:
  - **`RomFamily`** redefined from 27 vendor-named entries to 8 neutral
    capability tiers (`CUSTOM_ROM_PRIVILEGED`, `CUSTOM_ROM_PRIVACY`,
    `OEM_SKIN_PRIVILEGED`, `OEM_SKIN`, `OEM_STOCK`, `STOCK_GOOGLE`, `AOSP`,
    `OTHER`) describing what a build can do, not what it is called.
  - **`RomDetectionMatrix`** keeps every detection fingerprint (version
    properties, brand constraints, manufacturer fallbacks) as protocol evidence
    in one reviewed table, now mapping to the neutral tiers.
  - **Files renamed**: `EvolutionXSettingsBridge` → `CustomSettingsBridge`,
    `EvolverCatalog` → `RomSettingCatalog`, `EvoActionHandler` →
    `RomSettingsActionHandler`, `EvolverSettingPickerDialog` →
    `RomSettingPickerDialog`; the vendor autostart deep-link resolver now picks
    the first vendor gate activity that actually resolves on the device.
  - **Serialized action-type names neutralized** with full backward
    compatibility: `EVO_*` actions are now `ROM_*`
    (`ROM_CUSTOM_SETTING`, `ROM_QS_TILES`, `ROM_STATUS_BAR`, `ROM_LOCKSCREEN`,
    `ROM_NAVIGATION`, `ROM_THEME`, `ROM_AMBIENT_AOD`, `ROM_NOTIFICATIONS`,
    `ROM_BATCH`) and `SYSTEM_OPEN_GALAXY_STORE` is now
    `SYSTEM_OPEN_DEVICE_STORE`. Legacy `@JsonNames` aliases keep every existing
    saved automation, backup and execution record readable.
  - **All user-facing strings** (11 languages) reworded neutrally — the custom
    ROM settings picker, the device-store action and the ROM-setting trigger no
    longer advertise any product; per-locale translations re-verified.
  - **Real device setting keys are preserved as protocol surface** (`evo_*`,
    `sysui_*`, `lineage_*` prefixes) so the picker and the ROM-setting monitor
    keep working against actual on-device settings providers.

### Added

- **Vendor-neutrality CI gate** (`scripts/check_vendor_neutrality.py`, wired
  into the lint job with a self-test): any commercial ROM/OEM/device name
  outside the protocol-allowlisted files fails CI, so vendor coupling cannot
  silently return.

## [v3.77.0] - 2026-09-20

### Added

- **Capability-Adaptive Execution layer (Phase A)** — a single semantic decision
  point for device-state operations, replacing per-handler privilege guessing:
  `OperationRegistry` declares typed `OperationSpec` contracts and
  `CapabilityRouter` selects the best available strategy per device.
  - **24 paired semantic operations** across connectivity, display and audio
    interruption (`WIFI_GET_STATE`/`WIFI_SET_STATE`, `BLUETOOTH_*`,
    `MOBILE_DATA_*`, `HOTSPOT_*`, `NFC_*`, `LOCATION_*`, `AIRPLANE_MODE_*`,
    `ROTATION_*`, `BRIGHTNESS_GET`/`BRIGHTNESS_SET`, `SCREEN_TIMEOUT_*`,
    `DND_*`, `DATA_SAVER_*`), each with typed parameter schemas, risk,
    idempotency, retry-safety, verification and compensation contracts.
  - **`CapabilityRouter`** picks the least-privileged available strategy using
    explainable candidates — live availability, verified per-device evidence,
    strategy health with bounded cooldowns, and explicit user policy — rather
    than static privilege scores. Root is never chosen merely because it is
    available.
  - **`CapabilityEvidenceStore`** and **`StrategyHealthTracker`** record
    verified successes, failures, latency and cooldowns per (operation,
    strategy, device fingerprint). `EnvironmentInvalidator` applies targeted
    invalidation from environment events: a Shizuku binder death invalidates
    only Shizuku evidence, never a full rescan.
  - **Honest `UNKNOWN` outcome**: a transport timeout after a possible side
    effect is reconciled by reading the actual device state through the
    operation's paired GET instead of being reported as a definite failure or
    retried blindly. Exactly one execution, then observation.
  - **Strategies shipped**: `AndroidApiStateStrategy` (public framework APIs
    only, with correct API-level guards), `RootTypedStrategy` (exclusively
    through two new closed `PrivilegedOperation` shapes, `SetServiceState` and
    `ReadSettingState` — no workflow-supplied shell text), and
    `SettingsUserActionStrategy` (truthful `PENDING_USER_ACTION`, never fake
    success). Shizuku typed, device-owner and OEM strategies are declared in
    the contract but not selected until implemented.
  - **Routing migration**: `SYSTEM_WIFI`, `SYSTEM_BLUETOOTH`, `SYSTEM_LOCATION`,
    `SYSTEM_AIRPLANE_MODE`, `SYSTEM_SCREEN_ROTATION`, `SYSTEM_BRIGHTNESS`,
    `SYSTEM_SCREEN_TIMEOUT`, `SYSTEM_DND`, `SYSTEM_NFC`, `SYSTEM_HOTSPOT`,
    `SYSTEM_MOBILE_DATA` and `SYSTEM_DATA_SAVER` now route through the unified
    path first and fall back to their reviewed legacy handlers when the
    operation is unsupported, so existing automations keep working unchanged.
  - **Registry parity gates**: every operation must name at least one shipped
    strategy, every write operation must have a readable counterpart, writes
    require verification, and no operation may be reachable only through
    privileged strategies.
- **Typed `PrivilegedOperation` additions** (append-only wire contract):
  `SetServiceState` (closed `svc` radio services) and `ReadSettingState`
  (allowlisted reconciliation reads) keep the AIDL boundary free of free-form
  shell input.
- **Documentation**: new [capability-adaptive-execution](
  docs/architecture/capability-adaptive-execution.md) contract; README,
  ARCHITECTURE and CAPABILITY_CATALOG updated to describe the unified decision
  path truthfully.

### Tests

- `CapabilityRouterTest` (11 cases): least-privilege preference, privileged
  opt-in gating, transport-only fallback, `UNKNOWN` reconciliation for both
  matching and contradicting read-back, evidence recording, health-cooldown
  deprioritization, unregistered-operation and missing-parameter rejection.
- `SemanticActionMapperTest` (7 cases): strict typed parsing — legacy toggles
  keep their documented default only for pre-`configVersion` automations,
  unparseable booleans are rejected instead of defaulted, and every migrated
  action type maps to its semantic operation.
- `OperationRegistryParityTest` (5 cases): the registry gates listed above.
- Full module suites pass: 807 unit tests across `domain`, `core:execution`
  and `core:rom-integration` with zero failures; Detekt, Lint and
  `assembleDebug` are green.

### Changed

- `CapabilityActionMapper` privileged-backend resolution and the legacy
  handler paths remain in place for unmigrated actions; `SystemController` is
  no longer on the execution path of migrated device-state operations.

## [v3.76.0] - 2026-09-20

### Added

- **Wear OS companion app** — a new `:wear` module delivers a native Wear OS 3
  watch companion that surfaces NexaFlow automations directly on the user's
  wrist, enabling monitoring and execution without reaching for the phone.
  - **Automation list screen** (`AutomationListScreen`): circular-display-optimised
    `ScalingLazyColumn` presenting every automation with its enable/disable state,
    last-run outcome badge, and a dedicated "Run Now" button per card. While a
    run command is in-flight the button is replaced by a `CircularProgressIndicator`
    so the user always knows the watch is acting.
  - **Automation detail screen** (`AutomationDetailScreen`): swipe-to-dismiss
    detail view showing the full automation name, a timestamped last-run result
    chip (success ✓ / failure ✗ with message excerpt), an enable/disable
    `ToggleButton`, and a prominent "Run Now" button.
  - **Navigation**: `SwipeDismissableNavHost` provides the standard Wear OS
    swipe-back gesture between the list and detail destinations.
  - **Sealed UI state model** (`WearUiState`): four states — `Connecting`
    (awaiting first sync), `Empty` (phone has no automations), `Loaded` (normal
    view), and `Running` (a manual run is in-flight) — drive the UI without
    intermediate booleans or nullable fields.
  - **Real-time data sync** via the Wearable Data Layer: `WearSyncRepository`
    holds a `StateFlow<List<WearAutomationDto>>` that is updated by the
    background `WearDataListenerService` on every `DATA_CHANGED` event without
    polling or explicit refresh.
  - **Watch → Phone command channel**: `WearDataLayerClient` discovers the
    nearest connected phone node via `NodeClient` and sends typed
    `MessageClient` messages. Run commands are encoded as the automation ID;
    toggle commands encode `"automationId:true/false"` over a
    `WearableListenerService` bridge.
  - **Hilt dependency injection** in the watch app: `WearModule` provides
    singleton `MessageClient` and `NodeClient` instances; `WearViewModel` is a
    standard `@HiltViewModel`; `WearDataListenerService` uses
    `@AndroidEntryPoint`.
  - **String resources** localised in all 11 supported locales:
    `en ar de es fr hi ja pt ru tr zh-rCN`.
  - **Companion APK bundled** via `wearApp(project(":wear"))` in `:app` so a
    single Play Store install delivers both the phone and watch APKs.

- **Phone-side Wear OS bridge** (in `:app`):
  - `WearSyncManager`: subscribes to `AutomationRepository` and
    `HistoryRepository` via a `combine` flow, debounces rapid saves by 500 ms
    to coalesce bulk operations, serialises the result to `WearAutomationDto`
    JSON, and pushes it as an urgent DataItem to all connected watches. A
    monotonic `updatedAt` timestamp forces a `DATA_CHANGED` delivery even when
    the automation list is unchanged, guaranteeing state convergence after a
    watch reconnect.
  - `WearCommandListenerService`: a `WearableListenerService` declared in the
    phone manifest with a `MESSAGE_RECEIVED` filter scoped to `/nexaflow/`
    paths. Uses `EntryPointAccessors` (instead of `@AndroidEntryPoint`) for safe
    Hilt injection in a platform-managed service. Routes run commands to
    `ExecutionEngine.forceRun()` and toggle commands to
    `AutomationRepository.updateAutomationStatus()` followed by
    `ExecutionEngine.notifyAutomationsChanged()` so stateful monitors react
    immediately.
  - `WearSyncManager` is started in `NexaFlowApplication.onCreate()` inside the
    existing best-effort startup block; failures are caught and logged without
    impacting any other startup component.

- **Shared Wear OS protocol constants** added to `AutomationIntents.kt`
  (`WEAR_PATH_AUTOMATIONS`, `WEAR_PATH_RUN_COMMAND`, `WEAR_PATH_TOGGLE_COMMAND`,
  `WEAR_KEY_PAYLOAD`, `WEAR_KEY_UPDATED_AT`, `WEAR_TOGGLE_SEPARATOR`) as the
  single authoritative source for the Data Layer communication contract on the
  phone side, mirrored by `WearProtocol` in the watch module.

- **New Gradle dependencies** (`libs.versions.toml`):
  - `com.google.android.gms:play-services-wearable:19.0.0` — Data Layer API
    (both modules).
  - `androidx.wear.compose:compose-material3:1.5.0-alpha26` — Wear OS
    Material 3 component library.
  - `androidx.wear.compose:compose-foundation:1.5.0-alpha26` — `ScalingLazyColumn`
    and watch-optimised layout primitives.
  - `androidx.wear.compose:compose-navigation:1.5.0-alpha26` —
    `SwipeDismissableNavHost`.
  - `org.mockito.kotlin:mockito-kotlin:5.4.0` (test) — mock support for
    `WearDataLayerClient` in unit tests.
  - `app.cash.turbine:turbine:1.2.0` (test) — `StateFlow` assertion helpers.

### Tests

- `WearAutomationDtoSerializationTest` (7 cases): round-trip JSON
  serialisation, null optional fields, `ignoreUnknownKeys` forward
  compatibility, malformed JSON graceful degradation, empty-array handling,
  state replacement on successive calls, and multi-DTO parsing.
- `WearViewModelTest` (5 cases): initial `Connecting` state, `Loaded`
  transition after first sync, `Empty` state on empty push, `runNow`
  command delegation verification, `toggleEnabled` command delegation
  verification. Uses `StandardTestDispatcher` for deterministic coroutine
  control.
- `WearSyncManagerDtoTest` (4 cases): phone-side DTO round-trip
  serialisation, nullable-field encoding, empty-list serialisation, and
  `lastRunMessage` truncation boundary.
- `WearCommandProtocolTest` (8 cases): toggle payload encoding for `true` and
  `false`, separator parsing, invalid-boolean rejection, missing-separator
  detection, and protocol path constant verification.

### Validation

- Zero string parity problems across all 11 locales verified via
  `check_strings_parity.py` (covers both `:app` and `:wear` resource trees).
- Zero hardcoded non-English strings in shipped Kotlin sources verified via
  `check_hardcoded_text.py` (425 sources clean).
- Builder catalog parity verified clean (`CATALOG_PARITY: OK — 56 triggers,
  176 actions`) via `audit_catalog_and_releases.py catalog`. No new trigger or
  action enum values were added in this release.
- All 11 locale `values-*/strings.xml` files created for the `:wear` module
  with exact key parity against `values/strings.xml`.

## [v3.75.1] - 2026-09-20

### Added

- Seamless multi-tier capability failover: extended `CapabilityRuntime` with `candidateBackends` in `CapabilityResolution` and automatic backend failover in `CapabilityExecutionService`. If the primary selected backend encounters an operational or transport failure (`SHIZUKU_UNAVAILABLE`, `ROOT_UNAVAILABLE`, `BACKEND_UNAVAILABLE`, or execution `TIMEOUT`), the execution pipeline automatically promotes execution to the next available candidate tier (such as Root or Android Framework fallback) instead of aborting the routine.
- Deterministic boolean condition expression evaluator: introduced `ConditionExpressionEvaluator` providing safe, bounded expression evaluation for dynamic workflow routing. Supports logical operators (`&&`, `||`, `!`, `AND`, `OR`, `NOT`), value comparisons (`==`, `!=`, `<`, `<=`, `>`, `>=`), text pattern matching (`contains`, `startsWith`, `endsWith`, `matches`), and inspection helpers (`isEmpty`, `isNotEmpty`) with strict syntax validation and recursion depth limits.
- Per-action flow control and resilience in `ExecutionEngine`: added conditional action gating via the `"condition"` configuration parameter, automatic retries with configurable `"retryCount"` and `"retryDelayMs"` to gracefully absorb transient failures, and granular failure handling policies via `"onError"` (`"ABORT"` or `"CONTINUE"`).

### Tests

- Comprehensive unit tests in `ConditionExpressionEvaluatorTest` covering arithmetic comparisons, string operators, nested boolean expressions, malformed expressions, and safety bounds.
- New test suite in `ExecutionEngineControlFlowTest` verifying conditional execution gating, retry backoff convergence, retry exhaustion handling, and error abort vs. continue policy enforcement.
- End-to-end failover test in `CapabilityRuntimeTest` validating runtime promotion from Shizuku to Root upon transport disruption.

### Validation

- Full unit test verification across all modules (`:domain:testDebugUnitTest`, `:core:execution:testDebugUnitTest`).
- Strict resource hygiene verified clean via `auto_fix.py --check`.
- Zero missing or orphaned localization keys across all 11 locales via `check_strings_parity.py`.
- Zero hardcoded Arabic script in shipped code via `check_hardcoded_text.py --self-test`.
- Builder catalog parity verified with 0 problems via `audit_catalog_and_releases.py catalog`.

## [v3.75.0] - 2026-09-20

### Added

- Truthful capability execution architecture: introduced a dedicated `VerificationEngine` with exponential retry backoff (configurable up to 1,500ms max elapsed time) to strictly confirm actual device state transitions before marking actions successful.
- Live settings read-back verification: implemented namespace/key read-back via `settings get <namespace> <key>` in both Shizuku and Root backends to verify that system setting writes take effect on the device.
- Process lifecycle postcondition verification: implemented running process state inspection (`isPackageRunning`) for app force-stop capabilities to verify that processes actually terminate after execution.
- Privileged package clear data capability: added `ClearPackageData` (`PACKAGE_CLEAR_DATA` / `pm clear <package>`) to the privileged operation algebra, wire format serializer, and capability catalog.
- Action-to-capability execution bridge: expanded `CapabilityActionMapper` and connected `AppActionsHandler` and `SystemActionsHandler` to seamlessly dispatch `SYSTEM_FORCE_STOP_APP`, `APPLICATION_CLOSE_APP`, `SYSTEM_CLEAR_APP_DATA`, `SYSTEM_SET_SETTING`, and system settings shortcuts (`WIFI`, `BLUETOOTH`, `LOCATION`, `SOUND`, `DISPLAY`, `BATTERY`) through `CapabilityExecutionService` with `VerificationMode.REQUIRED`.

### Fixed

- Modern Compose UI testing deprecation: migrated `RoutineCardHeaderTest` from deprecated `createComposeRule` to `androidx.compose.ui.test.junit4.v2.createComposeRule` avoiding compiler warnings under `-Werror`.
- Fixed timing race condition in `TaskManagerTest`'s deadline expiration queue test using atomic clock progression.
- Eliminated unused expression warning in `ActiveExecutionStore.kt`.

### Tests

- Added comprehensive test suites: `VerificationEngineTest` (covering immediate match, retry backoff convergence, mismatch failure retention, and timeout), `CapabilityActionMapperTest` (covering all new mappings, parameters, and verification requirements), `PrivilegedCapabilityBackendsTest` (covering postcondition verification for settings and force-stop), and `CapabilityRuntimeTest` (covering end-to-end execution and verification pipeline).

### Validation

- Full 18-module unit test suite clean pass (`./gradlew testDebugUnitTest`).
- Catalog parity (`scripts/audit_catalog_and_releases.py catalog`) verified with 0 problems.
- String parity across all 11 locales verified with 0 missing/orphaned keys.
- CI hardcoded-text scanner (`scripts/check_hardcoded_text.py`) verified clean.

## [v3.74.7] - 2026-09-19

### Fixed

- Builder options missing on phones (issue #5): options whose only blocker is a user-grantable capability (Do-not-disturb access, Write settings) no longer disappear from the trigger and action pickers. The compatibility engine now distinguishes "permission required" from "unsupported" for grantable capabilities, and the builder renders those rows as locked entries that route into the existing grant flow instead of hiding them.
- Locked rows unlock immediately after granting. ROM capability probing is no longer frozen at process start: the integration manager re-reads integration level and permission state on every resume, so returning from the system settings screen flips a granted row to ready without restarting the app.
- Two builder strings ("extra actions when done" section and its description) were hardcoded Arabic regardless of locale. They now use localized string resources added to all 11 locales (issue #4).
- Elevated execution no longer dead-ends on a broken Shizuku transport when Root is granted. The multi-route runner tries every granted transport in order (Shizuku first, Root fallback); a transport-level failure (service dropped, UserService gone) retries the next channel, while genuine command failures still surface unchanged. This removes the class of silently skipped privileged operations on rooted devices without a working Shizuku.
- The Root path for the Do-not-disturb action used a `cmd notification set_interruption_filter` subcommand that does not exist in AOSP, so DND via Root failed every time. Both elevated paths now use the real `cmd notification set_dnd on|off` wire format.

### Changed

- The ad-hoc Arabic-in-Kotlin scanner used to find the issue #4 strings is now a permanent CI gate (`scripts/check_hardcoded_text.py`): it strips comments and fails the lint job when Arabic script survives in any shipped Kotlin/Java source. Legitimate occurrences (the native language name in the settings picker) live in a reviewed, anchor-verified allow-list that fails when it drifts. A self-test re-injects the exact v3.74.6 leak strings, and a dedicated unit-test suite pins the scanner, comment handling, allow-list rotation and wire format.

### Tests

- New Robolectric Compose UI suite (`CatalogOptionRowAvailabilityTest`) pins the three picker row states — ready (toggles the option), permission-required (locked, "Tap to grant", routes to the grant flow, never toggles), and unsupported (visible, unavailable message, not grantable) — for both the action and trigger pickers, so the issue #5 discovery regression cannot silently return.
- New `PrivilegedRunnerRoutesTest` regression suite pins the granted-channel matrix: Root-only execution, Shizuku-transport failure falling back to Root, Shizuku-first preference on healthy transports, real-failure passthrough, and the stable no-transport message the engine classifies.
- Compatibility-engine tests extended to cover the grantable-capability pending-permission mapping.

### Validation

- Unit tests for the execution, builder and ROM-integration modules pass; Detekt and Android Lint pass; string-parity and auto-fix resource gates pass.

## [v3.74.6] - 2026-09-18

### Fixed

- Restored the module boundary for the recovery-backlog reset by exposing the operation through `ExecutionEngine`. This removes the direct UI-to-DataStore dependency that prevented the v3.74.5 Android build from compiling.

### Validation

- Re-ran the affected Kotlin compilation path after the dependency-boundary repair.

## [v3.74.5] - 2026-09-18

### Fixed

- Replaced the removed hidden `WifiManager#setWifiApEnabled` reflection path with a reviewed, typed WifiShell Soft AP operation. Shizuku and Root now use `cmd wifi start-softap` or `cmd wifi stop-softap`, so modern Android devices no longer fail before an elevated hotspot request is attempted.
- Added an explicit, per-routine recovery-backlog reset. It removes only recovery records already marked as requiring manual review after a user confirmation; it never replays an uncertain action or marks it successful. A stale backlog can no longer permanently prevent later scheduled runs of that routine from being admitted.

### Validation

- Added durable-store coverage proving a per-routine recovery reset preserves other routines and non-recovery checkpoints.
- Added privileged-operation coverage for the typed hotspot wire format and modern WifiShell argv.

## [v3.74.4] - 2026-09-18

### Fixed

- Prevent repeated active-data-subscription notifications from recursively re-registering telephony listeners. Late Binder deliveries after executor shutdown no longer throw rejected-execution exceptions.

- Recovery admission deferrals retain an attention warning without inflating failure counts. Ordinary skipped runs no longer hide a pending recovery warning; later admitted execution supersedes it.
- History paging consistently places legacy recovery-capacity deferrals in the skipped filter and excludes them from failure filtering.
- History cards use localized execution summaries and correctly label legacy recovery deferrals as skipped.

### Validation

- Passed domain unit tests, execution-history database regression tests, and Kotlin compilation for automation details and history. Physical-device diagnosis remains in progress.

### Changed

- Removed the post-CI automatic release workflow. Tags and GitHub releases are now initiated explicitly, while tag builds retain their signing and package verification gates.
- Dependency checks for Gradle libraries and GitHub Actions now run daily. Verified Dependabot pull requests are squash-merged automatically after every pull-request check succeeds.

## [v3.74.3] - 2026-09-18

### Fixed

- Recovery-queue admission deferrals are now safe skipped runs rather than failures. Repeated state-trigger evaluations emit at most one diagnostic per routine and admission reason every five minutes, preventing history floods while preserving interrupted checkpoints for review.
- Existing persisted "recovery queue is full" entries are reclassified as skipped when health is calculated, so affected routines no longer remain stuck at a false failure count after upgrading. The recovery status is presented through localized UI text.
- Backend-specific Root and Shizuku commands now require their exact backend; a generic elevated shell no longer enables the other provider's command.
- Device compatibility capture re-reads capabilities and requires a connected Shizuku UserService before advertising an executable Shizuku route. Builder discovery refreshes on resume after permission changes.
- Devices exposing only supported numeric sensors, such as pressure, retain the sensor trigger in discovery.

### Documentation

- Added an implementation plan with staged acceptance gates and explicit outstanding work. This increment does not complete the competitive expansion roadmap.

## [v3.74.2] - 2026-09-17

### Fixed

- Root and Shizuku availability are now evaluated independently in the ROM capability provider. On devices where both are approved, the builder can expose both elevated execution routes instead of hiding Root because Shizuku was detected first.

### Changed

- Expanded the configuration and installation documentation to describe capability-aware discovery, locked grantable options, and the difference between a catalog entry, backend availability, and verified execution.

## [v3.74.1] - 2026-09-17

### Fixed

- Network-mode changes through Root or Shizuku now use a bounded read-back retry. Some modem implementations acknowledge the `cmd phone` write before exposing the new allowed-network-types mask; NexaFlow waits briefly and confirms the final state before recording a failure.
- Durable execution admission now records a precise, actionable outcome. Duplicate event delivery is identified as an already-admitted run, while a full recovery ledger is reported as a deferred run that requires recovery resolution. Interrupted work is never silently evicted to make room.

### Validation

- Added a regression test proving that duplicate durable admissions preserve the original checkpoint and expose the collision reason.

## [v3.74.0] - 2026-09-17

### Added

- Eight production data action families: text transformations, Base64/URL/hex encoding, SHA-256/SHA-512 hashing, secure random values, decimal arithmetic, date/time processing, JSON Pointer operations and array processing. Each has an editor, registry dispatch, bounded inputs and workflow-context output.
- Eight numeric sensor modes: pressure, ambient temperature, relative humidity, magnetic field, linear acceleration, gyroscope, gravity and hinge angle. Conditions support strict/inclusive thresholds and ranges, with device hardware checks.
- Custom HTTP request headers, HEAD/OPTIONS methods and explicit content-type configuration. User headers cannot override framing and are removed across origin redirects.
- Current architecture, configuration, security, validation and release guides, plus a reproducible catalog containing 56 trigger entries (54 generally exposed) and 176 action entries.

### Fixed

- Import streams now use API-26-compatible bounded reads. Full and single-task imports enforce byte, lexical and typed-model quotas before persistence and remain disabled for review.
- Webhook request/header limits apply during byte reads, preventing unbounded line allocation. Empty/oversized tokens cannot authorize execution; request deadlines, concurrency and rate limits are enforced.
- Task details now wires external access enablement, rotation, revocation, token-bearing sharing and legacy webhook token repair. Tokenless links open review; valid custom-scheme links require confirmation and a fresh authorization check.
- Portable serialization omits task-link capabilities and clears webhook tokens. Shared files cannot transfer external execution authorization.
- HTTP destination checks fail closed on unresolved or mixed public/private DNS answers. The transport pins validated addresses, checks each redirect and enforces HTTPS consistently. Private-network permission follows the actual explicit configuration.
- Oversized HTTP responses fail with an empty body and truncation metadata rather than silently passing partial JSON to later actions.
- Elevated failure warnings omit dynamic configuration and result messages. Release auditing checks production and merged manifests against exact permission gates.
- DataStore regression tests now use an isolated atomic Preferences store, avoiding Robolectric/Windows file-rename failures while preserving the store transition contracts under test.

### Changed

- HTTP-only endpoints must migrate to HTTPS, including private-network endpoints. Legacy empty-token webhooks require token regeneration; imported/shared tasks require local capability enablement after review.
- Tag-release publication waits for the lint job as well as the build/package gates. English release notes are derived from this versioned changelog section.
- Historical plans and audits are explicitly distinguished from the current project guides; competitive claims and device coverage are bounded by available evidence.

### Known limitations

- This release does not establish overall superiority or full feature parity with Automate. Catalog counts use different units from Automate blocks.
- New configuration copy has English fallback in several locales. Key parity is not complete translation.
- Physical-device/OEM background reliability, new sensor hardware behavior and framework-permission instrumentation results remain separate validation requirements. Original exported schema snapshots 2 and 13 remain unavailable.

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v3.73.0] - 2026-09-16

### Security

- **Elevated-action failures no longer leak task configuration into logcat.** The engine logged the raw
  `action.config` map (commands, package names, and any secret-bearing values) on elevated execution
  failures, bypassing the app-wide redacting log layer. Failures now log only the action type, error
  class, capability backend, and execution id; a dedicated diagnostics helper captures config *key
  names* (never values) when troubleshooting needs them, and a regression test injects a fake secret
  and asserts it never appears in any log sink.
- **Deep links can no longer run tasks by guessing an id.** `nexaflow://run-task/{id}` was registered
  browsable and executed the task directly — any app on the device could fire a task without consent
  because the scheme is not ownership-verified. External deep links now require a per-task opt-in:
  run-by-link is off by default, enabling it mints a 128-bit cryptographically random capability token
  (rotatable and revocable per task), and execution proceeds only on a constant-time token match.
  Without the token the link opens the task instead of running it. Tokens are stripped from imported
  backups so a shared file cannot silently grant execution rights.
- **Webhook server hardened against local denial-of-service and token leakage.** The token is now
  mandatory and accepted only in a header (query-string tokens are rejected — they leak into logs and
  diagnostics), compared in constant time. Request parsing enforces a maximum request line and header
  size/count, sockets get a read timeout, concurrent clients are capped by a semaphore, only the
  intended method is accepted, and the URL path is percent-decoded and canonicalized before matching.
  Rejections are counted by category (auth, oversize, timeout, rate limit) without ever logging the
  token itself.
- **HTTP actions are gated against SSRF and memory abuse.** A destination policy allows only
  `http`/`https`, rejects embedded credentials in URLs, classifies the target (public / loopback /
  link-local / private-LAN / multicast / any-local), and denies multicast, any-local, and — unless the
  task explicitly opts in — private destinations. Redirects are followed manually with a cap of five
  hops and the same policy re-applied to every target, POST→GET downgrade semantics respected.
  Response bodies are capped by an enforced byte limit before entering the workflow context, so a
  hostile server can no longer balloon memory.
- **`ACCESS_LOCAL_NETWORK` is now requested only when the URL actually needs it.** The Android 17
  local-network runtime permission was required for every HTTP action regardless of destination.
  The requirement is now inferred from the configured URL: public destinations need no permission,
  private/loopback destinations require it, and templated (`{variable}`) or host-name URLs are
  surfaced as conditional with the runtime re-checking the resolved destination before the request
  fires. This shrinks the permission surface and the Play declaration scope.

### Added

- **Exported-components threat-model audit.** A CI test enumerates every `exported="true"` component
  across all manifests and fails the build unless each one is protected by a system permission gate or
  listed in a reviewed allowlist with a documented justification — and stale allowlist entries (a
  component no longer exported) also fail. Bind-permission gates for SMS, screening, Shizuku, and
  QuickSettings components are asserted explicitly.

### Tests

- Webhook request guard: 12 tests (query-token rejection, size/line bounds, method policy,
  constant-time comparison, canonicalization).
- HTTP destination policy: 14 tests (scheme/credential/classification rules, redirect resolution and
  downgrade semantics, private-network opt-in, unresolvable-host modes).
- Conditional LAN requirement: 11 tests (literal/templated/host-name classification, runtime re-check,
  fail-closed behavior) plus 5 builder-side requirement-inference tests.
- Exported-components audit: 2 tests (allowlist + permission gates).
- Deep-link authorization: token match, rotation, and rejection cases.
- Import quotas: oversize config and element-count rejection.
- Config-logging redaction regression for elevated failures.

## [v3.72.0] - 2026-09-15

### Fixed

- **Shizuku elevated execution is now actually wired.** The Shizuku UserService was written as an Android
  `Service` returning the AIDL stub from `onBind` — but Shizuku's service starter reflectively instantiates
  the class inside its own privileged process and casts the instance itself to `IBinder`, so every bind
  failed with `ClassCastException: UserShellService cannot be cast to android.os.IBinder` and every elevated
  operation reported "Shizuku UserService is unavailable" even with the permission granted. The class now
  extends `IUserShellService.Stub` directly (the correct UserService contract), the meaningless manifest
  service declaration is removed, and the bridge pins a UserService `version` so the daemon restarts and
  serves fresh code after app updates instead of a stale cached instance. Verified on-device: service
  record registered (`version=2, daemon=true`), binder delivered, and a dedicated `:shell`-uid process held
  by the Shizuku server.

## [v3.71.0] - 2026-09-15

### Fixed

- **"Run now" no longer reports "could not verify" for device and app triggers.** The manual-run admission
  gate previously failed closed for `DEVICE` (Bluetooth events), `BLUETOOTH_DEVICE`, and `APPLICATION`
  triggers: without a dedicated evaluator these conditions fell through to the catch-all, so tapping run on a
  task waiting for a Bluetooth headset to disconnect — or for a specific app to be in the foreground — was
  always blocked with "some triggers could not be verified" even when the platform state was fully readable.
  All three trigger types now evaluate current, verifiable state with the same semantics as the live
  monitors: Bluetooth by bonded-device link state (with radio-off treated as an immediate disconnection,
  exactly like `BluetoothMonitor`; ANY-device matching supported; GATT read plus a classic-profile fallback
  for audio-only devices), and app triggers through the accessibility tracker's live foreground package with
  a usage-stats fallback. Unreadable state (missing `BLUETOOTH_CONNECT`, no usage access, dead service)
  still reports Unknown — a manual run is never authorized by a guess — but verified false now blocks with a
  precise trigger label instead of an unverifiable one.
- **Volume and ringer actions keep working after a reboot on Android 17.** Background-audio hardening
  silently discards volume and ringer writes unless the calling foreground service holds while-in-use (WIU)
  capability, and WIU is only granted when the service is started while the app is visible — a monitoring
  service started from the boot alarm never has it, so every scheduled volume/ringer change would fail
  after a reboot until the phone was reconnected to a fresh app process. Opening the app now refreshes the
  running monitoring service (stop + immediate restart from a visible context), granting WIU for the
  service's remaining lifetime. The refresh is idle when the service is not running or the platform
  predates the hardening, so the common open/close cycle costs nothing.

### Tests

- Added evaluator coverage for the merged device-event decision, the Bluetooth link-state decision table
  (mirroring `BluetoothMonitor` semantics for both event polarities), the app-foreground decision, and the
  manual-gate classification (radio-off connect-wait → Unsatisfied, radio-off disconnect-wait → Satisfied,
  missing bonded device → Unsatisfied, unreadable foreground → Unknown).

## [v3.70.0] - 2026-09-14

### Added

- **Live Update run-progress cards.** A running task now shows a silent, low-priority notification that
  advances with the action chain: one colored segment per action on Android 16+ (green = success, red =
  failed, blue = running), a standard progress bar below. The card honors the existing execution-notification
  preference, appears only after all admission gates pass, and disappears when the run finishes. Blocked or
  skipped runs never flash a card.
- **Single-task sharing (.nexaflow files).** Share any task to other apps from its dashboard menu; opening
  a received `.nexaflow` file imports it through the same validated pipeline as full-backup imports —
  structural preflight, workflow validation, ID-collision re-keying, and review-before-enable (imports land
  disabled). A full backup shared into the single-task importer is rejected with a clear message instead of
  importing "just the first task". Share/import result strings are fully localized.
- **Bond-loss diagnostics** for Bluetooth tasks, including the platform's bond-loss reason, recorded in the
  engine log when a pairing is removed.

### Changed

- **Material 3 Expressive progress surfaces.** List/screen loading states and the update-download dialog now
  use the expressive morphing `LoadingIndicator`, and the task-builder step bar uses the expressive wavy
  linear progress indicator — matching the 2026 Google-app motion language (the theme already runs the
  expressive `MotionScheme`).
- **Android 17 RemoteViews memory budget enforced for tile icons.** Every dynamically rendered Quick Settings
  tile icon is now verified against the platform's Bitmap+Icon payload limit (1.5 × screen frame, new fatal
  `IllegalArgumentException` on API 37) and downscaled when needed, eliminating a new crash class on
  small-display devices.

### Fixed

- **Background volume and ringer changes no longer report false success (Android 16/17).** Android 17's
  background-audio hardening silently discards volume writes from apps the user cannot see, so history
  could claim a task had changed a stream the system left untouched. `SYSTEM_RING_VOLUME` now reads the
  stream back after writing (matching the existing `SYSTEM_VOLUME` and `SYSTEM_RINGER_MODE` behavior), and
  every audio path returns an explicit failure naming the platform restriction instead of a fabricated
  success. Ringtone actions keep the `USAGE_ALARM`-eligible streams as the one exception the platform
  still honors.
- **App launches, URL opens, and app-settings jumps from background triggers now report platform denials.**
  Android 16/17 harden Background Activity Launch: a cold trigger context (alarm, broadcast, monitor) can be
  denied silently. The three launch paths in the system controller now classify the denial explicitly
  (SecurityException from the platform's background-activity policy) and record an honest failure with
  remediation guidance, instead of logging a launch the user never saw. Notification-tap and widget-tap
  entry points keep their explicit-user-intent privilege and are unaffected.
- **An unpaired Bluetooth device no longer leaves connect-condition tasks stuck active.** When Android
  reports a removed bond (key missing, encryption failure — previously vendor-log noise), tasks waiting for
  the device to disconnect now close immediately and record a bond-loss diagnostic with the platform's loss
  reason. Previously these tasks stayed durably "active" until the device returned.

### Tests

- Added RemoteViews memory-budget coverage (platform limit formula, per-configuration byte
  sizes, downscale behavior) and single-task export/import coverage (round-trip, collision
  re-keying, full-backup rejection), with Robolectric enabled for the widgets module.

## [v3.69.0] - 2026-09-13

### Changed

- **Privileged capability execution now selects the best authorized channel adaptively.**
  Capability requests that explicitly opt in to privileged execution no longer need to pin
  exactly one backend. NexaFlow now prefers Shizuku for capabilities supported by both
  Shizuku and Root, then falls back to Root when Shizuku is unavailable. Requests can still
  restrict execution with `allowedBackends` or override priority with `preferredBackends`.
- **Shizuku is now the preferred compatibility provider for shared elevated capabilities.**
  The legacy compatibility selector follows the same least-privilege ordering as the modern
  capability resolver, while Root remains exclusive for `ROOT_SHELL`.
- **Builder capability discovery now keeps grantable features visible without making them executable.**
  The trigger and action catalogues distinguish ready, permission-required, temporarily unavailable,
  and unsupported capabilities from the same live snapshot. Grantable rows stay discoverable with an
  explicit locked state and route into the existing permission flow; unsupported rows remain hidden.
- **Release automation now gates version tags on the latest green `main` commit.**
  A changelog-backed release candidate is tagged only after Android CI succeeds for the current head,
  then the tag build is dispatched explicitly so production-signing, certificate, APK/AAB, alignment,
  and release checks cannot be skipped by GitHub token recursion protections.

### Fixed

- **Privileged backend availability no longer reports false permission requirements when
  adaptive execution is explicitly authorized.** `allowPrivilegedBackends=true` is now the
  explicit authorization boundary; backend pinning remains optional rather than mandatory.
- **Shizuku/Root failures now map to more precise structured capability errors.**
  Shizuku connection loss and unavailable UserService states are reported as
  `SHIZUKU_UNAVAILABLE`, while unavailable Root execution is reported as `ROOT_UNAVAILABLE`.
- **Compatibility channel diagnostics now reflect Shizuku-first precedence.**
  The channel-status regression test was updated so devices exposing both Shizuku and Root
  report the same preferred elevated channel as the production selector.

### Tests

- Added adaptive-resolution coverage for Shizuku-first selection, Root fallback,
  explicit Root preference, backend pinning, and privileged-execution opt-in.
- Updated compatibility-provider coverage to keep `ROOT_SHELL` constrained to Root.

## [v3.68.0] - 2026-09-13

### Fixed

- **Bluetooth device picker is now a complete, self-healing flow (Android).** When a
  task's trigger is tied to a Bluetooth device and the radio is off, the picker no
  longer dead-ends with "No paired Bluetooth devices found" — the only state the
  logcat session reproduced (`BluetoothAdapter is not enabled` on a HyperOS 17
  device with a paired headset). The sheet now explains that Bluetooth is off and
  offers a single **Turn on** tap that raises the system `ACTION_REQUEST_ENABLE`
  dialog — the only user-visible, launcher-approved way to enable Bluetooth since
  `BluetoothAdapter.enable()` is unavailable to third-party apps — with a graceful
  fallback to the Bluetooth settings screen when a build ships no handler for the
  request. When the `BLUETOOTH_CONNECT` runtime grant is missing, the same sheet
  offers the runtime request inline. The bonded-device list is re-probed on every
  resume (lifecycle-aware `refreshTick`), so returning from the system dialog or
  Settings repopulates the list in place without closing the sheet; adapters
  without hardware keep the established empty message.
- **Saving a Bluetooth task no longer detours into a settings screen that cannot
  grant anything.** `PermissionCatalog` mapped the `BLUETOOTH_DEVICE` trigger to the
  special-permission channel, so the aggressive post-save flow opened Bluetooth
  *settings* — a screen structurally incapable of issuing the `BLUETOOTH_CONNECT`
  runtime permission the trigger actually needs. The requirement now travels the
  runtime channel (explain screen + system dialog), and the merged `DEVICE` trigger
  configured with `BLUETOOTH_CONNECTED`/`BLUETOOTH_DISCONNECTED` events — previously
  mapped to *no* permission at all — now requests the same grant. Regression tests
  pin the runtime-only contract for both trigger shapes.

### Added

- Localized Bluetooth-off empty-state strings (`bluetooth_turned_off_title` /
  `bluetooth_turned_off_body`) in all 11 locales, registered in the i18n catalog.

### Changed

- The Bluetooth explain-entry in `specialPermissionExplainInfo` and the
  `SpecialPermissionStatusRow` wiring remain available for the manual hint row,
  but the catalog no longer auto-routes Bluetooth triggers to it on save.

## [v3.67.0] - 2026-09-09

### Fixed

- **Evolver actions now honor every editor-exposed setting.** A rebuilt per-action
  config-key diff (`scripts/diff_action_keys.py`, brace-aware, string-literal safe) proved
  the EVO_STATUS_BAR, EVO_LOCKSCREEN and EVO_THEME fallback readers dropped seven keys
  the builder UI exposes: `clock_seconds`, `battery_percent`, `weather`, `shortcuts`,
  `media_art`, `monet` and `themed_icons` were accepted and silently ignored. Each now
  writes its catalog-correct bridge key (e.g. `evo_status_bar_show_battery_percent`,
  `lockscreen_weather_enabled`, `evolution_monet_enabled`).
- The diff parser itself: arm headers in brace-on-next-line style `when` blocks and URL
  literals like `"https://…"` (whose `//` was previously stripped as a comment, unbalancing
  brace depth) no longer break the scan.

### Changed

- `docs/options-audit.md` regenerated: 56 triggers / 168 actions with engine keys and
  end-behavior classifications; localization status unchanged (616 keys, complete in
  all 11 locales).
- End-behavior catalog cross-checked against the engine: all 31 toggle actions read
  `enabled` (so end-of-task on/off/revert behave exactly as configured), value actions
  and toggle sets are disjoint.

## [v3.66.0] - 2026-09-09

### Added
- **Diagnostics: end-behavior failures (Android):** the Configuration-problems screen now has a second finding bucket for end-behavior failures — any failed `*_END` action result (per-action "when the task ends" value/revert/restore) or failed whole-state restore surfaces under an "End-behavior problems" label, separate from action-config problems. A task can appear in both buckets; ordinary runtime errors are still excluded.
- **Options audit report (docs):** `docs/options-audit.md`, regenerated by `scripts/generate_options_audit.py`, tabulates all 56 triggers and 168 actions with their editor control shapes, the config keys the engine actually reads, per-action end-behavior classifications, and the localization status of all 616 editor string keys (complete in all 11 locales).

### Changed
- **Numeric editors: explicit ranges for engine clamps (Android):** the timer duration field now clamps inline to the engine's real 1 s–24 h bound via the bounded field; the display-density, Bluetooth-discoverability-timeout, and screensaver-timeout fields show supporting text stating the exact range the engine enforces or clamps to (120–1000 dpi; 0–3600 s; 1–1440 min), so silent truncation and rejected values are visible before saving.

### Tests
- New diagnostics derivation suite covering failed end-behavior results (`*_END`, `STATE_RESTORE`), config-marker matching (now case-insensitive against engine messages), the both-buckets case, and exclusion of ordinary runtime failures.

## [v3.65.0] - 2026-09-09

### Added
- **Blocked-calls details sheet (Android):** tapping a blocked-call entry opens a bottom sheet showing the masked number, the caller category (any/unknown/hidden/contact, localized), the matching rule(s) that blocked the call, the timestamp, and the actions the blocking rule ran at its latest engine execution (per-action type and message, with a fallback to the run summary). An "Open rule" button jumps straight into the blocking task in the builder. Call screening now persists the caller category and all matching rule names in a parseable record format; legacy entries fall back to the task name.
- **Deep-link force-run confirmation (Android):** `nexaflow://run-task/{id}?force=1` now shows the same force-run confirmation dialog as the in-app path instead of silently running the end behavior; confirming executes the full chain (durably logged as a force run) and dismissing runs nothing. Plain deep links (without `force=1`) keep the strict admission gate. The URI parser is extracted and covered by contract tests (scheme/host validation, `force` value strictness, trailing slashes, blank ids).

### Tests
- **Data module coverage raised 65.1% → gate-passing:** new `RepositoryImplTest` exercises the variable repository (plain/sensitive/typed round-trips, SecureStorage encryption routing, delete-side secret cleanup, legacy sensitive rows, missing-secret degradation), the automation repository (persistence round-trip, status forwarding, unknown-id nullability), and the history repository (record round-trip, stream mapping, skipped/failed/unfiltered outcome paging branches) against in-memory DAO fakes; the `:data:coverageGate` passes at the 80% threshold.

## [v3.64.0] - 2026-09-09

### Added
- **HTTP request action: retry timing (Android):** the editor now also exposes the engine's `retryBaseDelayMs` and `retryCapMs` knobs as chip presets (base 250–2000 ms, cap 5–60 s, defaults matching `RetryPolicy`), completing the retry customization surface started in v3.62.0.

### Changed
- **Pointer speed (Android):** the action editor now filters input to the integer format the engine actually parses (the previous decimal fallback silently executed as speed 0), shows the −7…7 range inline, and the end-behavior "end with value" editor gained chips (−3…3) plus the raw field instead of an empty editor.
- **Screensaver timeout end value (Android):** "end with value" now offers minute presets (1/5/10/30) and a numeric field, mirroring the action editor — previously it rendered an empty editor and applied no end value.

### Tests
- New end-behavior coverage tripwire: every action listed as a value action must have a default end value and an editor branch, and every toggle action must support revert (flashlight exempt) — future value actions without full end-value support fail the build instead of silently rendering an empty editor.

## [v3.63.0] - 2026-09-09

### Added
- **App picker (Android):** explicit search scope chips — by app name or by package name — with package subtitles shown while searching, plus a "Recently used in tasks" section listing packages referenced by other saved tasks so cross-task reuse needs no re-searching.
- **Bluetooth device and calendar pickers (Android):** the same explicit OK/Cancel confirmation bar as the app picker. Tapping now only marks a selection (pre-marked when reopening); OK applies it, Cancel discards and closes without changing anything.
- **Manual run mismatch dialog (Android):** Run now first asks the admission gate. When the task's triggers or constraints do not match, a typed dialog names exactly which trigger or constraint failed (e.g. "Trigger not met: DARK_MODE ON") and offers three honest paths: run the configured end behavior, cancel, or force.
- **Force run (Android):** an explicit user override of the manual admission gate behind its own confirmation dialog. The bypass is durably logged in history with a "Force run;" prefix so forced executions are always distinguishable from trigger-driven runs. Available from the dashboard, task details, and deep-link paths.
- **Configuration problems screen (Android):** a new diagnostics destination (Settings → Automation) listing tasks whose actions failed at runtime because of invalid configuration values, grouped per task with the failing action messages and a direct jump into the task editor — silent engine fallbacks become visible.
- **Blocked calls screen (Android):** a BlackList-style call log fed by the engine's execution log. Call screening now writes a durable "Call blocked" record per verdict, and the screen offers per-rule (per-task) filter chips, entries with masked numbers, and timestamps.
- **Connectivity trigger migration (Android):** a dashboard banner appears when saved tasks still use the legacy combined connection trigger; one tap converts each to the dedicated Wi-Fi / mobile data triggers preserving network selection and state. Non-splittable selections (hotspot, ethernet, VPN) are left untouched.
- **Clamp feedback in editors (Android):** bounded numeric fields (reminder/alarm hour and minute, wait duration) now show inline "value must be between …" feedback when an entry hits its clamp limit instead of silently truncating.

### Changed
- **Localization (Android):** 104 previously English-fallback strings (Evolver editor, map picker, HTTP request editor, wait durations, ROM-setting trigger, end-behavior hints) translated into all 10 non-English locales. Remaining identical-across-locales strings are technical literals (NFC, USB, VPN, JSON syntax) by design. Turkish apostrophe escaping fixed so all locales compile.

### Fixed
- **App picker (Android):** the OK/Cancel confirmation bar is now always visible when one or more apps are selected. The app list was measured as an unbounded sibling of the confirm bar, so with a full launcher list the bar was laid out off the bottom of the sheet and never appeared on real devices; the list is now the single weighted child of the picker sheet, pinning the live selection preview and OK/Cancel bar on screen at every list size.

### Tests
- New Robolectric suite covering the strict manual admission policy end to end: dashboard Run now rejection, typed mismatch reasons, admissible-task pass-through, force-run logging, and the deep-link policy contract through the real engine and its durable ledger.

## [v3.62.1] - 2026-09-09

### Added
- **Live selection preview in the app picker.** A preview row now appears above the OK/Cancel bar as soon as any app is checked: each selected app shows its launcher icon, label, and a remove (×) control that deselects it in place, with a live "Selected: N" counter. Users see exactly what OK will apply before confirming, in every supported language.

## [v3.62.0] - 2026-09-09

### Added
- **HTTP request action: full professional customization.** The editor now exposes the connection **timeout** (seconds, with hint), the **retry policy** (attempt count, matching the engine's existing retry/backoff knobs), and an optional **response output path** so later actions can read the response body via context selectors — all knobs the handler already supported but the builder never surfaced.
- **Evolver navigation: back-gesture height.** The EVO_NAVIGATION editor now offers chip presets for the gesture bar height instead of leaving the engine-only `back_height` key unreachable from the UI.
- **Block / clear notifications: multi-app selection.** Both notification actions accept multiple packages at the engine level; the editor now uses the multi-select app picker (with OK (count) / Cancel confirmation) instead of a single free-text package field.
- **Font scale and display density gained end behavior.** These two display-scaling actions were the only settings with no "when the task ends" option. The device snapshot now captures the original font scale and forced display density, restore-original writes them back (density via `wm density`), the end-behavior catalog lists both as value actions, and their end-value editor offers practical preset chips plus a validated numeric field.

### Fixed
- **Package picker fields no longer wipe sibling action settings.** Editing an action's package field rebuilt the whole config map with only the package key, silently clearing other keys (e.g. the enabled toggle of "block notifications"). Editing now preserves every other configured key and clears only the opposite single/multi package key.
- **End-behavior section strings are localized.** Two hard-coded Arabic strings ("add actions first", "no action supports end behavior") in the builder now use the existing string resources, so they render correctly in all 11 languages.
- **XML validity: `Heads-up & Notifications`** contained a bare ampersand that broke resource packaging in all 11 locale files; the JSON catalog was corrected too so the generator cannot reintroduce it.

## [v3.61.0] - 2026-09-09

### Fixed
- **Location picker now completes the maps round-trip.** "Choose on map" opened the installed maps app but offered no way to bring coordinates back. The picker gained **Paste coordinates** (reads lat,lng from the clipboard — supports plain pairs, Google Maps share text and place-link URL formats) and **Copy coordinates** (writes the current fields back to the clipboard), alongside the existing manual entry; the round-trip is now: open maps app → copy the place → paste → save. All picker text is localized instead of hard-coded English.
- **App picker got explicit OK/Cancel buttons.** Selecting one or more apps now confirms with **OK (count)** — disabled until something is selected — and **Cancel** discards the selection and closes the sheet, replacing the ambiguous back-button-only flow.
- **Quick Settings tiles now toggle tasks exactly like the app UI.** Tapping a task's tile previously only flipped the stored enabled flag. It now matches the in-app toggle semantics: disabling persists the flag and immediately runs the task's configured end behavior (restore state / exit actions); enabling runs the main chain only when the triggers and conditions currently match.

### Changed
- **ROM setting trigger editor is now chip-driven and fully localized.** The key is chosen from the categorized live-device/catalog picker (shown on the button, with its category summary below) instead of being typed by hand, and the target value is selected from chips that follow the chosen key's value type (on/off for booleans, the key's fixed option set for enums, common values otherwise) — no free-text entry remains. All previously hard-coded English editor strings (map picker, ROM editor, app picker) are now localized across all 11 supported languages, with real Arabic translations and two removed orphaned keys.

## [v3.60.0] - 2026-09-08

### Changed
- **Strict paired-trigger lifecycle: the end behavior now runs even when the process restarts mid-session.** Every stateful trigger already ran its actions directly on the trigger event and its configured end behavior ("when the task ends") when the condition ended; with no end behavior configured the state is left untouched. Two trigger families kept that contract only in memory, so if the monitoring process died while a task was active, the later exit was skipped as "task was not active" and the end behavior never ran:
  - **App open/close trigger** (`AppTriggerAccessibilityService`): a task that fires when its app opens now records a durable, occurrence-scoped lifecycle; when the app leaves the foreground, the exit goes through the same `ExitCoordinator` as the time-window triggers (exactly-once, restart-safe, recovery-capable). A session that survives a process restart re-arms only its exit side — the main chain never re-runs, and the end behavior still fires on the next real foreground change.
  - **Bluetooth device connect/disconnect trigger** (`BluetoothMonitor`): same strict pattern — durable admission on the triggering event, coordinator-driven exit on the opposite event, and ledger restoration after restart so a connected session still ends correctly.
  - **NFC tag, SMS, and other one-shot triggers** were already strict (`completeExitOnFinish`), and **location enter/exit** already used the durable coordinator; both are unchanged.
- New unit tests pin the restored-session semantics: a restored session must not re-run its main chain and must emit exactly one exit on the next foreground change.

## [v3.59.6] - 2026-09-08

### Changed
- Resource-hygiene cleanup: removed the unreferenced `trigger_type_sms_sub` string (left behind when the SMS trigger's picker summary was reworked in earlier releases) from all 11 locales, and dropped it from the i18n catalog so the string generator cannot resurrect it. CI's auto-fix gate had correctly flagged the commit as not auto-fix-clean; this commit is the sanctioned `auto_fix.py` output.

## [v3.59.5] - 2026-09-08

### Fixed
- **Option sets now match the nature of what they configure** across trigger, action, and end-behavior editors (no options were merged; every editor keeps its own option set):
  - **Battery temperature trigger**: the threshold field accepted arbitrary text that the engine silently discarded; it now accepts digits with a single decimal separator only, uses a numeric keyboard, and shows a "Degrees Celsius" hint.
  - **Send SMS action**: the phone-number field uses the phone keyboard instead of a generic text keyboard.
  - **Reminder and Set alarm actions**: the hour/minute fields are numeric-only with 0–23 / 0–59 clamping (previously free text that the engine replaced with defaults when invalid).
  - **Set timer action**: the duration field is digits-only.
  - **Key event action**: the free-text key field frequently produced values the engine could not map (it accepts only ~30 named keys or a raw KEYCODE). The editor now offers the supported key set as chips plus an explicitly-labeled custom field for names/codes.
  - Reviewed every other editor for the same class of mismatch — state triggers, Wi-Fi/cell signal, volume, brightness, storage, thresholds, webhook, HTTP, shell, and the end-behavior catalog (toggle → turn on/off, URI-type → revert-only, value-type → revert/set) already matched their semantics and were left untouched.
- New localized strings added across all 11 locales.

## [v3.59.4] - 2026-09-08

### Fixed
- **"Run now" no longer bypasses the task's triggers and conditions.** A manual run (dashboard card, task details screen, or the `nexaflow://run-task` deep link) executed the main action chain unconditionally, even when the trigger window did not match (e.g. an 11:00 scheduled task tapped at 15:40). Manual admission is now strict and follows one policy shared by every manual entry point:
  - Triggers and constraints **satisfied** → the main action chain runs as before.
  - **Not satisfied** → only the configured end behavior ("when the task ends") runs; if no end behavior is configured, nothing is executed and an explicit **"Triggers or conditions of the task are not satisfied, so it cannot run now"** outcome is recorded in history and shown in the UI.
  - When an end behavior runs because of a mismatch, its completion message now says so explicitly.
- The engine's condition-gated path (previously used only by the enable toggle and builder save) is now the single manual-admission policy; the duplicate unconditional path was removed. Event-driven monitors (time alarms, SMS, sensors, …) are unchanged — they fire on their own triggers by design.
- Conditions-not-satisfied messages rewritten across all 11 locales to name triggers/conditions explicitly (real Arabic translations; English fallback elsewhere).

## [v3.59.3] - 2026-09-08

### Changed
- **Split the combined network trigger into separate Wi-Fi and mobile-data triggers.** The Connection section previously offered a single "Connectivity" option that covered both Wi-Fi and mobile data through a secondary selector. New tasks now choose **Wi-Fi connected** or **Mobile data connected** directly — each with its own connected/disconnected state, label, and icon. The combined trigger remains in the engine for saved tasks (it no longer appears in the picker), and its editor no longer allows changing the saved network choice.

### Fixed
- **Incoming-call trigger showed SMS matching labels.** The number-mode chips reused the SMS trigger's "Any text" option, which is meaningless for phone numbers. The call editor now shows "Any number", hides the number field when that mode is selected (matching the SMS editor's behavior for "Any text"), labels the filter "Caller number" instead of the SMS-sender label, and adds an explicit hint that matching applies to the caller number, not a message text.

## [v3.59.2] - 2026-09-08

### Fixed
- Android Lint errors blocking CI from v3.59.0's call-control code:
  - `NexaCallScreeningService` called `CallResponse.Builder.setSilenceCall` (API 29+) unguarded. It is now gated on `Build.VERSION.SDK_INT >= Q`; on API 26-28 a SILENCE verdict lets the phone ring normally while the task still executes through the engine.
  - `CallActionsHandler` called `TelecomManager.endCall` (API 28+, `ANSWER_PHONE_CALLS`) without a version or permission gate. It now fails honestly on pre-API-28 devices, checks `ANSWER_PHONE_CALLS` explicitly before calling, and returns an actionable failure reason when the permission is missing.
- No behavior change for BLOCK pre-ring screening, which already ran through the call-screening role.

## [v3.59.1] - 2026-09-08

### Fixed
- Resource-hygiene gate failure from v3.59.0's string injections: 66 orphaned or duplicate string entries across locale files (leftovers from earlier i18n batches that my prefix-filtered injections had reintroduced or kept alive). All entries removed via the repository's own `scripts/auto_fix.py`; `auto_fix.py --check`, string parity, and the unified resource gate now pass. No runtime behavior change.

## [v3.59.0] - 2026-09-08

### Added

#### Communication triggers (SMS + calls)
- **SMS trigger match modes.** The SMS trigger's body filter now supports three modes: **Contains** (default, legacy behavior), **Exact** (the full message body must equal the text, ignoring case and surrounding whitespace), and **Any text** (every message from the sender matches). Legacy tasks without a stored mode keep the historical Contains semantics.
- **Incoming-call trigger.** A new `INCOMING_CALL` trigger fires pre-ring via the Android call-screening role. Matching reuses the SMS contract (`from` + match mode) against the caller number, plus a caller **category** filter: any caller, unknown numbers, private/hidden numbers, or contacts only. Tasks without the screening role degrade gracefully to the existing post-ring `CALL_STATE` path.
- **Call actions: Block and Silence.** Two new actions — `CALL_BLOCK` (reject the call pre-ring, skip the call log and notification) and `CALL_SILENCE` (let the call continue without ringing). The decision core enforces **emergency numbers are never screened**, a **disabled task never screens**, and **Block beats Silence** when several tasks match the same call. Tasks carrying neither action act as pure observers (log-only automations).
- **Schedule constraint.** A new `SCHEDULE` constraint narrows any task to a day/time window: selected weekdays (or every day), a start/end time, with overnight windows (e.g. 22:00→06:00) supported. Corrupt window configs fail closed. The call-screening policy evaluates it per call, so a blocking rule can be confined to night hours.

#### Scheduling and templates
- **Scheduled messages made first-class.** The TIME trigger already supports interval, specific date, and weekday modes; combined with the `SYSTEM_SEND_SMS` action (plus the new `SCHEDULE` constraint for send windows) this composes into full message scheduling — send or reply at an interval, at a time/date, or on selected days. Two new starter templates surface it: a scheduled SMS send and a scheduled reply.

#### Builder and platform integration
- Builder UI for all of the above: match-mode selector in the SMS trigger editor, the incoming-call trigger editor with category picker, call-action config cards with permission hints, and the schedule-window editor (weekday chips + time fields) in the constraints card.
- New trigger/action types are wired through the full platform stack: command catalog specs (version requirements, availability, hardware checks), event-source mapping, permission catalog, trigger-state manual gates, details/dashboard presentations, and 14-locale strings (English translated; other locales seeded with English until community translations land).
- The existing `TaskTileService` covers BlackList's quick block-all toggle (pin any block-all-unknowns task to a Quick Settings tile), and blocked-call history falls out of the engine's execution log.

### Adapted from BlackList
- The [BlackList](https://github.com/Alaa91H/BlackList) call-control model (block/silence decisions, unknown/private/unknown caller categories, schedule windows, emergency-number protection) is merged into the engine as per-automation screening rules rather than a separate rule database — every task with an incoming-call trigger is one rule, evaluated under NexaFlow's existing constraint system.

## [v3.58.16] - 2026-09-08

### Fixed
- Static analysis (Detekt) failure from v3.58.15's probe-diagnostics change: the caller-chain log built its stack frames through a bare `Throwable()` constructor, which Detekt rejects (`ThrowingExceptionsWithoutMessageOrCause`). The frames are now taken from `Thread.currentThread().stackTrace` — same diagnostic output, no exception object. No runtime behavior change; this release exists so the lint gate is green for the shipped probe-storm fix.

## [v3.58.15] - 2026-09-08

### Fixed
- **Completed the capability probe-storm fix that v3.58.13/v3.58.14 only partially applied.** On-device verification with root granted still showed the system killing the process (`Too many Binders sent to SYSTEM`): invalidations arriving *while a capability scan was running* bypassed the throttle and each queued a full scan (the mutex serialized them but never coalesced them), and every scan re-probed privileged capabilities.
- `CapabilityStateStore` now routes every refresh through a single worker: at most one scan is ever in flight, a burst of any size collapses into one pending request, and consecutive scans are spaced at least 30 seconds apart (the first snapshot after startup remains immediate). Worst-case privileged-probe rate drops from ~12/min (kill territory) to ≤1 per 5 seconds.
- `SystemAppStatusDetector` `su`-probe spacing raised from 2s to 5s — each KernelSU spawn costs ~57 binder transactions (measured on device), so probe spacing directly bounds the binder rate.

### Diagnostics
- The `su probe` log line now carries the six-frame caller chain, so any future probe storm identifies its exact driver immediately (this is how the one-shot `RootPermissionGranter.requestAndGrantAll` path was ruled out on device).

### Verification (real device, KernelSU root granted)
- Process survives 4+ minutes with the same PID after launch — previously killed within ~25 seconds.
- 1 `su` probe in a 4-minute window — previously 65 probes in 3 minutes ending in a binder kill.
- Zero `Too many Binders` events; scheduled alarms remain registered and the monitoring foreground service stays up.

## [v3.58.14] - 2026-09-08

### Fixed
- **Corrupted `versionName` for releases with patch ≥ 10.** The version derived from git tags clamped the patch to 9 for the numeric `versionCode`, then split the tag with the *clamped* delimiter — so `v3.58.13` was reported as `v3.58.9v3.58.13` in the installed app (Settings > About, package manager). The suffix split now uses the unclamped patch, so the visible version name is the real tag (`v3.58.13`) while `versionCode` keeps its monotonic clamped scheme. This also affected the v3.58.10–v3.58.13 releases already published; install v3.58.14 to see the correct name.

### Known limitations
- `versionCode` intentionally clamps patch ≥ 10 to 9 (e.g. `v3.58.10`–`v3.58.19` share `358090`), which is fine for direct APK distribution but would need a monotonic scheme before Play Store publishing.

## [v3.58.13] - 2026-09-08

### Fixed
- **Stopped the process kill loop that silently ended task execution on device.** On-device evidence (`logcat` on a rooted Evolution X device) captured the system killing the app with `Too many Binders sent to SYSTEM` (5,750 binder transactions in ~25 seconds) minutes after every launch, which left scheduled alarms registered but no process alive to receive them — the direct cause of "tasks do not execute". The flood came from a repeating chain: a Shizuku binder lifecycle event invalidated `CapabilityStateStore`, each full scan dropped `SystemAppStatusDetector`'s root cache, and the scan's privileged capability probes each spawned a fresh `su` process — repeated every ~2 seconds without bound.
- `CapabilityStateStore.invalidate()` is now throttled: bursts coalesce into one refresh per 10-second window plus a single trailing refresh, so an event storm can no longer multiply into a probe storm. `refreshNow()` also no longer drops the root-probe cache — root freshness is governed by the detector's own 2-second TTL.
- `SystemAppStatusDetector.isRootAvailable()` is now single-flight under a lock (concurrent callers share one probe instead of spawning parallel `su` processes) and enforces a minimum 2-second wall-clock spacing between real probes even when the cache is invalidated, so a misbehaving invalidation loop can never translate into unbounded process spawning.

### Tests
- Added `probe storm spacing reuses the last answer instead of spawning repeated su`: 50 invalidate+query pairs spawn at most 2 probes.
- Added `burst of invalidations coalesces into a single trailing refresh`: 5 invalidations inside one throttle window yield exactly one full capability scan.
- Adjusted the root-grant flow tests to disable the new spacing guard explicitly (grant transitions must remain observable), resetting it in teardown.
- `:core:execution` and `:core:rom-integration` unit suites pass.

### Known limitations
- Verified by unit tests and captured device evidence only; a follow-up on-device run (Phase 0 checklist: force-stop, reboot, Doze, cross-midnight range) must confirm the process survives past the previous kill window before the "tasks do not execute" report is closed.

## [v3.58.12] - 2026-09-08

### Fixed
- **Capability gate no longer blocks executions during the startup race.** When the process is woken from a kill by an alarm or broadcast, `WorkflowCapabilityValidator` could evaluate against a `CapabilitySnapshot` whose first scan had not completed yet (empty `reports`, `observedAtMs = 0`), which made every capability look `UNSUPPORTED` and rejected automations containing `SYSTEM_OPEN_URL` or `SYSTEM_OPEN_SETTINGS` with `Blocked: required capability is unavailable` before any handler ran. The validator now treats a never-observed snapshot as *undecided* and admits the workflow, leaving the live permission decision to the actual action handler — the same principle already applied to DIRECT/SHELL/ELEVATED commands. Snapshots from a completed scan keep their exact previous semantics: an observed-absent capability still blocks, with the same diagnostics.

### Tests
- Added `admits workflow when first capability scan has not completed yet` covering the admission path for a never-observed snapshot (`observedAtMs = 0`), and tightened the existing block test to use an *observed* empty snapshot so the two states can no longer be confused.
- `:core:execution` and `:domain` unit suites pass against the refreshed `main` baseline (post-v3.58.11).

### Known limitations
- **On-device evidence is still pending.** This fix is verified by unit tests only; the Phase-0 checklist from the task-execution investigation (force-stop, reboot, Doze, OEM autostart scenarios with saved `logcat`/`dumpsys` artifacts) has not been executed on real hardware yet and remains the gate for closing the "tasks do not execute" report.

## [v3.58.11] - 2026-09-06

### Performance
- **Atomic startup:** `NexaFlowApplication` now parallelizes `scheduler.initialize` and recovery on `Dispatchers.IO`, reducing cold-start blocking and ensuring `MonitoringService` starts atomically.
- **Compose stability:** `DashboardScreen` uses `derivedStateOf` for `filteredRows` and `remember` per-automation for `nextRunText`, avoiding per-frame `System.currentTimeMillis` and `TimeTriggerCalculator` recomputation.

## [v3.58.10] - 2026-09-06

### Performance
- **Sensor atomic debounce:** `SensorMonitor` now debounces 200ms per sensor, caches `candidatesBySensor` on refresh, and avoids coroutine storm on light flicker/shake.
- **Battery precise threshold:** `BatteryMonitor` allows `level 0` and validates `threshold 0..100`; `BatteryTriggerMatcher` supports `threshold`/`above`/`below` keys with `coerceIn`.

## [v3.58.9] - 2026-09-06

### Performance
- **Dashboard atomic load:** `HistoryRepository.getLatestExecutions()` now returns `O(automationCount)` via `SELECT MAX(executedAt) GROUP BY automationId` with composite index `(automationId, executedAt)`, instead of loading 1,000 rows and grouping in Kotlin. `DashboardViewModel` uses `distinctUntilChanged` to avoid redundant recompositions.
- **Database stability:** `AppDatabase` 17→18, `MIGRATION_17_18` creates `index_execution_history_automationId_executedAt` and `index_automations_enabled`; composite index accelerates latest-per-automation and retention pruning.

## [v3.58.8] - 2026-09-06

### Fixed
- **Atomic bounded ledger:** `ActiveExecutionStore.beginCheckpoint` now atomically prunes the oldest `COMPLETED` checkpoint when `MAX_CHECKPOINTS=128` is reached, preventing silent drop of executions during trigger bursts.
- **Precise location and connectivity:** `LocationMonitor` now requests updates on `Looper.getMainLooper()` (fixes `IllegalStateException` on `Dispatchers.Default`); `ConnectivityMonitor` only marks `initialized` after successful `registerDefaultNetworkCallback` and clears `HotspotStateReader` stale state on `stop`.
- **Strict WakeLock and time handling:** `ExecutionEngine.acquireWakeLock` truncates tag to 60 chars; `TimeTriggerCalculator` validates DST gaps via `getValidOffsets` and uses `safeZonedDateTime` for `nextFireTime` and `windowEndMillis`.

## [v3.58.7] - 2026-09-06

### Fixed
- **Manual Run now executes unconditionally:** `DashboardViewModel.runNow` and `AutomationDetailsViewModel.runNow` now call `runAutomation` directly; previously they used `runWithConditionGate` which checked triggers and, for a `11:00` monthly trigger invoked at `14:54`, returned `Conditions not satisfied; no end behavior` without running the main action.
- **Toast auto-dismiss exactly 3s:** split `LaunchedEffect(executionMessage)` (sets `toastText` and consumes) and `LaunchedEffect(toastText)` (delays 3000ms then clears); previously consuming the message cancelled the delay, leaving the dark bottom toast visible for >5s.

## [v3.58.6] - 2026-09-06

### Added
- **Dynamic Evolver engine (Evolution X 17 / API 37):** `EvolverCatalog` with 60+ keys including OEM `sec_*`, `miui_*`, `oplus_*`; distributed `EVO_*` actions across `DISPLAY`, `SYSTEM`, `NOTIFICATIONS`, `STATUS_BAR`, `LOCKSCREEN`, `THEME`, `AMBIENT`, `NAVIGATION`, `BATCH` with hardware-gated builder pickers.
- **`EvoActionHandler`, `CommandCatalog` shell passthrough (`EVOLUTION_X_SETTINGS`), `EvolverSettingPickerDialog` and `ActionRegistry` integration** for direct Evolver key execution.
- **ROM priv-app integration package** `rom/` (`Android.bp` privileged+presigned, `NexaFlow.mk`, `privapp-permissions`, `sepolicy/nexaflow.te`, `install-rom-integration.ps1/.sh`) for baking NexaFlow into Evolution X vendor builds.
- **Per-task toggle toast** — dark, message-sized, bottom-center (`40dp`, `Color(0xFF323232)`, `24dp` rounded) shown for 3 seconds when the per-task switch is enabled; visible for every task type (previously time-trigger only).
- **Strict hardware adaptation** `HardwareProfile` / `DeviceProfile` live probes (`NFC`, `Telephony`, `Bluetooth`, `Flash`, `Proximity/Light/Step/Accel/Gyro`, `GPS`, `USB/Ethernet`) with `CommandCompatibilityEngine.hardwareOkForType()` and `isSensorAvailable()` filtering.

### Changed
- **Exit-behavior header icon unified:** builder `IconBadge` now `containerColor=White`, `contentColor=selectedIconColor` (previously solid color); dashboard exit rows use `automation.iconColor`.
- **Icon picker categories de-duplicated and empty categories hidden** — `NexaFlowIcons.categories.distinct()` filtered by actual entries.
- **Network mode resilience:** `NetworkModeCapabilities` 7-layer fallback reader with `SettingsFallbackReader`; `BluetoothMonitor.matchesDevice()` accepts `ANY` (`""`/`__ANY__`/`*`).
- **Strict execution guarantees:** `AutomationScheduler` uses `setExactAndAllowWhileIdle` (no alarm-clock icon), `ExecutionEngine` 10-minute `WakeLock`, `ExitCoordinator.MAX_EXIT_ATTEMPTS` 2 → 5.
- **Toast placement fixed:** moved from `Scaffold` content to outer `Box` sibling so `Alignment.BottomCenter` is honored; `delay(3000)` and `consumeExecutionMessage` ordering corrected.

### Fixed
- **Task execution restored after root grant:** `SystemAppStatusDetector` TTL 5s → 2s, `refreshAndProbe()`, explicit KSU/Magisk paths (`/data/adb/ksu/bin/su`), diagnostic `Log.d` for `su probe`; `ExecutionEngine.elevatedHint()` and `MonitoringService` startup log prevent silent `No elevated runtime available`.
- **Internationalization parity:** added missing `action_evo_*`, `toast_on_toggle_*`, `task_*_toast`, `category_apps`/`category_security`, `any_device` keys to all 10 locales (`ar`, `de`, `es`, `fr`, `hi`, `ja`, `pt`, `ru`, `tr`, `zh-rCN`); removed 99 orphaned keys via `scripts/auto_fix.py`.
- **Static analysis and tests:** removed unused `hardwareOk(spec, hardware)` stub (Detekt), suppressed `NetworkModeCapabilities.createFakeSubscriptionInfo` false positive, supplied `HardwareProfile` to `CommandCompatibilityEngineTest`, hoisted `hiltViewModel()` from `RoutineDetails` to `RoutineCard` (fixes `RoutineCardHeaderTest` Robolectric Hilt crash), updated `ExitCoordinatorTest` bound to 5 attempts.

### Quality assurance
- `scripts/check_strings_parity.py` → `PARITY_PROBLEMS: 0`; `scripts/auto_fix.py --check` → `OK`; `scripts/tests/test_check_resources.py` → `OK`.
- `./gradlew detekt` and `./gradlew testDebugUnitTest` (excluding `core:datastore` Windows file-lock flake) pass locally; `lint` + `build` pass on `ubuntu-latest` CI (run `34031743262`).

## [v3.58.5] - 2026-09-05

### Added
- Extended the existing durable checkpoint contract with stable workflow identity, workflow revision, parent/correlation/causation metadata, deadline, current-node cursor, and checkpoint version fields.
- Added bounded durable node-attempt records containing attempt state, timestamps, selected backend, idempotency key, input/output hashes, verification state, and failure code.
- Connected action-start, action-complete, and unknown-outcome checkpoints to node-attempt persistence without breaking existing callers.

### Safety
- Preserved the fail-closed unknown-outcome path: interrupted nodes remain `UNKNOWN` and require verification or compensation instead of being reported as successful.
- Added a bounded node-attempt limit to prevent unbounded DataStore growth.

### Tests
- Added checkpoint assertions for node identity, backend, hashes, verification transitions, and explicit unknown-outcome persistence.

### Documentation
- Updated the architecture map and machine-readable gap analysis to distinguish the new durable foundation from the still-unproven repository-wide and cross-store recovery guarantees.

## [v3.58.4] - 2026-09-04

### Added
- Added explicit capability safety metadata for idempotency, retry safety, verification mode, compensation support, and side-effect classification.
- Added a conservative request boundary that rejects multi-attempt retries unless the capability declares retry safety, and rejects disabling verification when a capability requires post-condition proof.
- Updated the forensic architecture and gap analysis with the current repository inventory and the remaining persistence, cross-store recovery, security-boundary, and OEM-validation gaps.

### Fixed
- Removed a redundant `CancellationException` rethrow from `TaskManager`, preserving structured cancellation while satisfying static-analysis correctness.
- Restored newline-at-end-of-file compliance for automation and dashboard deletion regression tests.
- Declared `RECEIVER_NOT_EXPORTED` for the internal automation-change broadcast used by the deletion lifecycle regression test, satisfying Android's receiver security contract.

### Tests
- Added validator coverage for unsafe retry rejection and mandatory verification enforcement.
- Updated the structured retry fixture to declare its safe-retry contract explicitly rather than relying on an implicit default.

### Quality assurance
- Resource, translation, lint, unit-test, production-build, APK/AAB validation, signing, and release gates passed in GitHub Actions for commit `dbef833d`.
- The affected Detekt targets pass locally: `:core:execution:detekt`, `:feature:automations:detekt`, and `:feature:dashboard:detekt`.
- Resource hygiene, trigger-catalog parity, and whitespace checks remain clean.

## [v3.58.3] - 2026-09-04

### Added
- Introduced canonical workflow-run and node-visit execution states as a compatibility layer over the existing interpreter and durable lifecycle records.
- Added strict transition contracts that keep terminal outcomes closed and require explicit recovery or compensation paths for uncertain and failed work.
- Added truthful result adapters so failed and uncertain node outcomes cannot be reported as successful workflow completion.

### Tests
- Added deterministic contract coverage for forward-only transitions, terminal-state closure, explicit compensation, and success/failure/unknown result mapping.

## [v3.58.2] - 2026-09-04

### Fixed
- Enforced durable idempotency-key uniqueness before an action side effect starts, preventing a recovered execution from reserving the same action claim twice.
- Preserved `SubworkflowProvider` compatibility for legacy three-argument implementations and budget-aware providers while retaining the shared nested-execution safety budget.
- Stabilized saga compensation regression coverage around the invariant that declared compensation executes after the original side effect.

### Quality assurance
- Resource hygiene checks passed with zero orphaned, unused, missing, or extra translation resources.
- GitHub Actions lint and build gates passed on the verified mainline commit.
- No published tag was modified; this release is based on the green `main` baseline after the v3.58.1 release.

## [v3.58.1] - 2026-09-03

### Fixed
- Stateful triggers now evaluate their condition **immediately when a task is enabled** instead of waiting for the next device-state broadcast. Enabling a battery task while the level is already below the threshold, an airplane-mode task while airplane mode is already on, a dark-mode task while the dark theme is active, a ringer task while the sound mode already matches, a screen/headset task while the condition already holds, or a DND/state-28 task whose state condition is already satisfied now fires the task right away — matching the long-standing sticky-battery behavior for freshly saved tasks.
- Every enable/disable path now notifies the monitors instead of only writing to the database: dashboard toggle, routine-details toggle, the automation builder save, the home-screen widget **toggle all**, and the quick-settings task tiles all broadcast the automations-changed signal, and `MonitoringService` re-evaluates each stateful monitor against the **current** device state.
- A task disabled or deleted while its condition still holds is now dropped from monitor tracking immediately (its durable mark is pruned) instead of leaking an active marker until the next process restart, and a condition that ended while tracking was down fires its missed end behavior on the next reconcile instead of waiting for the state to flip again.
- Airplane-mode, dark-mode, ringer, device-event, and device-state monitors now reconcile and fire on **thread-safe** active sets (`ConcurrentHashMap`), since on-demand reconciles can now run concurrently with broadcast-driven evaluation; previously they used plain mutable maps that could race after a process/service restart.
- Battery and connectivity monitors gained the same on-demand full reconcile through their existing durable occurrence ledger (re-arm + current-state evaluation), so the enable/disable behavior is uniform across every stateful trigger family.

### Tests
- The pinned exit-reconcile contracts continue to pass: a condition that ended during downtime fires its missed exit on restart, a task whose condition still holds after restart stays active, and a stale mark for a disabled task is pruned without firing a stale exit.
- Full engine unit suite (141 tests) and the connected-device `androidTest` suite (16 tests) pass on a real SDK 37 phone, including the notification read-back, resource-permit, workflow-interpreter, recovery-coordinator, trigger-index, and reminder-alarm contracts.

### Quality assurance
- Compile, detekt, unit tests, and the connected-device suite are green locally; GitHub Actions lint, unit tests, and the production build are green on `main`.

## [v3.58.0] - 2026-09-03

### Added
- Activated the workflow control-flow primitives that previously failed closed: **subworkflows** (bounded recursion, input/output parameter passing, isolated or shared context), **human approval gates** (explicit gateway contract with configurable timeout policy), **saga compensation** (reverse-order undo of declared side effects), and **ForEach loops** (per-item variable scoping with index exposure and failure policy).
- Added a **shared execution budget** (`WorkflowExecutionBudget`) spanning the whole tree — including nested subworkflows — with an atomic node-visit ceiling and a monotonic wall-clock deadline, so loops, retries, fan-out, and nested providers can no longer multiply work past the run's safety limits.
- Added per-node **execution journaling** (optional, non-blocking) that records run id, node, action type, timestamps, outcome, and error code without ever failing a workflow.
- Wired the budget-aware `executeSubworkflow` overload through the provider contract so child interpreters inherit the parent's visit counter and deadline; the legacy three-argument method remains source-compatible.
- Added a simplified GPS location-mode workflow with explicit **ON** and **OFF** choices for easier device control.
- Added quick timer presets for **1 minute**, **5 minutes**, **10 minutes**, and **24 hours**, plus a custom duration field supporting values from **1 second through 24 hours**.

### Changed
- Condition evaluation for `BranchNode`, `WhileNode`, and `WaitUntilNode` is now strictly suspend-aware: an evaluator exception fails the node closed with a diagnostic instead of being treated as `false`, and cancellation always propagates instead of being swallowed.
- `WhileNode` re-checks its condition after the final iteration and reports `While iteration limit reached` truthfully when the condition is still true, instead of returning success.
- Timeout handling is unified: node-level timeouts and the workflow time budget both produce explicit failed results with side-effect-verification warnings, never unstructured coroutine cancellations.
- Retry reconciles declared compensations between attempts so a partially applied failed attempt cannot leak stale undo entries into a later successful attempt.
- The `workflow` failure result and the execution journal now share the same truthful outcome and error-code classification (`EXECUTION_TIME_LIMIT`, `NODE_VISIT_LIMIT`).
- Expanded `SYSTEM_WAIT` runtime validation to support the complete 1–86,400 second range while keeping imported workflows bounded.

### Fixed
- Enforced durable idempotency-key uniqueness before an action side effect starts, preventing a recovered execution from reserving the same action claim twice.
- Preserved `SubworkflowProvider` compatibility for both legacy three-argument implementations and budget-aware providers, while keeping nested execution inside the shared safety budget.
- Stopped a background-thread `PhoneStateListener` constructor crash (`Handler(Looper.myLooper()!!)` NPE on a Looper-less thread) that could silently kill telephony registration for connectivity and call-state monitoring on modern and legacy Android versions.
- Stopped `TaskManager.shutdown()` from crashing the process when it raced an idle worker: closing the queue wake-up channel while the worker was suspended on `receive()` delivered a `ClosedReceiveChannelException` into an unhandled `SupervisorJob` scope. The poll now consumes the close benignly via `receiveCatching` while preserving the historical cancel ordering.

### Tests
- Added deterministic JVM coverage for subworkflow input/output contracts, missing-provider fail-closed behavior, recursion-depth enforcement, approval gateway decision and timeout policy, ForEach scope restoration, saga reverse-order compensation, shared-budget propagation to nested execution, strict condition-failure semantics, and node-visit-limit enforcement.
- Repaired the connected-device `androidTest` suite, which had drifted from the current APIs and never compiled (CI runs no device): updated `Trigger` construction to the persisted `config` model, pinned the reminder notification read-back to an explicit `StatusBarNotification` type, and declared the kotlinx-serialization artifact for plugin-event payload assertions.
- Ran the full device-validation suite on a real phone (SDK 37): all tests pass, including the notification read-back contract, the production `TaskManager` resource-permit path, and the workflow-interpreter execution contract.

### Quality assurance
- GitHub Actions lint, unit tests, and the production build are green on `main`, with the JDK 21 unit-test toolchain pre-installed in CI for deterministic Robolectric runs.
- The strict execution contract keeps unsupported device/backend work unavailable rather than pretending it succeeded: subworkflows require a provider, approvals require a gateway, and both fail closed with diagnostics when absent.
- The on-device crash fixes were verified in both permission states (`READ_PHONE_STATE` granted and not granted): monitoring registers cleanly and the app process stays error-free.

## [v3.57.0] - 2026-09-02

### Added
- Added the master improvement and development plan ([`docs/ROADMAP_2026.md`](docs/ROADMAP_2026.md)): a comprehensive, evidence-based roadmap covering truthful execution, device-adaptive compatibility, catalog completeness, performance budgets, security, and release governance across six workstreams.
- Added deterministic **catalog parity gates** (`scripts/audit_catalog_and_releases.py`): every `TriggerType` (53) and `ActionType` (157) enum value must appear exactly once in the builder picker, with `PLUGIN_EVENT` pinned as the only restricted trigger; drift in either direction now fails CI instead of shipping unconfigurable catalog entries.
- Added **tag hygiene gate** for version tags: a `v*` tag must have its own complete `CHANGELOG.md` section (with `###` subsections), and any leftover `[Unreleased]` content blocks the release so notes can never be stale.
- Added **release-notes generator** (`scripts/generate_release_notes.py`): renders the tagged changelog section, absolute documentation links, the standing quality-evidence table, and install guidance into the GitHub Release body — replacing auto-generated commit lists with a professional, reviewed change record.
- Added a nightly CI schedule (06:00 UTC) on `main` to catch dependency rot and flaky tests between releases, with a schedule-scoped concurrency group so nightlies never cancel release builds.
- Added `docs/REQUIRED_CHECKS.md` documenting every CI gate with branch-protection setup instructions.
- Added `CatalogParityTest` in the builder module mirroring the catalog-parity invariants inside the JVM test suite.

### Changed
- GitHub Releases for version tags now publish **generated professional notes** (changelog-derived) instead of `--generate-notes`, and re-render the body when an existing release is re-published.
- Wired the catalog-parity and tag-hygiene gates into the `lint` CI job, running on every push, pull request, and tag.

### Quality assurance
- Catalog parity verified locally: 53 triggers (52 exposed, `PLUGIN_EVENT` restricted) and 157 actions each appear exactly once in the builder.
- Tag-hygiene and release-notes scripts verified against existing release tags (`v3.56.2` passes; unknown tags fail closed).
- Both new scripts are UTF-8-explicit and CRLF/LF line-ending agnostic, so Windows checkouts and Linux CI behave identically.

## [v3.56.2] - 2026-09-02

### Added
- Added the **GPS Geofence** trigger to the builder catalog, clearly labeled for location-based automation.
- Added the unified `CONNECTIVITY` trigger and `LOCATION_STATE` trigger to the builder, completing the user-facing catalog for all supported non-plugin trigger types.

### Changed
- Improved trigger categorization so legacy-compatible network and location-mode conditions can be configured for new automations without changing the persisted model or runtime contracts.
- Kept `PLUGIN_EVENT` intentionally restricted to the verified plugin configuration flow; it is not exposed in the generic picker and cannot be created with incomplete or untrusted configuration.

### Tests
- Added regression coverage for GPS geofences, unified connectivity, location-mode triggers, and the security boundary around plugin events.
- Verified the trigger catalog against the authoritative `TriggerType` enum and checked the working tree for whitespace errors.

## [v3.56.1] - 2026-09-02

### Fixed
- Made the `TaskManager.awaitIdle()` terminal barrier atomic by evaluating queue emptiness and active-task ownership under the same lock used by queue polling.
- Prevented deadline and recovery tests from observing a transient empty-queue/idle-worker window while lifecycle status publication was still in progress.

### Quality assurance
- Diagnosed the v3.56.0 CI failure from the failing deadline test, applied the runtime race fix, and passed the complete Android Lint and production build/unit-test workflow for commit `920116cd`.

## [v3.56.0] - 2026-09-02

### Added
- Persisted the immutable `workflowVersion` captured from the Automation definition at durable execution admission.
- Added regression coverage proving that the workflow revision survives checkpoint serialization and recovery reads.

### Changed
- Durable execution checkpoints now retain the workflow schema revision required to interpret an interrupted run safely.
- Existing checkpoint constructors remain backward-compatible through a schema-safe default revision of `1`.

### Quality assurance
- Passed the repository resource gate, Android Lint, and the complete Android unit-test and production build workflow in GitHub Actions for commit `1590184c`.

## [v3.55.1] - 2026-09-02

### Fixed
- Corrected the durable recovery regression test to follow the production contract: a checkpoint must be atomically claimed before it can be marked `RECOVERY_REQUIRED`.
- Preserved the strict persistence boundary while keeping recovery-required checkpoints excluded from automatic re-claim.

### Quality assurance
- Resolved the failure reported by CI, then passed the complete Android Lint and production build/unit-test workflow for commit `ddbac5dd`.

## [v3.55.0] - 2026-09-02

### Added
- Added an explicit transition contract for durable execution checkpoints, covering action progress, uncertainty, exit reconciliation, recovery claims, recovery-required state, and terminal completion.
- Added regression coverage for valid durable recovery paths and terminal-state immutability.

### Fixed
- Enforced durable lifecycle transitions inside the atomic `ActiveExecutionStore` update transaction instead of allowing arbitrary status replacement.
- Prevented `RECOVERY_REQUIRED` checkpoints from being claimed again automatically, preserving the unresolved state for explicit operator or coordinator handling.
- Preserved truthful recovery evidence by rejecting invalid checkpoint transitions immediately at the persistence boundary.

### Quality assurance
- Passed the repository resource gate, Android Lint, and the complete Android unit-test and production build workflow in GitHub Actions for commit `db70338a`.

## [v3.54.1] - 2026-09-01

### Fixed
- Preserved terminal deadline evidence when a cancellation request races with the worker's deadline checkpoint. The runtime now allows `CANCEL_REQUESTED` to resolve to `DEADLINE_EXCEEDED` without reopening or silently discarding the task.
- Kept lifecycle transitions explicit and bounded across queued, running, retry, cancellation, timeout, deadline, rejection, and terminal states.

### Quality assurance
- Corrected the regression test scope identified by CI, then passed the complete Android Lint and production build/release validation workflow for commit `1d31ccd5`.

## [v3.54.0] - 2026-09-01

### Added
- Added an explicit transition contract for the existing `TaskLifecycleState` runtime. Initial admission, execution, retry, cancellation, timeout, deadline, rejection, and terminal states now have documented legal transitions.
- Added regression coverage for valid lifecycle paths, terminal-state immutability, invalid cancellation, and cancellation after successful completion.

### Fixed
- Prevented terminal task statuses from being reopened by late cancellation requests.
- Prevented duplicate or raced task rejections from overwriting an already-running task's lifecycle status under the same task ID.
- Made invalid lifecycle writes fail immediately at the runtime boundary instead of silently replacing durable-looking status evidence.

### Documentation
- Added [`docs/audit/CURRENT_ARCHITECTURE_2026.md`](docs/audit/CURRENT_ARCHITECTURE_2026.md), [`docs/audit/GAP_ANALYSIS_2026.md`](docs/audit/GAP_ANALYSIS_2026.md), and the machine-readable [`docs/audit/GAP_ANALYSIS_2026.json`](docs/audit/GAP_ANALYSIS_2026.json) as the forensic baseline required before broader architecture work.

### Quality assurance
- Passed the repository resource gate, Android Lint, and production build/release validation in GitHub Actions for commit `834b8257`.

## [v3.53.1] - 2026-09-01

### Fixed
- Normalized empty Private DNS specifier read-back values across Android and OEM settings providers. `OFF` and `AUTOMATIC` requests now succeed only when the mode matches and an empty or null provider specifier is confirmed, while strict hostname mode continues to require an exact hostname match.
- Preserved truthful outcome reporting: a mismatched mode or provider hostname remains a failed action and is never converted into a success based solely on the write command result.

### Quality assurance
- Passed the repository resource gate, Android Lint, and production build/release validation in GitHub Actions for commit `5e5c43ea`.

## [v3.53.0] - 2026-08-30

### Added
- Added read-only DNS inspection through Android's public `ConnectivityManager` and `LinkProperties` APIs, including active-network DNS servers and Private DNS mode, hostname, and active state.
- Added a ROM-aware DNS provider catalog. NexaFlow discovers provider profiles exposed by ROM resources and falls back to a validated built-in catalog containing Cloudflare, Google, Quad9, and AdGuard profiles when no ROM catalog is available.
- Added DNS provider selection to connectivity action configuration, with hostname and server-address validation before a configuration request is accepted.
- Added system-controller DNS inspection and guarded configuration façades so unsupported or unverifiable platform operations are reported explicitly instead of being reported as successful.

### Changed
- DNS inspection now fails closed when `ACCESS_NETWORK_STATE` is unavailable and returns an explicit diagnostic rather than attempting an unverified read.
- Kept DNS capability boundaries explicit: Android public APIs can inspect current DNS state, while Private DNS changes require a supported ROM or privileged implementation and postcondition verification.

### Tests
- Added DNS hostname-validation coverage and provider-catalog integrity tests, including duplicate prevention, malformed profile rejection, and stable provider metadata checks.
- Passed the repository resource gate, Android Lint, and the production build/release validation workflow for commit `70eaa9cf`.

### Documentation
- Added [`docs/dns-platform-findings.md`](docs/dns-platform-findings.md) describing Android API limitations, ROM resource discovery, capability boundaries, and device-verification requirements.

## [v3.52.3] - 2026-08-29

### Fixed
- Prevented valid time schedules from being reported as armed when AlarmManager registration fails. The durable occurrence is rolled back unless its START and, when applicable, END alarms are both accepted by the platform.
- Added bounded receiver-level re-delivery for transient failures. Retries reuse the same occurrence and generation, so recovery cannot create a duplicate logical execution.
- Preserved strict observability by logging an explicit terminal diagnostic when bounded re-delivery is exhausted instead of silently dropping the event.

### Quality assurance
- Resource hygiene and repository checks pass. Android unit tests and release validation are executed by the GitHub Actions pipeline.

## [v3.52.2] - 2026-08-29

### Fixed
- Stopped discarding valid time-range START occurrences when Android delivers the alarm after the nominal window end. NexaFlow now executes the admitted START occurrence and closes that same occurrence through `ExitCoordinator`.
- Preserved strict malformed-range rejection: a RANGE trigger without a valid end remains blocked and is recorded instead of being executed ambiguously.
- Ensured delayed range delivery cannot leave the main action unexecuted merely because the device was in Doze, the process was recreated, or AlarmManager delivered late.

### Tests
- Added regression coverage proving late valid range starts remain executable, malformed ranges are rejected, and one-shot schedules retain their existing behavior.

### Documentation
- Extended the strict lifecycle documentation and release notes to cover delayed AlarmManager delivery and guaranteed terminal cleanup.

## [v3.52.1] - 2026-08-29

### Fixed
- Routed location-triggered automation activation through the durable `AutomationLifecycleContext` and `AutomationRuntimeStore` path. A location task is now considered active only after its exact occurrence is durably admitted.
- Routed location condition endings through `ExitCoordinator` instead of dispatching end behavior directly from `LocationMonitor`. End actions are now atomically claimed, executed, recorded, and completed through the same lifecycle contract as other stateful triggers.
- Preserved active markers and durable `EXIT_FAILED` state when an end action fails or is already in progress. The task is no longer cleared or reported as finished until a successful exit is confirmed.
- Restored location lifecycle ownership from the durable runtime ledger before listening for fixes, preventing process-death gaps from silently losing an active task.
- Serialized location evaluations to prevent activation and exit callbacks from racing each other.

### Documentation
- Added [`docs/STRICT_TASK_LIFECYCLE.md`](docs/STRICT_TASK_LIFECYCLE.md) describing the failure modes, guarantees, and recovery behavior.

### Quality assurance
- The local resource gate passes. Android unit tests require an Android SDK, which is unavailable in the local sandbox; GitHub Actions remains the authoritative Android validation gate.

## [v3.52.0] - 2026-08-28

### Added
- Added provider-independent **Selected Location** automation triggers alongside the existing **Current Location** flow. Selected locations persist latitude, longitude, radius, event type, and an optional source marker through the existing trigger configuration contract.
- Added strict coordinate and radius validation, lightweight Haversine distance evaluation, inclusive radius boundaries, and transition-only `ENTER` / `EXIT` event detection.
- Added restart-safe fixed-location initialization. A persisted selected location enters `UNKNOWN` state after restart and does not emit a false event until a real outside/inside transition is observed.
- Added an external maps selection flow using Android's standard `ACTION_VIEW` `geo:` URI. When no compatible maps application is available or coordinate return is unsupported, NexaFlow provides a validated manual coordinate-entry fallback.

### Changed
- Reused the existing location monitor, permission handling, background execution, persistence, event bus, and workflow execution layers. The trigger emits normalized events and never executes device actions directly.
- Removed all embedded map-rendering infrastructure, including Google Maps SDK, Maps API-key configuration, OpenStreetMap/Leaflet assets, map tiles, and provider-specific verification metadata.

### Tests
- Added unit coverage for coordinate validation, `NaN`/infinity rejection, radius bounds, distance evaluation, boundary inclusion, ENTER/EXIT transitions, duplicate suppression, and restart semantics.
- Added builder validation coverage for malformed coordinates and unsupported radius values.

### Documentation
- Added [`docs/FIXED_LOCATION_TRIGGER.md`](docs/FIXED_LOCATION_TRIGGER.md) with architecture, persistence, permissions, testing, command, and device-verification details.

## [v3.50.4] - 2026-08-27

### Fixed
- Declared `ACCESS_NETWORK_STATE` in the `core:common` Android library manifest, matching the public `TetheringManager.registerTetheringEventCallback()` contract. This removes the CI-blocking `MissingPermission` finding while preserving strict Android Lint enforcement.
- Changed workflow branch evaluation to fail closed. If a branch condition throws, NexaFlow now records the failure and executes neither branch instead of treating the error as `false` and potentially running the fallback path.
- Preserved failed rollback attempts in the workflow timeline, so compensation errors are visible alongside the original action failure.
- Retained the last `WaitUntil` condition exception in its timeout outcome, separating an unavailable device state from an ordinary unmet condition.
- Verified volume writes after `AudioManager.setStreamVolume()`. Android 17 can reject background audio changes silently; NexaFlow now reports a clear failed action instead of a false success when read-back does not match the requested value.

### Changed
- Added Android 17 automation research, a release-quality audit record, and a device acceptance protocol covering exact alarms, 22:00–06:00 ranges, Doze, reboot recovery, Hotspot state, telephony capability, permissions, local-network HTTP, and background UI restrictions.
- Kept the execution architecture capability-gated: protected telephony, hotspot, and secure-settings actions remain explicitly unavailable without a verified privileged route and postcondition evidence.

### Quality assurance
- CI now passes its complete lint, unit-test, debug/release APK, AAB, signing, dependency-verification, page-alignment, and bundle validation gates for this release candidate.
- Added a regression test proving that a failed branch condition cannot execute either the true or false action path.

## [v3.50.3] - 2026-08-27

### Fixed
- Rebuilt all durable time-trigger occurrences immediately after the Root/Shizuku permission pipeline has **verified** Android 12+ exact-alarm access. This repairs the case where Android had already canceled exact `START` and `END` alarms and a privileged app-op repair did not produce the framework grant broadcast.
- Added the explicit `END_AUTOMATION` receiver action to the app manifest, so a scheduled range end is declared alongside its paired `RUN_AUTOMATION` start under hardened intent matching.
- Closed a task-cancellation race exposed by remote CI: a child execution job is now registered before it starts, so a cancellation cannot land in the launch-to-registration window and wait for the task's next suspension.
- Stopped treating the `USER` allowed-network-types mask as the cellular hardware capability menu. A current GSM-only preference no longer hides modem-supported LTE or NR profiles by itself.

### Changed
- Added a reviewed, closed elevated profile read. A Shizuku UserService first reads AOSP `ITelephony.getRadioAccessFamily(slot)`; when that binder route is unavailable, Root/Shizuku can read only `ro.telephony.default_network` and NexaFlow maps a known AOSP RIL mode through a strict 0–33 table. Unknown, malformed, or slot-ambiguous values remain unavailable.
- Kept carrier and OEM limits authoritative: the picker still exposes only evidence-backed profiles, and a selected profile remains successful only after subscription-scoped allowed-network-type read-back confirms it.
- Added deterministic coverage for the private verified-exact-alarm recheck, the closed profile-read operation, AOSP default-network mappings including LTE/GSM/WCDMA, full LTE legacy coverage, NR-only, and NR/LTE, plus a cancellation test that waits for a real coroutine cancellation rather than a fixed delay.

### Compatibility and safety
- The modem-default property is a device capability hint, not a claim that the active carrier supports every profile. NexaFlow does not build a synthetic universal 2G/3G/4G/5G list, does not execute a global `preferred_network_mode` write, and does not report an unverified write as applied.
- Precise wall-clock automation continues to require Android exact-alarm access. With that access, NexaFlow uses the existing `RTC_WAKEUP` exact-and-idle-capable path; where Android blocks it, the documented inexact fallback is subject to system delay rather than being described as punctual execution.

## [v3.50.1] - 2026-08-27

### Fixed
- Re-armed the **existing immutable END alarm** for an already-active time range during schedule reconciliation. After Android drops alarms at reboot, a 22:00–06:00 range now restores only the END `PendingIntent` that matches its active occurrence id, schedule generation, and expected end; it does not create a new occurrence or execute an exit while re-arming.
- Added regression coverage proving that a time-window end dispatches a configured `SYSTEM_RINGER_MODE` `SET_VALUE` payload of `{mode=NORMAL}` exactly once through the same `ExecutionEngine` / `ActionRegistry` path as an ordinary action.
- Corrected all local `TelephonyManager.NetworkTypeBitMask` family values to AOSP's `1 << (NETWORK_TYPE - 1)` positions. Valid Android 17 LTE/NR masks can no longer be filtered to zero solely by NexaFlow's local mapping.
- Replaced the ineffective notification-policy `appops` grant with AOSP `cmd notification allow_dnd` for the current user. When Android rejects `SILENT`/`VIBRATE → NORMAL`, NexaFlow verifies the app-specific notification-policy grant and retries `AudioManager`; it does not change the user's interruption filter or notification policy.

### Changed
- New dynamic Cellular Network profiles now persist `aosp-network-type-bitmask-v1`. A saved dynamic mask without this schema is rejected safely until the user reselects the profile, avoiding an ambiguous radio-mode migration after the bit-position correction.
- Extended network-mode regression coverage to AOSP named, decimal, and binary LTE/LTE-CA/NR read-back values, and extended elevated-operation coverage to the closed notification-policy grant command.

### Compatibility and safety
- Static `2G`, `3G`, `4G`, `5G`, and `AUTO` actions without a dynamic mask continue using the corrected mappings. Existing raw per-subscription restore snapshots are preserved because they contain masks read from the device, not locally generated family profiles.
- An interrupted `EXITING` lifecycle is intentionally not replayed automatically. Arbitrary exit side effects remain uncertain until a future durable per-exit checkpoint and ownership epoch can make recovery safe. `EXIT_FAILED` remains observable and retains the established bounded recovery policy.
- Root or Shizuku does not override ROM, modem, SIM, carrier, or OEM telephony-service limits. Network writes remain subscription-scoped and require same-SIM read-back confirmation; the obsolete global `preferred_network_mode` write remains excluded.

## [v3.50.0] - 2026-08-27

### Added
- Added a first-class **Hotspot** trigger with an explicit `ON` / `OFF` state. It is indexed under the existing connectivity source, evaluated by the durable connectivity monitor, represented in manual condition gating, and rendered in the builder, dashboard, and routine details.
- Added bounded, local-only diagnostics to unreadable cellular network-mode capability snapshots. The editor can now distinguish a missing `READ_PHONE_STATE` grant, unavailable elevated session, failed privileged command, or unparseable returned cellular mask instead of showing one opaque unavailable state.
- Added regression coverage proving that Hotspot is addable, the legacy combined Connectivity picker option is hidden for new tasks, and the new type resolves to the canonical connectivity source.

### Changed
- Updated the network-mode capability card to use the targeted root permission-and-refresh flow when Shizuku is not the live route. After the root result, the card reloads its capability snapshot rather than retaining the stale unreadable result.
- Simplified the new-trigger connectivity picker by hiding the legacy combined **Connectivity** entry. Saved Connectivity automations remain readable, editable, and executable for compatibility; dedicated Hotspot and Cellular Network entries replace its duplicated new-task choices.
- Updated manual hotspot evaluation so an unreadable `tether_on` setting remains unknown. It cannot be interpreted as `OFF` or initiate a speculative end path.

### Fixed
- Escaped the French Hotspot label according to Android resource syntax, restoring Android resource compilation and lint acceptance.

### Compatibility and safety
- No saved automation is migrated, deleted, or rewritten by this release. Existing legacy Connectivity records—including historic hotspot/network-mode configurations—continue to load so users can preserve or intentionally replace them.
- Network-mode writes retain the existing same-subscription privileged write-and-read-back verification. A root grant does not override OEM, RIL, modem, SIM, or carrier restrictions; an unconfirmed or unparsable result stays unavailable rather than being reported as applied.

## [v3.49.0] - 2026-08-27

### Added
- Added occurrence-aware lifecycle ownership to the consolidated settings-state monitor. The supported settings/radio/state triggers now admit a durable occurrence before main effects, retain an unreadable device state as `UNKNOWN`, and route confirmed condition ends through `ExitCoordinator`.
- Added durable recovery preservation for interrupted main actions. An action cancelled after it has started is retained as `ACTION_UNKNOWN` and is classified at startup as verification-or-compensation work instead of being silently removed or replayed.
- Added regression coverage for checkpoint recovery classification, failed whole-snapshot restore reporting, manual event-trigger safety, settings-state `UNKNOWN` retention, and sustained charger state after restart.

### Changed
- Updated whole-snapshot revert to aggregate real `SystemControlResult` values for the setting families changed by the automation. `STATE_RESTORE` now accurately carries the resulting success state and diagnostic message.
- Updated manual **Run now** gating to use typed condition results. An unverifiable event or ambiguous legacy platform read records a visible safe skip; it does not synthesize configured end actions. A confirmed false condition continues to use the explicit end-behavior path.
- Updated hotspot state observation so an unreadable `tether_on` setting remains unknown rather than being interpreted as `OFF`.
- Updated the privileged location-mode command to use the shared `SafeCommandBuilder` and declared its security-module dependency explicitly.

### Fixed
- Fixed battery and charger monitor branches that could request an exit for an occurrence that was still in its configured active state after reconciliation or restart.
- Fixed settings-state monitor cleanup ordering: compatibility active keys are now mirrored only after durable admission and are removed only after successful coordinated completion or a verified absent/stale occurrence.
- Fixed a false-success path where whole-device restore failures could be recorded as a successful exit and therefore consume recovery evidence.
- Fixed CI-detected test contracts during validation, including coroutine/JUnit lifecycle signatures and the prior manual event-trigger expectation.

## [v3.48.0] - 2026-08-27

### Added
- Added a bounded, local occurrence-aware runtime ledger for stateful automation exits. The ledger persists `ACTIVE`, `EXITING`, and observable `EXIT_FAILED` lifecycle states before main actions can acquire effects.
- Added a single `ExitCoordinator` that atomically claims a matching occurrence before end behavior executes, preventing duplicate exits when trigger-false, time-window-end, process-recovery, or boot-recovery signals race.
- Added immutable time-range occurrence and configuration-generation tokens to scheduled start/end alarms, together with deterministic coverage for stale-generation, end-window, atomic-claim, failed-exit, and recovery behavior.
- Added local snapshot serialization support for restore-on-exit recovery; malformed stored snapshots are rejected safely.

### Changed
- Updated time-range scheduling and alarm delivery to validate occurrence id, generation, window start, and window end against durable schedule state before execution. A late start for an expired range is consumed and rescheduled rather than applied late.
- Updated the connectivity and battery/charger monitors to use durable lifecycle admission and coordinated exits. Compatibility active keys are retained until exit has completed or no active occurrence remains.
- Updated process, boot, clock, and exact-alarm-access reconciliation to rebuild valid schedule identity and resume only safe elapsed-window or visible failed-exit cleanup.
- Bounded automatic failed-exit recovery to one additional attempt after the initial exit attempt; a further failure remains visible for diagnosis instead of producing an unbounded retry loop.

### Fixed
- Prevented a monitor race, process death, or exit-action failure from clearing the only evidence that a stateful automation still requires exit cleanup.
- Prevented stale or reconfigured time-window end alarms from consuming a newer automation lifecycle.
- Resolved CI findings uncovered during this release cycle, including a lifecycle-state exhaustiveness condition, test/module visibility contracts, and deterministic monitor-fixture assertions.

## [v3.47.0] - 2026-08-27

### Added
- Added an explicit active-data subscription identity to the read-only network-mode capability snapshot. New network-mode actions now prefer that confirmed data SIM when the user has not previously selected a SIM, while a valid saved subscription remains authoritative.
- Added distinct configured and known-effective allowed-network-type masks. The effective value is shown only when Android exposes both the USER and CARRIER restrictions; it is never inferred from the live radio technology.
- Added deterministic JVM coverage for user/carrier bitmask intersection and single-/dual-SIM selection precedence.

### Changed
- Updated the network-mode editor to identify the active data SIM and to display configured network types separately from a stricter effective restriction when it is readable.
- Updated dynamic network-mode summaries so saved device-specific profiles describe their confirmed radio families instead of being incorrectly shown as **Auto**.
- Moved `SYSTEM_NETWORK_MODE` application onto `Dispatchers.IO` at the action-handler boundary, preventing its telephony binder, Root, or Shizuku work from blocking a caller on the main thread.

### Fixed
- Removed the synthetic subscription-id fallback from legacy all-SIM network-mode writes. NexaFlow now rejects an unreadable subscription set rather than risking a write to an inferred SIM.
- Preserved elevated USER-mask read-back when Android can expose selectable hardware/carrier capability but blocks the app-level USER getter.
- Resolved two CI findings during release validation: a Turkish Android-resource apostrophe was escaped correctly, and the explicit phone-state permission guard is documented for Android Lint.

## [v3.46.0] - 2026-08-27

### Added
- Added a local **Skipped runs** filter to global and routine-scoped execution history. It is backed by a dedicated Room Paging query that selects only successful records using the established `Skipped:` protocol, preserving newest-first ordering.
- Added a contextual **View skipped runs** action to the routine Execution health card when it has recorded skips, opening the same routine history with `outcome=skipped` selected.
- Added localized skipped-state labels and empty-state guidance across every shipped locale, domain outcome-classification coverage, database paging coverage, route coverage, and history-screen interaction coverage.

### Changed
- Replaced the binary history-filter state with an explicit outcome model shared by route parsing, paging, health aggregation, result presentation, and history status pills.
- Made the three local filter controls wrap on narrow displays or with longer localized labels rather than forcing a single fixed row.

### Fixed
- Corrected History status pills so intentionally skipped runs are labeled **Skipped** instead of being visually reported as ordinary successful completions.

## [v3.45.0] - 2026-08-27

### Added
- Added a local **All runs / Failures** filter to execution history. It works in both global and routine-scoped history and is backed by a Room Paging query that returns only persisted failed executions when selected.
- Added a contextual **View failures** action to the Execution health card when a routine has recorded failures, taking users directly to that routine’s filtered evidence.
- Added localized filter labels and empty-state guidance across every shipped locale, plus DAO and navigation regression coverage for the failure-only route and query.

### Changed
- Extended the optional History navigation arguments with a typed outcome value. The History ViewModel derives its initial filter from the route and recreates the Paging source when the user changes the selected outcome.
- Kept the existing global and routine-scoped history views unchanged by default: both still show all recorded runs unless the user explicitly selects **Failures**.

### Fixed
- Resolved two release-validation issues found by CI: the Compose state-delegate import was missing from the new filter UI, and the English filter label conflicted with the existing failed-status assertion in the screen test.

## [v3.44.0] - 2026-08-27

### Added
- Added routine-scoped execution history. The Execution health card now opens a paged history view filtered to the selected routine, so users can inspect the evidence behind a health summary without scanning unrelated runs.
- Added a database-side Room Paging query for automation-specific execution records, preserving newest-first ordering and the existing bounded retention policy.
- Added localized routine-history navigation labels across every shipped locale, DAO coverage for filtered paging, and a regression test for the routine-history route.

### Changed
- Extended the History destination with an optional routine identifier while preserving the existing global history view from Settings when no identifier is supplied.

### Fixed
- Corrected typed retrieval of the optional Navigation argument and kept routine-history route construction JVM-safe, resolving CI compilation and unit-test failures discovered during release validation.

## [v3.43.0] - 2026-08-26

### Added
- Added a read-only Execution health card to routine details. It summarizes persisted local execution history as no recorded runs, activity recorded, or needs attention after repeated failures.
- Added completed, skipped, and failed execution counts plus the latest recorded failure message when available, so users can investigate a routine without starting from a global history screen.
- Added localized execution-health guidance across every shipped locale and regression coverage for health-status presentation mapping.

### Changed
- Connected routine details to the existing HealthRepository flow so the displayed health summary updates reactively from local execution history without telemetry, a background logging service, or a schema change.

## [v3.42.0] - 2026-08-26

### Added
- Made capability-filtered, bundled starter routines discoverable from the new-task builder. Users can choose an editable local starting point only when its declared trigger and action requirements are available on the current device.
- Added localized starter-routine names and review guidance across every shipped locale.
- Added regression coverage for starter-routine title mapping and the first-save activation policy.

### Changed
- Newly saved starter routines now remain disabled until the user reviews the generated trigger/action configuration and explicitly enables the routine from the dashboard. Manual creation and edits keep their existing activation behavior.

### Fixed
- Escaped French starter-routine strings correctly so Android resource compilation and Android Lint complete successfully.

## [v3.41.5] - 2026-08-26

### Fixed
- Rejected backup files that contain duplicate automation IDs before any write occurs. This prevents ambiguous dependency remapping and protects against silent replacement of one imported automation by another.

### Added
- Added a regression test that proves duplicate automation IDs are rejected atomically, with no automation saved.
- Added a 2026 competitive and Android-platform research record to guide reliability, portability, and release-quality investments.

## [v3.41.4] - 2026-08-25

### Fixed
- Prevented imported JSON automations from silently replacing local automations when IDs collide; imported maintenance dependencies are remapped with the copied automation and all imported automations remain disabled for review.
- Stopped requesting the Android notification runtime permission automatically on first launch; notification posting access is now requested through user-visible features that need it.
- Corrected the notification trigger permission catalog: notification monitoring requires Notification Listener access, not the unrelated POST_NOTIFICATIONS runtime permission.

### Added
- Added regression coverage for collision-safe backup import with dependency remapping and for notification-trigger permission separation.
- Added an Arabic benchmark remediation report documenting the evidence, Android constraints, selected fixes, and deferred roadmap.

## [Historical baseline]

### Added
- Initial project setup and modular architecture.
- Basic UI for Dashboard, Automation Builder, Profiles, History, Capability Center, Icon Picker, Themes, Widgets, and Settings.
- Core domain, data, and database layers.
- Automation engine structure.
- Real Profiles feature: Room-backed `ProfileEntity`/`ProfileDao`, domain model + repository, CRUD dialogs, activate/deactivate, delete disables automations.
- Real Themes feature: accent-aware color schemes, dark mode toggle, 6 accent swatches persisted via DataStore `ThemePreferences`, applied in `MainActivity`.
- Real Widgets feature: `NexaFlowToggleWidgetProvider` + `NexaFlowStatusWidgetProvider` home screen widgets with automatic refresh on automation changes.
- Real Settings feature: accessibility service status, monitoring service toggle, About dialog, theme/widgets/capability center/history navigation rows.
- Foreground `MonitoringService` (`specialUse`) hosting battery/device/connectivity/location monitors, with `isRunning`/`stop`.
- Automation-change broadcast `ACTION_AUTOMATIONS_CHANGED` sent after each execution.
- Boot recovery: scheduled time automations and the monitoring service are restored after device reboot.
- Functional Capability Center with real permission status pills and deep-link Grant/Settings actions.
- Unit tests for `ConditionEvaluator`, `AutomationMapper`, `ProfileMapper`, and `ExecutionRecordMapper`; CI now runs `testDebugUnitTest`.
- Removed empty `core/common`, `core/permissions`, and `core/security` modules; disabled Jetifier; `allowBackup=false`; monochrome launcher icon.
