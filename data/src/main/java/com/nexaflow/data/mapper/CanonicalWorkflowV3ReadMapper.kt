package com.nexaflow.data.mapper

import com.nexaflow.domain.canonical.BooleanValue
import com.nexaflow.domain.canonical.CanonicalActionNode
import com.nexaflow.domain.canonical.CanonicalArguments
import com.nexaflow.domain.canonical.CanonicalEndBehaviorV3
import com.nexaflow.domain.canonical.CanonicalNode
import com.nexaflow.domain.canonical.CanonicalPersistedNodeV3
import com.nexaflow.domain.canonical.CanonicalWorkflowDocumentV3
import com.nexaflow.domain.canonical.ConditionLogic
import com.nexaflow.domain.canonical.CoordinateValue
import com.nexaflow.domain.canonical.DateValue
import com.nexaflow.domain.canonical.DecimalValue
import com.nexaflow.domain.canonical.DurationValue
import com.nexaflow.domain.canonical.EnumTokenValue
import com.nexaflow.domain.canonical.ExpressionValue
import com.nexaflow.domain.canonical.IntegerValue
import com.nexaflow.domain.canonical.JsonValue
import com.nexaflow.domain.canonical.ObserveNode
import com.nexaflow.domain.canonical.PackageIdValue
import com.nexaflow.domain.canonical.SecretReferenceValue
import com.nexaflow.domain.canonical.TextValue
import com.nexaflow.domain.canonical.TimeOfDayValue
import com.nexaflow.domain.canonical.UriValue
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigField
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType

/**
 * Legacy-model rehydration boundary for Canonical V3 reads.
 *
 * The canonical package stays independent from TriggerType/ActionType. This
 * mapper is deliberately in data: Room still exposes the historical model,
 * while the V3 document is authoritative for the workflow graph it represents.
 */
internal object CanonicalWorkflowV3ReadMapper {

    fun toAutomation(
        document: CanonicalWorkflowDocumentV3,
        legacy: Automation,
    ): Automation {
        require(document.workflowId == legacy.id) {
            "canonical workflow id does not match legacy row"
        }

        val triggers = document.triggers.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<TriggerType>(persisted.sourceType, "trigger")
            val fallback = legacy.triggers.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Trigger(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
            )
        }

        val actions = document.actions.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<ActionType>(persisted.sourceType, "action")
            val fallback = legacy.actions.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Action(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
                endBehavior = persisted.endBehavior?.toDomainEndBehavior(),
            )
        }

        val exitActions = document.exitActions.mapIndexed { index, persisted ->
            val type = enumValueOrThrow<ActionType>(persisted.sourceType, "exit action")
            val fallback = legacy.exitActions.getOrNull(index)
                ?.takeIf { it.type == type }
                ?.config
                .orEmpty()
            Action(
                type = type,
                config = rehydrateConfig(
                    definition = AutomationNodeCatalog.definitionFor(type),
                    persisted = persisted,
                    legacyConfig = fallback,
                ),
                endBehavior = persisted.endBehavior?.toDomainEndBehavior(),
            )
        }

        return legacy.copy(
            triggers = triggers,
            actions = actions,
            exitActions = exitActions,
            triggerMatch = when (document.conditionLogic) {
                ConditionLogic.ALL -> TriggerMatchMode.ALL
                ConditionLogic.ANY -> TriggerMatchMode.ANY
            },
        )
    }

    private fun CanonicalEndBehaviorV3.toDomainEndBehavior(): EndBehavior =
        EndBehavior(
            mode = runCatching { EndMode.valueOf(mode) }.getOrElse {
                throw IllegalArgumentException("unknown end behavior mode")
            },
            config = config.associate { it.key to it.rawValue },
        )

    private fun rehydrateConfig(
        definition: AutomationNodeDefinition,
        persisted: CanonicalPersistedNodeV3,
        legacyConfig: Map<String, String>,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        persisted.preservedConfig.forEach { entry ->
            result[entry.key] = entry.rawValue
        }
        val arguments = nodeArguments(persisted.node)
            .entries
            .associateBy { it.id.value }

        definition.configuration.fields.forEach { field ->
            val value = arguments[field.key]?.value ?: return@forEach
            if (field.sensitive || field.valueType == NodeConfigValueType.SECRET) {
                require(value is SecretReferenceValue) {
                    "sensitive field ${field.key} lost its secret reference"
                }
                val rawSecret = legacyConfig[field.key]
                if (!rawSecret.isNullOrEmpty()) {
                    result[field.key] = rawSecret
                } else if (persisted.legacyFallbackRequired) {
                    throw IllegalArgumentException(
                        "legacy secret fallback missing for " +
                            "${definition.legacyTypeName}/${field.key}",
                    )
                }
                return@forEach
            }
            canonicalValueToLegacy(field, value)?.let { raw ->
                result[field.key] = raw
            }
        }
        return result
    }

    private fun canonicalValueToLegacy(
        field: NodeConfigField,
        value: com.nexaflow.domain.canonical.CanonicalValue,
    ): String? {
        if (value is ExpressionValue) return value.source
        return when (field.valueType) {
            NodeConfigValueType.STRING -> (value as? TextValue)?.value
            NodeConfigValueType.INTEGER -> (value as? IntegerValue)?.value?.toString()
            NodeConfigValueType.DECIMAL -> (value as? DecimalValue)?.value
            NodeConfigValueType.BOOLEAN -> (value as? BooleanValue)?.value?.toString()
            NodeConfigValueType.ENUM -> (value as? EnumTokenValue)?.token
            NodeConfigValueType.TIME -> (value as? TimeOfDayValue)?.let {
                "%02d:%02d".format(it.minuteOfDay / 60, it.minuteOfDay % 60)
            }
            NodeConfigValueType.DATE -> (value as? DateValue)?.isoDate
            NodeConfigValueType.DURATION_SECONDS -> (value as? DurationValue)?.let {
                require(it.milliseconds % 1000L == 0L) {
                    "legacy seconds field cannot represent fractional milliseconds"
                }
                (it.milliseconds / 1000L).toString()
            }
            NodeConfigValueType.PACKAGE -> (value as? PackageIdValue)?.packageName
            NodeConfigValueType.URL -> (value as? UriValue)?.value
            NodeConfigValueType.SECRET -> null
            NodeConfigValueType.JSON -> (value as? JsonValue)?.value?.toString()
            NodeConfigValueType.COORDINATE -> when (value) {
                is DecimalValue -> value.value
                is CoordinateValue -> "${value.latitude},${value.longitude}"
                else -> null
            }
        }
    }

    private fun nodeArguments(node: CanonicalNode): CanonicalArguments = when (node) {
        is CanonicalActionNode -> node.arguments
        is ObserveNode -> node.arguments
        else -> CanonicalArguments.EMPTY
    }

    private inline fun <reified T : Enum<T>> enumValueOrThrow(
        raw: String,
        label: String,
    ): T = enumValues<T>().firstOrNull { it.name == raw }
        ?: throw IllegalArgumentException("unknown canonical source $label: $raw")
}
