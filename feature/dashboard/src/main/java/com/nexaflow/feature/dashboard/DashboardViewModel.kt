package com.nexaflow.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val automationsFlow = automationRepository.getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<Automation>> = automationsFlow

    val profiles: StateFlow<List<Profile>> = profileRepository.getProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = automationsFlow
        .map { list -> list.count { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Toggles a single routine on/off straight from the home screen. */
    fun toggleAutomation(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateAutomationStatus(automation.id, enabled)
        }
    }

    /** Toggles a mode; switching it on/off also flips every routine it contains. */
    fun toggleProfile(profile: Profile, active: Boolean) {
        viewModelScope.launch {
            profileRepository.setProfileActive(profile.id, active)
            profile.automationIds.forEach { automationId ->
                automationRepository.updateAutomationStatus(automationId, active)
            }
        }
    }
}
