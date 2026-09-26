package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    fun getAutomations(): Flow<List<Automation>>
    suspend fun getAutomationById(id: String): Automation?
    suspend fun saveAutomation(automation: Automation)

    /**
     * Optimistic-concurrency boundary for API/agent updates.
     *
     * Production repositories override this with one storage transaction.
     * The default keeps lightweight test implementations source-compatible.
     */
    suspend fun saveAutomationIfRevisionMatches(
        automation: Automation,
        expectedRevision: Long
    ): Boolean {
        val current = getAutomationById(automation.id) ?: return false
        if (current.updatedAt != expectedRevision) return false
        saveAutomation(automation)
        return true
    }

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
