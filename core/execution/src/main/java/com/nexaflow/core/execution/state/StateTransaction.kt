package com.nexaflow.core.execution.state

import android.content.Context
import com.nexaflow.core.execution.DeviceStateSnapshot
import com.nexaflow.core.rom.model.SystemControlResult

/**
 * A reversible device-state change. Device rollback requires explicit
 * ownership evidence before writing captured values.
 */
interface StateTransaction {
    /** Applies the captured change. */
    fun apply(context: Context): SystemControlResult

    /** Reverts only state changes this transaction can prove it still owns. */
    fun rollback(context: Context): SystemControlResult
}

/**
 * [StateTransaction] built on [DeviceStateSnapshot]. The ownership-aware
 * overload is used by the store; the generic interface method fails closed
 * because it has no list of settings this run may restore.
 */
class DeviceStateTransaction private constructor(
    private val snapshot: DeviceStateSnapshot
) : StateTransaction {

    fun rollback(
        context: Context,
        ownerships: List<Pair<com.nexaflow.domain.models.Action, DeviceStateSnapshot>>
    ): SystemControlResult = runCatching {
        when {
            ownerships.isEmpty() -> SystemControlResult.ok("Nothing to restore")
            else -> snapshot.restoreIfUnchanged(context, ownerships)
        }
    }.getOrElse { SystemControlResult.fail("Restore failed: ${it.message}", outcomeUncertain = true) }

    override fun apply(context: Context): SystemControlResult {
        // The snapshot already captured the original state; nothing to apply.
        return SystemControlResult.ok("Device state captured")
    }

    override fun rollback(context: Context): SystemControlResult =
        SystemControlResult.ok("Nothing to restore without recorded action ownership")

    companion object {
        fun capture(context: Context): DeviceStateTransaction =
            DeviceStateTransaction(DeviceStateSnapshot.capture(context))
    }
}
