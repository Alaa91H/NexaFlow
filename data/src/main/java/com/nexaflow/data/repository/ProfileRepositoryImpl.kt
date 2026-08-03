package com.nexaflow.data.repository

import com.nexaflow.core.database.ProfileDao
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.models.Profile
import com.nexaflow.domain.repositories.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun getProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getProfileById(id: String): Profile? {
        return profileDao.getProfileById(id)?.toDomain()
    }

    override suspend fun saveProfile(profile: Profile) {
        profileDao.insertProfile(profile.toEntity())
    }

    override suspend fun deleteProfile(profile: Profile) {
        profileDao.deleteProfile(profile.toEntity())
    }

    override suspend fun setProfileActive(id: String, active: Boolean) {
        profileDao.setProfileActive(id, active)
    }

    override suspend fun updateProfileAutomations(id: String, automationIds: List<String>) {
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        profileDao.updateProfileAutomations(id, gson.toJson(automationIds, type))
    }
}
