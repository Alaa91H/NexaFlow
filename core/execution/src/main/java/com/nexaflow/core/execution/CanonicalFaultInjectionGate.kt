package com.nexaflow.core.execution

import com.nexaflow.domain.canonical.AtomicCommand
import com.nexaflow.domain.canonical.FaultInjectionController

/**
 * T32 product fault-injection seam.
 *
 * Production uses [DISABLED]. Tests and device diagnostics may install a
 * deterministic schedule, but the decision is made against the exact
 * [AtomicCommand.commandId] that is about to cross the provider boundary.
 */
fun interface CanonicalFaultInjectionGate {
    fun decide(command: AtomicCommand): FaultInjectionController.Decision

    companion object {
        val DISABLED: CanonicalFaultInjectionGate =
            CanonicalFaultInjectionGate { FaultInjectionController.Decision.Pass }
    }
}

/** Deterministic adapter over the pure T32 schedule/tracker contract. */
class ScheduledCanonicalFaultInjectionGate(
    private val schedule: FaultInjectionController.FaultSchedule,
    private val scheduleExpired: () -> Boolean = { false },
    private val tracker: FaultInjectionController.AttemptTracker =
        FaultInjectionController.AttemptTracker(),
) : CanonicalFaultInjectionGate {

    override fun decide(command: AtomicCommand): FaultInjectionController.Decision =
        FaultInjectionController.decide(
            schedule = schedule,
            tracker = tracker,
            commandId = command.commandId,
            scheduleExpired = scheduleExpired(),
        )

    fun attemptCount(commandId: String): Int = tracker.attemptCount(commandId)
}
