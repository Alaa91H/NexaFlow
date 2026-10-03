package com.nexaflow.data.agents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.agentruntime.AgentApproval
import com.nexaflow.core.agentruntime.AgentApprovalDecision
import com.nexaflow.core.agentruntime.AgentRunStatus
import com.nexaflow.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentRunRepositoryTest {
    private lateinit var database: AppDatabase
    private var now = 1_000L
    private var nextId = 0
    private lateinit var repository: AgentRunRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = AgentRunRepository(
            database.agentRunDao(),
            nowMillis = { now },
            idGenerator = { "id-${++nextId}" }
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun idempotencyIsHashedAndRunHasOneTerminalEvent() = runTest {
        val created = repository.start("agent-one", "secret-request-key", "private prompt", 3, 2, 60_000)
        val run = assertIs<AgentRunStartResult.Created>(created).run
        assertFalse(run.idempotencyKeyHash.contains("secret-request-key"))
        assertFalse(run.requestFingerprint.contains("private prompt"))
        val guessablePromptHash = MessageDigest.getInstance("SHA-256")
            .digest("private prompt".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertFalse("Persisted fingerprint must be keyed, not a prompt digest", run.requestFingerprint == guessablePromptHash)
        assertIs<AgentRunStartResult.Existing>(
            repository.start("agent-one", "secret-request-key", "private prompt", 3, 2, 60_000)
        )
        assertIs<AgentRunStartResult.IdempotencyConflict>(
            repository.start("agent-one", "secret-request-key", "different prompt", 3, 2, 60_000)
        )

        assertTrue(repository.markRunning(run.id))
        assertTrue(repository.finish(run.id, AgentRunStatus.COMPLETED, "completed"))
        assertFalse(repository.finish(run.id, AgentRunStatus.FAILED, "provider_failure"))
        val events = repository.observeEvents(run.id).first()
        assertEquals(listOf("QUEUED", "STARTED", "TERMINAL"), events.map { it.type.name })
        assertEquals(listOf(1L, 2L, 3L), events.map { it.sequence })
        assertTrue(events.none { it.safeCode.contains("private") })
    }

    @Test
    fun activeRunLimitIsEnforcedAndProcessRecoveryNeverQueuesAReplay() = runTest {
        val first = assertIs<AgentRunStartResult.Created>(
            repository.start("agent-one", "key-1", "request-1", 1, 1, 60_000)
        ).run
        assertIs<AgentRunStartResult.ConcurrentRunLimit>(
            repository.start("agent-one", "key-2", "request-2", 1, 1, 60_000)
        )
        assertTrue(repository.markRunning(first.id))
        assertEquals(1, repository.recoverInFlight())
        assertEquals(AgentRunStatus.INTERRUPTED, repository.find(first.id)?.status)
        assertEquals("process_restarted", repository.find(first.id)?.outcomeCode)
        assertEquals(0, repository.recoverInFlight())
    }

    @Test
    fun approvalMustMatchDefinitionAndDeviceAndCanOnlyBeConsumedOnce() = runTest {
        val run = assertIs<AgentRunStartResult.Created>(
            repository.start("agent-one", "key", "request", 2, 1, 60_000)
        ).run
        assertTrue(repository.markRunning(run.id))
        val fingerprint = "a".repeat(64)
        val deviceHash = "b".repeat(64)
        val approval = AgentApproval(
            id = "approval-one",
            runId = run.id,
            agentId = run.agentId,
            definitionRevision = run.definitionRevision,
            toolName = "automation.create",
            callFingerprint = fingerprint,
            deviceBindingHash = deviceHash,
            expiresAtMillis = 10_000
        )
        assertTrue(repository.createApproval(approval))
        assertEquals(AgentRunStatus.WAITING_FOR_APPROVAL, repository.find(run.id)?.status)
        assertFalse(repository.resolveApproval("approval-one", AgentApprovalDecision.APPROVED, 3, deviceHash))
        assertFalse(repository.resolveApproval("approval-one", AgentApprovalDecision.APPROVED, 2, "c".repeat(64)))
        assertTrue(repository.resolveApproval("approval-one", AgentApprovalDecision.APPROVED, 2, deviceHash))
        assertFalse(repository.resolveApproval("approval-one", AgentApprovalDecision.APPROVED, 2, deviceHash))
        assertEquals(AgentRunStatus.RUNNING, repository.find(run.id)?.status)
    }

    @Test
    fun approvalWaiterImmediatelySeesDecisionThatArrivedBeforeSubscription() = runTest {
        val run = assertIs<AgentRunStartResult.Created>(
            repository.start("agent-one", "key", "request", 2, 1, 60_000)
        ).run
        assertTrue(repository.markRunning(run.id))
        val approval = AgentApproval(
            id = "approval-ready",
            runId = run.id,
            agentId = run.agentId,
            definitionRevision = run.definitionRevision,
            toolName = "automation.create",
            callFingerprint = "a".repeat(64),
            deviceBindingHash = "b".repeat(64),
            expiresAtMillis = 10_000
        )
        assertTrue(repository.createApproval(approval))
        assertTrue(repository.resolveApproval("approval-ready", AgentApprovalDecision.APPROVED, 2, "b".repeat(64)))

        val waiter = launch { assertEquals(AgentApprovalDecision.APPROVED, repository.awaitApprovalDecision("approval-ready")) }
        waiter.join()
    }
}

private inline fun <reified T> assertIs(value: Any): T {
    assertTrue("Expected ${T::class.java.simpleName}, got ${value::class.java.simpleName}", value is T)
    return value as T
}
