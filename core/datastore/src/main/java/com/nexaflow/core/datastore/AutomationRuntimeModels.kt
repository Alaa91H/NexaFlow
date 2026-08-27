package com.nexaflow.core.datastore

import kotlinx.serialization.Serializable

/**
 * Durable, occurrence-scoped lifecycle for a stateful automation.
 *
 * The state is deliberately separate from action checkpoints: it answers
 * whether a particular trigger occurrence still owns an exit behavior, while
 * checkpoints describe progress inside an individual action chain.
 */
@Serializable
enum class AutomationRuntimeLifecycleState {
    ACTIVE,
    EXITING,
    EXIT_FAILED
}

/** Every event source uses the same typed exit vocabulary; no monitor owns strings. */
@Serializable
enum class ExitReason {
    TIME_WINDOW_ENDED,
    TRIGGER_FALSE,
    AUTOMATION_DISABLED,
    MANUAL_STOP,
    PROCESS_RECOVERY,
    BOOT_RECOVERY,
    SCHEDULE_RECONCILIATION,
    SYSTEM_STATE_CHANGED,
    UNKNOWN
}

/**
 * Minimal durable state needed to make one exit decision exactly once.
 *
 * [snapshotJson] holds a bounded, local snapshot encoded by the execution
 * layer only when restore-on-exit is required. It never contains action
 * payloads, variables, notification content, command output, or credentials.
 */
@Serializable
data class AutomationRuntimeState(
    val automationId: String,
    val occurrenceId: String,
    val source: String,
    val sourceKey: String,
    val lifecycleState: AutomationRuntimeLifecycleState,
    val activatedAt: Long,
    val expectedEndAt: Long? = null,
    val scheduleGeneration: String? = null,
    val snapshotJson: String? = null,
    val exitStartedAt: Long? = null,
    val exitAttempt: Int = 0,
    val exitReason: ExitReason? = null,
    val lastError: String? = null,
    val schemaVersion: Int = 1
) {
    init {
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(occurrenceId.isNotBlank()) { "occurrenceId must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(sourceKey.isNotBlank()) { "sourceKey must not be blank" }
        require(activatedAt >= 0L) { "activatedAt must not be negative" }
        require(expectedEndAt == null || expectedEndAt >= activatedAt) {
            "expectedEndAt must not precede activation"
        }
        require(exitAttempt >= 0) { "exitAttempt must not be negative" }
        require(snapshotJson == null || snapshotJson.length <= MAX_SNAPSHOT_LENGTH) {
            "snapshotJson exceeds the bounded runtime ledger"
        }
        require(lastError == null || lastError.length <= MAX_ERROR_LENGTH) {
            "lastError exceeds the bounded runtime ledger"
        }
        require(schemaVersion == 1) { "Unsupported runtime schema version" }
    }

    companion object {
        const val MAX_SNAPSHOT_LENGTH = 24_000
        const val MAX_ERROR_LENGTH = 512
    }
}

/** Immutable lifecycle context supplied only by stateful trigger sources. */
data class AutomationLifecycleContext(
    val occurrenceId: String,
    val source: String,
    val sourceKey: String,
    val expectedEndAt: Long? = null,
    val scheduleGeneration: String? = null
) {
    init {
        require(occurrenceId.isNotBlank()) { "occurrenceId must not be blank" }
        require(source.isNotBlank()) { "source must not be blank" }
        require(sourceKey.isNotBlank()) { "sourceKey must not be blank" }
    }
}

/** Result of attempting the only allowed transition into exit execution. */
sealed interface ExitClaim {
    data class Claimed(val state: AutomationRuntimeState) : ExitClaim
    data object NoActiveOccurrence : ExitClaim
    data object OccurrenceMismatch : ExitClaim
    data object AlreadyExiting : ExitClaim
    data class RecoveryRequired(val state: AutomationRuntimeState) : ExitClaim
}

/**
 * Current scheduled start window. Its immutable token validates both START and
 * END delivery; a time-range record remains until its matching END has been
 * processed, while a one-shot start is removed after its single delivery.
 */
@Serializable
data class ScheduledAutomationOccurrence(
    val automationId: String,
    val occurrenceId: String,
    val generation: String,
    val windowStartAt: Long,
    val windowEndAt: Long? = null,
    val schemaVersion: Int = 1
) {
    init {
        require(automationId.isNotBlank()) { "automationId must not be blank" }
        require(occurrenceId.isNotBlank()) { "occurrenceId must not be blank" }
        require(generation.isNotBlank()) { "generation must not be blank" }
        require(windowStartAt >= 0L) { "windowStartAt must not be negative" }
        require(windowEndAt == null || windowEndAt >= windowStartAt) {
            "windowEndAt must not precede windowStartAt"
        }
        require(schemaVersion == 1) { "Unsupported schedule schema version" }
    }
}
