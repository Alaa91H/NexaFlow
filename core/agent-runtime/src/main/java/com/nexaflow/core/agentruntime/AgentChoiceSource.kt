package com.nexaflow.core.agentruntime

import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of the saved agent definitions for pickers.
 *
 * UI modules that must offer an agent (the automation builder's AGENT_RUN
 * editor, for example) depend on this contract instead of the persistence
 * layer, so the feature-to-persistence boundary stays intact while the data
 * layer binds the real repository through Hilt.
 */
interface AgentChoiceSource {
    /** Enabled agents only: a disabled agent can never run. */
    fun observeEnabled(): Flow<List<EnabledAgent>>
}

/** The minimum a picker needs; instructions and policy stay internal. */
data class EnabledAgent(
    val id: String,
    val name: String,
    val description: String = "",
    val modelId: String? = null,
)
