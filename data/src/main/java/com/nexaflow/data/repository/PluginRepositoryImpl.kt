package com.nexaflow.data.repository

import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import com.nexaflow.core.pluginsdk.PluginType
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.repositories.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Legacy picker facade over the protocol-aware registry. A plug-in is listed
 * only when the public Locale setting Activity/receiver pair is discoverable
 * and valid under Android package-visibility rules.
 */
class PluginRepositoryImpl @Inject constructor(
    private val registry: PluginDiscoveryRegistry
) : PluginRepository {

    /**
     * Preserves the legacy picker contract while delegating discovery to the
     * shared protocol-aware registry. Package lifecycle events invalidate this
     * cache; discovery itself is still demand-driven and never periodic.
     */

    override suspend fun discoverPlugins(): List<PluginInfo> = withContext(Dispatchers.IO) {
        registry.refresh().descriptors
            .asSequence()
            .filter { it.type == PluginType.SETTING }
            .filter { it.compatibility == PluginCompatibilityStatus.COMPATIBLE }
            .mapNotNull { descriptor ->
                val receiver = descriptor.receiver ?: return@mapNotNull null
                PluginInfo(
                    packageName = descriptor.packageName,
                    receiverClass = receiver.className,
                    label = descriptor.displayName,
                    editActivityClass = descriptor.editActivity?.className.orEmpty()
                )
            }
            .distinctBy { it.packageName to it.receiverClass }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
