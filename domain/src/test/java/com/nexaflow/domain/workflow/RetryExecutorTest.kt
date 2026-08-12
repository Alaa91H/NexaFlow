package com.nexaflow.domain.workflow

import java.io.IOException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryExecutorTest {

    // Deterministic RNG so delay math is exact.
    private val fixedRng = Random(42)

    @Test
    fun `delayMs with zero jitter follows exact exponential backoff`() {
        val executor = RetryExecutor(fixedRng)
        val policy = RetryPolicy(maxAttempts = 5, baseDelayMs = 1000, capMs = 60_000, jitter = 0.0)
        assertEquals(1000L, executor.delayMs(1, policy))
        assertEquals(2000L, executor.delayMs(2, policy))
        assertEquals(4000L, executor.delayMs(3, policy))
        assertEquals(8000L, executor.delayMs(4, policy))
        assertEquals(16_000L, executor.delayMs(5, policy))
    }

    @Test
    fun `delayMs caps at the policy cap`() {
        val executor = RetryExecutor(fixedRng)
        val policy = RetryPolicy(maxAttempts = 10, baseDelayMs = 1000, capMs = 5000, jitter = 0.0)
        assertEquals(4000L, executor.delayMs(3, policy))  // 4s < cap, not capped yet
        assertEquals(5000L, executor.delayMs(4, policy))  // 8s → capped
        assertEquals(5000L, executor.delayMs(10, policy))
    }

    @Test
    fun `delayMs stays within the jitter bounds per attempt`() {
        val executor = RetryExecutor(Random(7))
        val policy = RetryPolicy(maxAttempts = 8, baseDelayMs = 1000, capMs = 60_000, jitter = 0.2)
        repeat(200) { iteration ->
            val attempt = iteration % 8 + 1
            val delay = executor.delayMs(attempt, policy)
            // Expected center: min(cap, 1000 * 2^(n-1)), ±20%.
            val center = minOf(60_000L, 1000L.shl(attempt - 1))
            val bound = (center * 0.2).toLong()
            assertTrue(
                "attempt $attempt delay out of bounds: $delay",
                delay in (center - bound)..(center + bound),
            )
        }
    }

    @Test
    fun `delayMs never goes negative even with heavy jitter`() {
        val executor = RetryExecutor(Random(3))
        val policy = RetryPolicy(maxAttempts = 5, baseDelayMs = 100, capMs = 200, jitter = 1.0)
        repeat(1000) { attempt ->
            val delay = executor.delayMs(attempt % 5 + 1, policy)
            assertTrue("negative delay: $delay", delay >= 0L)
        }
    }

    @Test
    fun `attempt below one clamps to the first attempt`() {
        val executor = RetryExecutor(fixedRng)
        val policy = RetryPolicy(baseDelayMs = 1000, jitter = 0.0)
        assertEquals(1000L, executor.delayMs(0, policy))
        assertEquals(1000L, executor.delayMs(-3, policy))
    }

    @Test
    fun `idempotency key is stable for the same triple`() {
        val executor = RetryExecutor(fixedRng)
        val a = executor.idempotencyKey("wf-1", "action-0", "abc123")
        val b = executor.idempotencyKey("wf-1", "action-0", "abc123")
        assertEquals(a, b)
        // SHA-256 hex = 64 chars.
        assertEquals(64, a.length)
        assertTrue(a.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `idempotency key differs across workflow node or input`() {
        val executor = RetryExecutor(fixedRng)
        val base = executor.idempotencyKey("wf-1", "action-0", "abc123")
        assertNotEquals(base, executor.idempotencyKey("wf-2", "action-0", "abc123"))
        assertNotEquals(base, executor.idempotencyKey("wf-1", "action-1", "abc123"))
        assertNotEquals(base, executor.idempotencyKey("wf-1", "action-0", "abc124"))
    }

    @Test
    fun `transient failures are retryable`() {
        val executor = RetryExecutor(fixedRng)
        assertTrue(executor.isRetryable(IOException("network down")))
        assertTrue(executor.isRetryable(RuntimeException("timeout")))
        assertTrue(executor.isRetryable(Throwable("unknown")))
    }

    @Test
    fun `contract violations are permanent`() {
        val executor = RetryExecutor(fixedRng)
        assertFalse(executor.isRetryable(IllegalArgumentException("bad request")))
        assertFalse(executor.isRetryable(IllegalStateException("invalid state")))
        assertFalse(executor.isRetryable(NullPointerException()))
        assertFalse(executor.isRetryable(IndexOutOfBoundsException()))
        assertFalse(executor.isRetryable(UnsupportedOperationException()))
    }

    @Test
    fun `policy rejects invalid parameters`() {
        assertThrows { RetryPolicy(maxAttempts = 0) }
        assertThrows { RetryPolicy(baseDelayMs = -1) }
        assertThrows { RetryPolicy(capMs = -5) }
        assertThrows { RetryPolicy(jitter = 1.5) }
        assertThrows { RetryPolicy(jitter = -0.1) }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
