# T07 implementation plan

Use issue #130 and `2026-10-08-task-manager-safety-design.md` as the contract. Preserve one queue dispatcher. Every production behavior change must follow a focused failing regression.

## Files and responsibilities

- `core/execution/src/main/java/com/nexaflow/core/execution/task/TaskManager.kt`: fair selection, deadline-aware retry, explicit unknown outcomes, cancellation reconciliation, and permit lifecycle.
- `core/execution/src/main/java/com/nexaflow/core/execution/task/TaskRuntimeModels.kt`: task safety opt-in and lifecycle transitions.
- `core/execution/src/test/java/com/nexaflow/core/execution/task/TaskManagerTest.kt`: fairness, retry, timeout, cancellation, shutdown, and resource tests.
- `core/execution/src/main/java/com/nexaflow/core/execution/state/StateTransactionStore.kt` and `StateTransaction.kt`: per-automation ownership ledger and safe restore boundary.
- `core/execution/src/main/java/com/nexaflow/core/execution/DeviceStateSnapshot.kt`: target-specific readback and guarded restore.
- `core/execution/src/main/java/com/nexaflow/core/execution/compat/AutomationWorkflowRunner.kt`: report each action result to the existing state transaction owner.
- Existing runner/state tests: prove only certain successful actions acquire ownership and a changed live target is preserved.

## Tasks

1. Extend lifecycle and result types with terminal `UNKNOWN`; add tests proving uncertain results, post-dispatch exceptions, and dispatched timeouts do not retry. Add a cancellation-safety declaration defaulting to conservative behavior. Keep queued and retry-wait cancellation known; resolve races from the observed attempt result before publishing one terminal result.
2. Add queue enqueue timestamps and an aging threshold to `TaskManagerLimits`. Select the oldest aged task before younger work while retaining current priority/FIFO behavior for non-aged work. Add a fake-clock test that fails under strict priority. Saturate retry backoff arithmetic and clamp waits to remaining deadline; test deadline expiry during retry delay.
3. Make shutdown/cancel transitions atomic under the manager lock. Test cancellation before dispatch, while a side effect is active, during retry wait, during shutdown, and while a worker is idle. Verify one terminal result per task, no worker resurrection, and no resource permit leak after cooperative cancellation.
4. Track ownership per reversible action target. Record ownership only after a successful certain action. At restore, compare the current value with the last observed automation-owned value and skip changed/unreadable values with an explicit preservation result. Cover a user edit, two automations touching the same target, unknown results, and duplicate same-target actions.
5. Run targeted tests first, then `:core:execution:testDebugUnitTest` and `:core:automation-engine:testDebugUnitTest`; run repository static gates and exact-SHA GitHub CI. Update the issue/master evidence and preserve NOT TESTED for device/OEM behavior.

## Review focus

- A cancellation can race a normal result; only one publisher may resolve the final status, and known completed success must not regress to cancellation.
- A task canceled while waiting for a semaphore must not be marked `UNKNOWN`, while a dispatched non-cooperative effect must not be marked `CANCELLED`.
- A timed-out dispatched action must never be automatically replayed by the task retry loop.
- Aging decisions must not mutate a priority queue comparator while entries remain inside the heap.
- Restore conflicts and unreadable values must never be converted into successful writes or silent state loss.

## Local evidence (2026-10-08)

- `:core:execution:testDebugUnitTest` — exit 0; 716 tests, 0 failures, 0 errors, 0 skipped.
- `:core:automation-engine:testDebugUnitTest` — exit 0 when run as a standalone suite (234 actionable Gradle tasks, 1 executed). A combined execution+engine run hit one Windows DataStore temporary-file rename collision in `CalendarMonitorExitReconcileTest`; the affected class passed when rerun alone, and the standalone engine suite passed.
- Focused `TaskManagerTest`, `TaskManagerHardeningTest`, `AutomationWorkflowRunnerTest`, and `StateRestoreOwnershipPolicyTest` — exit 0.
- `DeviceStateSnapshotOwnershipTest` — exit 0; Robolectric confirms a user-edited font scale is preserved at exit.
- `:app:compileDebugAndroidTestKotlin` — exit 0; instrumentation sources compile. Connected-device and OEM behavior remain unverified.
- `python scripts/check_atomic_architecture_fitness.py` — exit 0.
- `python scripts/check_persistence_safety.py` — exit 0; 25 exported schemas, latest=27.
- `git diff --check` — exit 0.
- Physical Android/OEM acceptance and long soak remain NOT TESTED. The Android device resource integration test was updated but not run on a connected device.
