package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType

/**
 * Webhook trigger matching, kept pure so it is unit-testable without a live
 * socket. [WebhookServer] feeds the parsed request line into [matches].
 *
 * Config keys: `path` (default "/"), `method` (POST/GET/ANY, default ANY),
 * `token` (optional shared secret — must equal the request token/header).
 */
object WebhookTriggerMatcher {

    fun matches(config: Map<String, String>, method: String, path: String, token: String?): Boolean {
        val wantPath = config["path"]?.takeIf { it.isNotBlank() } ?: "/"
        val wantMethod = (config["method"] ?: "ANY").uppercase()
        val wantToken = config["token"].orEmpty()
        val pathMatch = path == wantPath
        val methodMatch = wantMethod == "ANY" || wantMethod == method.uppercase()
        val tokenMatch = wantToken.isEmpty() || wantToken == token
        return pathMatch && methodMatch && tokenMatch
    }

    /** Automations (enabled) with at least one WEBHOOK trigger. */
    fun webhookAutomations(automations: List<Automation>): List<Automation> =
        automations.filter { automation ->
            automation.enabled && automation.triggers.any { it.type == TriggerType.WEBHOOK }
        }
}
