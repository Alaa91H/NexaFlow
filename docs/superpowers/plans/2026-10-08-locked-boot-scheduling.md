# Locked Boot and Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align boot recovery, wall-clock scheduling, preview, and missed-occurrence diagnostics with one unlock-safe, durable schedule policy.

**Architecture:** Keep the existing credential-protected Room/DataStore, `AutomationScheduler`, `AutomationAlarmReceiver`, `TimeTriggerCalculator`, and `ExecutionHistoryOutcome` owners. Add no second scheduler or device-protected state; share one immutable next-occurrence calculation and classify late delivery before action side effects.

**Tech Stack:** Kotlin, Android AlarmManager/BroadcastReceiver, Java Time, Room-backed repositories, JUnit, Robolectric, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-08-locked-boot-scheduling-design.md`

## Global Constraints

- Wait until `BOOT_COMPLETED`; never enable `directBootAware` for credential-protected state.
- Preserve schedule ledger, generation validation, T04 durable occurrence admission, and ExitCoordinator ownership.
- Use `RTC_WAKEUP`; exact alarms require current access and otherwise use existing idle-safe inexact fallback.
- Do not claim connected-device or OEM results unless actually run.
- A point occurrence more than 15 minutes late is skipped; a range start at/after its end is skipped; both are recorded as `Skipped:` history.

## Review Focus

- API 26+ locked boot delivers no receiver callback before unlock.
- A repeated fold time does not run twice; a gap start does not shift silently.
- An old or duplicate PendingIntent cannot run after edit, pause, delete, or ledger replacement.
- Inexact fallback can miss the 15-minute grace without creating a later phantom occurrence.
- A range that crosses DST or midnight closes only the lifecycle owning its exact end identity.

### Task 1: Align locked-boot manifest and receiver contract

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt`
- Modify: `app/src/test/java/com/nexaflow/app/AutomationRuntimeManifestTest.kt`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverBootContractTest.kt`
- Modify: `docs/OPEN_QUESTIONS.md`

**Interfaces:**
- `AutomationAlarmReceiver.isBootRecoveryAction(action: String?)` accepts `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` only.
- `AutomationAlarmReceiver.firesBootTriggers(action: String?)` returns true only for `BOOT_COMPLETED`.

- [ ] Add failing tests asserting locked boot is not a recovery action, package replacement does not fire boot triggers, and actual boot does.
- [ ] Run `:core:automation-engine:testDebugUnitTest --tests com.nexaflow.core.engine.AutomationAlarmReceiverBootContractTest`; verify failure is the missing policy.
- [ ] Remove the locked-boot action/dispatch, assert `receiverInfo.directBootAware` is false, and query that this receiver does not match `ACTION_LOCKED_BOOT_COMPLETED`.
- [ ] Record OQ-06 as resolved by issue #128 and the first-unlock policy.
- [ ] Re-run both focused receiver and app manifest tests.

### Task 2: Pin canonical occurrence identity and DST policy

**Files:**
- Modify: `domain/src/main/java/com/nexaflow/domain/schedule/TimeTriggerCalculator.kt` only if an uncovered transition violates the selected rules.
- Modify: `domain/src/test/java/com/nexaflow/domain/schedule/TimeTriggerCalculatorTest.kt`
- Modify: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationSchedulerExactAlarmPolicyTest.kt`
- Inspect without changing unless evidence requires it: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationScheduler.kt`, `feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/DashboardScreen.kt`.

**Interfaces:**
- Keep `TimeTriggerCalculator.nextFireTime(config, fromMillis, zone)` as the shared source used by both runtime scheduling and Dashboard preview.
- Keep `AutomationScheduler.occurrenceId(startAt,endAt)` and `generationOf(automationId,config,startAt,endAt)` as the durable identity source; preview remains a time preview and does not invent a second occurrence ledger.

- [ ] Add deterministic transition-matrix tests for Berlin and New York gap starts, both valid offsets in a fold, an overnight range crossing a DST offset change, and equal-input schedule IDs/generations.
- [ ] Run the focused calculator and scheduler classes; inspect each result against the decision table. Existing behavior may already pass; retain tests as regression evidence and change production logic only if a mismatch appears.
- [ ] If a test exposes an ambiguity, implement the smallest resolver change in `TimeTriggerCalculator`; skip gap starts, choose the earlier fold offset, and move a gap end forward by the gap length.
- [ ] Confirm by source review that `AutomationScheduler` and Dashboard both call `TimeTriggerCalculator.nextFireTime`; do not add another preview engine or expose unrelated UI controls.
- [ ] Run `:domain:test` and the focused automation-engine scheduler tests.

### Task 3: Apply bounded misfire policy before effects

**Files:**
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverMisfireTest.kt`
- Modify: `domain/src/test/java/com/nexaflow/domain/models/ExecutionOutcomeTest.kt` only if the existing skipped classifier lacks coverage.

**Interfaces:**
- Keep `AutomationAlarmReceiver.shouldExecuteRangeStart(isTimeRange, windowEndAt, deliveredAt)` as the pure range acceptance seam; make expired/missing range ends return false.
- Add pure `AutomationAlarmReceiver.shouldExecutePointStart(windowStartAt, deliveredAt)`; accept delays `0..15 * 60 * 1000L`, skip negative or later delays.

- [ ] Add a failing test against the existing range helper with `windowEndAt = deliveredAt - 1`, plus table-driven point tests at 0 ms, exactly 15 minutes, 15 minutes + 1 ms, and a future scheduled time.
- [ ] Run the focused test and verify the expired range assertion fails because the current helper accepts any non-null end.
- [ ] Apply the policy after durable occurrence validation and before `ExecutionEngine.runAutomation`; expired events record one existing `ExecutionRecord` with a `Skipped:` message, clear that occurrence, and schedule the next future one.
- [ ] Use immutable `windowStartAt` as `TriggerOccurrence.occurredAtEpochMs`; never use receiver wall time as event identity/time.
- [ ] Test that no execution call is made for skipped deliveries, history classifies the diagnostic as skipped, and repeated delivery does not create a second schedule identity.

### Task 4: Document recovery, capability, and change behavior

**Files:**
- Create: `docs/architecture/boot-and-recovery-policy.md`
- Modify: `docs/OPEN_QUESTIONS.md`
- Modify: `docs/archive/AUTOMATION_ENGINE_AUDIT_2026.md`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverTimeChangeTest.kt`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationSchedulerExactAlarmPolicyTest.kt`

- [ ] Pin `TIME_SET`, `TIMEZONE_CHANGED`, `TIMEZONE_OFFSET_CHANGED`, grant/revocation, exact/inexact fallback, pause/edit/delete, reboot recovery ordering, and the no-replay misfire policy.
- [ ] Document zone-local versus fixed-IANA behavior, DST gap/fold/overnight rules, limitations, permission requirements, and physical OEM validation as `NOT TESTED` unless run.
- [ ] Run focused datastore, domain schedule, receiver, manifest, scheduler, and execution unit tests; run `detekt lintDebug assembleDebug` and repository architecture/inventory gates.
- [ ] Review `git diff --check`, verify no Room/schema migration or new direct-boot storage, and publish a reviewer-ready PR tied to #128.

## Interface Pre-flight

- Task 2 produces `TimeTriggerOccurrence`; Task 3 consumes its immutable start/end fields. Keep the policy independent of Android and pass the canonical values from Task 2.
- Task 1 changes action routing only; Task 4 verifies the same actions remain registered after the locked-boot filter is removed.

## Completion Contract

Close #128 only after the PR is merged, exact-head CI is green, parent #122 is updated, and device-dependent behavior is either evidenced or precisely labeled `BLOCKED_EXTERNAL` / `NOT TESTED`.
