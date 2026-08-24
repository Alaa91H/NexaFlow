package com.nexaflow.core.execution.verification

import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.VerificationResult
import com.nexaflow.core.execution.capability.CapabilityRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Verification Engine for NexaFlow.
 * 
 * Verifies post-conditions of an action after execution. Instead of assuming
 * an action succeeded because the OS didn't throw an exception, this engine
 * explicitly queries the state (via the backend's `verify` method) to ensure
 * the requested outcome was actually achieved.
 */
class VerificationEngine(
    private val registry: CapabilityRegistry,
    private val defaultMaxRetries: Int = 3,
    private val defaultBackoffMs: Long = 500L
) {
    /**
     * Attempts to verify the outcome of a capability execution.
     * Retries with exponential backoff if the verification initially fails,
     * as some system state changes take time to reflect.
     *
     * @param request The original capability request that was executed.
     * @param result The result returned by the backend execution.
     * @param maxRetries Max attempts for verification polling.
     * @param backoffMs Initial wait time between polls.
     * @return [VerificationResult] indicating success or failure.
     */
    suspend fun verify(
        request: CapabilityRequest,
        result: CapabilityResult,
        maxRetries: Int = defaultMaxRetries,
        backoffMs: Long = defaultBackoffMs
    ): VerificationResult {
        val backendId = result.backend
            ?: return VerificationResult(
                attempted = false,
                verified = false,
                message = "Cannot verify without a specific backend"
            )

        val backend = registry.backendFor(backendId)
            ?: return VerificationResult(
                attempted = false,
                verified = false,
                message = "Backend ${backendId.name} not found in registry"
            )

        var currentBackoff = backoffMs
        for (attempt in 1..maxRetries) {
            val verification = runCatching { backend.verify(request, result) }.getOrElse { e ->
                VerificationResult(
                    attempted = true,
                    verified = false,
                    message = "Verification threw an exception: ${e.message}"
                )
            }

            if (verification.verified || !verification.attempted) {
                // If verified, or if the backend doesn't support verification, we stop polling.
                return verification
            }

            if (attempt < maxRetries) {
                delay(currentBackoff)
                currentBackoff *= 2
            }
        }

        return VerificationResult(
            attempted = true,
            verified = false,
            message = "Verification failed after $maxRetries attempts"
        )
    }
}
