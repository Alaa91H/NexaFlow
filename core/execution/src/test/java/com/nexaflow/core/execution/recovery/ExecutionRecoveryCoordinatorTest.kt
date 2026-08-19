package com.nexaflow.core.execution.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExecutionRecoveryCoordinatorTest {

    @Test
    fun actionStartedCheckpointRequiresVerifyOrCompensationInsteadOfReplay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val runId = "recovery-${System.nanoTime()}"
        try {
            assertTrue(
                store.beginCheckpoint(
                    DurableExecutionCheckpoint(
                        runId = runId,
                        automationId = "automation-a",
                        totalActions = 1,
                        nextActionIndex = 0,
                        status = DurableExecutionStatus.STARTED,
                        startedAt = 10L,
                        updatedAt = 10L
                    )
                )
            )
            store.markActionStarted(runId, 0, "$runId:0:ACTION", 20L)

            val report = ExecutionRecoveryCoordinator(store).reconcileStartup()
            val item = report.items.single { it.checkpoint.runId == runId }

            assertEquals(RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED, item.disposition)
            assertEquals(DurableExecutionStatus.ACTION_STARTED, item.checkpoint.recoverySourceStatus)
            assertEquals(DurableExecutionStatus.RECOVERY_REQUIRED, store.checkpoint(runId)?.status)
        } finally {
            store.clearCheckpoint(runId)
        }
    }
}
