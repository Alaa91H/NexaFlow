package com.nexaflow.data.repository

import com.nexaflow.domain.models.AutomationHealthAnalyzer
import com.nexaflow.domain.models.AutomationHealthReport
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HealthRepository
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Read-only health projection over the automation catalogue and execution
 * history. No derived state is persisted, so retention remains controlled by
 * the existing execution-history storage policy.
 */
class HealthRepositoryImpl @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val historyRepository: HistoryRepository
) : HealthRepository {

    override suspend fun getHealthReport(automationId: String): AutomationHealthReport =
        AutomationHealthAnalyzer.analyze(
            automationId = automationId,
            records = historyRepository.getExecutionHistory().first()
        )

    override fun getHealthReports(): Flow<List<AutomationHealthReport>> =
        combine(
            automationRepository.getAutomations(),
            historyRepository.getExecutionHistory()
        ) { automations, records ->
            automations.map { automation ->
                AutomationHealthAnalyzer.analyze(automation.id, records)
            }
        }
}
