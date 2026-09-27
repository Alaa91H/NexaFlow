package com.nexaflow.core.execution

import com.nexaflow.domain.models.ExecutionRecord

/**
 * Fan-out for run lifecycle observations.
 *
 * The engine invokes the listener for every admitted run start and every
 * persisted history record; listener failures never affect execution. Host
 * layers bridge this to user-visible surfaces (e.g. the agent event
 * stream) without the engine depending on them.
 */
interface AutomationRunListener {
    suspend fun onTriggered(automationId: String, runId: String)

    suspend fun onRecord(record: ExecutionRecord)

    companion object {
        val NO_OP = object : AutomationRunListener {
            override suspend fun onTriggered(automationId: String, runId: String) = Unit
            override suspend fun onRecord(record: ExecutionRecord) = Unit
        }
    }
}
