package com.nexaflow.feature.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val automationRepository: AutomationRepository
) : ViewModel() {

    private val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val profiles = profileRepository.getProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<Automation>> = automationRepository.getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The mode being viewed (null while loading or after deletion). */
    val profile: StateFlow<Profile?> = profiles
        .map { list -> list.find { it.id == profileId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Routines that belong to this mode, kept in automation insertion order. */
    val memberRoutines: StateFlow<List<Automation>> = combine(profiles, automations) { profiles, automations ->
        val profile = profiles.find { it.id == profileId } ?: return@combine emptyList()
        automations.filter { it.id in profile.automationIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The whole list of routines (used by the add-to-mode picker). */
    val allAutomations: StateFlow<List<Automation>> = automations

    /** Toggles the mode itself; flipping it also toggles every member routine. */
    fun toggleMode(active: Boolean) {
        viewModelScope.launch {
            profileRepository.setProfileActive(profileId, active)
            profile.value?.automationIds?.forEach { automationId ->
                automationRepository.updateAutomationStatus(automationId, active)
            }
        }
    }

    /** Toggles a single member routine straight from the mode page. */
    fun toggleRoutine(automation: Automation, enabled: Boolean) {
        viewModelScope.launch {
            automationRepository.updateAutomationStatus(automation.id, enabled)
        }
    }

    /** Sets the full membership list of the mode. */
    fun setMemberRoutines(automationIds: List<String>) {
        viewModelScope.launch {
            profileRepository.updateProfileAutomations(profileId, automationIds)
        }
    }

    fun deleteMode() {
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(profileId) ?: return@launch
            profileRepository.deleteProfile(profile)
        }
    }
}
