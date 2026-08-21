package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.UpdateCheckFrequency
import com.nexaflow.core.datastore.UpdatePreferences
import com.nexaflow.core.datastore.UpdateSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings-only boundary for automatic update checks. */
@HiltViewModel
class UpdateSettingsViewModel @Inject constructor(
    private val updatePreferences: UpdatePreferences
) : ViewModel() {

    val settings: StateFlow<UpdateSettings> = updatePreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateSettings())

    fun setAutomaticChecksEnabled(enabled: Boolean) {
        viewModelScope.launch { updatePreferences.setAutomaticChecksEnabled(enabled) }
    }

    fun setFrequency(frequency: UpdateCheckFrequency) {
        viewModelScope.launch { updatePreferences.setFrequency(frequency) }
    }
}
