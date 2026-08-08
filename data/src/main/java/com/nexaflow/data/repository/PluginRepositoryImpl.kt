package com.nexaflow.data.repository

import android.content.Context
import android.content.Intent
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.repositories.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Discovers Locale-compatible plugins through the package manager: any app
 * with an exported receiver for the FIRE_SETTING broadcast (exactly what
 * Tasker / MacroDroid / Automate expose as "Locale plugins").
 *
 * The app manifest already declares QUERY_ALL_PACKAGES, so receivers of every
 * installed app are visible (no per-package `<queries>` needed).
 */
class PluginRepositoryImpl @Inject constructor(
    private val context: Context
) : PluginRepository {

    override suspend fun discoverPlugins(): List<PluginInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(LocaleContract.ACTION_FIRE_SETTING)
        runCatching {
            pm.queryBroadcastReceivers(intent, 0)
                .mapNotNull { resolveInfo ->
                    val info = resolveInfo.activityInfo ?: return@mapNotNull null
                    PluginInfo(
                        packageName = info.packageName,
                        receiverClass = info.name,
                        label = runCatching {
                            resolveInfo.loadLabel(pm)?.toString() ?: info.packageName
                        }.getOrDefault(info.packageName)
                    )
                }
                // One receiver per plugin — the picker lists plugins, not actions.
                .distinctBy { it.receiverClass }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }
}
