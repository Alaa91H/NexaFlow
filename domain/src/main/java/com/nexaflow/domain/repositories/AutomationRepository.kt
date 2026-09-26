package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    fun getAutomations(): Flow<List<Automation>>
    suspend fun getAutomationById(id: String): Automation?
    suspend fun saveAutomation(automation: Automation)

    /**
     * Persists a batch as one logical operation. Production repositories should
     * override this with a storage transaction; the default keeps lightweight
     * test/legacy implementations source-compatible.
     */
    suspend fun saveAutomationsAtomically(automations: List<Automation>) {
        automations.forEach { saveAutomation(it) }
    }

    suspend fun deleteAutomation(automation: Automation)
    suspend fun updateAutomationStatus(id: String, enabled: Boolean)
}
