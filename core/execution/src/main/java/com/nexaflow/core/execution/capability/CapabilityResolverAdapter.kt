package com.nexaflow.core.execution.capability

import android.content.Context
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityErrorCode
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.domain.capability.CapabilityBackend as DomainCapabilityBackend
import com.nexaflow.domain.capability.CapabilityResolver

/**
 * Concrete implementation of [CapabilityResolver] that bridges the domain
 * resolver interface to the existing [CapabilityRuntime] resolver.
 *
 * This adapter avoids duplicating backend-ranking logic and instead delegates
 * to the already-existing [CapabilityResolver] (runtime class) while satisfying
 * the domain-layer contract.
 */
class CapabilityResolverAdapter(
    private val registry: CapabilityRegistry,
    private val runtimeResolver: CapabilityResolver
) : com.nexaflow.domain.capability.CapabilityResolver {

    private val domainBackendAdapters = mutableMapOf<CapabilityBackendId, DomainCapabilityBackend>()

    override suspend fun resolve(
        context: Context,
        request: CapabilityRequest,
        snapshot: CapabilityDeviceState
    ): DomainCapabilityBackend? {
        val resolution = runtimeResolver.resolve(request, snapshot)
        val selected = resolution.selectedBackend ?: return null
        return domainBackendAdapters.getOrPut(selected.id) { adaptBackend(selected, context) }
    }

    override suspend fun resolveWithFallbacks(
        context: Context,
        request: CapabilityRequest,
        snapshot: CapabilityDeviceState
    ): List<DomainCapabilityBackend> {
        val resolution = runtimeResolver.resolve(request, snapshot)
        return resolution.candidates
            .filter { it.availability == CapabilityAvailability.AVAILABLE }
            .mapNotNull { avail -> registry.backendFor(avail.backend) }
            .map { runtimeBackend ->
                domainBackendAdapters.getOrPut(runtimeBackend.id) {
                    adaptBackend(runtimeBackend, context)
                }
            }
    }

    override fun registerBackend(backend: DomainCapabilityBackend) {
        domainBackendAdapters[backend.backendId] = backend
    }

    override fun unregisterBackend(backendId: CapabilityBackendId) {
        domainBackendAdapters.remove(backendId)
    }

    /**
     * Wraps a runtime [CapabilityBackend] (which has no Android Context)
     * into the domain [CapabilityBackend] interface which includes Context.
     */
    private fun adaptBackend(
        runtime: CapabilityBackend,
        context: Context
    ): DomainCapabilityBackend = object : DomainCapabilityBackend {
        override val backendId: CapabilityBackendId = runtime.id

        override fun supports(capability: CapabilityId): Boolean =
            capability in runtime.supportedCapabilities

        override suspend fun checkAvailability(
            context: Context,
            capability: CapabilityId
        ): BackendAvailability = runtime.availability(
            CapabilityRequest(capability = capability)
        )

        override suspend fun execute(
            context: Context,
            request: CapabilityRequest
        ): CapabilityResult = runtime.execute(request)

        override suspend fun verify(
            context: Context,
            request: CapabilityRequest
        ): VerificationResult = runtime.verify(
            request,
            CapabilityResult(
                status = com.nexaflow.domain.capability.CapabilityStatus.SUCCESS,
                message = "adapter verify pass"
            )
        )

        override suspend fun cancel(executionId: String) {
            // Runtime backends do not currently support in-flight cancellation by ID.
            // Future: route via TaskManager cancellation.
        }

        override suspend fun diagnostics(context: Context): Map<String, String> =
            mapOf("backendId" to runtime.id.name)
    }
}
