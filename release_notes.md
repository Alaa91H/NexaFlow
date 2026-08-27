# NexaFlow v3.48.0 — Durable Exit Lifecycle

NexaFlow v3.48.0 delivers a reliability-focused hardening of stateful automation exits. The release addresses conditions in which an automation had already applied changes but its required exit behavior could be lost during a monitor race, a stale scheduled alarm, an action failure, or process recovery.

## What changed

| Area | Improvement |
|---|---|
| Durable lifecycle ownership | Added a bounded, local DataStore runtime ledger with explicit `ACTIVE`, `EXITING`, and `EXIT_FAILED` states. A stateful occurrence is admitted and persisted before its main actions run. |
| Idempotent exit coordination | Added one `ExitCoordinator` that atomically claims a matching active occurrence before invoking exit behavior. Competing trigger-false, time-window-end, or recovery signals cannot execute the same logical exit twice. |
| Time-range alarms | Start and end alarms now carry a deterministic occurrence identifier, configuration generation, window start, and window end. The receiver validates all of them against the durable schedule record before acting, so an old or reconfigured end alarm cannot consume a newer lifecycle. |
| Late and recovered work | An already-expired range start is consumed safely and the next occurrence is armed instead of applying late side effects. Process, boot, clock, and exact-alarm-access reconciliation can resume safe elapsed-window cleanup. |
| Connectivity and battery | Connectivity plus battery/charger paths now acquire lifecycle ownership through the durable ledger and route known condition-end signals through the coordinator. Legacy active-key cleanup occurs only after a completed exit or verified absence of an active occurrence. |
| Recoverable failure | An unsuccessful exit remains visible as `EXIT_FAILED`; it is never silently discarded. Automatic recovery is bounded to one additional attempt after the initial exit attempt, after which the durable failure remains available for diagnosis. |

## Reliability and safety model

A lifecycle transition is the source of truth, not a process-local collection. The coordinator performs `ACTIVE → EXITING` durably before exit actions begin, records an unsuccessful result as `EXIT_FAILED`, and removes an occurrence only after successful exit completion. Device-state snapshots used for restore-on-exit are serialized locally for recovered exits and malformed stored snapshots fail closed.

Receiver work remains bounded: the alarm receiver retains ownership while it validates the durable occurrence and calls the coordinator, using `goAsync()` and a bounded wake lock. This follows Android's receiver-lifetime guidance: asynchronous receiver work must still complete promptly and must call `finish()`.[1]

> Recovery never treats an unknown connectivity or battery reading as a false condition. A known elapsed time-range end or visible failed exit is required before automatic cleanup is attempted.

## Verification

The final candidate passed the full remote GitHub Actions pipeline: resource hygiene and locale parity, Python resource-gate tests, Detekt, Android Lint, all Android unit tests, debug and release APK builds, release AAB build, dependency-verification validation, packaged-permission checks, APK signature verification, 16 KB native-library alignment, zip alignment, and bundle validation.

The release validation cycle also corrected CI-detected Kotlin exhaustiveness, visibility-boundary, and deterministic test-fixture issues. No local Gradle build or test was run; build and test acceptance is provided by the remote pipeline.

## Scope and limitations

This release deliberately covers **time ranges, connectivity, and battery/charger** stateful exit paths. Other legacy monitor families that still own direct exit paths are not represented as covered by this release; converting them requires source-specific occurrence ownership and regression coverage rather than a broad, unsafe rewrite.

NexaFlow adds no account, cloud service, telemetry, permission, schema migration, fixed-delay workaround, background retry loop, or user-supplied shell execution. Android can cancel alarms on shutdown and may constrain exact-alarm access; the scheduler therefore rebuilds schedules after relevant system changes, but the release does not claim universal delivery or universal action success across all OEM, power-management, carrier, or device states.[2]

The architecture rationale, platform references, accepted scope, and deferred monitor work are recorded in [`docs/RESEARCH_2026.md`](docs/RESEARCH_2026.md).

## References

[1]: https://developer.android.com/reference/android/content/BroadcastReceiver "Android Developers — BroadcastReceiver"
[2]: https://developer.android.com/develop/background-work/services/alarms "Android Developers — Schedule alarms"
