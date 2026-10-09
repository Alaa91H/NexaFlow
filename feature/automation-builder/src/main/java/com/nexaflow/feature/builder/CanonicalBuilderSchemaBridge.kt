package com.nexaflow.feature.builder

import com.nexaflow.domain.canonical.FamilyPhase18MediaNavigation
import com.nexaflow.domain.canonical.FamilyPhase19Connectivity
import com.nexaflow.domain.canonical.FamilyPhase20DisplaySound
import com.nexaflow.domain.canonical.FamilyPhase22Communication
import com.nexaflow.domain.canonical.FamilyPhase23PowerSensors
import com.nexaflow.domain.canonical.FamilyPhase24TimeLocation
import com.nexaflow.domain.canonical.FamilyPhase25AdvancedExternal
import com.nexaflow.domain.canonical.LegacyCatalogCanonicalContractNormalizer
import com.nexaflow.domain.canonical.LegacyConfigEntry
import com.nexaflow.domain.canonical.LegacyMappingTable
import com.nexaflow.domain.canonical.LegacyNodeInput
import com.nexaflow.domain.canonical.LegacyNodeKind
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.NodeFieldType
import com.nexaflow.domain.canonical.CanonicalFieldId
import com.nexaflow.domain.canonical.NodeSchemaField
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.PilotOpenFamily
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.catalog.NodeConfigValueType
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Product compatibility bridge from the append-only V1/V2 enum surface to
 * canonical schemas.
 *
 * It is the ONLY builder file allowed to translate persisted enum identities.
 * Rendering is enum-free: [CanonicalSchemaFieldEditor] consumes only
 * [CanonicalBuilderSchemaBinding]/[NodeSchema].
 */
internal data class CanonicalBuilderSchemaBinding(
    val schema: NodeSchema,
    /** canonical field id -> legacy config key */
    val legacyKeys: Map<String, String> = emptyMap(),
)

internal object CanonicalBuilderSchemaBridge {

    private val skeletonRules by lazy {
        LegacyMappingTable.all().associateBy { it.kind to it.legacyType }
    }

    private val genericFieldTypes = setOf(
        NodeConfigValueType.STRING,
        NodeConfigValueType.INTEGER,
        NodeConfigValueType.BOOLEAN,
        NodeConfigValueType.ENUM,
        NodeConfigValueType.URL,
    )

    fun forAction(type: ActionType): CanonicalBuilderSchemaBinding? = when (type) {
        ActionType.SYSTEM_OPEN_SETTINGS -> CanonicalBuilderSchemaBinding(
            PilotOpenFamily.openSettingsSchema(),
        )

        ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
        ActionType.SYSTEM_MEDIA_NEXT,
        ActionType.SYSTEM_MEDIA_PREVIOUS,
        ActionType.SYSTEM_MEDIA_STOP,
        ActionType.SYSTEM_MEDIA_FAST_FORWARD,
        ActionType.SYSTEM_MEDIA_REWIND,
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH -> CanonicalBuilderSchemaBinding(
            FamilyPhase18MediaNavigation.mediaSchema(),
            legacyKeys = mapOf("sessionPackage" to "package"),
        )

        ActionType.SYSTEM_GO_HOME,
        ActionType.SYSTEM_OPEN_RECENTS,
        ActionType.SYSTEM_OPEN_NOTIFICATIONS,
        ActionType.SYSTEM_OPEN_QUICK_SETTINGS,
        ActionType.SYSTEM_OPEN_APP_DRAWER,
        ActionType.SYSTEM_EXPAND_STATUS_BAR,
        ActionType.SYSTEM_COLLAPSE_STATUS_BAR,
        ActionType.SYSTEM_STATUS_BAR_TOGGLE -> CanonicalBuilderSchemaBinding(
            FamilyPhase18MediaNavigation.navigationSchema(),
        )

        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_MOBILE_DATA,
        ActionType.SYSTEM_HOTSPOT,
        ActionType.SYSTEM_NFC,
        ActionType.SYSTEM_AIRPLANE_MODE,
        ActionType.SYSTEM_DATA_SAVER,
        ActionType.SYSTEM_DATA_ROAMING,
        ActionType.SYSTEM_WIFI_SCANNING -> CanonicalBuilderSchemaBinding(
            FamilyPhase19Connectivity.enableSchema(),
        )

        ActionType.SYSTEM_RINGER_MODE -> CanonicalBuilderSchemaBinding(
            FamilyPhase20DisplaySound.ringerModeSchema(),
        )
        ActionType.SYSTEM_BRIGHTNESS -> CanonicalBuilderSchemaBinding(
            catalogBindingForAction(type).schema,
        )
        ActionType.SYSTEM_SEND_SMS -> CanonicalBuilderSchemaBinding(
            FamilyPhase22Communication.smsSchema(),
        )
        ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> CanonicalBuilderSchemaBinding(
            FamilyPhase23PowerSensors.batterySaverThresholdSchema(),
            legacyKeys = mapOf("threshold" to "percent"),
        )
        ActionType.SYSTEM_HTTP_REQUEST -> CanonicalBuilderSchemaBinding(
            FamilyPhase25AdvancedExternal.httpSchema(),
        )
        else -> null
    }

    /**
     * Complete canonical contracts replace handwritten field UIs when every
     * legacy field is representable by the generic renderer. Specialized
     * pickers remain for package/secret/JSON/coordinate/time/date contracts
     * and for explicit ADVANCED product surfaces.
     */
    fun editingBindingForAction(type: ActionType): CanonicalBuilderSchemaBinding? {
        when (type) {
            ActionType.SYSTEM_OPEN_SETTINGS,
            ActionType.SYSTEM_RINGER_MODE,
            ActionType.SYSTEM_BRIGHTNESS,
            ActionType.SYSTEM_SEND_SMS,
            ActionType.SYSTEM_BATTERY_SAVER_THRESHOLD -> return forAction(type)
            // This action needs live modem/SIM capabilities and must never
            // expose its storage-only mask/schema/subscription fields.
            ActionType.SYSTEM_NETWORK_MODE -> return null
            else -> Unit
        }

        if (AutomationOptionCatalog.tierFor(type) == OptionTier.ADVANCED) return null
        val definition = AutomationNodeCatalog.definitionFor(type)
        if (definition.configuration.fields.any { it.valueType !in genericFieldTypes }) {
            return null
        }
        return catalogBindingForAction(type)
    }

    fun forTrigger(type: TriggerType): CanonicalBuilderSchemaBinding? = when (type) {
        TriggerType.TIME -> CanonicalBuilderSchemaBinding(
            FamilyPhase24TimeLocation.scheduleSchema(),
        )
        else -> null
    }

    fun editingBindingForTrigger(type: TriggerType): CanonicalBuilderSchemaBinding? {
        if (AutomationOptionCatalog.tierFor(type) == OptionTier.ADVANCED) return null
        val definition = AutomationNodeCatalog.definitionFor(type)
        if (definition.configuration.fields.any { it.valueType !in genericFieldTypes }) {
            return null
        }
        return catalogBindingForTrigger(type)
    }

    /** Shared typed filter controls for specialized and advanced trigger editors. */
    fun temporalFiltersBindingForTrigger(type: TriggerType): CanonicalBuilderSchemaBinding? {
        val full = catalogBindingForTrigger(type)
        val fields = full.schema.fields
            .filter { it.id.value in temporalFilterFieldIds }
            .map { field ->
                field.copy(
                    minimum = if (field.id.value in temporalDurationFieldIds) 0L else field.minimum,
                    maximum = if (field.id.value in temporalDurationFieldIds) {
                        604_800_000L
                    } else {
                        field.maximum
                    },
                    type = temporalFieldTypes.getValue(field.id.value),
                )
            }
            .toMutableList()
        val fieldIds = fields.mapTo(linkedSetOf()) { it.id.value }
        temporalFieldTypes.forEach { (id, fieldType) ->
            if (id !in fieldIds && id in supportedTemporalFields(type)) {
                fields += NodeSchemaField(
                    id = CanonicalFieldId(id),
                    type = fieldType,
                    minimum = temporalMinimum(id),
                    maximum = temporalMaximum(id, type),
                )
            }
        }
        if (fields.isEmpty()) return null
        return full.copy(schema = full.schema.copy(fields = fields))
    }

    private val temporalDurationFieldIds = setOf(
        "debounceMs",
        "rateLimitWindowMs",
        "minIntervalMs",
        "cooldownMs",
        "stableForMs",
    )

    private val temporalFilterFieldIds = setOf(
        "debounceMs",
        "rateLimitCount",
        "rateLimitWindowMs",
        "minIntervalMs",
        "cooldownMs",
        "stableForMs",
        "hysteresis",
    )

    private val temporalFieldTypes = mapOf(
        "debounceMs" to NodeFieldType.DURATION_MS,
        "rateLimitCount" to NodeFieldType.INTEGER,
        "rateLimitWindowMs" to NodeFieldType.DURATION_MS,
        "minIntervalMs" to NodeFieldType.DURATION_MS,
        "cooldownMs" to NodeFieldType.DURATION_MS,
        "stableForMs" to NodeFieldType.DURATION_MS,
        "hysteresis" to NodeFieldType.DECIMAL,
    )

    private fun supportedTemporalFields(type: TriggerType): Set<String> = when (type) {
        TriggerType.VOLUME_CHANGED -> setOf("debounceMs", "stableForMs", "hysteresis")
        TriggerType.BATTERY,
        TriggerType.BRIGHTNESS_LEVEL,
        TriggerType.WIFI_SIGNAL_STRENGTH,
        TriggerType.CELL_SIGNAL_STRENGTH,
        TriggerType.BATTERY_TEMPERATURE -> setOf("stableForMs", "hysteresis")
        else -> emptySet()
    }

    private fun temporalMinimum(id: String): Long? = when (id) {
        "rateLimitCount" -> 1L
        else -> 0L
    }

    private fun temporalMaximum(id: String, type: TriggerType): Long? = when (id) {
        "rateLimitCount" -> 1_000L
        "debounceMs", "rateLimitWindowMs", "minIntervalMs", "cooldownMs", "stableForMs" -> 604_800_000L
        "hysteresis" -> when (type) {
            TriggerType.BATTERY, TriggerType.VOLUME_CHANGED -> 100L
            TriggerType.BRIGHTNESS_LEVEL -> 255L
            TriggerType.WIFI_SIGNAL_STRENGTH, TriggerType.CELL_SIGNAL_STRENGTH -> 4L
            TriggerType.BATTERY_TEMPERATURE -> 100L
            else -> null
        }
        else -> null
    }

    private fun catalogBindingForAction(type: ActionType): CanonicalBuilderSchemaBinding {
        val definition = AutomationNodeCatalog.definitionFor(type)
        val node = canonicalSkeleton(
            type.name,
            LegacyNodeKind.ACTION,
            definition.configuration.fields.mapNotNull { field ->
                field.defaultValue?.let { LegacyConfigEntry(field.key, it) }
            },
        )
        return CanonicalBuilderSchemaBinding(
            LegacyCatalogCanonicalContractNormalizer.normalize(
                definition = definition,
                node = node,
                config = emptyList(),
                kind = NodeSchemaKind.ACTION,
            ).schema,
        )
    }

    private fun catalogBindingForTrigger(type: TriggerType): CanonicalBuilderSchemaBinding {
        val definition = AutomationNodeCatalog.definitionFor(type)
        val node = canonicalSkeleton(
            type.name,
            LegacyNodeKind.TRIGGER,
            definition.configuration.fields.mapNotNull { field ->
                field.defaultValue?.let { LegacyConfigEntry(field.key, it) }
            },
        )
        return CanonicalBuilderSchemaBinding(
            LegacyCatalogCanonicalContractNormalizer.normalize(
                definition = definition,
                node = node,
                config = emptyList(),
                kind = NodeSchemaKind.TRIGGER,
            ).schema,
        )
    }

    private fun canonicalSkeleton(
        legacyType: String,
        kind: LegacyNodeKind,
        config: List<LegacyConfigEntry>,
    ) = skeletonRules[kind to legacyType]
        ?.canonicalize(LegacyNodeInput(legacyType, kind, config))
        ?: error("T12 builder schema bridge has no reviewed mapping for $kind/$legacyType")
}
