package com.nexaflow.data.repository

import com.nexaflow.core.automationcontrol.AutomationAuditEvent
import com.nexaflow.core.automationcontrol.AutomationAuditSink
import com.nexaflow.core.database.AgentAuditEntity
import com.nexaflow.core.database.AgentPlatformDao
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RoomAutomationAuditSink(
    private val agentPlatformDao: AgentPlatformDao,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : AutomationAuditSink {

    override suspend fun record(event: AutomationAuditEvent) {
        require(event.actorId.matches(ACTOR_ID_PATTERN))
        requireOptionalBound(event.agentId)
        requireOptionalBound(event.automationId)
        requireOptionalBound(event.requestId)
        requireOptionalBound(event.transport)
        require(event.details.size <= MAX_DETAILS_ENTRIES)

        val detailsJson = event.details
            .takeIf { it.isNotEmpty() }
            ?.let { details ->
                buildJsonObject {
                    details.toSortedMap().forEach { (key, value) ->
                        require(key.length <= MAX_DETAIL_KEY_LENGTH)
                        require(value.length <= MAX_DETAIL_VALUE_LENGTH)
                        put(key, value)
                    }
                }.toString()
            }
        require(detailsJson == null || detailsJson.length <= MAX_DETAILS_JSON_LENGTH)

        agentPlatformDao.insertAudit(
            AgentAuditEntity(
                id = idGenerator(),
                eventType = event.eventType,
                outcome = event.outcome,
                actorId = event.actorId,
                agentId = event.agentId,
                automationId = event.automationId,
                requestId = event.requestId,
                transport = event.transport,
                detailsJson = detailsJson,
                createdAt = event.createdAt
            )
        )
    }

    private fun requireOptionalBound(value: String?) {
        require(value == null || value.length <= MAX_METADATA_VALUE_LENGTH)
    }

    private companion object {
        const val MAX_METADATA_VALUE_LENGTH = 256
        const val MAX_DETAILS_ENTRIES = 16
        const val MAX_DETAIL_KEY_LENGTH = 64
        const val MAX_DETAIL_VALUE_LENGTH = 256
        const val MAX_DETAILS_JSON_LENGTH = 2_048
        val ACTOR_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
    }
}
