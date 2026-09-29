package com.nexaflow.feature.builder

import com.nexaflow.domain.canonical.FamilyPhase18MediaNavigation
import com.nexaflow.domain.canonical.FamilyPhase19Connectivity
import com.nexaflow.domain.canonical.FamilyPhase20DisplaySound
import com.nexaflow.domain.canonical.FamilyPhase22Communication
import com.nexaflow.domain.canonical.FamilyPhase23PowerSensors
import com.nexaflow.domain.canonical.FamilyPhase24TimeLocation
import com.nexaflow.domain.canonical.FamilyPhase25AdvancedExternal
import com.nexaflow.domain.canonical.NodeSchema
import com.nexaflow.domain.canonical.PilotOpenFamily
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType

/**
 * Product bridge from the append-only V1/V2 enum surface to the canonical
 * schemas already implemented by T17-T25.
 *
 * This bridge contains no rendering logic. It exists only while the persisted
 * legacy enum names remain readable; the schema remains the source of truth
 * for field type, bounds, defaults, visibility and validation.
 */
internal data class CanonicalBuilderSchemaBinding(
    val schema: NodeSchema,
    /** canonical field id -> legacy config key */
    val legacyKeys: Map<String, String> = emptyMap(),
)

internal object CanonicalBuilderSchemaBridge {

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
            FamilyPhase20DisplaySound.brightnessSchema(),
            legacyKeys = mapOf("level" to "value"),
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

    fun forTrigger(type: TriggerType): CanonicalBuilderSchemaBinding? = when (type) {
        TriggerType.TIME -> CanonicalBuilderSchemaBinding(
            FamilyPhase24TimeLocation.scheduleSchema(),
        )
        else -> null
    }
}
