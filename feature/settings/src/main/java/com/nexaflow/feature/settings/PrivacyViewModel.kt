package com.nexaflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.PrivacyPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val privacyPreferences: PrivacyPreferences
) : ViewModel() {

    /** Whether the user opted into anonymous crash/ANR reporting. */
    val crashReportingEnabled: StateFlow<Boolean> =
        privacyPreferences.settings
            .map { it.crashReportingEnabled }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false
            )

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            privacyPreferences.setCrashReportingEnabled(enabled)
        }
    }
}
