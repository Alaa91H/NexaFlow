package com.nexaflow.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PluginManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginDiscoveryRegistry: PluginDiscoveryRegistry
) : ViewModel() {

    private val _catalog = MutableStateFlow(PluginCatalogUiState())
    val catalog: StateFlow<PluginCatalogUiState> = _catalog

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /**
     * Re-discovers Locale declarations and checks the curated catalog against
     * package presence. This makes an app installed from Play move to the
     * installed section after the next lifecycle refresh without trusting a
     * remote availability feed.
     */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val next = runCatching {
                val snapshot = pluginDiscoveryRegistry.refresh()
                val installedPackages = withContext(Dispatchers.IO) {
                    PluginCatalog.recommended
                        .mapNotNull { definition ->
                            runCatching {
                                context.packageManager.getApplicationInfo(definition.packageName, 0)
                            }.getOrNull()?.let { appInfo ->
                                definition.packageName to appInfo.enabled
                            }
                        }
                        .toMap()
                }
                PluginCatalog.organize(installedPackages, snapshot.descriptors)
            }.getOrElse { _catalog.value }
            _catalog.value = next
            _refreshing.value = false
        }
    }
}
