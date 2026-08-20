package com.nexaflow.app.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.core.execution.recovery.ExecutionRecoveryCoordinator
import com.nexaflow.core.execution.recovery.RecoveryDisposition
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected-device recovery-decision test backed by the production DataStore ledger.
 *
 * It deliberately does not simulate a process kill. A kill at specific action boundaries remains
 * a real-device validation requirement; this test proves the persisted ACTION_UNKNOWN path itself
 * is claimed once and converted to an explicit no-blind-replay recovery requirement.
 */
@RunWith(AndroidJUnit4::class)
class ExecutionRecoveryCoordinatorAndroidTest {

    private lateinit var store: ActiveExecutionStore
    private val createdRunIds = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = ActiveExecutionStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        createdRunIds.forEach { runId -> store.clearCheckpoint(runId) }
    }

    @Test
    fun unknownActionRequiresVerificationAndIsNotClaimedForBlindReplayTwice() = runBlocking {
        val runId = "android-recovery-${UUID.randomUUID()}"
        createdRunIds += runId
        assertTrue(
            store.beginCheckpoint(
                DurableExecutionCheckpoint(
                    runId = runId,
                    automationId = "android-recovery-automation",
                    totalActions = 1,
                    nextActionIndex = 0,
                    status = DurableExecutionStatus.STARTED,
                    startedAt = 1_700_000_000_000L,
                    updatedAt = 1_700_000_000_000L
                )
            )
        )
        store.markActionStarted(runId, 0, "$runId:0:ACTION", 1_700_000_000_100L)
        store.markActionUnknown(runId, "injected boundary interruption", 1_700_000_000_200L)

        val report = ExecutionRecoveryCoordinator(store).reconcileStartup()
        val item = report.items.single { it.checkpoint.runId == runId }
        assertEquals(RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED, item.disposition)
        assertTrue(item.reason.contains("verify or compensate", ignoreCase = true))
        assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, item.checkpoint.recoverySourceStatus)
        assertEquals(DurableExecutionStatus.RECOVERY_REQUIRED, store.checkpoint(runId)?.status)

        assertTrue(ExecutionRecoveryCoordinator(store).reconcileStartup().items.none { it.checkpoint.runId == runId })
    }
}
