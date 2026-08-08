package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.PluginInfo
import com.nexaflow.domain.repositories.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginManagerViewModel @Inject constructor(
    private val pluginRepository: PluginRepository
) : ViewModel() {

    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Re-discovers installed plugins from the package manager. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _plugins.value = runCatching { pluginRepository.discoverPlugins() }
                .getOrDefault(_plugins.value)
            _refreshing.value = false
        }
    }
}
