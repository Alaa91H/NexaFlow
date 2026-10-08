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

- [x] Add failing tests asserting locked boot is not a recovery action, package replacement does not fire boot triggers, and actual boot does.
- [x] Remove the locked-boot action/dispatch, assert `receiverInfo.directBootAware` is false, and query that this receiver does not match `ACTION_LOCKED_BOOT_COMPLETED`.
- [x] Record OQ-06 as resolved by issue #128 and the first-unlock policy.
- [x] Re-run receiver contract and app manifest tests (`:core:automation-engine:testDebugUnitTest`; `:app:testDebugUnitTest --tests ...AutomationRuntimeManifestTest`).

### Task 2: Pin canonical occurrence identity and DST policy

**Files:**
- Modify: `domain/src/main/java/com/nexaflow/domain/schedule/TimeTriggerCalculator.kt` only if an uncovered transition violates the selected rules.
- Modify: `domain/src/test/java/com/nexaflow/domain/schedule/TimeTriggerCalculatorTest.kt`
- Modify: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationSchedulerExactAlarmPolicyTest.kt`
- Inspect without changing unless evidence requires it: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationScheduler.kt`, `feature/dashboard/src/main/java/com/nexaflow/feature/dashboard/DashboardScreen.kt`.

**Interfaces:**
- Keep `TimeTriggerCalculator.nextFireTime(config, fromMillis, zone)` as the shared source used by both runtime scheduling and Dashboard preview.
- Keep `AutomationScheduler.occurrenceId(startAt,endAt)` and `generationOf(automationId,config,startAt,endAt)` as the durable identity source; preview remains a time preview and does not invent a second occurrence ledger.

- [x] Add deterministic transition tests for Berlin gap starts, fold starts, overnight gap/fold ends, existing New York DST recurrence, and deterministic schedule IDs/generations.
- [x] Run `:domain:testDebugUnitTest --tests com.nexaflow.domain.schedule.TimeTriggerCalculatorTest`; current resolver behavior passed so no production calculator change was required.
- [x] Confirm by source review that `AutomationScheduler` and Dashboard both call `TimeTriggerCalculator.nextFireTime`; no duplicate preview engine was added.
- [x] Run `:core:automation-engine:testDebugUnitTest --tests ...AutomationSchedulerExactAlarmPolicyTest` (included in focused module verification).

### Task 3: Apply bounded misfire policy before effects

**Files:**
- Modify: `core/automation-engine/src/main/java/com/nexaflow/core/engine/AutomationAlarmReceiver.kt`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverMisfireTest.kt`
- Modify: `domain/src/test/java/com/nexaflow/domain/models/ExecutionOutcomeTest.kt` only if the existing skipped classifier lacks coverage.

**Interfaces:**
- Keep `AutomationAlarmReceiver.shouldExecuteRangeStart(isTimeRange, windowEndAt, deliveredAt)` as the pure range acceptance seam; make expired/missing range ends return false.
- Add pure `AutomationAlarmReceiver.shouldExecutePointStart(windowStartAt, deliveredAt)`; accept delays `0..15 * 60 * 1000L`, skip negative or later delays.

- [x] Add range-end boundary and table-driven point grace tests at 0 ms, exactly 15 minutes, 15 minutes + 1 ms, and a future scheduled time.
- [x] Apply the policy after durable occurrence validation and before `ExecutionEngine.runAutomation`; late events record an existing `ExecutionRecord` with `Skipped:`, consume that occurrence, and schedule the next future one. The diagnostic ID is stable across receiver redelivery to keep the history upsert idempotent.
- [x] Use immutable `windowStartAt` as `TriggerOccurrence.occurredAtEpochMs`.
- [x] Test that history diagnostic records classify as skipped; schedule identity validation remains before the misfire gate and existing deterministic generation tests pass.

### Task 4: Document recovery, capability, and change behavior

**Files:**
- Create: `docs/architecture/boot-and-recovery-policy.md`
- Modify: `docs/OPEN_QUESTIONS.md`
- Modify: `docs/archive/AUTOMATION_ENGINE_AUDIT_2026.md`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationAlarmReceiverTimeChangeTest.kt`
- Test: `core/automation-engine/src/test/java/com/nexaflow/core/engine/AutomationSchedulerExactAlarmPolicyTest.kt`

- [x] Pin time/zone/offset and exact-alarm reschedule broadcast coverage, exact/inexact fallback, durable cancellation/reconciliation, boot recovery ordering, and no-replay misfires.
- [x] Document zone-local and fixed-IANA behavior, DST gap/fold/overnight rules, permissions, and physical OEM validation as `NOT TESTED`.
- [x] Run focused receiver, app manifest, domain schedule and scheduler tests; run `detekt lintDebug assembleDebug` and architecture/inventory gates.
- [x] Review `git diff --check`; no Room/schema migration or device-protected storage was introduced.
- [ ] Push the implementation, obtain exact-head CI, and publish a reviewer-ready PR tied to #128.

## Interface Pre-flight

- Task 2 produces `TimeTriggerOccurrence`; Task 3 consumes its immutable start/end fields. Keep the policy independent of Android and pass the canonical values from Task 2.
- Task 1 changes action routing only; Task 4 verifies the same actions remain registered after the locked-boot filter is removed.

## Completion Contract

Close #128 only after the PR is merged, exact-head CI is green, parent #122 is updated, and device-dependent behavior is either evidenced or precisely labeled `BLOCKED_EXTERNAL` / `NOT TESTED`.
