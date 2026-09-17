package com.nexaflow.app

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.repositories.AutomationRepository
import com.nexaflow.domain.security.ExternalAccessPolicy

/** Parsing, repository authorization and confirmation are shared by the real activity and tests. */
internal class DeepLinkDispatcher(private val repository: AutomationRepository) {
    suspend fun open(uri: android.net.Uri?, review: (Automation) -> Unit,
        confirm: (Automation, RunTaskDeepLink) -> Unit) {
        val link = runCatching { parseRunTaskDeepLink(uri) }.getOrNull() ?: return
        val automation = repository.getAutomationById(link.automationId) ?: return
        review(automation)
        if (ExternalAccessPolicy.authorized(automation.deepLinkToken, link.token)) confirm(automation, link)
    }

    suspend fun runConfirmed(link: RunTaskDeepLink, execute: suspend (Automation) -> Unit) {
        val current = repository.getAutomationById(link.automationId) ?: return
        if (ExternalAccessPolicy.authorized(current.deepLinkToken, link.token)) execute(current)
    }
}
