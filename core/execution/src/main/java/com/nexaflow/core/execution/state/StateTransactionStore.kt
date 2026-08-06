package com.nexaflow.core.execution.state

import android.content.Context
import com.nexaflow.core.rom.model.SystemControlResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores captured [StateTransaction]s per automation so the exit path can
 * roll the device back to its pre-run state (revert-on-exit). Abstracted so the
 * workflow runner stays testable without Android.
 */
interface StateTransactionStore {

    /** Captures the current device state for [automationId]. Returns false on failure. */
    fun capture(automationId: String): Boolean

    /** Rolls back the captured transaction; ok when nothing was captured. */
    fun rollback(automationId: String): SystemControlResult

    /** Discards a stored transaction (e.g. when the automation is deleted). */
    fun clear(automationId: String)
}

/**
 * Production store backed by [DeviceStateTransaction] (which wraps the legacy
 * `DeviceStateSnapshot`). Thread-safe map, best-effort capture.
 */
class DeviceStateTransactionStore(private val context: Context) : StateTransactionStore {

    private val transactions = ConcurrentHashMap<String, DeviceStateTransaction>()

    override fun capture(automationId: String): Boolean {
        return runCatching {
            transactions[automationId] = DeviceStateTransaction.capture(context)
        }.isSuccess
    }

    override fun rollback(automationId: String): SystemControlResult {
        val transaction = transactions.remove(automationId)
            ?: return SystemControlResult.ok("Nothing to restore")
        return transaction.rollback(context)
    }

    override fun clear(automationId: String) {
        transactions.remove(automationId)
    }
}
