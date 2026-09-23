# Changelog

## [Unreleased]

### Added — Wear OS automation foundation

- Added a shared `:core:wear-protocol` contract used by the phone, execution core,
  and Wear app so Data Layer paths, keys, capabilities and protocol versioning have
  one source of truth instead of mirrored literals.
- Added versioned v1 paths for commands, events, results, device state and capability
  negotiation while preserving the existing run/toggle/sync paths for installed-version
  compatibility.
- Added typed serializable envelopes, command/result models, watch event kinds, device
  descriptors, capability snapshots, TTL handling and forward-compatible JSON decoding,
  with contract tests covering legacy stability and round trips.


## [v3.87.0] - 2026-09-23

### Added — Structured execution diagnostics

- **Added a user-facing “Why didn’t this run?” diagnosis card to execution details.**
  History entries are correlated with typed execution traces through a stable per-run
  identifier, with an exact-time compatibility fallback for legacy rows.
- Diagnostics now explain the most important blocked and failed execution paths,
  including constraints, ALL-trigger gates, missing capabilities or permissions,
  invalid task configuration, maintenance-window waits and duplicates,
  admission/lifecycle conflicts, and failed actions.
- Supporting detail is redacted at the logging boundary and accompanied by localized
  suggested fixes. Successful runs remain free of failure-oriented diagnostics.

### Changed — Capability outcomes and privileged execution

- **Hardened capability-result semantics across the execution engine.** Only a terminal
  capability `SUCCESS` is promoted to action success; `PARTIAL`,
  `PENDING_USER_ACTION`, `UNKNOWN`, failures, and cancellations are preserved as
  non-success outcomes instead of being accidentally reported as completed actions.
- **Completed bounded Shizuku and Root fallbacks for registered semantic writes.**
  Location, airplane mode, Data Saver, rotation, brightness, screen timeout, and DND
  now use typed privileged operations rather than free-form shell input.
- Preserved required post-action verification, including scalar read-back for
  brightness and screen timeout, and removed unwired `WRITE_SETTINGS` strategy
  declarations from the semantic registry.
- Shizuku permission requests now return definitive grant results, coalesce concurrent
  callers behind a single prompt, recover pending callbacks on binder/listener
  failures, and run the verified permission-repair pass after a confirmed grant.
- Bluetooth semantic availability on Android 12+ now requires
  `BLUETOOTH_CONNECT`, matching the actual execution requirement. Public DND
  permission failures are reported as permission denials instead of ambiguous
  capability failures.

### Hardened — Workflow contracts, identity, and secret safety

- Strengthened the v3.86 workflow-document foundation so legacy automation state is
  preserved losslessly across document round-trips, including visual metadata,
  priority/enabled state, ANY/ALL trigger mode, cooldown, workflow version,
  maintenance configuration, exit behavior, categories, and timestamps.
- Workflow identity hashing now canonicalizes nested JSON object keys, producing
  deterministic hashes for semantically equivalent configuration maps regardless of
  insertion order.
- Structural validation now aligns more closely with runtime bounds, including retry
  backoff limits and duplicate exit-action identifiers.
- Deep-link authorization tokens remain outside workflow documents/exports, and
  maintenance metadata is kept behind schema-pinned data-transfer contracts.

### Improved — Builder UX and background update checks

- **Improved trigger-card identification in the automation builder.** Each card now
  shows the localized trigger type as its primary label, keeps the configured value
  as a concise secondary summary, and preserves locale-aware RTL/LTR rendering while
  keeping reorder/remove controls stable.
- Automatic update checks now default to a weekly cadence, perform the first quiet
  background check after a six-hour grace period, retry transient GitHub/network
  failures with bounded exponential backoff, and avoid unnecessary WorkManager
  rescheduling.
- Update notifications remain silent/low-priority, are deduplicated per release, and
  are cancelled together with periodic work when automatic checks are disabled.

### Fixed

- Prevented timed-out Root probes from surfacing spurious
  `java.io.IOException: Stream closed` failures when forced process termination
  closes a reader pipe during cleanup.
- Fixed brightness and screen-timeout semantic mapping so scalar writes are not
  rejected by an unrelated boolean compatibility rule.
- Reconciled bounded brightness and timeout state read-back after branch integration,
  preserving strict verification behavior on the final mainline.

### Tests and reliability

- Expanded regression coverage for strict capability-result mapping, semantic
  operation parity, Shizuku/Root command shapes and verification, Bluetooth
  readiness, scalar writes, workflow document round-trips and validation, execution
  diagnostics, and the History UI.
- Added targeted persistence regression coverage proving edited automations replace
  their stored trigger state only after persistence completes, including trigger
  removal/addition and ANY → ALL changes.
- Branch-integration and CI contracts were hardened so the consolidated mainline
  preserves the release, privileged-execution, and Wear Data Layer identity
  invariants established by earlier releases.

### Compatibility

- No database schema migration is introduced by this release.
- Existing automations remain compatible with the current persisted storage model.
- Privileged actions continue to require the corresponding Android, Shizuku, or Root
  capability and permission before execution.

## [v3.86.1] - 2026-09-23

### Fixed — Wear OS companion synchronization

- **Restored Wearable Data Layer connectivity between the phone and watch apps.**
  The Wear APK now uses the same installed application ID as the phone app
  (`com.nexaflow.app`) while retaining its independent Kotlin namespace
  (`com.nexaflow.wear`).
- **Resolved the permanent “Connecting” state reported on correctly paired
  devices.** Google Play services requires Wearable Data Layer peers to share
  both the package name and signing certificate; the previous package mismatch
  prevented NexaFlow from establishing its companion communication channel.
- **Hardened the build configuration against future identity drift.** The phone
  and Wear modules now consume a single Gradle property for the shared
  application ID, so release builds cannot silently diverge again.

### Compatibility

- No automation data, trigger configuration, or execution behavior is changed.
- Existing phone installations remain on the same application ID.
- Users should install the matching v3.86.1 phone and Wear APKs so both peers
  have the same release identity and signing certificate.

Fixes #3.

## [v3.86.0] - 2026-09-23

### Added — WorkflowDocumentV1: versioned persisted workflow foundation (P0.1) and typed execution tracing (P0.4)

**Milestone A groundwork** toward a unified authoring/runtime model. No user-facing
behavior changes: every existing task continues to read, run and sync exactly
as before — the legacy `Automation` remains the storage format.

- **`WorkflowDocumentV1`** (new, `domain/workflow`): the versioned persisted
  workflow contract — `schemaVersion` gate, immutable-revision semantics,
  stable node ids, declared variables, dependency and risk descriptors, and a
  deterministic content hash for diagnostics equality.
- **Conditions are data**: `ConditionExpr` / `ValueExpr` replace persisted
  lambdas end-to-end. The runtime `WorkflowCondition` lambda is produced only
  at the execution boundary by the new `WorkflowDocumentCompiler`
  (`core:execution`), which maps documents onto the existing graph runtime
  and fails loudly on unregistered named predicates instead of guessing.
- **Lossless migration surface**: `WorkflowDocumentMappers` maps legacy tasks
  to documents and back without interpretation or invented defaults; the
  legacy-flat structure is enforced loudly (complex graphs route to the
  graph runtime, never silently flattened).
- **Safe forward rejection**: unknown schema versions and unknown node kinds
  fail with typed errors instead of loading half-parsed definitions.
- **Bounded structural validator**: empty graphs, duplicate node ids and
  runaway loop/retry/timeout bounds are rejected before persist or run.
- **Typed execution trace events (P0.4)**: `ExecutionTraceEvent`,
  `TracePhase`, canonical `TraceReasons` and a `TraceRecorder` that writes
  structured rows onto the *existing* timeline (no parallel logging system)
  with secret redaction applied at the record boundary. The engine now emits
  typed gate-blocked events (`CONSTRAINT_BLOCKED`,
  `TRIGGER_ALL_GATE_BLOCKED`) — the data source for the upcoming
  "Why didn't this run?" surface.
- **22 new contract tests** pinning: legacy↔document round-trips,
  unknown-version/kind rejection, validation parity with runtime bounds,
  migration idempotency, revision-independent content hash, literal/data
  condition evaluation, fail-closed context references, and trace redaction.

### Hardened — CapabilityRouter decision integrity (NF-P0-002, NF-P0-003)

**NF-P0-002 — Evidence and health are scored only after verification:**

- `CapabilityRouter` no longer records success evidence/health on a raw
  transport `SUCCESS`. Verification now runs first; the post-verification
  verdict is what gets scored (verified success → verified evidence + healthy;
  unverified success → unverified evidence only; verification failure or
  unconfirmed outcome → failure evidence + unhealthy).
- `VerificationMode.REQUIRED` is now strict. A transport success whose
  read-back is observable but contradicts the request fails with
  `VERIFICATION_FAILED`; a transport success whose post-condition **cannot**
  be read back is reclassified as `UNKNOWN` (outcome unconfirmed) instead of
  being reported as success — callers reconcile instead of trusting an
  unobserved claim.
- Value-write verification now reads the actual applied scalar
  (`screen_brightness`, `screen_off_timeout`) via a new
  `CapabilityStrategy.readStateValue` seam implemented by the Android public
  API and Shizuku strategies, instead of fabricating a boolean verdict.
- Reconciliation of `UNKNOWN` outcomes is itself the verification pass: a
  matched read-back scores verified evidence and healthy health; a mismatched
  or unreadable read scores failure.
- `PENDING_USER_ACTION` and `CANCELLED` are terminal by contract: the router
  neither scores a failure (nothing failed inside the strategy) nor falls
  through to a privileged candidate (no privilege escalation by accident).

**NF-P0-003 — Central typed parameter validation:**

- New `OperationParameterValidator` is the single validation point between
  the registry spec and strategy dispatch. Type, integer range, allowlist and
  length checks are all enforced there — presence-only checks are gone.
- Every `CapabilityParameterType` now enforces an exact grammar: `BOOLEAN`
  accepts only canonical/wire forms (`true`/`false`/`1`/`0`), `INTEGER`
  accepts canonical digits within `minimumInteger`/`maximumInteger`,
  `PACKAGE_NAME` enforces the Android package grammar (no spaces, shell
  metacharacters, empty labels or leading digits), `HTTPS_URL` requires the
  https scheme with a host and no embedded credentials, `CONTENT_URI`
  requires a `content://` provider URI, `OPAQUE_REFERENCE` rejects whitespace
  and shell metacharacters, and `STRING` honors length and allowlist bounds.
- All violations for a request are reported together in the spec's parameter
  order with `INVALID_CONFIGURATION`, before any strategy availability probe
  or execution runs.

### Migrated — package operations fully on the semantic layer

- Package force-stop, enable/disable and clear-data now run exclusively
  through the semantic operation chain (`PACKAGE_FORCE_STOP`,
  `PACKAGE_SET_ENABLED_STATE`, `PACKAGE_CLEAR_DATA`) via the closed
  `PrivilegedOperation` algebra dispatched by `ShizukuTypedStrategy` and
  `RootTypedStrategy` — the legacy direct-handler paths remain only as
  unrouted fallbacks and can no longer bypass the router for these actions.
- `PACKAGE_SET_ENABLED_STATE` keeps strict REQUIRED verification: the router
  reads back the actual enabled state (`pm list packages -d` probe) and fails
  with `VERIFICATION_FAILED` when the observed state contradicts the request.
- `PACKAGE_FORCE_STOP` and `PACKAGE_CLEAR_DATA` are honestly declared
  BEST_EFFORT: they have no reliable observable post-condition (a killed
  process may be restarted instantly; the enabled-state probe says nothing
  about cleared data), so their transport success stays honest-but-unverified
  instead of fabricating a verdict.
- Reconciliation of an uncertain privileged dispatch now distinguishes three
  outcomes: a matching read-back reclassifies the operation as verified
  SUCCESS; a contradicting read-back fails it; and when no comparable
  post-condition exists (one-shot transitions) the outcome stays UNKNOWN —
  never a fabricated failure, never a claimed success, and never a blind
  re-execution.

### Tests

- New `PackageSemanticMigrationTest`: end-to-end contract tests running the
  real strategies under the real router for both privileged transports —
  closed argv shapes, verified enable/disable cycles, contradicted dispatches
  failing with `VERIFICATION_FAILED`, uncertain dispatches surfacing UNKNOWN
  with exactly one dispatch (no blind retry), invalid packages rejected
  before any transport call, and legacy action types routing through the
  full chain.
- `OperationRegistryParityTest` pins the documented BEST_EFFORT exception so
  the honesty of one-shot package transitions cannot silently regress.
- New `OperationParameterValidatorTest`: per-type grammar coverage plus
  router-integration tests proving malformed values never reach a strategy
  probe.
- Extended `CapabilityRouterTest` with the NF-P0-002 contract: strict
  REQUIRED verification (match → verified success, contradiction →
  `VERIFICATION_FAILED`, unreadable → `UNKNOWN`), reconciliation scoring,
  failed-verification evidence, and `PENDING_USER_ACTION` terminality.

### Added — Variables 1.0 domain contracts (P0.3) and "Why didn't this run?" explainer (P0.4)

- **Variables 1.0 contracts** (new, `domain/variables/Variables10.kt`): the typed variable
  declaration model with the roadmap's six scopes (action output, node,
  execution, workflow-persistent, global-persistent, secret), deterministic
  scope-resolution precedence, bounded size/depth quotas, cycle-safe computed
  references, and `SecretReference` — a keystore-backed reference type that
  carries a key alias instead of a value, refuses to serialize the underlying
  secret, and is excluded from export by default.
- **RunExplainer** (new, `core/logging/RunExplainer.kt`): turns the typed execution-trace
  events recorded in the previous milestone into a user-facing answer for the
  single most-asked question — *"why didn't this run?"* — with a reason code,
  a plain-language explanation, and a concrete fix step where one exists
  (grant permission, enable Shizuku, adjust trigger, unsupported on device).
  Secret values can never enter an explanation by construction; the input
  surface is the redaction-safe trace model.

## [v3.85.1] - 2026-09-22

### Fixed

- **Skipped-run reasons are now visible in the UI.** Skipped executions store an exact gate reason (e.g. `Skipped: not all trigger conditions are true (charger, run time)`) in the backend diagnostic message, but both the history list and the execution-details screen showed only a generic localized "Task was skipped." label. The stored reason is now surfaced verbatim beneath the summary in the routine history row and on the execution-details header, so a silent skip is always diagnosable from the UI without connecting a debugger. Backend messages remain untouched (diagnostics protocol preserved); presentation-only change.

### Notes

- The skip-reason protocol (`Skipped:` message prefix recorded by the engine) predates this release; this change closes the visibility gap between the persisted diagnostics and the user-facing history without altering any recorded data or engine behavior.

## [v3.85.0] - 2026-09-21

### Fixed

- **A failed exit no longer disables the task forever.** Found on a real
  device: when an end action kept failing, the durable lifecycle row stayed
  `EXIT_FAILED` after its bounded retry budget (5 attempts) was spent, and
  every future activation was then rejected with "a prior lifecycle still
  requires cleanup" — silently disabling the whole automation with no user
  visible cue. An exhausted failed row is now reaped by the next activation,
  so the task runs again from a clean state while the failed exit remains in
  history. A failed row still inside its budget is preserved exactly as
  before (strict recovery semantics unchanged, both behaviors pinned by
  tests).
- **New multi-trigger tasks default to ALL semantics.** The dominant support
  request: users set several conditions (e.g. charging + night window) and
  expect the task to run only when every condition holds — not when any one
  of them fires. The builder now starts new tasks in "all conditions" mode;
  the ANY selector stays one tap away, and tasks saved before this change
  keep their stored value untouched.
- **The builder's ALL-mode advisory now derives from the engine's own policy.**
  The hard-coded draft list had drifted from the runtime's verifiable-state
  classification (`APPLICATION` and `CALL_STATE` do have state evaluators).
  The warning now delegates to `TriggerMatchPolicy.isEventOnly` — the single
  source of truth the engine and the manual gate use — so it can never
  disagree with what the runtime will actually verify.
- **Quiet logs on phones without Wear support.** `WearSyncManager` probed
  Wearable availability on every push and logged a full `API_UNAVAILABLE`
  stack trace each time on devices with no watch. Availability is now checked
  once and the sync path stands down with a single informational line.

## [v3.84.0] - 2026-09-21

### Fixed

- **Watch shows automations instantly, even while the phone app is asleep.**
  Studied two open-source companions with proven sync (PixelWater,
  WearFiles) and adopted their decisive pattern: on startup the watch now
  reads the **cached automation DataItem directly from the local Data Layer
  store** (`getDataItems`) instead of depending entirely on the live
  request chain (pull-request message → phone listener service → push →
  DATA_CHANGED). The snapshot may be one edit stale, but the UI shows real
  content immediately; the background pull request then refreshes it. Any
  single failure in that chain previously left the watch on its
  "Connecting" spinner forever.
- **Symmetric process wake-up on the phone side.** The phone listener now
  also declares the `DATA_CHANGED` intent filter for `/nexaflow/` paths
  (the pattern both reference apps use), so Play Services can start the
  phone process for Data Layer traffic with the same reliability it already
  had for command messages. A `onDataChanged` handler consumes the buffer
  and ignores self-echo, keeping the audit surface explicit and reviewed.

### Tests

- Snapshot contract suite: wire-format parity for the automation path and
  payload key between the standalone wear module and the phone constants,
  the exact `wear://*` URI shape the cache read parses, and DTO round-trip
  through the same `Json` decoder both entry points share.

## [v3.83.0] - 2026-09-21

### Fixed

- **Edited trigger removals now persist reliably.** The automation builder waits for
  its ViewModel-owned save job to finish before leaving the navigation stack.
  Previously the screen could pop immediately after Save, clear the destination
  ViewModel, and cancel the in-flight Room write; removed triggers could then
  reappear when the task was opened again. A regression test now guards the
  post-save ordering. Fixes #7.

### Changed

- **Trigger-match ALL mode is now a full evaluation policy, not just a
  multi-trigger gate.** The dedicated `TriggerMatchPolicy` centralizes the
  ANY/ALL combination (truth table: ANY requires at least one verifiably
  satisfied condition; ALL requires every condition verifiably satisfied;
  an empty condition list can never start a run under either mode), and the
  execution engine routes every trigger evaluation through it. A task with a
  **single** condition in ALL mode is now live-evaluated like any other —
  the firing monitor only starts the evaluation and is never treated as
  proof that its condition still holds.
- **Honest typed condition results for state-read adapters.** `CHARGER` and
  `AIRPLANE_MODE` are classified as definitive-false-when-false state reads
  (like `TIME` and `DEVICE`): a false answer is a verified current state and
  an unreadable state surfaces as `Unknown`, so the ALL gate and the manual
  run gate no longer over-report unverifiable conditions.

### Added

- **Builder advisory for event-only triggers in ALL mode.** When a task set
  to "all conditions" contains a momentary trigger that can never be
  re-verified from device state (notification, boot, NFC tag scan, SMS,
  webhook, sensor, plugin, geofence, ...), the builder shows an explicit
  warning that such a condition will keep the task from running in ALL mode,
  instead of failing silently at runtime. Localized across all 10 supported
  languages.

### Tests

- Complete ANY/ALL truth-table policy suite (17 cases) including 3-condition
  combinations, the empty-condition guard, and event-only advisory
  classification.
- Cross-midnight time-range matrix: `22:00–07:00` is satisfied at 22:30,
  01:00 and 06:59 and unsatisfied at 12:00, 18:00 and 07:01, plus the
  charging-at-night acceptance scenario.
- Engine gate tests for ALL mode with a single condition (both the skip and
  the run path) proving a firing monitor is not current truth.

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
    synchronously while the refresh triggered on screen entry was still in
    flight, so the pre-scan answer classified runnable tasks as inadmissible
    and saved them **disabled**. Admission is now decided on
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
