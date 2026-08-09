package com.nexaflow.core.execution.compat

import com.nexaflow.domain.models.TriggerType

/**
 * A source of automation events. Existing monitors (battery, connectivity,
 * location, ...) and the time scheduler are adapters over system signals; this
 * interface gives them a uniform lifecycle so the event layer stays decoupled
 * from monitor internals. The canonical mapping from a trigger to its source is
 * [TriggerSource.forTrigger] — the "entry node" resolution for a workflow.
 */
interface EventSource {
    /** Stable identifier of the source (matches [TriggerSource.sourceId]). */
    val sourceId: String

    /** Human-readable description (shown in the capability center). */
    val description: String

    /** Registers the source (receivers, listeners, alarms). Idempotent. */
    fun start()

    /** Unregisters the source. Idempotent. */
    fun stop()
}

/** Canonical source ids per trigger type — the entry-node mapping for workflows. */
enum class TriggerSource(val sourceId: String) {
    TIME("time"),
    BATTERY("battery"),
    APPLICATION("application"),
    DEVICE("device"),
    CONNECTIVITY("connectivity"),
    LOCATION("location"),
    SMS("sms"),
    BLUETOOTH_DEVICE("bluetooth"),
    RINGER_MODE("ringer"),
    NOTIFICATION("notification"),
    CALENDAR("calendar"),
    SENSOR("sensor"),
    WEBHOOK("webhook");

    companion object {
        fun forTrigger(type: TriggerType): TriggerSource = when (type) {
            TriggerType.TIME -> TIME
            TriggerType.BATTERY -> BATTERY
            TriggerType.APPLICATION -> APPLICATION
            TriggerType.DEVICE -> DEVICE
            TriggerType.CONNECTIVITY -> CONNECTIVITY
            TriggerType.LOCATION -> LOCATION
            TriggerType.SMS -> SMS
            TriggerType.BLUETOOTH_DEVICE -> BLUETOOTH_DEVICE
            TriggerType.RINGER_MODE -> RINGER_MODE
            TriggerType.NOTIFICATION -> NOTIFICATION
            TriggerType.CALENDAR -> CALENDAR
            TriggerType.SENSOR -> SENSOR
            TriggerType.WEBHOOK -> WEBHOOK
        }
    }
}
