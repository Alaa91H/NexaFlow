package com.nexaflow.core.execution.dryrun

import com.nexaflow.core.execution.capability.CapabilityBackend
import com.nexaflow.core.execution.capability.CapabilityRegistry
import com.nexaflow.core.execution.capability.CapabilityResolver
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.NetworkRequirement
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.MaintenanceKind
import com.nexaflow.domain.models.MaintenanceProfile
import com.nexaflow.domain.workflow.WorkflowValidationCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowDryRunServiceTest {
    private val automation = Automation(
        id = "a1",
        name = "Dry run",
        description = "",
        icon = "bolt",
        iconColor = 1L,
        backgroundColor = 2L,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun dryRunReportsPolicyBlockWithoutExecutingBackend() = runBlocking {
        val backend = object : CapabilityBackend {
            override val id = CapabilityBackendId.ANDROID_API
            override val supportedCapabilities = setOf(CapabilityId.PACKAGE_READ)
            var executions = 0
            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(id, CapabilityAvailability.AVAILABLE)
            override suspend fun execute(request: CapabilityRequest): CapabilityResult {
                executions++
                return CapabilityResult.unsupported("must not run")
            }
        }
        val resolver = CapabilityResolver(
            CapabilityRegistry.of(
                descriptors = listOf(
                    CapabilityDescriptor(
                        id = CapabilityId.PACKAGE_READ,
                        displayName = "Read package",
                        description = "",
                        supportedBackends = listOf(CapabilityBackendId.ANDROID_API)
                    )
                ),
                backends = listOf(backend)
            )
        )
        val service = WorkflowDryRunService(resolver) {
            CapabilityDeviceState(capturedAt = 1L, wifiConnected = false)
        }

        val report = service.inspect(
            WorkflowDryRunInput(
                automation,
                listOf(CapabilityRequest(CapabilityId.PACKAGE_READ, policy = com.nexaflow.domain.capability.ExecutionPolicy(network = NetworkRequirement.WIFI_ONLY)))
            )
        )

        assertFalse(report.executable)
        assertEquals(0, backend.executions)
        assertEquals(false, report.capabilityResolutions.single().policy.allowed)
    }

    @Test
    fun dryRunRejectsDependencyCycleBeforeCapabilityResolution() = runBlocking {
        val first = automation.copy(
            id = "first",
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("second")
            )
        )
        val second = automation.copy(
            id = "second",
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                dependencyAutomationIds = listOf("first")
            )
        )
        val service = WorkflowDryRunService(
            CapabilityResolver(CapabilityRegistry.of(emptyList(), emptyList()))
        ) { CapabilityDeviceState(capturedAt = 1L) }

        val report = service.inspect(
            WorkflowDryRunInput(
                automation = first,
                automationCatalog = listOf(second)
            )
        )

        assertFalse(report.executable)
        assertTrue(report.workflowValidation.issues.any {
            it.code == WorkflowValidationCode.CIRCULAR_DEPENDENCY
        })
        assertTrue(report.capabilityResolutions.isEmpty())
    }
}
