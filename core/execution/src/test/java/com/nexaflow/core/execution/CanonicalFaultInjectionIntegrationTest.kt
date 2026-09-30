package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.DurableExecutionStatus
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.execution.recovery.ExecutionRecoveryCoordinator
import com.nexaflow.core.execution.recovery.RecoveryDisposition
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.canonical.FaultInjectionController
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * T32 product integration tests. Faults are injected at the exact
 * AtomicCommand boundary after ACTION_STARTED is durable, so these assertions
 * exercise the production checkpoint/recovery path rather than a simulator.
 */
@RunWith(RobolectricTestRunner::class)
class CanonicalFaultInjectionIntegrationTest {

    private class NoopHistory : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            object : PagingSource<Int, ExecutionRecord>() {
                override fun getRefreshKey(state: PagingState<Int, ExecutionRecord>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ExecutionRecord> =
                    LoadResult.Page(emptyList(), null, null)
            }
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private class CountingHandler(
        override val supportedTypes: Set<ActionType>,
    ) : ActionHandler {
        var calls: Int = 0

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext,
        ): SystemControlResult {
            calls += 1
            return SystemControlResult.ok("provider completed")
        }
    }

    private fun automation(
        id: String,
        action: Action,
    ) = Automation(
        id = id,
        name = "Fault injection",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(action),
        createdAt = 1L,
        updatedAt = 2L,
    )

    private fun gate(
        action: FaultInjectionController.FaultAction,
        trigger: FaultInjectionController.Trigger =
            FaultInjectionController.Trigger.FIRST_ATTEMPT,
        label: String = "injected",
    ) = ScheduledCanonicalFaultInjectionGate(
        FaultInjectionController.FaultSchedule(
            listOf(
                FaultInjectionController.FaultSpec(
                    commandId = "v3.action.0",
                    action = action,
                    trigger = trigger,
                    errorLabel = label,
                ),
            ),
        ),
    )

    @Test
    fun firstAttemptFaultRetriesOnlyThroughIdempotentCanonicalCommand() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val handler = CountingHandler(setOf(ActionType.SYSTEM_WIFI))
        val faultGate = gate(FaultInjectionController.FaultAction.FAIL)
        val task = automation(
            id = "fault-retry",
            action = Action(
                ActionType.SYSTEM_WIFI,
                mapOf("enabled" to "true", "retryCount" to "1", "retryDelayMs" to "0"),
            ),
        )
        val runId = "fault-retry-run"

        try {
            val record = ExecutionEngine(
                context = context,
                historyRepository = NoopHistory(),
                notificationPreferences = NotificationPreferences(context),
                actionRegistry = ActionRegistry.from(listOf(handler)),
                activeExecutionStore = store,
                faultInjectionGate = faultGate,
            ).runAutomation(task, WorkflowRunContext(runId, task.id, 1L))

            assertTrue(record.success)
            assertEquals(2, faultGate.attemptCount("v3.action.0"))
            assertEquals(1, handler.calls)
            assertNull(store.checkpoint(runId))
        } finally {
            store.clearCheckpoint(runId)
            store.clear(task.id)
        }
    }

    @Test
    fun injectedHangTimesOutAsUnknownAndRequiresVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val handler = CountingHandler(setOf(ActionType.SYSTEM_SEND_NOTIFICATION))
        val task = automation(
            "fault-timeout",
            Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "test")),
        )
        val runId = "fault-timeout-run"

        try {
            val record = ExecutionEngine(
                context = context,
                historyRepository = NoopHistory(),
                notificationPreferences = NotificationPreferences(context),
                actionRegistry = ActionRegistry.from(listOf(handler)),
                activeExecutionStore = store,
                faultInjectionGate = gate(
                    FaultInjectionController.FaultAction.HANG,
                    label = "provider_timeout",
                ),
                faultInjectionHangTimeoutMs = 5L,
            ).runAutomation(task, WorkflowRunContext(runId, task.id, 1L))

            assertFalse(record.success)
            assertEquals(0, handler.calls)
            assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, store.checkpoint(runId)?.status)

            val item = ExecutionRecoveryCoordinator(store)
                .reconcileStartup()
                .items
                .single { it.checkpoint.runId == runId }
            assertEquals(RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED, item.disposition)
        } finally {
            store.clearCheckpoint(runId)
            store.clear(task.id)
        }
    }

    @Test
    fun cancellationDuringInjectedStallSurvivesAsRebootRecoveryWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val handler = CountingHandler(setOf(ActionType.SYSTEM_SEND_NOTIFICATION))
        val task = automation(
            "fault-cancel",
            Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "test")),
        )
        val runId = "fault-cancel-run"
        val stallEntered = CompletableDeferred<Unit>()
        val stallGate = gate(
            FaultInjectionController.FaultAction.STALL,
            label = "process_interrupted",
        )
        val engine = ExecutionEngine(
            context = context,
            historyRepository = NoopHistory(),
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            activeExecutionStore = store,
            faultInjectionGate = CanonicalFaultInjectionGate { command ->
                val decision = stallGate.decide(command)
                if (decision is FaultInjectionController.Decision.Inject) {
                    stallEntered.complete(Unit)
                }
                decision
            },
        )

        try {
            val job = async {
                engine.runAutomation(task, WorkflowRunContext(runId, task.id, 1L))
            }
            withTimeout(5_000L) { stallEntered.await() }
            job.cancelAndJoin()

            assertEquals(0, handler.calls)
            assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, store.checkpoint(runId)?.status)

            // A fresh coordinator models service/process restart or reboot:
            // it claims the durable row and refuses blind replay.
            val item = ExecutionRecoveryCoordinator(store)
                .reconcileStartup()
                .items
                .single { it.checkpoint.runId == runId }
            assertEquals(RecoveryDisposition.VERIFY_OR_COMPENSATE_REQUIRED, item.disposition)
            assertEquals(DurableExecutionStatus.ACTION_UNKNOWN, item.checkpoint.recoverySourceStatus)
        } finally {
            store.clearCheckpoint(runId)
            store.clear(task.id)
        }
    }

    @Test
    fun providerPermissionAndNetworkFaultsAreKnownFailuresWithoutCorruptState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ActiveExecutionStore(context)
        val handler = CountingHandler(setOf(ActionType.SYSTEM_SEND_NOTIFICATION))

        for (label in listOf("provider_unavailable", "permission_revoked", "network_down")) {
            val task = automation(
                "fault-$label",
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to label)),
            )
            val runId = "run-$label"
            try {
                val record = ExecutionEngine(
                    context = context,
                    historyRepository = NoopHistory(),
                    notificationPreferences = NotificationPreferences(context),
                    actionRegistry = ActionRegistry.from(listOf(handler)),
                    activeExecutionStore = store,
                    faultInjectionGate = gate(
                        FaultInjectionController.FaultAction.FAIL,
                        label = label,
                    ),
                ).runAutomation(task, WorkflowRunContext(runId, task.id, 1L))

                assertFalse(record.success)
                assertEquals("FAULT_INJECTED", record.actionResults.single().errorCode)
                assertNull("known failure must not leave recovery ownership", store.checkpoint(runId))
            } finally {
                store.clearCheckpoint(runId)
                store.clear(task.id)
            }
        }
        assertEquals(0, handler.calls)
    }
}
