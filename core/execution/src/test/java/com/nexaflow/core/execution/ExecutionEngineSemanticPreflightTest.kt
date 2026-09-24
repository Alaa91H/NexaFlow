package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.capability.semantic.CapabilityEvidenceStore
import com.nexaflow.core.execution.capability.semantic.CapabilityRouter
import com.nexaflow.core.execution.capability.semantic.CapabilityStrategy
import com.nexaflow.core.execution.capability.semantic.DeviceFingerprint
import com.nexaflow.core.execution.capability.semantic.OperationOutcome
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.OperationRegistry
import com.nexaflow.core.execution.capability.semantic.SemanticActionRouter
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowPlanner
import com.nexaflow.core.execution.capability.semantic.StrategyAvailability
import com.nexaflow.core.execution.capability.semantic.StrategyHealthTracker
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExecutionEngineSemanticPreflightTest {

    private class RecordingHandler : ActionHandler {
        var executions = 0
        override val supportedTypes = setOf(ActionType.SYSTEM_WIFI)

        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            executions++
            return SystemControlResult.ok("legacy handler executed")
        }
    }

    private class RecordingHistory : HistoryRepository {
        val records = mutableListOf<ExecutionRecord>()

        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(records)
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> = emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? =
            records.firstOrNull { it.id == id }
        override suspend fun recordExecution(record: ExecutionRecord) {
            records += record
        }
    }

    private class SettingsOnlyStrategy : CapabilityStrategy {
        override val id = StrategyId.SETTINGS_USER_ACTION
        override val supportedOperations = setOf(SemanticOperationId.WIFI_SET_STATE)
        var executions = 0

        override suspend fun availability(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ) = StrategyAvailability(true)

        override suspend fun execute(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): OperationOutcome {
            executions++
            return OperationOutcome(
                operation = operation,
                status = OperationOutcomeStatus.PENDING_USER_ACTION,
                strategy = id,
                message = "user action required"
            )
        }
    }

    @Test
    fun `settings-only semantic route blocks run before any action side effect`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handler = RecordingHandler()
        val history = RecordingHistory()
        val settings = SettingsOnlyStrategy()
        val planner = SemanticWorkflowPlanner(
            SemanticActionRouter(
                router = CapabilityRouter(
                    registry = OperationRegistry.default(),
                    strategies = listOf(settings),
                    evidenceStore = CapabilityEvidenceStore(),
                    healthTracker = StrategyHealthTracker(),
                    fingerprint = DeviceFingerprint(
                        manufacturer = "test",
                        model = "test",
                        device = "test",
                        androidApi = 37,
                        securityPatch = "2026-09-01",
                        romFamily = RomFamily.AOSP
                    )
                ),
                privilegedPolicyEnabled = { false }
            )
        )
        val engine = ExecutionEngine(
            context = context,
            historyRepository = history,
            notificationPreferences = NotificationPreferences(context),
            actionRegistry = ActionRegistry.from(listOf(handler)),
            semanticWorkflowPlanner = planner
        )
        val automation = Automation(
            id = "semantic-preflight",
            name = "Semantic preflight",
            description = "",
            icon = "wifi",
            iconColor = 0L,
            backgroundColor = 0L,
            category = "test",
            priority = 1,
            enabled = true,
            triggers = emptyList(),
            actions = listOf(
                Action(
                    ActionType.SYSTEM_WIFI,
                    mapOf("enabled" to "true", "configVersion" to "2")
                )
            ),
            createdAt = 1L,
            updatedAt = 1L
        )

        val record = engine.runAutomation(automation)

        assertFalse(record.success)
        assertEquals(0, handler.executions)
        assertEquals(0, settings.executions)
        assertEquals(1, history.records.size)
    }
}
