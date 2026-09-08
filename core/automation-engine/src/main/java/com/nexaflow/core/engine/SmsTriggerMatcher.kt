package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType

/**
 * SMS trigger matching shared by the legacy [SmsReceiver] (SMS_RECEIVED
 * broadcast) and the Android 17-safe [SmsConsentReceiver] (User Consent API).
 * Matching itself is pure and unit-testable; the companion-level [lastRunAt]
 * map is deliberately shared mutable state so both paths cannot double-fire
 * the same automation for one message.
 */
object SmsTriggerMatcher {

    /** Body-match modes for the SMS trigger. */
    const val MATCH_CONTAINS = "CONTAINS"
    const val MATCH_EXACT = "EXACT"
    const val MATCH_ANY = "ANY"

    /**
     * A trigger matches when its optional "from" filter is blank or contained
     * in the sender, and its body filter passes per the "matchMode" config:
     *  - CONTAINS (default, legacy): body must contain the text
     *  - EXACT: body must equal the text (ignoring surrounding whitespace)
     *  - ANY: body is ignored — every message from the sender matches
     *
     * All comparisons are case-insensitive. With no "matchMode" stored
     * (legacy configs) the behavior stays the historical CONTAINS semantics.
     */
    fun matches(config: Map<String, String>, sender: String, body: String): Boolean {
        val from = config["from"].orEmpty().trim()
        val mode = config["matchMode"].orEmpty().trim().uppercase().ifEmpty { MATCH_CONTAINS }
        val contains = config["contains"].orEmpty().trim()
        val fromMatch = from.isEmpty() || sender.contains(from, ignoreCase = true)
        val textMatch = when (mode) {
            MATCH_EXACT -> contains.isNotEmpty() && body.trim().equals(contains, ignoreCase = true)
            MATCH_ANY -> true
            else -> contains.isEmpty() || body.contains(contains, ignoreCase = true)
        }
        return fromMatch && textMatch
    }

    /** Automations (enabled) whose first SMS trigger matches the message. */
    fun matchingAutomations(
        automations: List<Automation>,
        sender: String,
        body: String
    ): List<Automation> = automations.filter { automation ->
        automation.enabled && automation.triggers.any { trigger ->
            trigger.type == TriggerType.SMS && matches(trigger.config, sender, body)
        }
    }

    /** The reply text of the first SMS trigger of the automation, or null. */
    fun replyOf(automation: Automation): String? =
        automation.triggers.firstOrNull { it.type == TriggerType.SMS }?.config?.get("reply")

    /**
     * Shared per-automation cooldown across BOTH SMS paths (legacy broadcast
     * + User Consent). Because the same message can reach both receivers on
     * some devices, a single map prevents the automation from double-firing.
     */
    val lastRunAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
}
