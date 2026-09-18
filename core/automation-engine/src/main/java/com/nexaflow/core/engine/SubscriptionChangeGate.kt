package com.nexaflow.core.engine

/** Initial registration delivers the current SIM; only a later change needs rebinding. */
internal class SubscriptionChangeGate {
    private var observed: Int? = null

    @Synchronized
    fun changed(subscriptionId: Int): Boolean {
        val previous = observed
        observed = subscriptionId
        return previous != null && previous != subscriptionId
    }

    @Synchronized
    fun reset() {
        observed = null
    }
}
