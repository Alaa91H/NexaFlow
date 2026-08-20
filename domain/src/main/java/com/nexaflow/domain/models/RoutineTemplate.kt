package com.nexaflow.domain.models

/**
 * A local starting point for a routine. Templates are not persisted until the
 * user reviews and saves the resulting draft in the builder.
 */
data class RoutineTemplate(
    val id: String,
    val triggers: List<Trigger>,
    val actions: List<Action>,
    /** Optional recurring-maintenance metadata applied to the editable draft. */
    val maintenanceProfile: MaintenanceProfile? = null
)

/**
 * Small, device-agnostic templates for common daily automations. Capability
 * filtering is still performed by the UI before a template is offered. Each
 * template deliberately uses only existing Android integration paths; it does
 * not claim unattended Play Store updates, automatic storage deletion, or a
 * cloud synchronization service.
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
        ),
        RoutineTemplate(
            id = DAILY_APP_MAINTENANCE,
            triggers = listOf(
                Trigger(TriggerType.TIME, mapOf("time" to "02:00", "repeat" to "DAILY"))
            ),
            actions = listOf(
                // The executor records a typed unavailable/unsupported result
                // when Android exposes no official update-discovery route.
                Action(
                    ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS,
                    mapOf("chargingOnly" to "true", "wifiOnly" to "true")
                )
            ),
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.APP,
                window = MaintenanceWindow(
                    startTime = "02:00",
                    endTime = "05:00",
                    chargingRequired = true,
                    unmeteredWifiRequired = true
                )
            )
        ),
        RoutineTemplate(
            id = WEEKLY_STORAGE_CLEANUP,
            triggers = listOf(
                Trigger(TriggerType.TIME, mapOf("time" to "03:00", "repeat" to "WEEKENDS"))
            ),
            actions = listOf(
                // Android does not expose a general consumer-app cache-cleanup
                // API. This opens the official storage page for user review.
                Action(ActionType.SYSTEM_OPEN_SETTINGS, mapOf("page" to "STORAGE"))
            ),
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.STORAGE,
                window = MaintenanceWindow(
                    startTime = "03:00",
                    endTime = "05:00",
                    allowedDays = setOf(6, 7),
                    chargingRequired = true
                )
            )
        ),
        RoutineTemplate(
            id = NIGHTLY_AUTOMATION_SYNC,
            triggers = listOf(
                Trigger(TriggerType.TIME, mapOf("time" to "02:00", "repeat" to "DAILY"))
            ),
            actions = listOf(
                // Local completion notice only. NexaFlow has no configured cloud
                // sync service, so the template never claims to synchronize one.
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())
            ),
            maintenanceProfile = MaintenanceProfile(
                kind = MaintenanceKind.AUTOMATION,
                window = MaintenanceWindow(
                    startTime = "02:00",
                    endTime = "05:00",
                    screenOffRequired = true,
                    deviceIdleRequired = true
                )
            )
        )
    )

    fun find(id: String?): RoutineTemplate? = all.firstOrNull { it.id == id }

    const val SLEEP = "sleep"
    const val LOW_BATTERY = "low_battery"
    const val CHARGING = "charging"
    const val DAILY_APP_MAINTENANCE = "daily_app_maintenance"
    const val WEEKLY_STORAGE_CLEANUP = "weekly_storage_cleanup"
    const val NIGHTLY_AUTOMATION_SYNC = "nightly_automation_sync"
}
