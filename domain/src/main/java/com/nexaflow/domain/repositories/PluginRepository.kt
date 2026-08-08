package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.PluginInfo

/**
 * Discovers external plugins installed on the device. Plugins are any apps
 * declaring an exported receiver for the Locale FIRE_SETTING broadcast — the
 * same contract Tasker, MacroDroid and Automate use.
 */
interface PluginRepository {

    /** All installed plugins, sorted by label. Empty when none are installed. */
    suspend fun discoverPlugins(): List<PluginInfo>
}
