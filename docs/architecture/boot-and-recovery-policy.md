# Boot and scheduled-delivery policy

## Boot boundary

NexaFlow waits for the user's first unlock after a device restart. Automation definitions, schedules, runtime lifecycle state, and the recovery ledger are backed by credential-protected Room/DataStore and the normal Hilt graph. The app therefore does not mark `AutomationAlarmReceiver` as direct-boot aware and does not subscribe it to `LOCKED_BOOT_COMPLETED`. It does not attempt a partial device-protected schedule before unlock.

After unlock, `BOOT_COMPLETED` and package replacement use the same recovery sequence: reconcile durable elapsed or failed exits, load current automation definitions, initialize the scheduler, rebuild pending alarms, dispatch boot triggers only for `BOOT_COMPLETED`, and schedule monitoring through its deferred start alarm. This keeps schedule restoration separate from package replacement trigger semantics. Android grants no guarantee that this work has been physically validated on every OEM.

## Occurrence identity and delivery

Every scheduled start is an immutable occurrence with an ID, generation, scheduled window start, and optional window end. The receiver validates the durable schedule identity before reading or executing the automation. Reboot, edit, pause, delete, or replacement must not make a stale PendingIntent valid; the scheduler reconciles and replaces/cancels IDs through its ledger.

Point-in-time occurrences have a 15-minute delivery window starting at the scheduled instant. A delivery before the instant or later than the grace is recorded once in execution history as `Skipped:` and the occurrence is consumed without running actions. The diagnostic uses a stable history ID derived from the occurrence, so receiver redelivery after a process crash remains an idempotent upsert. A valid range start runs only while its exclusive end is still in the future. If Android delivers it at or after that end, NexaFlow records a skipped occurrence and consumes it. A range that starts in time but whose actions finish after the end requests the normal durable exit immediately after start execution.

The scheduled instant, rather than receiver delivery time, is retained as `TriggerOccurrence.occurredAtEpochMs`. Duplicate event admission still passes through the shared durable execution boundary. The end alarm and a delayed-start exit race through `ExitCoordinator`, which owns idempotent lifecycle transition and cleanup.

## Wall clock, zone, DST, and exact-alarm access

The scheduler and dashboard preview use the same `TimeTriggerCalculator`. Recurrences are computed in the configured wall-clock zone and must be recomputed after manual clock, time-zone, fixed UTC-offset, exact-alarm grant, or permission-revocation changes. A spring-forward gap skips a nonexistent start time; an end time inside a gap moves forward by the gap duration. In a fall-back fold, the earlier offset is selected consistently for start and end. Overnight ranges end on the next calendar date, including when the date crosses a DST transition.

Exact alarms are used only when access is available; the scheduler keeps its inexact fallback for denied access. When permission changes, pending alarms are recomputed rather than assuming the old alarm remains scheduled. Pause and delete cancel associated start/end identities; edits reconcile them to the newly persisted configuration.

## Evidence and limits

Unit tests cover the receiver's merged manifest contract, delivery-grace boundaries, schedule preview calculations across DST gaps/folds and overnight windows, schedule identity generations, and exact-alarm permission paths. These tests do not establish reboot/unlock delivery on a physical API 26+ device, OEM-specific alarm behavior, or exact-alarm Settings behavior. Those remain `NOT TESTED` until captured on a connected device.
