package com.nexaflow.core.execution.constraints

import com.nexaflow.core.execution.capability.CapabilityBackend
import com.nexaflow.core.execution.capability.CapabilityExecutionService
import com.nexaflow.core.execution.capability.CapabilityRegistry
import com.nexaflow.core.execution.capability.CapabilityResolver
import com.nexaflow.domain.capability.BackendAvailability
import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityBackendId
import com.nexaflow.domain.capability.CapabilityDescriptor
import com.nexaflow.domain.capability.CapabilityDeviceState
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityParameterSpec
import com.nexaflow.domain.capability.CapabilityParameterType
import com.nexaflow.domain.capability.CapabilityRequest
import com.nexaflow.domain.capability.CapabilityResult
import com.nexaflow.domain.capability.CapabilityStatus
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationConstraintGateTest {

    @Test
    fun unknownPluginStateRemainsUnknownAtConstraintGate() = runBlocking {
        val gate = AutomationConstraintGate(serviceReturning(ConditionResult.Unknown))

        val result = gate.evaluate(pluginAutomation(), state = null)

        assertEquals(ConditionResult.Unknown, result)
    }

    @Test
    fun missingCapabilityServiceIsReportedAsUnavailableNotUnsatisfied() = runBlocking {
        val gate = AutomationConstraintGate(capabilityExecutionService = null)

        val result = gate.evaluate(pluginAutomation(), state = null)

        assertEquals(ConditionResult.Unavailable, result)
    }

    private fun serviceReturning(condition: ConditionResult): CapabilityExecutionService {
        val backend = object : CapabilityBackend {
            override val id = CapabilityBackendId.PLUGIN
            override val supportedCapabilities = setOf(CapabilityId.PLUGIN_CONDITION_READ)

            override suspend fun availability(request: CapabilityRequest) = BackendAvailability(
                backend = id,
                availability = CapabilityAvailability.AVAILABLE
            )

            override suspend fun execute(request: CapabilityRequest): CapabilityResult = CapabilityResult(
                status = if (condition == ConditionResult.Unknown) CapabilityStatus.PARTIAL else CapabilityStatus.SUCCESS,
                backend = id,
                message = "test condition",
                conditionResult = condition
            )
        }
        val descriptor = CapabilityDescriptor(
            id = CapabilityId.PLUGIN_CONDITION_READ,
            displayName = "Test plugin condition",
            description = "Test-only typed condition",
            supportedBackends = listOf(CapabilityBackendId.PLUGIN),
            parameters = listOf(
                CapabilityParameterSpec(
                    name = "pluginInstance",
                    type = CapabilityParameterType.OPAQUE_REFERENCE,
                    required = true
                )
            )
        )
        return CapabilityExecutionService(
            resolver = CapabilityResolver(CapabilityRegistry.of(listOf(descriptor), listOf(backend))),
            deviceStateProvider = { CapabilityDeviceState(capturedAt = 0L) }
        )
    }

    private fun pluginAutomation(): Automation = Automation(
        id = "typed-plugin-constraint",
        name = "Typed plugin constraint",
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "Test",
        priority = 0,
        enabled = true,
        triggers = emptyList(),
        actions = emptyList(),
        constraints = listOf(
            Constraint(
                type = ConstraintType.PLUGIN,
                config = mapOf("pluginInstance" to "plugin:condition")
            )
        ),
        createdAt = 0L,
        updatedAt = 0L
    )
}
