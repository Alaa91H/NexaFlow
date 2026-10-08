# Task Manager fairness, cancellation, and restore safety

**Baseline:** `main@34132942c9b52a0abf331b5527bba8f91e428ce4` (T06 merge).
**Issue:** [#130 — T07](https://github.com/Alaa91H/NexaFlow/issues/130).

## Confirmed behavior

- `TaskManager` uses one sequential dispatcher and a strict-priority queue. A continuous stream of higher-priority tasks can indefinitely postpone an older low-priority task.
- `runAttempt` ignores `SystemControlResult.outcomeUncertain`; it converts the result to an ordinary retryable failure. A dispatched timeout can also be retried even though the effect may have landed.
- Cancellation currently reports `CANCELLED` for a running child even when cancellation interrupts a dispatched external effect. That overstates what the runtime knows.
- `DeviceStateSnapshot.restore` writes the captured original value for each selected target without comparing the live value to the value last written by the automation.

## Design

Keep the existing single `TaskManager` queue and sequential device-action model. Add bounded priority aging: once a queued task has waited for the configured aging interval, schedule the oldest aged task before younger work. Preserve FIFO order within equal effective age/priority, and keep resource permits acquired in stable resource order with `withPermit` so cancellation releases them only when the protected coroutine unwinds.

Represent an uncertain result explicitly as terminal `UNKNOWN`. An explicit `outcomeUncertain` result, exception after dispatch, or timeout/cancellation while the task body is dispatched must not trigger retries. Queued cancellation, cancellation while waiting for resources, and cancellation during retry delay remain `CANCELLED`. A caller may mark a task `safeToCancel` only when cancellation/timeout is guaranteed not to leave an external effect. A completed result observed before a cancellation request remains authoritative.

Bound retry waits by the remaining task deadline and saturate exponential backoff arithmetic. A deadline that expires before dispatch is `DEADLINE_EXCEEDED`; a dispatched timeout with an unconfirmed effect is `UNKNOWN`.

For revert-on-exit, capture target ownership after each successful, certain action. Before restoring a target, read the live target and compare it with the automation's last observed value. Restore only when both values are readable and still match; otherwise preserve the current value and include a conflict count in the result. Unknown or failed actions relinquish ownership for their target. This is a guarded read-then-write because Android settings APIs do not expose atomic compare-and-set; unreadable values fail closed and are not restored.

## Compatibility and limits

- No Room schema, persisted workflow, permission, or second dispatcher is introduced.
- Existing status/result consumers gain an `UNKNOWN` terminal variant and must handle it explicitly.
- `safeToCancel` defaults to false, so existing generic dispatched tasks become conservative on timeout/cancellation. Call sites may opt into known cancellation only where their operation is demonstrably side-effect free or idempotent.
- Android user/OEM writes can race between the ownership read and restore write; the guard prevents stale overwrites observed before the write but cannot make the platform operation atomic. Device/OEM confirmation remains a separate validation gate.

## Verification

Use deterministic JVM tests for uncertain results, dispatched timeouts/cancellation, deadline-bounded retry waits, priority aging/FIFO, resource release, and lifecycle races. Add tests for target ownership mismatch, unreadable target values, and successful guarded restore. Run the execution and automation-engine unit suites, then exact-SHA repository CI. Do not claim physical-device validation from JVM or CI results.
