package com.nexaflow.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Profile
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val automationRepository: AutomationRepository
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = profileRepository.getProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<Automation>> = automationRepository.getAutomations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProfile(name: String, description: String, icon: String, color: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val profile = Profile(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                description = description.trim(),
                icon = icon,
                color = color,
                active = false,
                automationIds = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            profileRepository.saveProfile(profile)
        }
    }

    fun setProfileAutomations(profileId: String, automationIds: List<String>) {
        viewModelScope.launch {
            profileRepository.updateProfileAutomations(profileId, automationIds)
        }
    }

    fun toggleProfile(profile: Profile, active: Boolean) {
        viewModelScope.launch {
            profileRepository.setProfileActive(profile.id, active)
            profile.automationIds.forEach { automationId ->
                automationRepository.updateAutomationStatus(automationId, active)
            }
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            if (profile.active) {
                profile.automationIds.forEach { automationId ->
                    automationRepository.updateAutomationStatus(automationId, false)
                }
            }
            profileRepository.deleteProfile(profile)
        }
    }
}
