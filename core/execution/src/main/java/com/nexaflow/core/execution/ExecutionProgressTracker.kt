package com.nexaflow.core.execution

import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class LiveActionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    UNKNOWN
}

data class LiveActionProgress(
    val index: Int,
    val actionType: String,
    val status: LiveActionStatus,
    val channel: String? = null,
    val errorCode: String? = null,
    val verificationAttempted: Boolean = false,
    val verified: Boolean? = null
)

data class AutomationExecutionProgress(
    val automationId: String,
    val startedAt: Long,
    val finished: Boolean,
    val actions: List<LiveActionProgress>
)

/**
 * In-process progress only. Durable truth remains execution history/checkpoints;
 * this tracker exists so an open details screen can show the current action
 * without polling logs or coupling Compose to the notification progress card.
 */
class ExecutionProgressTracker {
    private val state =
        MutableStateFlow<Map<String, AutomationExecutionProgress>>(emptyMap())

    fun observe(automationId: String): Flow<AutomationExecutionProgress?> =
        state.map { it[automationId] }.distinctUntilChanged()

    fun start(automation: Automation, startedAt: Long) {
        val progress = AutomationExecutionProgress(
            automationId = automation.id,
            startedAt = startedAt,
            finished = false,
            actions = automation.actions.mapIndexed { index, action ->
                LiveActionProgress(
                    index = index,
                    actionType = action.type.name,
                    status = LiveActionStatus.PENDING
                )
            }
        )
        state.update { it + (automation.id to progress) }
    }

    fun markRunning(automationId: String, index: Int) {
        updateAction(automationId, index) {
            it.copy(status = LiveActionStatus.RUNNING)
        }
    }

    fun markSkipped(automationId: String, index: Int) {
        updateAction(automationId, index) {
            it.copy(status = LiveActionStatus.SKIPPED)
        }
    }

    fun markResult(
        automationId: String,
        index: Int,
        result: SystemControlResult,
        fallbackChannel: String?
    ) {
        updateAction(automationId, index) {
            it.copy(
                status = if (result.success) {
                    LiveActionStatus.SUCCEEDED
                } else {
                    LiveActionStatus.FAILED
                },
                channel = result.executionChannel ?: fallbackChannel,
                errorCode = result.errorCode,
                verificationAttempted = result.verificationAttempted,
                verified = result.verified
            )
        }
    }

    fun markUnknown(automationId: String, index: Int) {
        updateAction(automationId, index) {
            it.copy(status = LiveActionStatus.UNKNOWN)
        }
    }

    fun finish(automationId: String) {
        state.update { all ->
            val current = all[automationId] ?: return@update all
            all + (automationId to current.copy(finished = true))
        }
    }

    fun clear(automationId: String) {
        state.update { it - automationId }
    }

    private inline fun updateAction(
        automationId: String,
        index: Int,
        transform: (LiveActionProgress) -> LiveActionProgress
    ) {
        state.update { all ->
            val current = all[automationId] ?: return@update all
            val next = current.actions.map { action ->
                if (action.index == index) transform(action) else action
            }
            all + (automationId to current.copy(actions = next))
        }
    }
}
