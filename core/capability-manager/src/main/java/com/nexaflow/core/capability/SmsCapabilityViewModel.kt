package com.nexaflow.core.capability

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.core.datastore.SmsPreferences
import com.nexaflow.core.engine.SmsConsentManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Android 17 SMS awareness card in the Capability Center: shows
 * whether Play services (SMS User Consent) is available, exposes the opt-in
 * toggle, and lets the user arm the consent request immediately.
 */
@HiltViewModel
class SmsCapabilityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsPreferences: SmsPreferences,
    private val consentManager: SmsConsentManager
) : ViewModel() {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed

    val consentEnabled: StateFlow<Boolean> = smsPreferences.settings
        .map { it.userConsentEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun isConsentAvailable(): Boolean = consentManager.isAvailable()

    fun setConsentEnabled(value: Boolean) {
        viewModelScope.launch {
            smsPreferences.setUserConsentEnabled(value)
            if (value) {
                consentManager.startListening()
                _armed.value = true
            } else {
                _armed.value = false
            }
        }
    }

    /** Re-arms the consent request (e.g. after the user dismissed the dialog). */
    fun armNow() {
        _armed.value = consentManager.startListening()
    }
}
