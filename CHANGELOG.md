# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [v3.58.0] - 2026-09-02

### Added
- Activated the workflow control-flow primitives that previously failed closed: **subworkflows** (bounded recursion, input/output parameter passing, isolated or shared context), **human approval gates** (explicit gateway contract with configurable timeout policy), **saga compensation** (reverse-order undo of declared side effects), and **ForEach loops** (per-item variable scoping with index exposure and failure policy).
- Added a **shared execution budget** (`WorkflowExecutionBudget`) spanning the whole tree — including nested subworkflows — with an atomic node-visit ceiling and a monotonic wall-clock deadline, so loops, retries, fan-out, and nested providers can no longer multiply work past the run's safety limits.
- Added per-node **execution journaling** (optional, non-blocking) that records run id, node, action type, timestamps, outcome, and error code without ever failing a workflow.
- Wired the budget-aware `executeSubworkflow` overload through the provider contract so child interpreters inherit the parent's visit counter and deadline; the legacy three-argument method remains source-compatible.

### Changed
- Condition evaluation for `BranchNode`, `WhileNode`, and `WaitUntilNode` is now strictly suspend-aware: an evaluator exception fails the node closed with a diagnostic instead of being treated as `false`, and cancellation always propagates instead of being swallowed.
- `WhileNode` re-checks its condition after the final iteration and reports `While iteration limit reached` truthfully when the condition is still true, instead of returning success.
- Timeout handling is unified: node-level timeouts and the workflow time budget both produce explicit failed results with side-effect-verification warnings, never unstructured coroutine cancellations.
- Retry reconciles declared compensations between attempts so a partially applied failed attempt cannot leak stale undo entries into a later successful attempt.
- The `workflow` failure result and the execution journal now share the same truthful outcome and error-code classification (`EXECUTION_TIME_LIMIT`, `NODE_VISIT_LIMIT`).

### Tests
- Added deterministic JVM coverage for subworkflow input/output contracts, missing-provider fail-closed behavior, recursion-depth enforcement, approval gateway decision and timeout policy, ForEach scope restoration, saga reverse-order compensation, shared-budget propagation to nested execution, strict condition-failure semantics, and node-visit-limit enforcement.

### Quality assurance
- The strict execution contract keeps unsupported device/backend work unavailable rather than pretending it succeeded: subworkflows require a provider, approvals require a gateway, and both fail closed with diagnostics when absent.
- Android unit tests, lint, and the production build are executed authoritatively by the GitHub Actions pipeline; no published tag was modified, and this release ships on top of the green `v3.57.0` baseline.

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
## [v3.56.3] - 2026-09-03

### Added
- Added a simplified GPS location-mode workflow with explicit **ON** and **OFF** choices for easier device control.
- Added quick timer presets for **1 minute**, **5 minutes**, **10 minutes**, and **24 hours**.
- Added a custom timer duration field supporting values from **1 second through 24 hours**.

### Changed
- Expanded `SYSTEM_WAIT` runtime validation to support the complete 1–86,400 second range while keeping imported workflows bounded.
- Kept the complete supported trigger catalog available in the builder, including connectivity and location-mode controls, while retaining the verified-plugin security boundary.

### Quality assurance
- Passed trigger-catalog, resource-parity, resource-hygiene, and diff-integrity checks locally.
- GitHub Actions validation and release-artifact checks are executed for the `v3.56.3` tag.
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

