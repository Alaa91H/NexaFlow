package com.nexaflow.core.engine

import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.TriggerType
import java.security.MessageDigest

/**
 * SMS trigger matching shared by the legacy [SmsReceiver] (SMS_RECEIVED
 * broadcast) and the Android 17-safe [SmsConsentReceiver] (User Consent API).
 * Matching itself is pure and unit-testable. Cross-receiver duplicate
 * suppression is handled by the durable SmsDeliveryStore, so process death or
 * two overlapping receiver instances cannot double-fire one physical message.
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
            MATCH_CONTAINS -> contains.isEmpty() || body.contains(contains, ignoreCase = true)
            else -> false
        }
        return fromMatch && textMatch
    }

    /**
     * Saved trigger indices satisfied by this concrete SMS event.
     *
     * Returning every matching index matters for ALL: one physical message may
     * legitimately satisfy more than one SMS filter in the same automation.
     */
    fun matchingTriggerIndices(
        automation: Automation,
        sender: String,
        body: String
    ): Set<Int> = automation.triggers.mapIndexedNotNull { index, trigger ->
        index.takeIf {
            trigger.type == TriggerType.SMS && matches(trigger.config, sender, body)
        }
    }.toSet()

    /** Enabled automations with at least one SMS trigger matched by this event. */
    fun matchingAutomations(
        automations: List<Automation>,
        sender: String,
        body: String
    ): List<Automation> = automations.filter { automation ->
        automation.enabled && matchingTriggerIndices(automation, sender, body).isNotEmpty()
    }

    /** True only for a matching, enabled workflow with the explicit block action. */
    fun blocksIncoming(automation: Automation, sender: String, body: String): Boolean {
        val matching = matchingTriggerIndices(automation, sender, body)
        if (!automation.enabled || matching.isEmpty() || automation.constraints.isNotEmpty()) return false
        val expressionSatisfied = when (automation.triggerMatch) {
            com.nexaflow.domain.models.TriggerMatchMode.ANY -> true
            com.nexaflow.domain.models.TriggerMatchMode.ALL -> matching.size == automation.triggers.size
        }
        return expressionSatisfied && automation.actions.any { it.type == ActionType.SMS_BLOCK_INCOMING }
    }

    /** The reply text of the first SMS trigger of the automation, or null. */
    fun replyOf(automation: Automation): String? =
        automation.triggers.firstOrNull { it.type == TriggerType.SMS }?.config?.get("reply")

    /**
     * Privacy-safe identity for one physical SMS delivery.
     *
     * The service-center timestamp is part of the digest so two legitimate
     * identical texts sent later are different events. When Android omits the
     * timestamp, callers pass a short receive-time bucket instead.
     */
    fun deliveryFingerprint(sender: String, body: String, messageTimestamp: Long): String {
        val normalizedSender = sender.trim().lowercase()
        val normalizedBody = body.trim()
        val input = "$normalizedSender\u0000$normalizedBody\u0000$messageTimestamp"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
