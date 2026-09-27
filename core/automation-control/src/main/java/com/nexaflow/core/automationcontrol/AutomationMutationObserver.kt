package com.nexaflow.core.automationcontrol

import com.nexaflow.domain.models.Automation

/**
 * Side-effect-free fan-out for committed definition mutations.
 *
 * The service invokes the observer after a mutation is durably committed;
 * observer failures never fail the mutation itself. Host layers bridge this
 * to user-visible surfaces (e.g. the agent event stream) without the control
 * layer depending on them.
 */
fun interface AutomationMutationObserver {
    suspend fun onCommitted(
        kind: AutomationMutationKind,
        automation: Automation,
        revision: Long,
        context: AutomationMutationContext
    )

    companion object {
        val NO_OP = AutomationMutationObserver { _, _, _, _ -> }
    }
}

/**
 * First-party (human/UI) mutation contexts.
 *
 * Human flows keep today's permissiveness (`requireExecutable = false`: the
 * UI never dry-run-gated saves) while gaining structural validation,
 * transactional commits, provenance metadata, audit rows and event fan-out.
 * Every tap is a distinct user intent, so no idempotency key is attached
 * unless the caller passes one explicitly.
 */
object HumanAutomationMutations {
    fun context(
        screen: String,
        idempotencyKey: String? = null,
        requestId: String? = null
    ): AutomationMutationContext {
        require(screen.isNotBlank()) { "Mutation screen must not be blank" }
        return AutomationMutationContext(
            actorId = "human:ui",
            origin = AutomationMutationOrigin.HUMAN,
            requireExecutable = false,
            transport = "UI:${screen.take(120)}",
            requestId = requestId?.take(256),
            idempotencyKey = idempotencyKey
        )
    }

    fun importContext(
        idempotencyKey: String?,
        requestId: String? = null
    ): AutomationMutationContext {
        require(!idempotencyKey.isNullOrBlank()) { "Bulk import requires an idempotency key" }
        return AutomationMutationContext(
            actorId = "human:import",
            origin = AutomationMutationOrigin.IMPORT,
            requireExecutable = false,
            transport = "UI:import",
            requestId = requestId?.take(256),
            idempotencyKey = idempotencyKey
        )
    }
}
