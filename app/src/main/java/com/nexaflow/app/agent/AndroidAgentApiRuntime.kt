package com.nexaflow.app.agent

import com.nexaflow.core.agentapi.AgentApiAuditEventV1
import com.nexaflow.core.agentapi.AgentApiBackendAvailabilityV1
import com.nexaflow.core.agentapi.AgentApiCapabilitiesV1
import com.nexaflow.core.agentapi.AgentApiCapabilityV1
import com.nexaflow.core.agentapi.AgentApiPrivilegeV1
import com.nexaflow.core.agentapi.AgentApiRunContext
import com.nexaflow.core.agentapi.AgentApiRunReservation
import com.nexaflow.core.agentapi.AgentApiRunReservationResult
import com.nexaflow.core.agentapi.AgentApiRuntime
import com.nexaflow.core.automationcontrol.AutomationAuditEvent
import com.nexaflow.core.automationcontrol.AutomationAuditSink
import com.nexaflow.core.database.AgentIdempotencyEntity
import com.nexaflow.core.database.AgentPlatformDao
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.core.execution.capability.CapabilityStateStore
import com.nexaflow.core.execution.capability.PrivilegeStateStore
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.repositories.HistoryRepository
import java.security.MessageDigest
import java.util.Base64
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
    private val auditSink: AutomationAuditSink,
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

    override suspend fun onEnabled(automation: Automation) {
        executionEngine.runWithConditionGate(automation)
    }

    override suspend fun onDisabled(automation: Automation) {
        executionEngine.runDisableCleanup(automation)
    }

    override suspend fun prepareForDeletion(automation: Automation): Boolean =
        executionEngine.prepareForDeletion(automation)

    override suspend fun reserveRun(
        request: AgentApiRunReservation
    ): AgentApiRunReservationResult {
        val now = System.currentTimeMillis()
        agentPlatformDao.pruneExpiredIdempotency(now)

        val keyHash = sha256(request.idempotencyKey)
        val fingerprint = sha256(
            "RUN\n${request.automationId}\n${request.revision}"
        )
        val reservation = AgentIdempotencyEntity(
            actorId = request.actorId,
            keyHash = keyHash,
            requestFingerprint = fingerprint,
            operation = "RUN",
            automationId = request.automationId,
            resultRevision = request.revision,
            createdAt = now,
            expiresAt = now + RUN_IDEMPOTENCY_RETENTION_MS
        )
        if (agentPlatformDao.reserveIdempotency(reservation) != -1L) {
            return AgentApiRunReservationResult.Acquired
        }

        val existing = agentPlatformDao.getIdempotency(request.actorId, keyHash)
            ?: return AgentApiRunReservationResult.Conflict
        return if (
            existing.operation == "RUN" &&
            existing.automationId == request.automationId &&
            existing.resultRevision == request.revision &&
            existing.requestFingerprint == fingerprint
        ) {
            AgentApiRunReservationResult.Replay
        } else {
            AgentApiRunReservationResult.Conflict
        }
    }

    override suspend fun run(
        automation: Automation,
        request: AgentApiRunContext
    ): ExecutionRecord {
        val startedAt = System.currentTimeMillis()
        auditSink.record(
            AutomationAuditEvent(
                eventType = "TASK_RUN_REQUESTED",
                outcome = "REQUESTED",
                actorId = request.actorId,
                agentId = request.agentId,
                automationId = automation.id,
                requestId = request.requestId,
                transport = "LOCAL_REST",
                createdAt = startedAt
            )
        )
        return try {
            val record = executionEngine.forceRun(automation)
            auditSink.record(
                AutomationAuditEvent(
                    eventType = "TASK_RUN_COMPLETED",
                    outcome = if (record.success) "SUCCESS" else "FAILED",
                    actorId = request.actorId,
                    agentId = request.agentId,
                    automationId = automation.id,
                    requestId = request.requestId,
                    transport = "LOCAL_REST",
                    details = mapOf(
                        "executionId" to record.id,
                        "channel" to (record.channel ?: "NONE")
                    ),
                    createdAt = System.currentTimeMillis()
                )
            )
            record
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            auditSink.record(
                AutomationAuditEvent(
                    eventType = "TASK_RUN_CANCELLED",
                    outcome = "CANCELLED",
                    actorId = request.actorId,
                    agentId = request.agentId,
                    automationId = automation.id,
                    requestId = request.requestId,
                    transport = "LOCAL_REST",
                    createdAt = System.currentTimeMillis()
                )
            )
            throw cancelled
        } catch (failure: Exception) {
            auditSink.record(
                AutomationAuditEvent(
                    eventType = "TASK_RUN_FAILED",
                    outcome = "FAILED",
                    actorId = request.actorId,
                    agentId = request.agentId,
                    automationId = automation.id,
                    requestId = request.requestId,
                    transport = "LOCAL_REST",
                    details = mapOf(
                        "failureType" to failure::class.java.simpleName.take(128)
                    ),
                    createdAt = System.currentTimeMillis()
                )
            )
            throw failure
        }
    }

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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
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
    private companion object {
        const val RUN_IDEMPOTENCY_RETENTION_MS =
            7L * 24L * 60L * 60L * 1000L
    }
}
