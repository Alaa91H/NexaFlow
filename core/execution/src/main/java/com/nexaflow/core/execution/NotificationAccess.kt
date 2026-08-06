package com.nexaflow.core.execution

import java.util.concurrent.ConcurrentHashMap

/**
 * Shared state between the [ExecutionEngine] (which runs the
 * SYSTEM_BLOCK_NOTIFICATION / SYSTEM_CLEAR_APP_NOTIFICATIONS actions) and the
 * system-bound [NotificationListenerService] that actually cancels the posted
 * notifications. The listener registers itself here whenever the system binds
 * it (i.e. while the user has granted notification access).
 *
 * All members are thread-safe: [ExecutionEngine] runs on a coroutine while the
 * listener is invoked on the system's binder thread.
 */
object NotificationAccess {

    /** The bridge implemented by the active NotificationListenerService, if any. */
    @Volatile
    var listener: NotificationListenerBridge? = null

    /** Packages the user asked to silence via a SYSTEM_BLOCK_NOTIFICATION action. */
    private val blockedPackages = ConcurrentHashMap.newKeySet<String>()

    fun isBlocked(packageName: String): Boolean = packageName in blockedPackages

    /** Blocks (or unblocks) all notifications from the given package. */
    fun setBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            blockedPackages.add(packageName)
        } else {
            blockedPackages.remove(packageName)
        }
        // When blocking starts, dismiss whatever that app already posted.
        if (blocked) {
            listener?.cancelForPackage(packageName)
        }
    }

    /** Dismisses every currently active notification posted by [packageName]. */
    fun clearForPackage(packageName: String) {
        listener?.cancelForPackage(packageName)
    }

    fun clearBlockedState() {
        blockedPackages.clear()
    }
}

/** Implemented by the bound NotificationListenerService so the engine can cancel notifications. */
interface NotificationListenerBridge {
    fun cancelForPackage(packageName: String)
}
