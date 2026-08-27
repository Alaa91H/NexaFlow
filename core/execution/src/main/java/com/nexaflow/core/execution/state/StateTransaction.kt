package com.nexaflow.core.execution.state

import android.content.Context
import com.nexaflow.core.execution.DeviceStateSnapshot
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * A reversible device-state change. [apply] performs the change; [rollback]
 * undoes it. This is the workflow-level generalization of the legacy
 * `DeviceStateSnapshot` (capture → apply → rollback) used by both revert-on-exit
 * and future branching/rollback flows.
 */
interface StateTransaction {
    /** Applies the captured change. */
    fun apply(context: Context): SystemControlResult

    /** Reverts the change, restoring the device to its pre-apply state. */
    fun rollback(context: Context): SystemControlResult
}

/**
 * [StateTransaction] built on the legacy [DeviceStateSnapshot]: applying is a
 * no-op (the snapshot itself holds the original values), while rolling back
 * restores the device to those values — exactly the revert-on-exit behavior.
 */
class DeviceStateTransaction private constructor(
    private val snapshot: DeviceStateSnapshot
) : StateTransaction {

    override fun apply(context: Context): SystemControlResult {
        // The snapshot already captured the original state; nothing to apply.
        return SystemControlResult.ok("Device state captured")
    }

    override fun rollback(context: Context): SystemControlResult = runCatching {
        snapshot.restore(context)
    }.getOrElse { SystemControlResult.fail("Restore failed: ${it.message}") }

    companion object {
        fun capture(context: Context): DeviceStateTransaction =
            DeviceStateTransaction(DeviceStateSnapshot.capture(context))
    }
}
