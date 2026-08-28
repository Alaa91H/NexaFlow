# Fixed Location Trigger Implementation Report

## Summary

NexaFlow now supports provider-independent fixed-location automation triggers. Users can keep the existing **Current Location** flow or configure a **Selected Location** with latitude, longitude, radius, and `ENTER`/`EXIT` semantics. The application stores only normalized geographic configuration and reuses the existing location monitor, permission flow, event bus, execution engine, and persistence model.

## Architecture

The feature follows the existing pipeline:

`Android Location → LocationMonitor → FixedLocationEvaluator → normalized trigger event → ExecutionEngine → workflow`

The evaluator provides strict coordinate and radius validation, Haversine distance calculation, inclusive radius comparison, and transition detection. A selected fixed location initializes from `UNKNOWN` without emitting an event after process or device restart. Events are emitted only for `OUTSIDE → INSIDE` (`ENTER`) and `INSIDE → OUTSIDE` (`EXIT`) transitions.

## External maps integration

NexaFlow no longer embeds a map renderer. The picker opens the installed maps application through Android `ACTION_VIEW` with a standard `geo:` URI. Android maps applications do not expose one universal coordinate-return contract, so the screen includes a validated coordinate-entry fallback and displays a clear message when no compatible maps application is installed.

No Google Maps SDK, Google Maps API key, MapLibre, OpenStreetMap SDK, tile package, offline map database, or provider-specific persisted state is required.

## Files changed

| Area | Changes |
|---|---|
| Runtime evaluation | Added `FixedLocationEvaluator.kt`; integrated strict validation and restart-safe selected-location transitions into `LocationMonitor.kt`. |
| Builder UI | Replaced the embedded map screen with an external maps launcher and validated coordinate/radius fallback; updated selected-location source handling. |
| Dependencies | Removed `play-services-maps`, Maps manifest metadata, API-key setup, verification metadata, and bundled Leaflet assets. |
| Tests | Added evaluator unit tests and replaced renderer tests with coordinate/radius validation tests. |
| Documentation | Added this report and the release changelog entry. |

## Persistence and permissions

The existing `Trigger.config` map remains the persistence contract. Selected locations use the existing keys `lat`, `lng`, `radius`, `event`, and `source=selected`; no schema migration or provider-specific data is introduced. The implementation continues to use NexaFlow's existing fine/coarse location permission and background monitoring infrastructure.

## Validation and tests

The added tests cover valid and invalid coordinates, `NaN`, infinity, radius bounds, zero and negative radii, distance evaluation, inclusive boundary behavior, ENTER/EXIT transitions, repeated-state suppression, and restart behavior. The complete workflow was not verified on a physical Android device in this environment. External maps behavior remains dependent on the device's installed maps application, while manual coordinate entry is deterministic on all supported devices.

## Commands executed

The following checks were executed locally:

```text
git diff --check
./gradlew :core:automation-engine:testDebugUnitTest :feature:automation-builder:testDebugUnitTest --no-daemon --stacktrace
```

The Gradle test command could not complete in the sandbox because no Android SDK location is installed. GitHub Actions remains the authoritative Android build and test environment for this repository.
