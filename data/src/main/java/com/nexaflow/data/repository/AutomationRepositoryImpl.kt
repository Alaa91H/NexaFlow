package com.nexaflow.data.repository

import com.nexaflow.core.database.AutomationDao
import com.nexaflow.data.mapper.toDomain
import com.nexaflow.data.mapper.toEntity
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AutomationRepositoryImpl @Inject constructor(
    private val automationDao: AutomationDao
) : AutomationRepository {

    override fun getAutomations(): Flow<List<Automation>> {
        return automationDao.getAllAutomations().map {
            it.map { entity -> entity.toDomain() }
        }
    }

    override suspend fun getAutomationById(id: String): Automation? {
        return automationDao.getAutomationById(id)?.toDomain()
    }

    override suspend fun saveAutomation(automation: Automation) {
        automationDao.insertAutomation(automation.toEntity())
    }

    override suspend fun saveAutomationIfRevisionMatches(
        automation: Automation,
        expectedRevision: Long
    ): Boolean = automationDao.compareAndSetAutomation(
        automation = automation.toEntity(),
        expectedRevision = expectedRevision
    )

    override suspend fun saveAutomationsAtomically(automations: List<Automation>) {
        if (automations.isEmpty()) return
        automationDao.insertAutomations(automations.map { it.toEntity() })
    }

    override suspend fun deleteAutomation(automation: Automation) {
        automationDao.deleteAutomation(automation.toEntity())
    }

    override suspend fun deleteAutomationIfRevisionMatches(
        automationId: String,
        expectedRevision: Long
    ): Boolean = automationDao.deleteAutomationIfRevisionMatches(
        id = automationId,
        expectedRevision = expectedRevision
    ) == 1

    override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
        automationDao.updateAutomationStatus(id, enabled)
    }
}
