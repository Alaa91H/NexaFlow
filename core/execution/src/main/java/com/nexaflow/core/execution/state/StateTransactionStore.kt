package com.nexaflow.core.execution.state

import android.content.Context
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores captured [StateTransaction]s per automation so the exit path can
 * roll the device back to its pre-run state (revert-on-exit). Abstracted so the
 * workflow runner stays testable without Android.
 */
interface StateTransactionStore {

    /** Captures the current device state for [automationId]. Returns false on failure. */
    fun capture(automationId: String): Boolean

    /** Atomically captures state and marks the run active when supported. */
    fun captureAndBeginRun(automationId: String): Boolean {
        val captured = capture(automationId)
        if (captured) beginRun(automationId)
        return captured
    }

    /** Rolls back the captured transaction; ok when nothing was captured. */
    fun rollback(automationId: String): SystemControlResult

    /** Marks the interval in which a run may still complete device mutations. */
    fun beginRun(automationId: String) = Unit

    /** Closes the interval opened by [beginRun]. */
    fun endRun(automationId: String) = Unit

    /** Records the action's observed result immediately after execution. */
    fun recordActionOutcome(automationId: String, action: Action, result: SystemControlResult) = Unit

    /** Discards a stored transaction (e.g. when the automation is deleted). */
    fun clear(automationId: String)
}

/**
 * Production store backed by [DeviceStateTransaction] (which wraps the legacy
 * `DeviceStateSnapshot`). Thread-safe map, best-effort capture.
 */
class DeviceStateTransactionStore(private val context: Context) : StateTransactionStore {

    private data class Entry(
        val transaction: DeviceStateTransaction,
        var activeRuns: Int = 0,
        val ownerships: LinkedHashMap<String, Pair<Action, com.nexaflow.core.execution.DeviceStateSnapshot>> =
            linkedMapOf()
    )

    private val transactions = ConcurrentHashMap<String, Entry>()

    override fun capture(automationId: String): Boolean {
        return runCatching {
            transactions[automationId] = Entry(DeviceStateTransaction.capture(context))
        }.isSuccess
    }

    override fun captureAndBeginRun(automationId: String): Boolean = runCatching {
        var began = false
        transactions.compute(automationId) { _, current ->
            if (current != null && synchronized(current) { current.activeRuns > 0 }) {
                current
            } else {
                Entry(DeviceStateTransaction.capture(context), activeRuns = 1).also { began = true }
            }
        }
        began
    }.getOrDefault(false)

    override fun rollback(automationId: String): SystemControlResult {
        val entry = transactions[automationId]
            ?: return SystemControlResult.ok("Nothing to restore")
        val result = synchronized(entry) {
            if (entry.activeRuns > 0) {
                return@synchronized SystemControlResult.fail(
                    "Restore deferred while an automation action is still running",
                    errorCode = "EXECUTION_IN_PROGRESS"
                )
            }
            entry.transaction.rollback(context, entry.ownerships.values.toList())
        }
        if (result.success) transactions.remove(automationId, entry)
        return result
    }

    override fun beginRun(automationId: String) {
        transactions[automationId]?.let { entry -> synchronized(entry) { entry.activeRuns++ } }
    }

    override fun endRun(automationId: String) {
        transactions[automationId]?.let { entry -> synchronized(entry) {
            if (entry.activeRuns > 0) entry.activeRuns--
        } }
    }

    override fun recordActionOutcome(
        automationId: String,
        action: Action,
        result: SystemControlResult
    ) {
        val entry = transactions[automationId] ?: return
        synchronized(entry) {
            val key = if (action.type.name == "SYSTEM_STREAM_VOLUME") {
                "${action.type.name}:${action.config["stream"] ?: "MUSIC"}"
            } else action.type.name
            if (result.success && !result.outcomeUncertain) {
                val expected = runCatching {
                    com.nexaflow.core.execution.DeviceStateSnapshot.capture(context)
                }.getOrNull()
                if (expected == null) entry.ownerships.remove(key)
                else entry.ownerships[key] = action to expected
            } else {
                entry.ownerships.remove(key)
            }
        }
    }

    override fun clear(automationId: String) {
        transactions.remove(automationId)
    }
}
