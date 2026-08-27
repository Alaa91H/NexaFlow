# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

