# P1-11 Package B: connectivity evidence

**State: IMPLEMENTED LOCALLY; NOT READY TO CLOSE.** This work is on
`audit/2026-10-09-t12-connectivity-events` at base
`4838fc35511eed126bf23520edb3c2990ebddd77`. No PR or remote CI run exists yet.

## Implemented

- Wi-Fi trigger schema and editor now expose `validated`, `captivePortal`, and
  `metered` ANY/YES/NO filters and optional exact SSID/BSSID filters.
- Runtime reads `NetworkCapabilities` and Wi-Fi `transportInfo`. A known failed
  filter is a non-match; unavailable/redacted identity remains UNKNOWN and does
  not synthesize an exit. The runtime explicitly checks fine-location permission
  before reading identity; SSID/BSSID editing requests the existing permission
  path, and a revoke/regrant is observed as UNKNOWN until a later permitted read.
- One `@Suppress("DEPRECATION")` keeps the `WifiManager.connectionInfo`
  fallback for Android 8/9, where `NetworkCapabilities.transportInfo` is not
  available. The suppression budget records this compatibility fallback.
- VPN remains the existing `VPN_CONNECTED` state trigger in
  `DeviceStateMonitor28`; no duplicate trigger enum or source was added.
- Reachability uses Android's framework-owned `NET_CAPABILITY_VALIDATED` signal.
  The app does not send its own endpoint probes or run a polling loop; this keeps
  probe scheduling and network traffic bounded by the platform.

## Local verification

- `:core:common:testDebugUnitTest --tests com.nexaflow.core.common.ReaderStateTest` — PASS (9 tests).
- `:domain:testDebugUnitTest --tests com.nexaflow.domain.catalog.TriggerNodeSchemasTest` — PASS (1 test).
- `:core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.ConnectivityMonitorExitReconcileTest` — PASS (3 tests).
- `:feature:automation-builder:compileDebugKotlin` — PASS.
- `python scripts/check_strings_parity.py` — PASS (`PARITY_PROBLEMS: 0`).
- Combined Gradle command completed successfully; 267 actionable tasks, 132 executed.
- `git diff --check` — PASS.

## Remaining before issue closure

- No exact-SHA remote CI or code review yet.
- No physical-device/OEM validation; permission revoke/regrant behavior is only
  represented by UNKNOWN/redaction unit contracts.
- Keep #134 open until all eight packages and the remaining lifecycle,
  malformed-config, duplicate, cancellation, and restart evidence are complete.
