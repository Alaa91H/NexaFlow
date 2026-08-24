package com.nexaflow.domain.capability

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Unified resolver that matches a CapabilityRequest against available backends.
 * It enforces ExecutionPolicy, Risk Analysis, and capabilities of the device.
 */
interface CapabilityResolver {

    /**
     * Finds the best backend for the given capability request.
     * Evaluates permission state, privilege state, device compatibility,
     * reliability, performance, security, and ranking.
     *
     * @return The best available CapabilityBackend, or null if no backend can satisfy the request.
     */
    suspend fun resolve(context: Context, request: CapabilityRequest, snapshot: CapabilityDeviceState): CapabilityBackend?
    
    /**
     * Similar to resolve, but returns the primary backend and a list of fallback backends
     * ordered by preference and safety.
     */
    suspend fun resolveWithFallbacks(context: Context, request: CapabilityRequest, snapshot: CapabilityDeviceState): List<CapabilityBackend>
    
    /**
     * Registers a backend into the resolver ecosystem.
     */
    fun registerBackend(backend: CapabilityBackend)
    
    /**
     * Unregisters a backend from the resolver ecosystem.
     */
    fun unregisterBackend(backendId: CapabilityBackendId)
}
