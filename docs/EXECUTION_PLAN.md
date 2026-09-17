# Capability expansion implementation plan

Status: in progress. This document tracks deliverables, not completed-product claims.

## Delivery order and acceptance gates

1. Inventory every trigger, action and sub-operation. Map editor fields to handlers, permissions, outputs and tests. Compare equivalent outcomes with the official Automate block reference; do not compare enum totals directly with block totals.
2. Correct backend admission and discovery. Distinguish platform support, grantable permission, connected backend and runtime verification. Test normal, Root-only, Shizuku-only, combined and disconnected environments. Preserve saved workflows when capabilities change.
3. Audit durable execution. Diagnose duplicate admission and capacity separately; implement reviewable recovery resolution. Exercise cancellation, process death, uncertain effects, bounded queues and partial failure. Never replay an uncertain non-idempotent operation automatically.
4. Define reusable configuration schemas: typed inputs, defaults, bounds, dependencies, variable support, outputs, permissions, migration and exit behavior. Audit all existing editors before expanding the catalog.
5. Expand triggers where inventory proves gaps: schedules, network state, files, notifications, sensor stability/hysteresis, variables and workflow events. Each requires an actual event source, subscription cleanup and lifecycle tests.
6. Expand actions where inventory proves gaps: data, files, network transfers, media and approved integrations. Each requires production dispatch, editor support, bounded outputs and failure tests.
7. Complete advanced workflow customization: typed variables, branching, bounded parallelism, error routes, subroutines, cancellation and safe previews.
8. Measure resource use and usability. Share observers, bound queues, remove unnecessary polling, verify RTL, accessibility and translation coverage. Record baselines before setting numeric improvement targets.
9. Generate reference documentation from verified schemas. Update README, configuration, security, diagnostics, comparison, migration and validation guides. Preserve dated historical audits as historical evidence.
10. Validate on JVM, emulator and physical devices separately. Publish exact commit, commands, exit status and device/ROM coverage. Missing device evidence remains explicit.
11. Push reviewed changes, resolve CI failures for the exact commit, tag an accepted candidate and verify signed release artifacts and publication. Write English changelog entries describing implemented behavior and known limits.

## Current increment

- Implemented, unit-tested: exact Root/Shizuku requirements for backend-specific commands.
- Implemented, verification pending: live capability capture and Shizuku UserService connection check.
- Implemented, unit-tested: numeric-only sensor hardware remains discoverable.
- Added regression cases for backend substitution, capability removal and pressure-only hardware.
- Implemented, verification pending: builder option lists refresh after returning from permission settings.
- Remaining: permission-required discovery, configuration-schema inventory, broader capability expansion, recovery resolution, device testing and release verification.

## Evidence rules

Local focused validation: `:core:execution:testDebugUnitTest --tests '*CommandCompatibilityEngineTest'` finished with exit code 0 and BUILD SUCCESSFUL; 14 tests, no failures or errors. Catalog parity and string-key parity passed. This does not certify physical-device behavior or the full project suite.

Builder Kotlin compilation and execution-module Detekt also finished with exit code 0 (BUILD SUCCESSFUL). Existing deprecation warnings remain. Backend connection changes still require physical-device validation.

Starting Gradle is not a passing build. Wait for its process exit and retain the final result. Existing XML reports cannot certify a new run. A pushed tag is not a published release. Catalog presence is not verified device support. Claim comparative advantages only for measured scenarios on equivalent devices and privileges.

## Reference

[Automate block reference](https://llamalab.com/automate/doc/block/index.html).
