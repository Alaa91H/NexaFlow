# NexaFlow v3.58.4 — Detekt Stability and Regression-Test Hygiene

**Release date:** 2026-09-04

## Overview

NexaFlow v3.58.4 is a focused stability release that clears the latest static-analysis gate and strengthens the quality baseline around task cancellation and deletion-flow regression tests. The release preserves the existing device-control behavior while ensuring the affected modules remain compliant with the repository's Detekt and whitespace policies.

## Delivered changes

| Area | Delivered behavior |
|---|---|
| Task cancellation | Removed a redundant rethrow of `CancellationException` from `TaskManager`; structured cancellation continues to propagate naturally through the surrounding coroutine scope. |
| Regression-test hygiene | Added the required newline termination to automation-details and dashboard deletion tests. |
| Broadcast security | Registered the internal automation-change test receiver with `RECEIVER_NOT_EXPORTED`, making the receiver boundary explicit on modern Android. |
| Existing device controls | GPS ON/OFF, geofence behavior, the 1-second–24-hour timer range, and the complete supported trigger catalog remain unchanged and covered by the existing test and catalog contracts. |

## Verification

The following checks passed locally for the affected scope:

- `:core:execution:detekt`
- `:feature:automations:detekt`
- `:feature:dashboard:detekt`
- Trigger-catalog parity audit
- Resource hygiene and translation parity gate
- `git diff --check`
- Android Lint receiver-flag correction is included in the release candidate and will be verified by the release CI gate.

The complete Android CI pipeline remains the release authority for unit tests, Android Lint, Detekt, APK/AAB generation, signature verification, dependency verification, alignment checks, and release-artifact validation.

## Safety and compatibility

No persisted workflow schema was changed. No published tag was modified. Existing automations, GPS settings, timer values, and device-control permissions remain compatible with this release.

## Upgrade guidance

Install the release over the existing application when upgrading from a production-signed build. After installation, verify one representative automation on the target device, especially if it relies on background execution, exact alarms, privileged network controls, or OEM-specific restrictions.

## Release evidence

The final GitHub Actions run and downloadable APK are linked in the published GitHub Release after all required gates complete.
