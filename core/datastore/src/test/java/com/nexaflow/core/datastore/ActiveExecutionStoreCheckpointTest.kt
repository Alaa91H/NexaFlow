package com.nexaflow.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveExecutionStoreCheckpointTest {

    private lateinit var store: ActiveExecutionStore

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = ActiveExecutionStore(context)
        store.checkpointsForTest().forEach { store.clearCheckpoint(it.runId) }
    }

    private fun checkpoint(runId: String = "run-checkpoint") = DurableExecutionCheckpoint(
        runId = runId,
        automationId = "automation-a",
        workflowVersion = 4,
        totalActions = 2,
        nextActionIndex = 0,
        status = DurableExecutionStatus.STARTED,
        startedAt = 100L,
        updatedAt = 100L
    )

    @Test
    fun durableTransitionContract_allowsRecoveryAndTerminalPaths() {
        assertTrue(DurableExecutionStatus.STARTED.canTransitionTo(DurableExecutionStatus.ACTION_STARTED))
        assertTrue(DurableExecutionStatus.ACTION_STARTED.canTransitionTo(DurableExecutionStatus.ACTION_UNKNOWN))
        assertTrue(DurableExecutionStatus.ACTION_UNKNOWN.canTransitionTo(DurableExecutionStatus.RECOVERY_REQUIRED))
        assertTrue(DurableExecutionStatus.ACTION_COMPLETED.canTransitionTo(DurableExecutionStatus.COMPLETED))
        assertTrue(DurableExecutionStatus.EXIT_PENDING.canTransitionTo(DurableExecutionStatus.RECOVERY_REQUIRED))
    }

    @Test
    fun durableTransitionContract_rejectsTerminalRegression() {
        assertFalse(DurableExecutionStatus.COMPLETED.canTransitionTo(DurableExecutionStatus.STARTED))
        assertFalse(DurableExecutionStatus.RECOVERY_REQUIRED.canTransitionTo(DurableExecutionStatus.ACTION_STARTED))
        assertFalse(DurableExecutionStatus.ACTION_UNKNOWN.canTransitionTo(DurableExecutionStatus.COMPLETED))
    }

    @Test
    fun checkpoint_commitsSequentialActionsAndClaimsRecoveryOnce() = runBlocking {
        assertTrue(store.beginCheckpoint(checkpoint()))
        assertFalse(store.beginCheckpoint(checkpoint()))
        assertEquals(4, store.checkpoint("run-checkpoint")?.workflowVersion)

        val started = store.markActionStarted(
            runId = "run-checkpoint",
            actionIndex = 0,
            idempotencyKey = "run-checkpoint:0:ACTION",
            updatedAt = 110L,
            nodeId = "node-install",
            backend = "PACKAGE_INSTALLER",
            inputHash = "input-hash"
        )
        assertEquals(DurableExecutionStatus.ACTION_STARTED, started?.status)
        assertTrue("run-checkpoint:0:ACTION" in started!!.idempotencyKeys)
        assertEquals("node-install", started.currentNodeId)
        assertEquals(DurableNodeExecutionState.RUNNING, started.nodeExecutions.single().state)
        assertEquals(DurableVerificationState.PENDING, started.verificationState)
        assertEquals("PACKAGE_INSTALLER", started.nodeExecutions.single().backend)

        val completed = store.markActionCompleted(
            runId = "run-checkpoint",
            actionIndex = 0,
            updatedAt = 120L,
            outputHash = "output-hash",
            verificationState = DurableVerificationState.VERIFIED
        )
        assertEquals(DurableExecutionStatus.ACTION_COMPLETED, completed?.status)
        assertEquals(1, completed?.nextActionIndex)
        assertEquals(setOf(0), completed?.completedActionIndexes)
        assertEquals(DurableVerificationState.VERIFIED, completed?.verificationState)
        assertEquals(DurableNodeExecutionState.SUCCEEDED, completed?.nodeExecutions?.single()?.state)
        assertEquals("output-hash", completed?.nodeExecutions?.single()?.outputHash)

        val firstClaim = store.claimRecoveryCandidates(130L)
        assertEquals(1, firstClaim.size)
        assertEquals(DurableExecutionStatus.RECOVERY_CLAIMED, firstClaim.single().status)
        assertEquals(DurableExecutionStatus.ACTION_COMPLETED, firstClaim.single().recoverySourceStatus)
        assertTrue(store.claimRecoveryCandidates(140L).isEmpty())

        store.markRecoveryRequired("run-checkpoint", "resume needs workflow validation", 150L)
        assertEquals(DurableExecutionStatus.RECOVERY_REQUIRED, store.checkpoint("run-checkpoint")?.status)
        assertTrue(store.completeCheckpoint("run-checkpoint"))
        assertEquals(null, store.checkpoint("run-checkpoint"))
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateIdempotencyKeyIsRejectedBeforeSecondSideEffect() {
        runBlocking {
            assertTrue(store.beginCheckpoint(checkpoint("run-idempotency")))
            store.markActionStarted("run-idempotency", 0, "run-idempotency:0:ACTION", 110L)
            store.markActionStarted("run-idempotency", 0, "run-idempotency:0:ACTION", 120L)
        }
    }

    @Test
    fun completedMaintenanceOccurrenceIsDurableAndDoesNotDuplicate() = runBlocking {
        val key = "maintenance:receipt-contract-${System.nanoTime()}"

        assertFalse(store.hasCompletedMaintenanceOccurrence(key))
        store.recordCompletedMaintenanceOccurrence(key, "automation-a", 1_000L)
        assertTrue(store.hasCompletedMaintenanceOccurrence(key))

        store.recordCompletedMaintenanceOccurrence(key, "automation-a", 2_000L)
        val receipts = store.maintenanceReceiptsForTest().filter { it.occurrenceKey == key }
        assertEquals(1, receipts.size)
        assertEquals(2_000L, receipts.single().completedAt)
    }

    @Test
    fun interruptedNodeIsPersistedAsUnknownAndNeverReportedAsSuccess() = runBlocking {
        assertTrue(store.beginCheckpoint(checkpoint("run-node-unknown")))
        store.markActionStarted(
            runId = "run-node-unknown",
            actionIndex = 0,
            idempotencyKey = "run-node-unknown:0:ACTION",
            updatedAt = 110L,
            nodeId = "node-unknown"
        )
        val unknown = store.markActionUnknown("run-node-unknown", "process killed", 120L)

        assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, unknown?.status)
        assertEquals(DurableVerificationState.UNKNOWN, unknown?.verificationState)
        assertEquals(DurableNodeExecutionState.UNKNOWN, unknown?.nodeExecutions?.single()?.state)
        assertEquals("UNKNOWN_OUTCOME", unknown?.nodeExecutions?.single()?.failureCode)
    }

    @Test
    fun recoveryRequiredCheckpointIsNotClaimedAgainAutomatically() = runBlocking {
        assertTrue(store.beginCheckpoint(checkpoint("run-recovery-required")))
        assertEquals(1, store.claimRecoveryCandidates(105L).size)
        store.markRecoveryRequired("run-recovery-required", "manual verification required", 110L)

        assertTrue(store.claimRecoveryCandidates(120L).isEmpty())
        assertEquals(
            DurableExecutionStatus.RECOVERY_REQUIRED,
            store.checkpoint("run-recovery-required")?.status
        )
    }

    @Test
    fun interruptedActionIsExplicitlyUnknownRatherThanReplayable() = runBlocking {
        assertTrue(store.beginCheckpoint(checkpoint("run-unknown")))
        store.markActionStarted("run-unknown", 0, "run-unknown:0:ACTION", 110L)
        val unknown = store.markActionUnknown("run-unknown", "process killed", 120L)
        assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, unknown?.status)
        assertEquals("process killed", unknown?.message)

        val claim = store.claimRecoveryCandidates(130L).single()
        assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, claim.recoverySourceStatus)
        assertNotNull(claim.message)
    }
}
