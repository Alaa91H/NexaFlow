package com.nexaflow.core.logging

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingLogStoreTest {

    @Test
    fun redactsCredentialsBeforeWritingTimelineAndErrors() = runBlocking {
        val delegate = InMemoryLogStore()
        val store = RedactingLogStore(delegate)
        val secret = "really-secret-token"

        store.recordExecution(
            ExecutionTimelineEntry(
                id = "e1",
                automationId = "a1",
                automationName = "Task",
                kind = "RUN",
                success = false,
                message = "Authorization: Bearer $secret&password=hunter2 vault:workflow.token",
                startedAt = 1L,
                durationMs = 1L
            )
        )
        store.recordError(
            ErrorLogEntry(
                id = "err1",
                source = "test",
                message = "{\"apiKey\":\"$secret\"}",
                stackTrace = "token=$secret",
                timestamp = 1L
            )
        )

        val timeline = delegate.timeline().first().single().message
        val error = delegate.errors().first().single()
        assertFalse(timeline.contains(secret))
        assertFalse(error.message.contains(secret))
        assertFalse(error.stackTrace.orEmpty().contains(secret))
        assertTrue(timeline.contains("[REDACTED]"))
        assertTrue(error.message.contains("[REDACTED]"))
    }
}
