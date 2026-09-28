package com.nexaflow.domain.canonical

import com.nexaflow.domain.capability.CapabilityId

/** Minimal immutable registry descriptors for T03 stable identity. */
data class CanonicalTargetDescriptor(val id: TargetId)
data class CanonicalOperationDescriptor(val id: OperationId)
data class CanonicalPredicateDescriptor(val id: PredicateId)
data class CanonicalCapabilityDescriptor(
    val id: CapabilityStableId,
    val legacyCapability: CapabilityId
)

/**
 * Stable identity registry shared by future compiler, schema and UI layers.
 *
 * T03 intentionally contains identity only. Data types, schemas, selection
 * semantics and runtime providers are added by later phases so identity cannot
 * accidentally become coupled to one implementation.
 */
class CanonicalIdentityRegistry private constructor(
    private val targetsById: Map<TargetId, CanonicalTargetDescriptor>,
    private val operationsById: Map<OperationId, CanonicalOperationDescriptor>,
    private val predicatesById: Map<PredicateId, CanonicalPredicateDescriptor>,
    private val capabilitiesById: Map<CapabilityStableId, CanonicalCapabilityDescriptor>,
) {
    fun target(id: TargetId): CanonicalTargetDescriptor? = targetsById[id]
    fun operation(id: OperationId): CanonicalOperationDescriptor? = operationsById[id]
    fun predicate(id: PredicateId): CanonicalPredicateDescriptor? = predicatesById[id]
    fun capability(id: CapabilityStableId): CanonicalCapabilityDescriptor? = capabilitiesById[id]

    fun requireTarget(id: TargetId): CanonicalTargetDescriptor =
        target(id) ?: error("Unknown canonical target: $id")

    fun requireOperation(id: OperationId): CanonicalOperationDescriptor =
        operation(id) ?: error("Unknown canonical operation: $id")

    fun requirePredicate(id: PredicateId): CanonicalPredicateDescriptor =
        predicate(id) ?: error("Unknown canonical predicate: $id")

    fun requireCapability(id: CapabilityStableId): CanonicalCapabilityDescriptor =
        capability(id) ?: error("Unknown canonical capability: $id")

    fun targets(): List<CanonicalTargetDescriptor> = targetsById.values.sortedBy { it.id.value }
    fun operations(): List<CanonicalOperationDescriptor> = operationsById.values.sortedBy { it.id.value }
    fun predicates(): List<CanonicalPredicateDescriptor> = predicatesById.values.sortedBy { it.id.value }
    fun capabilities(): List<CanonicalCapabilityDescriptor> = capabilitiesById.values.sortedBy { it.id.value }

    companion object {
        fun of(
            targets: List<CanonicalTargetDescriptor>,
            operations: List<CanonicalOperationDescriptor>,
            predicates: List<CanonicalPredicateDescriptor>,
            capabilities: List<CanonicalCapabilityDescriptor>,
        ): CanonicalIdentityRegistry {
            fun <K, V> uniqueBy(
                values: List<V>,
                label: String,
                key: (V) -> K,
            ): Map<K, V> {
                val result = values.associateBy(key)
                require(result.size == values.size) { "Duplicate $label stable identifier" }
                return result
            }

            return CanonicalIdentityRegistry(
                targetsById = uniqueBy(targets, "target") { it.id },
                operationsById = uniqueBy(operations, "operation") { it.id },
                predicatesById = uniqueBy(predicates, "predicate") { it.id },
                capabilitiesById = uniqueBy(capabilities, "capability") { it.id },
            )
        }

        fun default(): CanonicalIdentityRegistry = of(
            targets = CanonicalIdentityCatalog.targets,
            operations = CanonicalIdentityCatalog.operations,
            predicates = CanonicalIdentityCatalog.predicates,
            capabilities = CapabilityId.entries.map { capability ->
                CanonicalCapabilityDescriptor(capability.stableId, capability)
            },
        )
    }
}

/**
 * T01-reviewed canonical identities. Keeping these values in Kotlin makes them
 * compile-time/runtime contracts rather than documentation-only strings.
 */
object CanonicalIdentityCatalog {
    val targets: List<CanonicalTargetDescriptor> = listOf(
            "core.advanced.command",
            "core.application.camera",
            "core.application.contacts",
            "core.application.foreground",
            "core.application.lifecycle",
            "core.application.package",
            "core.application.package_settings",
            "core.application.picture_in_picture",
            "core.application.store",
            "core.application.store_updates",
            "core.audio.camera_shutter",
            "core.audio.dnd",
            "core.audio.headphone",
            "core.audio.notification_tone",
            "core.audio.ringer_mode",
            "core.audio.ringtone",
            "core.audio.sound_effects",
            "core.audio.volume",
            "core.audio.volume.music",
            "core.audio.volume.ring",
            "core.calendar.event",
            "core.communication.call",
            "core.communication.call.incoming",
            "core.communication.email",
            "core.communication.phone",
            "core.communication.sms",
            "core.communication.sms.incoming",
            "core.connectivity.airplane_mode",
            "core.connectivity.bluetooth",
            "core.connectivity.bluetooth_device",
            "core.connectivity.bluetooth_discoverability",
            "core.connectivity.cell_signal",
            "core.connectivity.data_roaming",
            "core.connectivity.data_saver",
            "core.connectivity.default_network",
            "core.connectivity.ethernet",
            "core.connectivity.hotspot",
            "core.connectivity.mobile_data",
            "core.connectivity.network_mode",
            "core.connectivity.nfc",
            "core.connectivity.nfc_tag",
            "core.connectivity.private_dns",
            "core.connectivity.vpn",
            "core.connectivity.wifi",
            "core.connectivity.wifi_network",
            "core.connectivity.wifi_scanning",
            "core.connectivity.wifi_signal",
            "core.connectivity.wifi_sleep_policy",
            "core.data.clipboard",
            "core.data.transform",
            "core.device.boot",
            "core.device.flashlight",
            "core.device.lifecycle",
            "core.device.lock",
            "core.device.lock_state",
            "core.device.power",
            "core.device.screenshot",
            "core.display.always_on_display",
            "core.display.animations",
            "core.display.auto_brightness",
            "core.display.auto_rotate",
            "core.display.brightness",
            "core.display.color_inversion",
            "core.display.dark_mode",
            "core.display.density",
            "core.display.extra_dim",
            "core.display.font_scale",
            "core.display.grayscale",
            "core.display.night_light",
            "core.display.orientation",
            "core.display.pointer_location",
            "core.display.screen",
            "core.display.screen_timeout",
            "core.display.screensaver",
            "core.display.screensaver_timeout",
            "core.display.show_taps",
            "core.display.stay_awake",
            "core.external.http",
            "core.external.url",
            "core.external.webhook",
            "core.flow.delay",
            "core.haptics.call_vibration",
            "core.haptics.feedback",
            "core.haptics.intensity",
            "core.haptics.vibration",
            "core.input.key",
            "core.input.paste",
            "core.input.pointer",
            "core.input.pointer_speed",
            "core.input.text",
            "core.location.geofence",
            "core.location.maps",
            "core.location.mode",
            "core.location.service",
            "core.media.active_session",
            "core.media.playback",
            "core.notification",
            "core.notification.alert",
            "core.notification.all",
            "core.notification.app",
            "core.notification.app_policy",
            "core.notification.battery_alert",
            "core.notification.charging",
            "core.notification.event",
            "core.notification.reminder",
            "core.notification.transient_message",
            "core.peripheral.hdmi",
            "core.peripheral.usb",
            "core.power.adaptive_battery",
            "core.power.battery",
            "core.power.battery_temperature",
            "core.power.charging",
            "core.power.charging_feedback",
            "core.power.charging_limit",
            "core.power.saver",
            "core.power.saver_threshold",
            "core.rom.customization",
            "core.rom.setting",
            "core.schedule.alarm",
            "core.schedule.clock",
            "core.schedule.timer",
            "core.sensor.reading",
            "core.storage.free_space",
            "core.system.auto_time",
            "core.system.auto_timezone",
            "core.system.navigation",
            "core.system.setting",
            "core.system.settings",
            "core.system.status_bar",
            "core.system.timezone",
            "core.system.ui",
            "core.wear.connection",
            "plugin.action",
            "plugin.event"
    ).map { CanonicalTargetDescriptor(TargetId(it)) }

    val operations: List<CanonicalOperationDescriptor> = listOf(
            "core.operation.batch_write",
            "core.operation.capture",
            "core.operation.clear",
            "core.operation.clear_data",
            "core.operation.compose_or_send",
            "core.operation.connect",
            "core.operation.create",
            "core.operation.date_time",
            "core.operation.dial",
            "core.operation.execute",
            "core.operation.force_stop",
            "core.operation.forget",
            "core.operation.generate_random",
            "core.operation.get_enabled",
            "core.operation.get_state",
            "core.operation.get_value",
            "core.operation.input",
            "core.operation.install",
            "core.operation.invoke",
            "core.operation.invoke_pattern",
            "core.operation.invoke_toggle",
            "core.operation.open",
            "core.operation.open_app_page",
            "core.operation.reboot",
            "core.operation.reject",
            "core.operation.restart",
            "core.operation.scan",
            "core.operation.schedule",
            "core.operation.search_and_play",
            "core.operation.send",
            "core.operation.set_blocked",
            "core.operation.set_configuration",
            "core.operation.set_enabled",
            "core.operation.set_state",
            "core.operation.set_value",
            "core.operation.show",
            "core.operation.shutdown",
            "core.operation.silence",
            "core.operation.soft_restart",
            "core.operation.swipe",
            "core.operation.tap",
            "core.operation.transform",
            "core.operation.uninstall",
            "core.operation.update_apps",
            "core.operation.wait",
            "core.operation.wake"
    ).map { CanonicalOperationDescriptor(OperationId(it)) }

    val predicates: List<CanonicalPredicateDescriptor> = listOf(
            "core.predicate.match_change_event",
            "core.predicate.match_event",
            "core.predicate.match_event_filter",
            "core.predicate.match_event_or_state",
            "core.predicate.match_reading",
            "core.predicate.match_schedule",
            "core.predicate.match_state",
            "core.predicate.match_state_or_event",
            "core.predicate.match_state_or_transition",
            "core.predicate.match_threshold",
            "core.predicate.match_threshold_with_filters",
            "core.predicate.match_transition"
    ).map { CanonicalPredicateDescriptor(PredicateId(it)) }
}
