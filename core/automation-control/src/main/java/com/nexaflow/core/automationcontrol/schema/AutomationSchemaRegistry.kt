package com.nexaflow.core.automationcontrol.schema

import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.AutomationNodeDefinition
import com.nexaflow.domain.catalog.NodeConfigField
import com.nexaflow.domain.models.ConstraintType
import kotlinx.serialization.Serializable

/**
 * Stable, provider-neutral schema view used by future MCP, A2A and OpenAPI
 * adapters. It projects the canonical domain catalog instead of maintaining a
 * second list that can drift from the builder/execution inventory.
 */
class AutomationSchemaRegistry {

    fun snapshot(): AgentAutomationSchemaCatalogV1 = AgentAutomationSchemaCatalogV1(
        triggers = AutomationNodeCatalog.triggerDefinitions.map(::toAgentSchema),
        actions = AutomationNodeCatalog.actionDefinitions.map(::toAgentSchema),
        constraints = ConstraintType.entries.map { type ->
            AgentNodeSchemaV1(
                id = "constraint.${type.name.lowercase()}",
                kind = "CONSTRAINT",
                family = "CONSTRAINT",
                type = type.name,
                visibility = "DISCOVERABLE",
                acceptsUnknownKeys = true,
                fields = emptyList()
            )
        }
    )

    private fun toAgentSchema(definition: AutomationNodeDefinition): AgentNodeSchemaV1 =
        AgentNodeSchemaV1(
            id = definition.id,
            kind = definition.kind.name,
            family = definition.family.name,
            type = definition.legacyTypeName,
            visibility = definition.visibility.name,
            acceptsUnknownKeys = definition.configuration.acceptsUnknownKeys,
            fields = definition.configuration.fields.map(::toAgentField)
        )

    private fun toAgentField(field: NodeConfigField): AgentConfigFieldSchemaV1 =
        AgentConfigFieldSchemaV1(
            key = field.key,
            valueType = field.valueType.name,
            required = field.required,
            defaultValue = field.defaultValue,
            allowedValues = field.allowedValues,
            minValue = field.minValue,
            maxValue = field.maxValue,
            sensitive = field.sensitive,
            expressionCapable = field.expressionCapable
        )
}

@Serializable
data class AgentAutomationSchemaCatalogV1(
    val schemaVersion: Int = 1,
    val triggers: List<AgentNodeSchemaV1>,
    val actions: List<AgentNodeSchemaV1>,
    val constraints: List<AgentNodeSchemaV1>
)

@Serializable
data class AgentNodeSchemaV1(
    val id: String,
    val kind: String,
    val family: String,
    val type: String,
    val visibility: String,
    val acceptsUnknownKeys: Boolean,
    val fields: List<AgentConfigFieldSchemaV1>
)

@Serializable
data class AgentConfigFieldSchemaV1(
    val key: String,
    val valueType: String,
    val required: Boolean,
    val defaultValue: String?,
    val allowedValues: List<String>,
    val minValue: Double?,
    val maxValue: Double?,
    val sensitive: Boolean,
    val expressionCapable: Boolean
)
