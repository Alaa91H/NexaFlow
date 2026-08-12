package com.nexaflow.domain.workflow

import java.security.MessageDigest
import kotlin.random.Random

/**
 * Computes retry delays (exponential backoff + jitter) and idempotency keys
 * for workflow nodes, and classifies failures as retryable vs permanent.
 *
 * Backoff formula for attempt n (1-based):
 * ```
 * delay(n) = min(capMs, baseDelayMs * 2^(n-1))  +  jitter ±(delay * jitter)
 * ```
 * - Transient failures (5xx, timeouts, 429 with Retry-After) are retried up to
 *   [RetryPolicy.maxAttempts].
 * - Permanent failures (4xx validation) fail immediately to the dead-letter
 *   queue — retrying a request that will never succeed just burns attempts.
 *
 * Pure and injectable ([rng] is a test seam), so the math is unit-testable
 * without Android.
 */
class RetryExecutor(
    private val rng: Random = Random.Default,
) {

    /**
     * Delay before retry attempt [attempt] (1-based) under [policy].
     * `min(capMs, baseDelayMs * 2^(n-1))` plus uniform jitter within
     * `±(delay * jitter)`. Always non-negative; attempts below 1 clamp to 1.
     */
    fun delayMs(attempt: Int, policy: RetryPolicy): Long {
        val n = attempt.coerceAtLeast(1)
        // Shift limited so overflow cannot produce a negative delay.
        val shift = (n - 1).coerceAtMost(62)
        val exponential = policy.baseDelayMs.shl(shift)
        val capped = exponential.coerceAtMost(policy.capMs)
        val jitterAmount = (capped * policy.jitter).toLong()
        val jittered = if (jitterAmount > 0) {
            capped + rng.nextLong(-jitterAmount, jitterAmount + 1)
        } else {
            capped
        }
        return jittered.coerceAtLeast(0)
    }

    /**
     * Stable idempotency key for one node execution: the same (workflow, node,
     * input) always yields the same key, so a retried delivery never duplicates
     * its side effect. [inputHash] is a hash of the node's resolved input.
     */
    fun idempotencyKey(workflowId: String, nodeId: String, inputHash: String): String {
        val raw = "$workflowId|$nodeId|$inputHash"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * True when [error] represents a transient condition worth retrying.
     * Network/timeout failures are retryable; contract violations (validation)
     * are permanent and must go straight to the dead-letter queue. Anything
     * unrecognized defaults to retryable (fail-safe: transient infrastructure
     * errors outnumber deterministic bugs in practice).
     */
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is IllegalArgumentException,
        is IllegalStateException,
        is UnsupportedOperationException,
        is NullPointerException,
        is ArithmeticException,
        is IndexOutOfBoundsException -> false
        else -> true
    }
}
