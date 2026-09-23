package com.nexaflow.core.execution

import android.content.Context
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason

/**
 * Read-only dashboard projection of the durable automation lifecycle ledger.
 *
 * The UI deliberately sees only operational metadata needed to explain whether
 * a stateful task is active, ending, or requires exit recovery. Device-state
 * snapshots and occurrence keys remain internal to the execution layer.
 */
data class AutomationLifecycleSnapshot(
    val state: AutomationLifecycleDisplayState,
    val activatedAt: Long,
    val expectedEndAt: Long?,
    val exitStartedAt: Long?,
    val exitAttempt: Int,
    val exitReason: AutomationExitReason?
)

enum class AutomationLifecycleDisplayState {
    ACTIVE,
    EXITING,
    EXIT_FAILED
}

enum class AutomationExitReason {
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

object AutomationLifecycleStateReader {

    suspend fun read(context: Context, automationId: String): AutomationLifecycleSnapshot? {
        val current = AutomationRuntimeStore(context.applicationContext).current(automationId)
            ?: return null

        return AutomationLifecycleSnapshot(
            state = when (current.lifecycleState) {
                AutomationRuntimeLifecycleState.ACTIVE ->
                    AutomationLifecycleDisplayState.ACTIVE
                AutomationRuntimeLifecycleState.EXITING ->
                    AutomationLifecycleDisplayState.EXITING
                AutomationRuntimeLifecycleState.EXIT_FAILED ->
                    AutomationLifecycleDisplayState.EXIT_FAILED
            },
            activatedAt = current.activatedAt,
            expectedEndAt = current.expectedEndAt,
            exitStartedAt = current.exitStartedAt,
            exitAttempt = current.exitAttempt,
            exitReason = current.exitReason?.toDisplayReason()
        )
    }

    private fun ExitReason.toDisplayReason(): AutomationExitReason = when (this) {
        ExitReason.TIME_WINDOW_ENDED -> AutomationExitReason.TIME_WINDOW_ENDED
        ExitReason.TRIGGER_FALSE -> AutomationExitReason.TRIGGER_FALSE
        ExitReason.AUTOMATION_DISABLED -> AutomationExitReason.AUTOMATION_DISABLED
        ExitReason.MANUAL_STOP -> AutomationExitReason.MANUAL_STOP
        ExitReason.PROCESS_RECOVERY -> AutomationExitReason.PROCESS_RECOVERY
        ExitReason.BOOT_RECOVERY -> AutomationExitReason.BOOT_RECOVERY
        ExitReason.SCHEDULE_RECONCILIATION -> AutomationExitReason.SCHEDULE_RECONCILIATION
        ExitReason.SYSTEM_STATE_CHANGED -> AutomationExitReason.SYSTEM_STATE_CHANGED
        ExitReason.UNKNOWN -> AutomationExitReason.UNKNOWN
    }
}
