package com.nexaflow.core.execution.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionCheckpoint
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExecutionRecoveryCoordinatorTest {

    private fun automation(
        id: String = "automation-a",
        updatedAt: Long = 50L,
        actionCount: Int = 1
    ) = Automation(
        id = id,
        name = "Recovery",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = List(actionCount) {
            Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "test-$it"))
        },
        createdAt = 1L,
        updatedAt = updatedAt
    )

    private class FixedRepository(
        private var automation: Automation?
    ) : AutomationRepository {
        override fun getAutomations(): Flow<List<Automation>> =
            flowOf(listOfNotNull(automation))
        override suspend fun getAutomationById(id: String): Automation? =
            automation?.takeIf { it.id == id }
        override suspend fun saveAutomation(automation: Automation) {
            this.automation = automation
        }
        override suspend fun deleteAutomation(automation: Automation) {
            if (this.automation?.id == automation.id) this.automation = null
        }
        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            automation = automation?.takeIf { it.id == id }?.copy(enabled = enabled)
        }
    }

    @Test
    fun validatedDurableBoundaryIsOnlyAResumeCandidateWhenExactRevisionMatches() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val runId = "safe-${System.nanoTime()}"
        val automation = automation(updatedAt = 50L)
        try {
            assertTrue(
                store.beginCheckpoint(
                    DurableExecutionCheckpoint(
                        runId = runId,
                        automationId = automation.id,
                        workflowVersion = automation.workflowVersion,
                        workflowRevision = automation.updatedAt,
                        totalActions = automation.actions.size,
                        nextActionIndex = 0,
                        status = DurableExecutionStatus.STARTED,
                        startedAt = 10L,
                        updatedAt = 10L
                    )
                )
            )

            val report = ExecutionRecoveryCoordinator(
                activeExecutionStore = store,
                automationRepository = FixedRepository(automation)
            ).reconcileStartup()

            assertEquals(
                RecoveryDisposition.SAFE_RESUME_CANDIDATE,
                report.items.single { it.checkpoint.runId == runId }.disposition
            )
        } finally {
            store.clearCheckpoint(runId)
        }
    }

    @Test
    fun editedAutomationDowngradesDurableBoundaryToManualDiagnostics() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val runId = "edited-${System.nanoTime()}"
        val admitted = automation(updatedAt = 50L)
        val edited = admitted.copy(updatedAt = 51L)
        try {
            assertTrue(
                store.beginCheckpoint(
                    DurableExecutionCheckpoint(
                        runId = runId,
                        automationId = admitted.id,
                        workflowVersion = admitted.workflowVersion,
                        workflowRevision = admitted.updatedAt,
                        totalActions = admitted.actions.size,
                        nextActionIndex = 0,
                        status = DurableExecutionStatus.STARTED,
                        startedAt = 10L,
                        updatedAt = 10L
                    )
                )
            )

            val item = ExecutionRecoveryCoordinator(
                activeExecutionStore = store,
                automationRepository = FixedRepository(edited)
            ).reconcileStartup().items.single { it.checkpoint.runId == runId }

            assertEquals(RecoveryDisposition.MANUAL_DIAGNOSTICS_REQUIRED, item.disposition)
            assertTrue(item.reason.contains("changed since execution admission"))
        } finally {
            store.clearCheckpoint(runId)
        }
    }

    @Test
    fun legacyCheckpointWithoutRevisionNeverClaimsSafeResume() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val runId = "legacy-${System.nanoTime()}"
        val automation = automation(updatedAt = 50L)
        try {
            assertTrue(
                store.beginCheckpoint(
                    DurableExecutionCheckpoint(
                        runId = runId,
                        automationId = automation.id,
                        workflowVersion = automation.workflowVersion,
                        totalActions = automation.actions.size,
                        nextActionIndex = 0,
                        status = DurableExecutionStatus.STARTED,
                        startedAt = 10L,
                        updatedAt = 10L
                    )
                )
            )

            val item = ExecutionRecoveryCoordinator(
                activeExecutionStore = store,
                automationRepository = FixedRepository(automation)
            ).reconcileStartup().items.single { it.checkpoint.runId == runId }

            assertEquals(RecoveryDisposition.MANUAL_DIAGNOSTICS_REQUIRED, item.disposition)
            assertTrue(item.reason.contains("Legacy checkpoint"))
        } finally {
            store.clearCheckpoint(runId)
        }
    }

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
