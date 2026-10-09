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
    private val automationDao: AutomationDao,
    private val clearTemporalHistory: suspend (String) -> Unit = {}
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
        automationDao.upsertDefinitionWithRevision(automation.toEntity())
        if (!automation.enabled) clearTemporalHistory(automation.id)
    }

    override suspend fun saveAutomationIfRevisionMatches(
        automation: Automation,
        expectedRevision: Long
    ): Boolean {
        val saved = automationDao.compareAndSetAutomation(
            automation = automation.toEntity(),
            expectedRevision = expectedRevision
        )
        if (saved && !automation.enabled) clearTemporalHistory(automation.id)
        return saved
    }

    override suspend fun saveAutomationsAtomically(automations: List<Automation>) {
        if (automations.isEmpty()) return
        automationDao.upsertDefinitionsWithRevision(automations.map { it.toEntity() })
        automations.filterNot { it.enabled }.forEach { clearTemporalHistory(it.id) }
    }

    override suspend fun deleteAutomation(automation: Automation) {
        automationDao.deleteAutomation(automation.toEntity())
        clearTemporalHistory(automation.id)
    }

    override suspend fun deleteAutomationIfRevisionMatches(
        automationId: String,
        expectedRevision: Long
    ): Boolean {
        val deleted = automationDao.deleteAutomationIfRevisionMatches(
            id = automationId,
            expectedRevision = expectedRevision
        ) == 1
        if (deleted) clearTemporalHistory(automationId)
        return deleted
    }

    override suspend fun updateAutomationStatus(id: String, enabled: Boolean) {
        automationDao.updateAutomationStatus(id, enabled)
        if (!enabled) clearTemporalHistory(id)
    }
}
