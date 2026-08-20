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
        totalActions = 2,
        nextActionIndex = 0,
        status = DurableExecutionStatus.STARTED,
        startedAt = 100L,
        updatedAt = 100L
    )

    @Test
    fun checkpoint_commitsSequentialActionsAndClaimsRecoveryOnce() = runBlocking {
        assertTrue(store.beginCheckpoint(checkpoint()))
        assertFalse(store.beginCheckpoint(checkpoint()))

        val started = store.markActionStarted("run-checkpoint", 0, "run-checkpoint:0:ACTION", 110L)
        assertEquals(DurableExecutionStatus.ACTION_STARTED, started?.status)
        assertTrue("run-checkpoint:0:ACTION" in started!!.idempotencyKeys)

        val completed = store.markActionCompleted("run-checkpoint", 0, 120L)
        assertEquals(DurableExecutionStatus.ACTION_COMPLETED, completed?.status)
        assertEquals(1, completed?.nextActionIndex)
        assertEquals(setOf(0), completed?.completedActionIndexes)

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
