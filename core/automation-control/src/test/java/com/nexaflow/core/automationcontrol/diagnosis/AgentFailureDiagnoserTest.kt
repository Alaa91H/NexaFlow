package com.nexaflow.core.automationcontrol.diagnosis

import com.nexaflow.core.automationcontrol.AutomationDryRunInspector
import com.nexaflow.core.execution.dryrun.WorkflowDryRunReport
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.workflow.WorkflowValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFailureDiagnoserTest {

    @Test
    fun missingAutomationReportsNotFound() = runTest {
        val diagnoser = diagnoser(FakeDiagnosisRepository())

        val diagnosis = diagnoser.diagnose(AgentFailureSignal(automationId = "gone"))

        assertEquals(listOf(AgentFailureCause.NOT_FOUND), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.NONE, diagnosis.proposedAction)
        assertNull(diagnosis.proposedSafeDraft)
    }

    @Test
    fun invalidDefinitionProposesSafeDisableDraft() = runTest {
        val broken = automation(id = "broken", name = "", enabled = true)
        val diagnoser = diagnoser(FakeDiagnosisRepository(listOf(broken)))

        val diagnosis = diagnoser.diagnose(AgentFailureSignal(automationId = "broken"))

        assertEquals(listOf(AgentFailureCause.VALIDATION_CHANGED), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.DISABLE, diagnosis.proposedAction)
        val draft = diagnosis.proposedSafeDraft
        assertTrue(draft != null)
        assertFalse(draft!!.enabled)
        assertTrue(diagnosis.evidence.any { it.contains("BLANK_AUTOMATION_NAME") })
    }

    @Test
    fun disabledBrokenDefinitionProposesNothing() = runTest {
        val broken = automation(id = "broken", name = "", enabled = false)
        val diagnoser = diagnoser(FakeDiagnosisRepository(listOf(broken)))

        val diagnosis = diagnoser.diagnose(AgentFailureSignal(automationId = "broken"))

        assertEquals(listOf(AgentFailureCause.VALIDATION_CHANGED), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.NONE, diagnosis.proposedAction)
        assertNull(diagnosis.proposedSafeDraft)
    }

    @Test
    fun nonExecutableReportsDryRunEvidence() = runTest {
        val stuck = automation(id = "stuck", name = "Stuck", enabled = true)
        val diagnoser = diagnoser(
            FakeDiagnosisRepository(listOf(stuck)),
            executable = false,
            dryRunSummary = "capability unavailable"
        )

        val diagnosis = diagnoser.diagnose(AgentFailureSignal(automationId = "stuck"))

        assertEquals(listOf(AgentFailureCause.NOT_EXECUTABLE), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.DISABLE, diagnosis.proposedAction)
        assertTrue(diagnosis.evidence.any { it.contains("capability unavailable") })
    }

    @Test
    fun capabilityErrorWhileExecutableIsClassified() = runTest {
        val automation = automation(id = "flaky", name = "Flaky", enabled = true)
        val diagnoser = diagnoser(FakeDiagnosisRepository(listOf(automation)))

        val diagnosis = diagnoser.diagnose(
            AgentFailureSignal(automationId = "flaky", errorCode = "UNKNOWN_ERROR")
        )

        assertEquals(listOf(AgentFailureCause.CAPABILITY_CHANGED), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.NONE, diagnosis.proposedAction)
        assertNull(diagnosis.proposedSafeDraft)
    }

    @Test
    fun healthyTaskWithNoSignalIsUnknown() = runTest {
        val automation = automation(id = "ok", name = "Ok", enabled = true)
        val diagnoser = diagnoser(FakeDiagnosisRepository(listOf(automation)))

        val diagnosis = diagnoser.diagnose(AgentFailureSignal(automationId = "ok"))

        assertEquals(listOf(AgentFailureCause.UNKNOWN), diagnosis.causes)
        assertEquals(AgentProposedSafeAction.NONE, diagnosis.proposedAction)
    }

    private fun diagnoser(
        repository: AutomationRepository,
        executable: Boolean = true,
        dryRunSummary: String = "ok"
    ) = AgentFailureDiagnoser(
        repository = repository,
        dryRunInspector = AutomationDryRunInspector {
            WorkflowDryRunReport(
                workflowValidation = WorkflowValidationResult(emptyList()),
                capabilityResolutions = emptyList(),
                executable = executable,
                summary = dryRunSummary
            )
        }
    )

    private fun automation(id: String, name: String, enabled: Boolean) = Automation(
        id = id,
        name = name,
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "custom",
        priority = 1,
        enabled = enabled,
        triggers = emptyList(),
        actions = emptyList(),
        cooldownSeconds = 0,
        createdAt = 10L,
        updatedAt = 11L
    )

    private class FakeDiagnosisRepository(
        initial: List<Automation> = emptyList()
    ) : AutomationRepository {
        private val state = MutableStateFlow(initial)

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
            state.value = state.value.map { automation ->
                if (automation.id == id) automation.copy(enabled = enabled) else automation
            }
        }
    }
}
