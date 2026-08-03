package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfiles(): Flow<List<Profile>>
    suspend fun getProfileById(id: String): Profile?
    suspend fun saveProfile(profile: Profile)
    suspend fun deleteProfile(profile: Profile)
    suspend fun setProfileActive(id: String, active: Boolean)
    suspend fun updateProfileAutomations(id: String, automationIds: List<String>)
}
