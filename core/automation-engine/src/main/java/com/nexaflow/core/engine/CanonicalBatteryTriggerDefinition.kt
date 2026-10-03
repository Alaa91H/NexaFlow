package com.nexaflow.core.engine

import android.os.BatteryManager
import com.nexaflow.core.execution.canonical.CanonicalTriggerSourceHandler
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.CanonicalNodeId
import com.nexaflow.domain.canonical.CanonicalNativeNodeSchemaRegistry
import com.nexaflow.domain.canonical.CanonicalTriggerSourceContract
import com.nexaflow.domain.canonical.CanonicalTriggerSourceKind
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.NodeFieldValue
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.NodeSecurityClass
import com.nexaflow.domain.canonical.ObserveNode
import com.nexaflow.domain.canonical.PredicateId
import com.nexaflow.domain.canonical.TargetId
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilityRequirement
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.schedule.BatteryTriggerMatcher

/** Battery observation with typed thresholds and the same matching semantics as legacy triggers. */
object CanonicalBatteryTriggerDefinition {
    const val ID = "android.battery.threshold"
    val target = TargetId("android.device.battery")
    val predicate = PredicateId(ID)
    val thresholdField = CanonicalFieldId("threshold_percent")
    val directionField = CanonicalFieldId("direction")
    val chargerField = CanonicalFieldId("charger_type")
    val chargingField = CanonicalFieldId("charging_state")

    val schema = CanonicalNativeNodeSchemaRegistry.batteryThreshold

    val contract = CanonicalTriggerSourceContract(
        definitionId = ID,
        schema = schema,
        sourceKind = CanonicalTriggerSourceKind.STATE,
        capabilityRequirement = CapabilityRequirement.Capability(CapabilityId.DEVICE_STATE_READ),
        duplicateWindowMs = 0,
    )

    class Handler(
        private val stateProvider: suspend (CanonicalWorkflowNode) -> ConditionResult,
    ) : CanonicalTriggerSourceHandler {
        override val definitionId = ID
        override suspend fun evaluate(node: CanonicalWorkflowNode, occurrenceId: String?): ConditionResult =
            stateProvider(node)
    }

    fun handler(stateProvider: suspend (CanonicalWorkflowNode) -> ConditionResult) = Handler(stateProvider)

    suspend fun readLiveState(
        context: android.content.Context,
        node: CanonicalWorkflowNode,
    ): ConditionResult {
        if (node.definitionId != ID || node.schema != schema) return ConditionResult.Unavailable
        val fields = node.arguments.associate { it.field to it.value }
        val threshold = (fields[thresholdField] as? IntegerValue)?.value?.toInt()
            ?: return ConditionResult.Error("Invalid battery threshold")
        val direction = (fields[directionField] as? EnumTokenValue)?.token
            ?: return ConditionResult.Error("Invalid battery direction")
        val chargerType = (fields[chargerField] as? EnumTokenValue)?.token
            ?: return ConditionResult.Error("Invalid battery charger")
        val chargingState = (fields[chargingField] as? EnumTokenValue)?.token
            ?: return ConditionResult.Error("Invalid battery charging state")
        val intent = runCatching {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return ConditionResult.Unknown
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        if (level !in 0..100) return ConditionResult.Unknown
        val config = mapOf(
            "threshold" to threshold.toString(),
            "direction" to direction,
            "chargerType" to chargerType,
            "chargingState" to chargingState,
        )
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return if (BatteryTriggerMatcher.isActive(config, level, plugged, charging)) {
            ConditionResult.Satisfied
        } else ConditionResult.Unsatisfied
    }

    fun adapter(provider: suspend (CanonicalWorkflowNode) -> ConditionResult) = handler(provider)

    fun node(
        id: String,
        sequenceIndex: Int = 0,
        thresholdPercent: Int = 20,
        direction: String = "BELOW",
        chargerType: String = "ANY",
        chargingState: String = "ANY",
    ) = CanonicalWorkflowNode(
        kind = NodeSchemaKind.TRIGGER,
        definitionId = ID,
        schema = schema,
        node = ObserveNode(CanonicalNodeId(id), target, predicate, arguments(thresholdPercent, direction, chargerType, chargingState)),
        arguments = listOf(
            NodeFieldValue(thresholdField, IntegerValue(thresholdPercent.toLong())),
            NodeFieldValue(directionField, EnumTokenValue("battery.direction", direction)),
            NodeFieldValue(chargerField, EnumTokenValue("battery.charger", chargerType)),
            NodeFieldValue(chargingField, EnumTokenValue("battery.charging", chargingState)),
        ),
        sequenceIndex = sequenceIndex,
    )

    fun evaluate(node: CanonicalWorkflowNode, level: Int, status: Int, plugged: Int): ConditionResult {
        if (node.definitionId != ID || node.schema != schema) return ConditionResult.Unavailable
        val fields = node.arguments.associate { it.field to it.value }
        val threshold = (fields[thresholdField] as? IntegerValue)?.value?.toInt() ?: return ConditionResult.Error("Invalid battery threshold")
        val direction = (fields[directionField] as? EnumTokenValue)?.token ?: return ConditionResult.Error("Invalid battery direction")
        val charger = (fields[chargerField] as? EnumTokenValue)?.token ?: return ConditionResult.Error("Invalid battery charger")
        val chargingState = (fields[chargingField] as? EnumTokenValue)?.token ?: return ConditionResult.Error("Invalid battery charging state")
        if (threshold !in 0..100 || direction !in listOf("ABOVE", "BELOW") ||
            charger !in listOf("ANY", "AC", "USB", "WIRELESS") ||
            chargingState !in listOf("ANY", "CHARGING", "NOT_CHARGING")
        ) return ConditionResult.Error("Battery condition is outside its schema")
        val config = mapOf(
            "threshold" to threshold.toString(),
            "direction" to direction,
            "chargerType" to charger,
            "chargingState" to chargingState,
        )
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return if (BatteryTriggerMatcher.isActive(config, level, plugged, charging)) ConditionResult.Satisfied else ConditionResult.Unsatisfied
    }

    private fun arguments(threshold: Int, direction: String, charger: String, charging: String) =
        com.nexaflow.domain.canonical.CanonicalArguments(listOf(
            com.nexaflow.domain.canonical.CanonicalArgument(thresholdField, IntegerValue(threshold.toLong())),
            com.nexaflow.domain.canonical.CanonicalArgument(directionField, EnumTokenValue("battery.direction", direction)),
            com.nexaflow.domain.canonical.CanonicalArgument(chargerField, EnumTokenValue("battery.charger", charger)),
            com.nexaflow.domain.canonical.CanonicalArgument(chargingField, EnumTokenValue("battery.charging", charging)),
        ))
}
