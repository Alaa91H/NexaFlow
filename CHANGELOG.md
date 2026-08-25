# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [v3.41.2] - 2026-08-25

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

