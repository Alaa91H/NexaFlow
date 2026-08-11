package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.LocationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Settings > Location section. The interval is persisted in
 * [LocationPreferences]; the periodic worker picks up the change on its next
 * run (and re-aligns its own schedule), so no cross-module coupling is needed.
 */
@HiltViewModel
class LocationSettingsViewModel @Inject constructor(
    private val locationPreferences: LocationPreferences
) : ViewModel() {

    val checkIntervalMinutes: StateFlow<Int> = locationPreferences.checkIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationPreferences.MANUAL)

    fun setCheckIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            locationPreferences.setCheckIntervalMinutes(minutes)
        }
    }
}
