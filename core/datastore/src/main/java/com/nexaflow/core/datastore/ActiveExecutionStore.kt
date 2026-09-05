package com.nexaflow.core.datastore

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.activeExecutionDataStore by preferencesDataStore(
    name = "nexaflow_active_executions",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Durable lifecycle ledger and bounded checkpoint store for active automation
 * runs. It remains intentionally separate from [ActiveTriggerStore]: a trigger
 * may be active while constraints block a task, which must never arm its exit.
 */
class ActiveExecutionStore(private val context: Context) {

    private val dataStore = context.activeExecutionDataStore
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    /** Records that [automationId] entered the executable task lifecycle. */
    suspend fun markStarted(automationId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_EXECUTIONS] =
                (preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()) + automationId
        }
    }

    /**
     * Atomically removes and returns whether an active lifecycle was present.
     * End behavior is one-shot, so consuming the marker prevents duplicate exit
     * actions if two monitor callbacks arrive around the same state transition.
     */
    suspend fun consumeStarted(automationId: String): Boolean {
        var wasStarted = false
        dataStore.edit { preferences ->
            val active = preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()
            wasStarted = automationId in active
            preferences[KEY_ACTIVE_EXECUTIONS] = active - automationId
        }
        return wasStarted
    }

    /** Removes a lifecycle marker when a task is deleted or deliberately reset. */
    suspend fun clear(automationId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_EXECUTIONS] =
                (preferences[KEY_ACTIVE_EXECUTIONS] ?: emptySet()) - automationId
        }
    }

    /**
     * Starts a durable execution checkpoint. False means the bounded ledger is
     * full or this run id was already registered; callers must not claim success
     * for a non-durable run when this returns false.
     */
    suspend fun beginCheckpoint(checkpoint: DurableExecutionCheckpoint): Boolean {
        var accepted = false
        dataStore.edit { preferences ->
            val checkpoints = checkpoints(preferences)
            if (checkpoint.runId !in checkpoints && checkpoints.size < MAX_CHECKPOINTS) {
                checkpoints[checkpoint.runId] = checkpoint
                writeCheckpoints(preferences, checkpoints)
                accepted = true
            }
        }
        return accepted
    }

    suspend fun checkpoint(runId: String): DurableExecutionCheckpoint? =
        checkpoints(dataStore.data.first())[runId]

    /** Marks an action started and reserves its idempotency key before side effects. */
    suspend fun markActionStarted(
        runId: String,
        actionIndex: Int,
        idempotencyKey: String,
        updatedAt: Long,
        nodeId: String = "action:$actionIndex",
        backend: String? = null,
        inputHash: String? = null
    ): DurableExecutionCheckpoint? = updateCheckpoint(runId) { checkpoint ->
        require(actionIndex == checkpoint.nextActionIndex) {
            "Action checkpoint ordering mismatch for run $runId"
        }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
        check(idempotencyKey !in checkpoint.idempotencyKeys) {
            "Duplicate idempotency key for run $runId: $idempotencyKey"
        }
        val attempt = checkpoint.nodeExecutions
            .filter { it.nodeId == nodeId }
            .maxOfOrNull { it.attempt }
            ?.plus(1)
            ?: 1
        val node = DurableNodeExecution(
            nodeId = nodeId,
            attempt = attempt,
            state = DurableNodeExecutionState.RUNNING,
            startedAt = updatedAt,
            backend = backend,
            idempotencyKey = idempotencyKey,
            inputHash = inputHash,
            verificationState = DurableVerificationState.PENDING
        )
        checkpoint.copy(
            status = DurableExecutionStatus.ACTION_STARTED,
            currentNodeId = nodeId,
            idempotencyKeys = checkpoint.idempotencyKeys + idempotencyKey,
            nodeExecutions = checkpoint.nodeExecutions + node,
            verificationState = DurableVerificationState.PENDING,
            updatedAt = updatedAt,
            message = null
        )
    }

    /** Commits one successful action and advances the cursor exactly once. */
    suspend fun markActionCompleted(
        runId: String,
        actionIndex: Int,
        updatedAt: Long,
        outputHash: String? = null,
        verificationState: DurableVerificationState = DurableVerificationState.UNKNOWN
    ): DurableExecutionCheckpoint? = updateCheckpoint(runId) { checkpoint ->
        require(actionIndex == checkpoint.nextActionIndex) {
            "Action completion ordering mismatch for run $runId"
        }
        val nodeId = checkpoint.currentNodeId ?: "action:$actionIndex"
        val activeNode = checkpoint.nodeExecutions.lastOrNull { it.nodeId == nodeId && it.state == DurableNodeExecutionState.RUNNING }
        val nextNodes = if (activeNode == null) {
            checkpoint.nodeExecutions
        } else {
            checkpoint.nodeExecutions.map { node ->
                if (node === activeNode) {
                    node.copy(
                        state = DurableNodeExecutionState.SUCCEEDED,
                        completedAt = updatedAt,
                        outputHash = outputHash,
                        verificationState = verificationState
                    )
                } else node
            }
        }
        checkpoint.copy(
            status = DurableExecutionStatus.ACTION_COMPLETED,
            nextActionIndex = actionIndex + 1,
            completedActionIndexes = checkpoint.completedActionIndexes + actionIndex,
            nodeExecutions = nextNodes,
            verificationState = verificationState,
            updatedAt = updatedAt,
            message = null
        )
    }

    /**
     * Records uncertainty after an interrupted side effect. Recovery must verify
     * the external effect or compensate; it must not blindly re-run this action.
     */
    suspend fun markActionUnknown(
        runId: String,
        message: String,
        updatedAt: Long
    ): DurableExecutionCheckpoint? = updateCheckpoint(runId) { checkpoint ->
        val nodeId = checkpoint.currentNodeId ?: "action:${checkpoint.nextActionIndex}"
        val nextNodes = checkpoint.nodeExecutions.map { node ->
            if (node.nodeId == nodeId && node.state == DurableNodeExecutionState.RUNNING) {
                node.copy(
                    state = DurableNodeExecutionState.UNKNOWN,
                    completedAt = updatedAt,
                    verificationState = DurableVerificationState.UNKNOWN,
                    failureCode = "UNKNOWN_OUTCOME"
                )
            } else node
        }
        checkpoint.copy(
            status = DurableExecutionStatus.ACTION_UNKNOWN,
            nodeExecutions = nextNodes,
            verificationState = DurableVerificationState.UNKNOWN,
            updatedAt = updatedAt,
            message = message.take(MAX_MESSAGE_LENGTH)
        )
    }

    suspend fun markExitPending(runId: String, updatedAt: Long): DurableExecutionCheckpoint? =
        updateCheckpoint(runId) { it.copy(status = DurableExecutionStatus.EXIT_PENDING, updatedAt = updatedAt) }

    /** Removes a terminal checkpoint only after history/exit state is committed by the caller. */
    suspend fun completeCheckpoint(runId: String): Boolean {
        var removed = false
        dataStore.edit { preferences ->
            val checkpoints = checkpoints(preferences)
            removed = checkpoints.remove(runId) != null
            if (removed) writeCheckpoints(preferences, checkpoints)
        }
        return removed
    }

    /** Removes an unrecoverable checkpoint after an explicit user/system reset. */
    suspend fun clearCheckpoint(runId: String): Boolean = completeCheckpoint(runId)

    /** True when a recurring-maintenance occurrence already completed successfully. */
    suspend fun hasCompletedMaintenanceOccurrence(occurrenceKey: String): Boolean =
        maintenanceReceipts(dataStore.data.first()).any { it.occurrenceKey == occurrenceKey }

    /**
     * Persists a bounded completion receipt only after history and action
     * checkpoints are committed. The same key replaces itself atomically;
     * stale receipts are pruned on write rather than by a background service.
     */
    suspend fun recordCompletedMaintenanceOccurrence(
        occurrenceKey: String,
        automationId: String,
        completedAt: Long
    ) {
        val receipt = MaintenanceOccurrenceReceipt(occurrenceKey, automationId, completedAt)
        dataStore.edit { preferences ->
            val retainedAfter = completedAt - MAINTENANCE_RECEIPT_RETENTION_MS
            val receipts = maintenanceReceipts(preferences)
                .filter { it.completedAt >= retainedAfter && it.occurrenceKey != occurrenceKey }
                .plus(receipt)
                .sortedByDescending { it.completedAt }
                .take(MAX_MAINTENANCE_RECEIPTS)
            writeMaintenanceReceipts(preferences, receipts)
        }
    }

    /**
     * Atomically claims every non-terminal checkpoint after a process restart.
     * A second recovery worker sees no claimable record, preventing duplicate
     * replay. Claimed ACTION_STARTED/ACTION_UNKNOWN work must be verified first.
     */
    suspend fun claimRecoveryCandidates(updatedAt: Long): List<DurableExecutionCheckpoint> {
        val claimed = mutableListOf<DurableExecutionCheckpoint>()
        dataStore.edit { preferences ->
            val checkpoints = checkpoints(preferences)
            checkpoints.values.toList().forEach { checkpoint ->
                if (!checkpoint.isTerminal &&
                    checkpoint.status != DurableExecutionStatus.RECOVERY_CLAIMED &&
                    checkpoint.status != DurableExecutionStatus.RECOVERY_REQUIRED
                ) {
                    check(checkpoint.status.canTransitionTo(DurableExecutionStatus.RECOVERY_CLAIMED)) {
                        "Invalid recovery transition for ${checkpoint.runId}: ${checkpoint.status} -> ${DurableExecutionStatus.RECOVERY_CLAIMED}"
                    }
                    val next = checkpoint.copy(
                        status = DurableExecutionStatus.RECOVERY_CLAIMED,
                        recoverySourceStatus = checkpoint.status,
                        updatedAt = updatedAt,
                        message = checkpoint.message?.take(MAX_MESSAGE_LENGTH)
                    )
                    checkpoints[checkpoint.runId] = next
                    claimed += next
                }
            }
            if (claimed.isNotEmpty()) writeCheckpoints(preferences, checkpoints)
        }
        return claimed.sortedBy { it.startedAt }
    }

    /** Releases a claimed run into an explicit recovery-required state for diagnostics/UI. */
    suspend fun markRecoveryRequired(
        runId: String,
        message: String,
        updatedAt: Long
    ): DurableExecutionCheckpoint? = updateCheckpoint(runId) { checkpoint ->
        checkpoint.copy(
            status = DurableExecutionStatus.RECOVERY_REQUIRED,
            updatedAt = updatedAt,
            message = message.take(MAX_MESSAGE_LENGTH)
        )
    }

    internal suspend fun activeIdsForTest(): Set<String> =
        dataStore.data.first()[KEY_ACTIVE_EXECUTIONS].orEmpty()

    internal suspend fun checkpointsForTest(): List<DurableExecutionCheckpoint> =
        checkpoints(dataStore.data.first()).values.sortedBy { it.startedAt }

    internal suspend fun maintenanceReceiptsForTest(): List<MaintenanceOccurrenceReceipt> =
        maintenanceReceipts(dataStore.data.first())

    private suspend fun updateCheckpoint(
        runId: String,
        transform: (DurableExecutionCheckpoint) -> DurableExecutionCheckpoint
    ): DurableExecutionCheckpoint? {
        var updated: DurableExecutionCheckpoint? = null
        dataStore.edit { preferences ->
            val checkpoints = checkpoints(preferences)
            val current = checkpoints[runId] ?: return@edit
            updated = transform(current)
            val next = checkNotNull(updated)
            check(current.status.canTransitionTo(next.status)) {
                "Invalid durable transition for $runId: ${current.status} -> ${next.status}"
            }
            checkpoints[runId] = next
            writeCheckpoints(preferences, checkpoints)
        }
        return updated
    }

    private fun checkpoints(preferences: Preferences): LinkedHashMap<String, DurableExecutionCheckpoint> {
        val decoded = LinkedHashMap<String, DurableExecutionCheckpoint>()
        preferences[KEY_CHECKPOINTS].orEmpty().forEach { serialized ->
            runCatching { json.decodeFromString(DurableExecutionCheckpoint.serializer(), serialized) }
                .getOrNull()
                ?.let { checkpoint -> decoded.putIfAbsent(checkpoint.runId, checkpoint) }
        }
        return decoded
    }

    private fun maintenanceReceipts(preferences: Preferences): List<MaintenanceOccurrenceReceipt> =
        preferences[KEY_MAINTENANCE_RECEIPTS].orEmpty().mapNotNull { serialized ->
            runCatching { json.decodeFromString(MaintenanceOccurrenceReceipt.serializer(), serialized) }
                .getOrNull()
        }

    private fun writeMaintenanceReceipts(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        receipts: List<MaintenanceOccurrenceReceipt>
    ) {
        preferences[KEY_MAINTENANCE_RECEIPTS] = receipts.mapTo(LinkedHashSet()) { receipt ->
            json.encodeToString(MaintenanceOccurrenceReceipt.serializer(), receipt)
        }
    }

    private fun writeCheckpoints(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        checkpoints: Map<String, DurableExecutionCheckpoint>
    ) {
        preferences[KEY_CHECKPOINTS] = checkpoints.values.mapTo(LinkedHashSet()) { checkpoint ->
            json.encodeToString(DurableExecutionCheckpoint.serializer(), checkpoint)
        }
    }

    private companion object {
        val KEY_ACTIVE_EXECUTIONS = stringSetPreferencesKey("active_executions")
        val KEY_CHECKPOINTS = stringSetPreferencesKey("execution_checkpoints")
        val KEY_MAINTENANCE_RECEIPTS = stringSetPreferencesKey("maintenance_occurrence_receipts")
        const val MAX_CHECKPOINTS = 128
        const val MAX_MAINTENANCE_RECEIPTS = 256
        const val MAINTENANCE_RECEIPT_RETENTION_MS = 45L * 24 * 60 * 60 * 1000
        const val MAX_MESSAGE_LENGTH = 512
    }
}
