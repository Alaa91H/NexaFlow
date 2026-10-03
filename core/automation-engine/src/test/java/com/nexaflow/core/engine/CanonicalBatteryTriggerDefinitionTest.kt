package com.nexaflow.core.engine

import android.os.BatteryManager
import com.nexaflow.core.execution.canonical.CanonicalTriggerDispatcher
import com.nexaflow.core.execution.canonical.CanonicalTriggerHandlerRegistry
import com.nexaflow.domain.canonical.CanonicalTriggerEvaluator
import com.nexaflow.domain.canonical.CanonicalTriggerSourceRegistry
import com.nexaflow.domain.canonical.ConditionLogic
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ConditionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalBatteryTriggerDefinitionTest {
    @Test
    fun `threshold direction and charging filters are evaluated together`() {
        val trigger = CanonicalBatteryTriggerDefinition.node(
            id = "battery-test",
            thresholdPercent = 30,
            direction = "BELOW",
            chargerType = "USB",
            chargingState = "NOT_CHARGING",
        )

        assertEquals(
            ConditionResult.Satisfied,
            CanonicalBatteryTriggerDefinition.evaluate(trigger, 25, BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_PLUGGED_USB),
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            CanonicalBatteryTriggerDefinition.evaluate(trigger, 25, BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_PLUGGED_AC),
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            CanonicalBatteryTriggerDefinition.evaluate(trigger, 25, BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB),
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            CanonicalBatteryTriggerDefinition.evaluate(trigger, 35, BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_PLUGGED_USB),
        )
    }

    @Test
    fun `invalid typed battery fields are rejected before persistence`() {
        val failure = runCatching {
            CanonicalBatteryTriggerDefinition.node(id = "battery-invalid", thresholdPercent = 110)
        }.exceptionOrNull()

        org.junit.Assert.assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `dispatcher evaluates repeated source definitions against each node`() = kotlinx.coroutines.runBlocking {
        val contract = CanonicalBatteryTriggerDefinition.contract.copy(
            capabilityRequirement = CapabilityRequirement.None,
        )
        val registry = CanonicalTriggerSourceRegistry(listOf(contract))
        val dispatcher = CanonicalTriggerDispatcher(
            evaluator = CanonicalTriggerEvaluator(registry),
            handlers = CanonicalTriggerHandlerRegistry(listOf(
                CanonicalBatteryTriggerDefinition.handler { node ->
                    CanonicalBatteryTriggerDefinition.evaluate(
                        node, 50, BatteryManager.BATTERY_STATUS_DISCHARGING, 0,
                    )
                },
            )),
            sources = registry,
        )
        val nodes = listOf(
            CanonicalBatteryTriggerDefinition.node("battery-first", thresholdPercent = 80),
            CanonicalBatteryTriggerDefinition.node("battery-second", thresholdPercent = 20),
        )

        val result = dispatcher.evaluate(nodes, ConditionLogic.ANY)

        assertEquals(ConditionResult.Satisfied, result.decision)
        assertEquals(listOf(ConditionResult.Satisfied, ConditionResult.Unsatisfied), result.results)
    }
}
