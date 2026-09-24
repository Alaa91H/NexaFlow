package com.nexaflow.core.rom

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lightweight process-local invalidation bus for authorization changes that do
 * not have a reliable Android callback (for example a freshly approved Root
 * prompt or an elevated permission-repair pass).
 *
 * It carries no grant data. Consumers must re-probe the real platform state.
 */
object PrivilegeStateEvents {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun notifyChanged() {
        listeners.forEach { listener ->
            runCatching { listener.invoke() }
        }
    }
}
