package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.NotificationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationManagerViewModel @Inject constructor(
    private val preferences: NotificationPreferences
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = preferences.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationSettings()
        )

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setEnabled(value) }
    }

    fun setExecutionEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setExecutionEnabled(value) }
    }

    fun setRemindersEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setRemindersEnabled(value) }
    }

    fun setMonitoringEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setMonitoringEnabled(value) }
    }
}
