package com.nexaflow.domain.capability

import android.content.Context

/**
 * Unified contract for all execution backends (Android API, Root, Shizuku, Shell, Accessibility, etc).
 * No workflow or plugin is allowed to bypass this contract to access raw privileged APIs directly.
 */
interface CapabilityBackend {
    
    /** The unique identifier of this backend type. */
    val backendId: CapabilityBackendId

    /**
     * True if this backend is technically capable of executing the requested capability.
     * This does NOT check if permissions or root/shizuku are currently granted.
     */
    fun supports(capability: CapabilityId): Boolean

    /**
     * Live evaluation of whether this backend is currently available for use.
     * Evaluates permissions, root/shizuku status, OEM restrictions, etc.
     */
    suspend fun checkAvailability(context: Context, capability: CapabilityId): BackendAvailability

    /**
     * Executes the capability request. Must return a well-formed CapabilityResult.
     * Must respect the timeout and retry constraints in request.policy.
     */
    suspend fun execute(context: Context, request: CapabilityRequest): CapabilityResult

    /**
     * Verifies if the operation requested by [request] had the desired side effect.
     * Used for stateful changes (e.g., WiFi toggle, file creation).
     */
    suspend fun verify(context: Context, request: CapabilityRequest): VerificationResult

    /**
     * Cancels an ongoing execution if supported by the backend.
     */
    suspend fun cancel(executionId: String)

    /**
     * Generates a snapshot of the backend's current health and limits.
     */
    suspend fun diagnostics(context: Context): Map<String, String>
}
