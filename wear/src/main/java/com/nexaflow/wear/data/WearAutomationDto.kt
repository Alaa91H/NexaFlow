package com.nexaflow.wear.data

import kotlinx.serialization.Serializable

/**
 * Serializable data transfer object for an automation as shown on the watch.
 *
 * Fields are a strict subset of [com.nexaflow.domain.models.Automation] and
 * [com.nexaflow.domain.models.ExecutionRecord]: only what the watch UI needs
 * is transmitted over the Wearable Data Layer to keep payloads small.
 *
 * Serialized as JSON by the phone-side [WearSyncManager] and deserialized by
 * the watch-side [WearSyncRepository]. Both sides must keep the field set and
 * default values in sync.
 */
@Serializable
data class WearAutomationDto(
    val id: String,
    val name: String,
    /** Material icon name string (from [com.nexaflow.domain.models.Automation.icon]). */
    val icon: String,
    /** ARGB color as a Long (compatible with Compose Color). */
    val iconColor: Long,
    val enabled: Boolean,
    /** Epoch-millis timestamp of the most recent execution, null when never run. */
    val lastRunAt: Long? = null,
    /** Whether the most recent execution succeeded; null when never run. */
    val lastRunSuccess: Boolean? = null,
    /** Short summary of the most recent execution outcome; null when never run. */
    val lastRunMessage: String? = null,
)
