package com.nexaflow.app.agent

import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentapi.AgentApiBackendAvailabilityV1
import com.nexaflow.core.agentapi.AgentApiCapabilitiesV1
import com.nexaflow.core.agentapi.AgentApiCapabilityV1
import com.nexaflow.core.agentapi.AgentApiPrivilegeV1
import com.nexaflow.core.agentapi.AgentApiRunContext
import com.nexaflow.core.agentapi.AgentApiRuntime
import com.nexaflow.core.database.AgentPlatformDao
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.capability.PrivilegeStateStore
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

@Singleton
class AndroidAgentApiRuntime @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val historyRepository: HistoryRepository,
    private val agentPlatformDao: AgentPlatformDao,
    private val executionEngine: ExecutionEngine,
    private val capabilityStateStore: CapabilityStateStore,
    private val privilegeStateStore: PrivilegeStateStore
) : AgentApiRuntime {

    override suspend fun effectiveRevision(automationId: String): Long? {
        val automation = automationRepository.getAutomationById(automationId) ?: return null
        val metadata = agentPlatformDao.getAutomationMetadata(automationId) ?: return 1L
        return if (metadata.definitionUpdatedAt == automation.updatedAt) {
            metadata.revision
        } else {
            metadata.revision + 1L
        }
    }

    override suspend fun run(
        automation: Automation,
        request: AgentApiRunContext
    ): ExecutionRecord = executionEngine.forceRun(automation)

    override suspend fun latestHistory(limit: Int): List<ExecutionRecord> =
        historyRepository.getExecutionHistory().first().take(limit)

    override suspend fun latestAudit(limit: Int): List<AgentApiAuditEventV1> =
        agentPlatformDao.latestAudit(limit).map { entity ->
            AgentApiAuditEventV1(
                id = entity.id,
                eventType = entity.eventType,
                outcome = entity.outcome,
                actorId = entity.actorId,
                agentId = entity.agentId,
                automationId = entity.automationId,
                requestId = entity.requestId,
                transport = entity.transport,
                detailsJson = entity.detailsJson,
                createdAt = entity.createdAt
            )
        }

    override suspend fun capabilities(): AgentApiCapabilitiesV1 = coroutineScope {
        val capabilityDeferred = async { capabilityStateStore.freshSnapshot() }
        val privilegeDeferred = async { privilegeStateStore.freshSnapshot() }
        val capability = capabilityDeferred.await()
        val privilege = privilegeDeferred.await()

        AgentApiCapabilitiesV1(
            observedAtMillis = capability.observedAtMs,
            neverObserved = capability.neverObserved,
            capabilities = capability.reports.values
                .sortedBy { it.capability.name }
                .map { report ->
                    AgentApiCapabilityV1(
                        id = report.capability.name,
                        availability = report.availability.name,
                        reason = report.reason,
                        backends = report.backends.map { backend ->
                            AgentApiBackendAvailabilityV1(
                                backend = backend.backend.name,
                                availability = backend.availability.name,
                                reason = backend.reason
                            )
                        }
                    )
                },
            privilegeObservedAtMillis = privilege.observedAtMs,
            privileges = privilege.observations
                .sortedWith(compareBy({ it.surface.name }, { it.key }))
                .map { observation ->
                    AgentApiPrivilegeV1(
                        surface = observation.surface.name,
                        key = observation.key,
                        state = observation.state.name,
                        detailCode = observation.detailCode
                    )
                }
        )
    }
}
