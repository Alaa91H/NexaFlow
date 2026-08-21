package com.nexaflow.feature.builder

import com.nexaflow.domain.models.ActionType

/**
 * Stable order for the routines category in the builder's category browser.
 *
 * The builder deliberately has no "common" or pre-search promotion API.
 * Options are discovered exclusively through local search or the category
 * browser rendered below it; this keeps one selectable source of truth per
 * step and prevents a duplicate top list from returning.
 */
internal object AutomationOptionCatalog {
    internal val recurringActionOrder = listOf(
        ActionType.SYSTEM_MEDIA_PLAY_PAUSE,
        ActionType.SYSTEM_MEDIA_PLAY_FROM_SEARCH,
        ActionType.SYSTEM_STREAM_VOLUME,
        ActionType.SYSTEM_DND,
        ActionType.SYSTEM_RINGER_MODE,
        ActionType.SYSTEM_WIFI,
        ActionType.SYSTEM_BLUETOOTH,
        ActionType.SYSTEM_LOCATION,
        ActionType.APPLICATION_LAUNCH_APP,
        ActionType.SYSTEM_OPEN_APP,
        ActionType.SYSTEM_SET_ALARM,
        ActionType.SYSTEM_SET_TIMER,
        ActionType.SYSTEM_SEND_NOTIFICATION,
        ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
        ActionType.SYSTEM_OPEN_PLAY_UPDATES,
        ActionType.SYSTEM_OPEN_GALAXY_STORE,
        ActionType.SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS
    )
}
