package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.Automation
import kotlinx.coroutines.flow.Flow

interface AutomationRepository {
    fun getAutomations(): Flow<List<Automation>>
    suspend fun getAutomationById(id: String): Automation?
    suspend fun saveAutomation(automation: Automation)
    suspend fun deleteAutomation(automation: Automation)
    suspend fun updateAutomationStatus(id: String, enabled: Boolean)
}
