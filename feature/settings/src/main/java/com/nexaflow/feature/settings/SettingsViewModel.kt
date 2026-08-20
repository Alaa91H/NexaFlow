package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.data.backup.BackupManager
import com.nexaflow.data.backup.ImportResult
import com.nexaflow.core.pluginsdk.PluginCompatibilityStatus
import com.nexaflow.core.pluginsdk.PluginDiscoveryRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PluginHealthState(
    val compatible: Int = 0,
    val unavailable: Int = 0,
    val partial: Int = 0,
    val refreshedAtMs: Long = 0L,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val pluginDiscoveryRegistry: PluginDiscoveryRegistry
) : ViewModel() {

    private val _importResult = MutableSharedFlow<ImportResult>()
    val importResult: SharedFlow<ImportResult> = _importResult.asSharedFlow()

    private val _pluginHealth = MutableStateFlow(PluginHealthState())
    val pluginHealth: StateFlow<PluginHealthState> = _pluginHealth.asStateFlow()

    init {
        refreshPluginHealth()
    }

    /** Explicit UI-driven refresh; registry performs no periodic package scan. */
    fun refreshPluginHealth() {
        viewModelScope.launch {
            _pluginHealth.value = _pluginHealth.value.copy(isRefreshing = true)
            val snapshot = runCatching { pluginDiscoveryRegistry.refresh() }.getOrNull()
            if (snapshot == null) {
                _pluginHealth.value = _pluginHealth.value.copy(isRefreshing = false)
                return@launch
            }
            _pluginHealth.value = PluginHealthState(
                compatible = snapshot.descriptors.count { it.compatibility == PluginCompatibilityStatus.COMPATIBLE },
                partial = snapshot.descriptors.count { it.compatibility == PluginCompatibilityStatus.PARTIALLY_COMPATIBLE },
                unavailable = snapshot.descriptors.count { it.compatibility !in setOf(
                    PluginCompatibilityStatus.COMPATIBLE,
                    PluginCompatibilityStatus.PARTIALLY_COMPATIBLE
                ) },
                refreshedAtMs = snapshot.refreshedAtMs,
                isRefreshing = false
            )
        }
    }

    /** Returns the pretty-printed JSON backup, or null on failure. */
    fun exportBackup(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val json = runCatching {
                backupManager.toJson(backupManager.export())
            }.getOrNull()
            onResult(json)
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            _importResult.emit(backupManager.import(json))
        }
    }
}
