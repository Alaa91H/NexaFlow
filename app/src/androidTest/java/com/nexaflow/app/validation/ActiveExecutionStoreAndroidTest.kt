package com.nexaflow.app.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side durable-checkpoint contract using the production Preferences DataStore.
 *
 * The test uses unique identifiers and clears only records it created, so it does not assume
 * exclusive ownership of the app's persistent store. A successful source compilation is not
 * evidence of Android execution; evidence exists only after this test is run on a connected target.
 */
@RunWith(AndroidJUnit4::class)
class ActiveExecutionStoreAndroidTest {

    private lateinit var context: Context
    private lateinit var store: ActiveExecutionStore
    private val createdRunIds = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ActiveExecutionStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        createdRunIds.forEach { runId -> store.clearCheckpoint(runId) }
    }

    @Test
    fun checkpointPersistsAcrossFreshStoreClaimsOnceAndCleansUp() = runBlocking {
        val runId = "android-checkpoint-${UUID.randomUUID()}"
        createdRunIds += runId
        val checkpoint = DurableExecutionCheckpoint(
            runId = runId,
            automationId = "android-checkpoint-automation",
            totalActions = 2,
            nextActionIndex = 0,
            status = DurableExecutionStatus.STARTED,
            startedAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        assertTrue(store.beginCheckpoint(checkpoint))
        assertFalse(store.beginCheckpoint(checkpoint))

        // A fresh facade shares the same production DataStore file, modelling recovery after
        // component recreation without faking persistence or asserting a process kill occurred.
        val recoveredStore = ActiveExecutionStore(context)
        assertEquals(checkpoint, recoveredStore.checkpoint(runId))

        val actionStarted = recoveredStore.markActionStarted(
            runId = runId,
            actionIndex = 0,
            idempotencyKey = "$runId:0:ACTION",
            updatedAt = 1_700_000_000_100L
        )
        assertEquals(DurableExecutionStatus.ACTION_STARTED, actionStarted?.status)
        assertTrue(actionStarted?.idempotencyKeys.orEmpty().contains("$runId:0:ACTION"))

        val claimed = ActiveExecutionStore(context).claimRecoveryCandidates(1_700_000_000_200L)
            .single { it.runId == runId }
        assertEquals(DurableExecutionStatus.RECOVERY_CLAIMED, claimed.status)
        assertEquals(DurableExecutionStatus.ACTION_STARTED, claimed.recoverySourceStatus)
        assertFalse(
            ActiveExecutionStore(context)
                .claimRecoveryCandidates(1_700_000_000_300L)
                .any { it.runId == runId }
        )

        assertTrue(recoveredStore.completeCheckpoint(runId))
        assertNull(ActiveExecutionStore(context).checkpoint(runId))
    }

    @Test
    fun lifecycleMarkerIsOneShotAcrossFreshStoreFacade() = runBlocking {
        val automationId = "android-lifecycle-${UUID.randomUUID()}"
        try {
            store.markStarted(automationId)

            val freshStore = ActiveExecutionStore(context)
            assertTrue(freshStore.consumeStarted(automationId))
            assertFalse(store.consumeStarted(automationId))
        } finally {
            store.clear(automationId)
        }
    }
}
