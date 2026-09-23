package com.nexaflow.domain.catalog

import kotlinx.serialization.Serializable

/**
 * Stable semantic identity for a workflow building block.
 *
 * The persisted Automation model intentionally continues to store TriggerType /
 * ActionType. This catalog is additive metadata used to progressively move UI,
 * validation and diagnostics away from scattered when-expressions without
 * changing saved-task compatibility.
 */
@Serializable
enum class AutomationNodeKind {
    TRIGGER,
    ACTION
}

/**
 * Device-agnostic semantic families. Presentation layers are free to merge
 * families into fewer visual categories, but the domain catalog keeps the
 * richer meaning so search, diagnostics and future capability packs can share
 * one classification.
 */
@Serializable
enum class AutomationNodeFamily {
    SCHEDULE,
    DEVICE,
    CONNECTIVITY,
    LOCATION,
    APPLICATIONS,
    COMMUNICATION,
    NETWORK,
    FILES,
    DISPLAY,
    SOUND,
    MEDIA,
    NOTIFICATIONS,
    BATTERY,
    SYSTEM,
    ROM,
    DATA,
    FLOW,
    PLUGINS,
    DEVELOPER
}

/**
 * Discovery policy is separate from persistence compatibility.
 *
 * LEGACY_HIDDEN entries remain fully readable/executable for old tasks but are
 * not intended for new generic picker flows. CONFIGURATION_ONLY entries can be
 * created only by a dedicated verified configuration surface (for example a
 * plugin chooser) rather than by the generic picker.
 */
@Serializable
enum class AutomationNodeVisibility {
    DISCOVERABLE,
    LEGACY_HIDDEN,
    CONFIGURATION_ONLY
}

/** Legacy string-map field semantics used by the first catalog migration. */
@Serializable
enum class NodeConfigValueType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    ENUM,
    TIME,
    DATE,
    DURATION_SECONDS,
    PACKAGE,
    URL,
    SECRET,
    JSON,
    COORDINATE
}

/**
 * One typed field in the configuration contract.
 *
 * Values are still persisted as strings in v1. The typed metadata is used for
 * validation/editor generation now and allows a future typed persistence
 * migration without guessing field meaning from UI controls.
 */
@Serializable
data class NodeConfigField(
    val key: String,
    val valueType: NodeConfigValueType,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val allowedValues: List<String> = emptyList(),
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val sensitive: Boolean = false,
    val expressionCapable: Boolean = false
) {
    init {
        require(key.isNotBlank()) { "Node config field key must not be blank" }
        require(allowedValues.distinct().size == allowedValues.size) {
            "Node config field '$key' contains duplicate enum values"
        }
        require(minValue == null || maxValue == null || minValue <= maxValue) {
            "Node config field '$key' has an invalid range"
        }
        if (valueType == NodeConfigValueType.ENUM) {
            require(allowedValues.isNotEmpty()) {
                "ENUM field '$key' requires at least one allowed value"
            }
        }
    }
}

/**
 * Typed view over the legacy config map.
 *
 * [acceptsUnknownKeys] remains true during the staged migration because some
 * advanced editors and imported automations carry versioned/dynamic keys. It
 * can be tightened per node once every producer/consumer has moved to schema
 * driven configuration.
 */
@Serializable
data class NodeConfigurationSchema(
    val fields: List<NodeConfigField> = emptyList(),
    val acceptsUnknownKeys: Boolean = true
) {
    init {
        val keys = fields.map(NodeConfigField::key)
        require(keys.distinct().size == keys.size) {
            "Node configuration schema contains duplicate field keys: $keys"
        }
    }

    fun field(key: String): NodeConfigField? = fields.firstOrNull { it.key == key }

    val knownKeys: Set<String>
        get() = fields.mapTo(linkedSetOf(), NodeConfigField::key)
}

/**
 * Domain metadata for one persisted TriggerType or ActionType.
 *
 * [legacyTypeName] is deliberately explicit rather than storing the enum itself
 * so the catalog model can later describe plugin/dynamic nodes without changing
 * this contract.
 */
@Serializable
data class AutomationNodeDefinition(
    val id: String,
    val kind: AutomationNodeKind,
    val family: AutomationNodeFamily,
    val legacyTypeName: String,
    val visibility: AutomationNodeVisibility = AutomationNodeVisibility.DISCOVERABLE,
    val canonicalId: String = id,
    val configuration: NodeConfigurationSchema = NodeConfigurationSchema()
) {
    init {
        require(id.isNotBlank()) { "Automation node id must not be blank" }
        require(legacyTypeName.isNotBlank()) { "Automation node legacy type must not be blank" }
        require(canonicalId.isNotBlank()) { "Automation node canonical id must not be blank" }
    }

    val isAlias: Boolean
        get() = canonicalId != id
}
