package com.nexaflow.domain.repositories

import com.nexaflow.domain.models.AutomationHealthReport
import kotlinx.coroutines.flow.Flow

/** Read-only execution-health view derived from the existing history stream. */
interface HealthRepository {
    suspend fun getHealthReport(automationId: String): AutomationHealthReport

    fun getHealthReports(): Flow<List<AutomationHealthReport>>
}
