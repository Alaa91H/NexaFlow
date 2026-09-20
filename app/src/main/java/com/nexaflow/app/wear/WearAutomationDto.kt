package com.nexaflow.app.wear

import kotlinx.serialization.Serializable

/**
 * Phone-side mirror of the watch's [com.nexaflow.wear.data.WearAutomationDto].
 *
 * Both sides must maintain identical field names and types so the JSON
 * produced by [WearSyncManager] can be deserialized by the watch app.
 * New optional fields (with default values) may be added without breaking
 * older watch builds.
 */
@Serializable
data class WearAutomationDto(
    val id: String,
    val name: String,
    /** Material icon name string (from [com.nexaflow.domain.models.Automation.icon]). */
    val icon: String,
    /** ARGB color as a Long (compatible with Compose Color on the watch). */
    val iconColor: Long,
    val enabled: Boolean,
    /** Epoch-millis timestamp of the most recent execution; null when never run. */
    val lastRunAt: Long? = null,
    /** Whether the most recent execution succeeded; null when never run. */
    val lastRunSuccess: Boolean? = null,
    /** Short summary of the most recent execution outcome; null when never run. */
    val lastRunMessage: String? = null,
)
