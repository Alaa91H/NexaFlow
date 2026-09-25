package com.nexaflow.core.engine

import com.nexaflow.domain.models.TriggerType

/**
 * Pure matcher for momentary device events owned by [DeviceStateMonitor28].
 *
 * Event payload is evaluated before a trigger index becomes CURRENT_EVENT
 * evidence. This prevents ALL from treating an unrelated event of the same
 * broad TriggerType as proof for a filtered trigger.
 */
internal object DeviceOneShotTriggerMatcher {

    fun matches(
        type: TriggerType,
        config: Map<String, String>,
        textValue: String? = null,
        numericValue: Long? = null,
        flagValue: Boolean? = null,
    ): Boolean = when (type) {
        TriggerType.CLIPBOARD_CHANGED ->
            containsFilter(config["contains"], textValue)

        TriggerType.TIMEZONE_CHANGED ->
            exactOptional(config["zone"], textValue)

        TriggerType.NFC_TAG_SCANNED ->
            containsFilter(config["contains"], textValue)

        TriggerType.SCREEN_TIMEOUT_CHANGED -> {
            val configuredSeconds = config["seconds"]?.trim()?.takeIf { it.isNotEmpty() }
                ?.toLongOrNull()
            configuredSeconds == null || numericValue == configuredSeconds
        }

        TriggerType.ALARM_SET_CHANGED -> {
            val event = config["event"]?.trim()?.uppercase().orEmpty()
            when {
                flagValue == null -> false
                event.isEmpty() -> true
                event == "SET" -> flagValue
                event == "CLEARED" -> !flagValue
                else -> false
            }
        }

        TriggerType.BOOT_COMPLETED -> true

        else -> false
    }

    private fun containsFilter(expected: String?, actual: String?): Boolean {
        val needle = expected?.trim().orEmpty()
        if (needle.isEmpty()) return true
        val value = actual ?: return false
        return value.contains(needle, ignoreCase = true)
    }

    private fun exactOptional(expected: String?, actual: String?): Boolean {
        val wanted = expected?.trim().orEmpty()
        if (wanted.isEmpty()) return true
        return actual?.equals(wanted, ignoreCase = true) == true
    }
}
