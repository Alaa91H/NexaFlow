package com.nexaflow.domain.models

/**
 * A local starting point for a routine. Templates are not persisted until the
 * user reviews and saves the resulting draft in the builder.
 */
data class RoutineTemplate(
    val id: String,
    val triggers: List<Trigger>,
    val actions: List<Action>
)

/**
 * Small, device-agnostic templates for common daily automations. Capability
 * filtering is still performed by the UI before a template is offered.
 */
object RoutineTemplateCatalog {
    val all: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            id = SLEEP,
            triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "22:00"))),
            actions = listOf(Action(ActionType.SYSTEM_DND, mapOf("enabled" to "true")))
        ),
        RoutineTemplate(
            id = LOW_BATTERY,
            triggers = listOf(
                Trigger(
                    TriggerType.BATTERY,
                    mapOf("direction" to "BELOW", "below" to "20", "chargerType" to "ANY")
                )
            ),
            actions = listOf(Action(ActionType.SYSTEM_POWER_SAVER, mapOf("enabled" to "true")))
        ),
        RoutineTemplate(
            id = CHARGING,
            triggers = listOf(Trigger(TriggerType.CHARGER, mapOf("event" to "CONNECTED"))),
            actions = listOf(Action(ActionType.SYSTEM_BLUETOOTH, mapOf("enabled" to "true")))
        )
    )

    fun find(id: String?): RoutineTemplate? = all.firstOrNull { it.id == id }

    const val SLEEP = "sleep"
    const val LOW_BATTERY = "low_battery"
    const val CHARGING = "charging"
}
