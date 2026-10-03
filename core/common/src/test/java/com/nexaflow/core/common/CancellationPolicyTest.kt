package com.nexaflow.core.common

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationPolicyTest {
    @Test
    fun cancellationIsRethrown() {
        val failure = runCatching {
            runCatchingPreservingCancellation<String> {
                throw CancellationException("cancel")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun ordinaryFailureRemainsAResult() {
        val result = runCatchingPreservingCancellation<String> {
            error("boom")
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
