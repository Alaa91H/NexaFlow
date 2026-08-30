# NexaFlow Production Audit and Hardening Report

## Scope and evidence

This audit traced the Android automation path from persisted configuration and trigger delivery through occurrence admission, action execution, exit behavior, recovery, history, and release validation. The review covered the application module, domain scheduling logic, automation engine, execution engine, runtime DataStore, Hilt wiring, manifests, resource gates, tests, and GitHub Actions workflow.

The audit does not claim that every Android device, OEM background policy, permission state, or privileged backend has been physically tested. Device-dependent behavior remains subject to the capabilities and restrictions reported by Android and the device manufacturer.

## Canonical lifecycle

The hardened time-trigger path is:

> persisted configuration → calculated future occurrence → durable occurrence admission → AlarmManager START/END delivery → immutable occurrence validation → durable lifecycle admission → sequential action dispatch → durable checkpoint completion → terminal exit coordination → history and diagnostics.

A schedule identity contains the automation ID, occurrence ID, generation, window start, and optional window end. This identity is validated before execution, which prevents stale alarms from a previous configuration from targeting a newer lifecycle.

## Findings and remediations

| Area | Finding | Remediation | Status |
|---|---|---|---|
| Time-range delivery | A late START alarm was discarded when its nominal END had already passed, so the configured main action could never run. | Execute the admitted START occurrence and close it through `ExitCoordinator` after the main chain returns. | Fixed in v3.52.2 |
| Alarm admission | The scheduler could retain a durable schedule even when AlarmManager registration failed. | Treat registration as a transaction; roll back the occurrence unless START and required END alarms are accepted. | Fixed in v3.52.3 |
| Receiver failure | A receiver exception could consume the only delivery without a same-occurrence retry. | Re-arm the same occurrence and generation with a bounded retry count. | Fixed in v3.52.3 |
| Exit lifecycle | Location exits previously bypassed the durable coordinator. | Route location exits through `ExitCoordinator`, preserving failed exits for recovery. | Fixed in v3.52.1 |
| Stale delivery | Old schedule identities could be delivered after edits or reconciliation. | Validate occurrence ID, generation, and expected end before dispatch. | Fixed |
| Reboot/process recovery | AlarmManager alarms are not retained across reboot, and lifecycle state can outlive the process. | Reconcile durable lifecycle state and re-arm retained future END occurrences during recovery. | Fixed |
| Resource integrity | Removed map implementation left obsolete resource declarations. | Removed stale resources and enforced the repository resource gate. | Fixed |

## Strict guarantees now enforced

A valid time-range occurrence is not ignored merely because delivery is late. A malformed range without a valid end is rejected explicitly. A schedule is not treated as armed unless the platform accepts its alarm registration. A receiver-level failure can retry the same logical occurrence only within a bounded limit, preventing both silent loss and infinite execution loops.

The main action chain is checkpointed before side effects. An interrupted action is retained as unknown/recovery-required evidence rather than being replayed blindly. Exit actions are executed only by the durable coordinator for the matching lifecycle, and a failed exit remains visible instead of being cleared as successful.

## Validation

The repository resource gate passed with zero orphaned resources, zero unused resources, zero missing or extra translations, zero hardcoded-text findings, and zero lint-gate findings. The main-branch Android CI pipeline passed after the time-range execution fix, and the v3.52.2 tagged release pipeline passed the Android build, test, lint, signature, dependency, alignment, bundle, and native-library checks.

The v3.52.3 changes are prepared for the next CI run. Local Android compilation is not treated as evidence because the sandbox does not provide a complete Android SDK/device environment; the remote GitHub Actions pipeline is the authoritative Android gate.

## Residual platform limitations

Alarm timing on Android remains subject to exact-alarm permission, Doze, OEM background restrictions, process termination, and platform policy. The implementation preserves occurrence identity and bounded recovery, but no application can guarantee an exact wall-clock wake-up when the operating system denies the relevant capability. The application must therefore surface the capability state and retain diagnostic evidence rather than claiming an unverified execution.
