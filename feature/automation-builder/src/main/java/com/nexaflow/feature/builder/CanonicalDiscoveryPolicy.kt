package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType

/**
 * Product discovery policy during the canonical cutover.
 *
 * Persisted legacy action kinds remain loadable/editable, but aliases that
 * have a complete canonical UX replacement are not offered when authoring a
 * new workflow. This is intentionally separate from persistence compatibility.
 */
internal object CanonicalDiscoveryPolicy {

    val legacySettingsAliases: Set<ActionType> = setOf(
        ActionType.SYSTEM_OPEN_ABOUT_PHONE,
        ActionType.SYSTEM_OPEN_ACCESSIBILITY_SETTINGS,
        ActionType.SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS,
        ActionType.SYSTEM_OPEN_APP_SETTINGS_LIST,
        ActionType.SYSTEM_OPEN_BATTERY_SETTINGS,
        ActionType.SYSTEM_OPEN_BLUETOOTH_SETTINGS,
        ActionType.SYSTEM_OPEN_CAST_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_SAVER_SETTINGS,
        ActionType.SYSTEM_OPEN_DATA_USAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_DATE_SETTINGS,
        ActionType.SYSTEM_OPEN_DEFAULT_APPS_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVELOPER_SETTINGS,
        ActionType.SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS,
        ActionType.SYSTEM_OPEN_DISPLAY_SETTINGS,
        ActionType.SYSTEM_OPEN_INPUT_METHOD_SETTINGS,
        ActionType.SYSTEM_OPEN_LOCATION_SETTINGS,
        ActionType.SYSTEM_OPEN_NETWORK_SETTINGS,
        ActionType.SYSTEM_OPEN_NFC_SETTINGS,
        ActionType.SYSTEM_OPEN_NOTIFICATION_SETTINGS,
        ActionType.SYSTEM_OPEN_PRINT_SETTINGS,
        ActionType.SYSTEM_OPEN_PRIVACY_SETTINGS,
        ActionType.SYSTEM_OPEN_SECURITY_SETTINGS,
        ActionType.SYSTEM_OPEN_SOUND_SETTINGS,
        ActionType.SYSTEM_OPEN_STORAGE_SETTINGS,
        ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS,
        ActionType.SYSTEM_OPEN_USAGE_ACCESS_SETTINGS,
        ActionType.SYSTEM_OPEN_VPN_SETTINGS,
        ActionType.SYSTEM_OPEN_WIFI_SETTINGS,
    )

    fun isDiscoverableForNewWorkflow(type: ActionType): Boolean =
        type !in legacySettingsAliases
}
