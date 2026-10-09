# Package C — App and device triggers (2026-10-09)

## Scope

- Package replacement removal is ignored; the completed add broadcast emits one `UPDATED` event. Regular installs/removals remain `INSTALLED`/`REMOVED`.
- `DEVICE.event` is constrained to the eight existing screen, power, wired-headset, and Bluetooth monitor values. `USB_CONNECTED.state` remains `ON`/`OFF`.
- The existing app picker selects one optional `APP_INSTALLED.package`; `APPLICATION` remains multi-select.

## Local verification

| Gate | Result |
|---|---|
| Combined Gradle regression command: `:core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.PackageEventClassifierTest --tests com.nexaflow.core.engine.DeviceEventMonitorExitReconcileTest :domain:testDebugUnitTest --tests com.nexaflow.domain.catalog.TriggerNodeSchemasTest --tests com.nexaflow.domain.catalog.AutomationNodeCatalogTest :feature:automation-builder:testDebugUnitTest --tests com.nexaflow.feature.builder.TriggerAppSelectionTest` | PASS — exit 0, BUILD SUCCESSFUL |
| Builder Kotlin compilation | PASS — exit 0 |
| `python scripts/audit_atomic_inventory.py --check` | PASS — `ATOMIC_INVENTORY: OK` after deterministic refresh |
| `python scripts/check_strings_parity.py` | PASS — `PARITY_PROBLEMS: 0` |
| `python scripts/check_resources.py` | PASS — all checks zero, `RESOURCE_GATE: OK` |
| `git diff --check` | PASS — exit 0 |

The combined Gradle run executed in 3m29s (307 actionable tasks: 19 executed, 288 up-to-date); a fresh pre-commit rerun completed in 1m29s with exit 0 (307 tasks up-to-date). Builder Kotlin compilation was included in the fresh rerun.

## Runtime evidence boundary

Package broadcasts are dynamic process-lifetime inputs. The classifier has deterministic unit coverage; Android emulator integration, physical-device behavior, package-manager OEM behavior, and a multi-OEM soak are `NOT TESTED`.

## Hosted CI

- Exact PR head SHA: `206df027175e0503ff632adaa17c3e3e527fe209`.
- PR #241 merged at `2d3c50266ed7ae52f6cb3fc66c6dc227a474167e`: https://github.com/Alaa91H/NexaFlow/pull/241
- Exact-SHA Actions run: https://github.com/Alaa91H/NexaFlow/actions/runs/37980279533 — secret scan, lint (7m38s), coverage (14m38s), and build (25m53s) all passed.
- The emulator integration job was skipped by the PR workflow condition; physical-device and OEM behavior are `NOT TESTED`.
