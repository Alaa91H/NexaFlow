package com.nexaflow.core.automationcontrol.simulation

import com.nexaflow.core.automationcontrol.AutomationCommandService
import com.nexaflow.core.automationcontrol.AutomationDryRunInspector
import com.nexaflow.core.automationcontrol.AutomationMutationPersistence
import com.nexaflow.core.automationcontrol.api.AgentActionDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTriggerDraftV1
import com.nexaflow.core.automationcontrol.schedule.AgentSchedulePreviewService
import com.nexaflow.core.automationcontrol.schedule.AgentScheduleZonePolicyV1
import com.nexaflow.core.execution.capability.semantic.OperationExecutionPlan
import com.nexaflow.core.execution.capability.semantic.OperationPlanStatus
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowExecutionPlan
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowNodePlan
import com.nexaflow.core.execution.capability.semantic.StrategyPlanCandidate
import com.nexaflow.core.execution.compat.WorkflowCapabilityValidationResult
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.domain.capability.ExecutionRequirementState
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSimulationServiceTest {

    @Test
    fun validTaskReturnsExecutionAndSchedulePreviewWithoutPersistence() = runTest {
        var commits = 0
        val commandService = commandService(
            persistence = AutomationMutationPersistence {
                commits += 1
                error("simulation must never persist")
            },
            report = executableReport()
        )
        val service = AgentSimulationService(
            commandService = commandService,
            schedulePreviewService = AgentSchedulePreviewService {
                ZoneId.of("UTC")
            }
        )

        val result = service.simulate(
            AgentSimulationRequestV1(
                task = scheduledTask(),
                schedule = AgentSimulationScheduleOptionsV1(
                    fromEpochMillis = Instant.parse("2026-09-26T07:00:00Z").toEpochMilli(),
                    count = 2
                )
            )
        )

        assertTrue(result.valid)
        assertTrue(result.executable)
        assertTrue(result.noSideEffects)
        assertEquals(0, commits)
        assertEquals("ANDROID_PUBLIC_API", result.nodes.single().selectedStrategy)
        assertEquals("READY", result.requirement?.state)
        assertEquals(2, result.schedulePreview?.triggers?.single()?.occurrences?.size)
        assertEquals("UTC", result.schedulePreview?.zoneId)
    }

    @Test
    fun mappingFailureIsReportedWithoutInvokingDryRunOrPersistence() = runTest {
        var dryRuns = 0
        var commits = 0
        val repository = FakeAutomationRepository()
        val commandService = AutomationCommandService(
            repository = repository,
            dryRunInspector = AutomationDryRunInspector {
                dryRuns += 1
                executableReport()
            },
            mutationPersistence = AutomationMutationPersistence {
                commits += 1
                error("simulation must never persist")
            },
            clockMillis = { 100L },
            idGenerator = { "simulation-id" }
        )
        val service = AgentSimulationService(
            commandService = commandService,
            schedulePreviewService = AgentSchedulePreviewService {
                ZoneId.of("UTC")
            }
        )

        val result = service.simulate(
            AgentSimulationRequestV1(
                task = AgentTaskDraftV1(
                    name = "Invalid",
                    actions = listOf(AgentActionDraftV1(type = "NOT_A_REAL_ACTION"))
                )
            )
        )

        assertFalse(result.valid)
        assertFalse(result.executable)
        assertEquals(0, dryRuns)
        assertEquals(0, commits)
        assertEquals("unknown_enum_value", result.issues.single().code)
        assertTrue(result.noSideEffects)
    }

    @Test
    fun invalidRequestedTimezoneFailsSimulationRequestButKeepsDryRunEvidence() = runTest {
        val commandService = commandService(
            persistence = AutomationMutationPersistence {
                error("simulation must never persist")
            },
            report = executableReport()
        )
        val service = AgentSimulationService(
            commandService = commandService,
            schedulePreviewService = AgentSchedulePreviewService {
                ZoneId.of("UTC")
            }
        )

        val result = service.simulate(
            AgentSimulationRequestV1(
                task = scheduledTask(),
                schedule = AgentSimulationScheduleOptionsV1(
                    fromEpochMillis = 1L,
                    zonePolicy = AgentScheduleZonePolicyV1.FIXED_IANA,
                    fixedZoneId = "Mars/Olympus"
                )
            )
        )

        assertFalse(result.valid)
        assertFalse(result.executable)
        assertEquals("invalid_timezone", result.scheduleIssue?.code)
        assertNotNull(result.requirement)
        assertTrue(result.nodes.isNotEmpty())
    }

    private fun commandService(
        persistence: AutomationMutationPersistence,
        report: WorkflowDryRunReport
    ): AutomationCommandService = AutomationCommandService(
        repository = FakeAutomationRepository(),
        dryRunInspector = AutomationDryRunInspector { report },
        mutationPersistence = persistence,
        clockMillis = { 100L },
        idGenerator = { "simulation-id" }
    )

    private fun executableReport(): WorkflowDryRunReport =
        WorkflowDryRunReport(
            workflowValidation = WorkflowValidationResult(emptyList()),
            capabilityResolutions = emptyList(),
            executable = true,
            summary = "Workflow and observed execution routes passed dry-run",
            requirementValidation = WorkflowCapabilityValidationResult(
                admissible = true,
                missingCapabilities = emptySet(),
                state = ExecutionRequirementState.READY
            ),
            semanticPlan = SemanticWorkflowExecutionPlan(
                listOf(
                    SemanticWorkflowNodePlan(
                        owner = "action:0:SYSTEM_SEND_NOTIFICATION",
                        actionType = ActionType.SYSTEM_SEND_NOTIFICATION,
                        plan = OperationExecutionPlan(
                            operation = SemanticOperationId.WIFI_SET_STATE,
                            status = OperationPlanStatus.READY,
                            selectedStrategy = StrategyId.ANDROID_PUBLIC_API,
                            candidates = listOf(
                                StrategyPlanCandidate(
                                    strategy = StrategyId.ANDROID_PUBLIC_API,
                                    available = true,
                                    selected = true,
                                    interactive = false,
                                    permissionRequired = false,
                                    confidence = 90,
                                    reason = "available",
                                    evidenceScore = 0L,
                                    privilegeCost = 0
                                )
                            ),
                            message = "ready"
                        )
                    )
                )
            )
        )

    private fun scheduledTask() = AgentTaskDraftV1(
        name = "Scheduled task",
        triggers = listOf(
            AgentTriggerDraftV1(
                type = "TIME",
                config = mapOf(
                    "time" to "08:00",
                    "repeat" to "DAILY"
                )
            )
        ),
        actions = listOf(
            AgentActionDraftV1(
                type = "SYSTEM_SEND_NOTIFICATION",
                config = mapOf("title" to "hello")
            )
        )
    )

    private class FakeAutomationRepository : AutomationRepository {
        private val state = MutableStateFlow<List<Automation>>(emptyList())

        override fun getAutomations(): Flow<List<Automation>> = state

        override suspend fun getAutomationById(id: String): Automation? =
            state.value.firstOrNull { it.id == id }

        override suspend fun saveAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id } + automation
        }

        override suspend fun deleteAutomation(automation: Automation) {
            state.value = state.value.filterNot { it.id == automation.id }
        }

        override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
            state.value = state.value.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
        }
    }
}
