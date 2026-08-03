# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

