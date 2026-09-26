package com.nexaflow.core.automationcontrol

import com.nexaflow.core.automationcontrol.api.AgentActionDraftV1
import com.nexaflow.core.automationcontrol.api.AgentConstraintDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTaskDraftV1
import com.nexaflow.core.automationcontrol.api.AgentTriggerDraftV1
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object AutomationMutationFingerprint {

    private val json = Json {
        encodeDefaults = true
    }

    fun draft(
        kind: AutomationMutationKind,
        automationId: String?,
        draft: AgentTaskDraftV1
    ): String {
        val canonical = draft.copy(
            triggers = draft.triggers.map(AgentTriggerDraftV1::canonical),
            actions = draft.actions.map(AgentActionDraftV1::canonical),
            constraints = draft.constraints.map(AgentConstraintDraftV1::canonical),
            exitActions = draft.exitActions.map(AgentActionDraftV1::canonical)
        )
        return hash(
            buildString {
                append(kind.name)
                append('|')
                append(automationId.orEmpty())
                append('|')
                append(json.encodeToString(canonical))
            }
        )
    }

    fun state(
        kind: AutomationMutationKind,
        automationId: String,
        enabled: Boolean? = null
    ): String = hash(
        buildString {
            append(kind.name)
            append('|')
            append(automationId)
            if (enabled != null) {
                append('|')
                append(enabled)
            }
        }
    )

    private fun AgentTriggerDraftV1.canonical(): AgentTriggerDraftV1 =
        copy(config = config.toSortedMap())

    private fun AgentActionDraftV1.canonical(): AgentActionDraftV1 =
        copy(
            config = config.toSortedMap(),
            endBehavior = endBehavior?.let {
                it.copy(config = it.config.toSortedMap())
            }
        )

    private fun AgentConstraintDraftV1.canonical(): AgentConstraintDraftV1 =
        copy(config = config.toSortedMap())

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
