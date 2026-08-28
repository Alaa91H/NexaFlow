# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Made condition-ending workflows first-class executions across trigger monitors.
- Serialized exit dispatch to prevent concurrent monitor callbacks from losing end events.
- Removed normal-run cooldown interference from exit workflows.
- Preserved exit behavior across process restarts through the durable active-trigger ledger.
- Corrected location transition baselining to prevent duplicate or synthetic ENTER/EXIT events.

### Added
- Provider-independent fixed-location trigger support using the shared location engine.
- External `geo:` intent integration with manual coordinate fallback and no embedded map SDK.

## [3.28.0] - 2026-08-28

### Fixed
- Ensured configured exit actions, state restoration, reruns, and value resets execute reliably when trigger conditions end.
- Added serialized, failure-isolated exit execution so one failing end action cannot suppress subsequent exit processing.

### Added
- Fixed location trigger with latitude, longitude, radius, and ENTER/EXIT semantics.
- Unit coverage for coordinate validation, geofence distance checks, and transition detection.

### Compatibility
- No Google Maps, MapLibre, OpenStreetMap, tile, or offline-map dependencies were added.
- Existing Current Location behavior and location permissions remain supported.


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

