# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

