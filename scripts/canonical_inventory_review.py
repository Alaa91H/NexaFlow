"""Curated T01 semantic review for every frozen legacy automation type.

This module is migration inventory, not runtime code. It intentionally records
one-to-one legacy meaning before any consolidation optimizer exists. Families
remain UX taxonomy; canonicalTarget/canonicalOperation describe the semantic
intent that later T03+ registries/compiler will encode as typed contracts.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Iterable


@dataclass(frozen=True)
class Review:
    canonicalTarget: str
    canonicalOperation: str
    selectionMode: str = "SINGLE"
    combinationMode: str = "N/A"
    capabilityRequirements: tuple[str, ...] = ()
    sideEffect: str = "NONE"
    idempotency: str = "N/A"
    retrySafety: str = "N/A"
    migrationNotes: str = ""
    reviewStatus: str = "REVIEWED"

    def as_dict(self) -> dict[str, object]:
        data = asdict(self)
        data["capabilityRequirements"] = list(self.capabilityRequirements)
        return data


def _put(
    out: dict[str, Review],
    names: Iterable[str],
    *,
    target: str,
    operation: str,
    selection: str = "SINGLE",
    combination: str = "N/A",
    caps: tuple[str, ...] = (),
    side: str = "NONE",
    idem: str = "N/A",
    retry: str = "N/A",
    notes: str = "",
) -> None:
    for name in names:
        if name in out:
            raise ValueError(f"duplicate semantic review for {name}")
        out[name] = Review(
            canonicalTarget=target,
            canonicalOperation=operation,
            selectionMode=selection,
            combinationMode=combination,
            capabilityRequirements=caps,
            sideEffect=side,
            idempotency=idem,
            retrySafety=retry,
            migrationNotes=notes,
        )


def trigger_reviews() -> dict[str, Review]:
    out: dict[str, Review] = {}

    _put(out, ["TIME"], target="core.schedule.clock", operation="MATCH_SCHEDULE")
    _put(out, ["CALENDAR"], target="core.calendar.event", operation="MATCH_EVENT")
    _put(out, ["TIMEZONE_CHANGED"], target="core.system.timezone", operation="MATCH_CHANGE_EVENT")
    _put(out, ["ALARM_SET_CHANGED"], target="core.schedule.alarm", operation="MATCH_CHANGE_EVENT")

    _put(
        out, ["BATTERY"], target="core.power.battery", operation="MATCH_THRESHOLD_WITH_FILTERS",
        combination="ALL", caps=("BATTERY_STATE",),
        notes="Level threshold is combined with charging-state and charger-type filters."
    )
    _put(out, ["CHARGER"], target="core.power.charging", operation="MATCH_STATE", caps=("BATTERY_STATE",))
    _put(out, ["POWER_SAVER"], target="core.power.saver", operation="MATCH_STATE")
    _put(out, ["BATTERY_TEMPERATURE"], target="core.power.battery_temperature", operation="MATCH_THRESHOLD", caps=("BATTERY_STATE",))

    _put(
        out, ["APPLICATION"], target="core.application.foreground", operation="MATCH_EVENT_OR_STATE",
        selection="MULTI", combination="ANY_OF", caps=("APP_FOREGROUND_OBSERVATION",),
        notes="Current schema supports package/packages compatibility; preserve multi-package filters."
    )
    _put(out, ["APP_INSTALLED"], target="core.application.lifecycle", operation="MATCH_EVENT", selection="MULTI", combination="ANY_OF", caps=("PACKAGE_EVENTS",))

    _put(out, ["DEVICE"], target="core.device.lifecycle", operation="MATCH_EVENT")
    _put(out, ["SENSOR"], target="core.sensor.reading", operation="MATCH_READING", caps=("SENSOR_HARDWARE",))
    _put(out, ["DARK_MODE"], target="core.display.dark_mode", operation="MATCH_STATE")
    _put(out, ["BRIGHTNESS_LEVEL"], target="core.display.brightness", operation="MATCH_THRESHOLD")
    _put(out, ["STORAGE_LOW"], target="core.storage.free_space", operation="MATCH_THRESHOLD")
    _put(out, ["AUTO_ROTATE"], target="core.display.auto_rotate", operation="MATCH_STATE")
    _put(out, ["DEVICE_LOCKED"], target="core.device.lock_state", operation="MATCH_STATE")
    _put(out, ["SCREEN_ROTATION_STATE"], target="core.display.orientation", operation="MATCH_STATE")
    _put(out, ["USB_CONNECTED"], target="core.peripheral.usb", operation="MATCH_STATE_OR_EVENT")
    _put(out, ["HDMI_CONNECTED"], target="core.peripheral.hdmi", operation="MATCH_STATE_OR_EVENT")
    _put(out, ["STAY_AWAKE_STATE"], target="core.display.stay_awake", operation="MATCH_STATE")
    _put(out, ["AUTO_BRIGHTNESS_STATE"], target="core.display.auto_brightness", operation="MATCH_STATE")
    _put(out, ["SCREEN_TIMEOUT_CHANGED"], target="core.display.screen_timeout", operation="MATCH_CHANGE_EVENT")
    _put(out, ["BOOT_COMPLETED"], target="core.device.boot", operation="MATCH_EVENT")
    _put(out, ["WEAR_EVENT"], target="core.wear.connection", operation="MATCH_STATE_OR_EVENT", caps=("WEAR_DATA_LAYER",))

    _put(out, ["CONNECTIVITY"], target="core.connectivity.default_network", operation="MATCH_STATE", notes="Legacy combined connectivity trigger; compatibility-only.")
    _put(out, ["WIFI_CONNECTED"], target="core.connectivity.wifi_network", operation="MATCH_STATE", caps=("WIFI_HARDWARE",))
    _put(out, ["MOBILE_DATA_CONNECTED"], target="core.connectivity.mobile_data", operation="MATCH_STATE", caps=("TELEPHONY",))
    _put(out, ["HOTSPOT"], target="core.connectivity.hotspot", operation="MATCH_STATE", caps=("WIFI_HARDWARE",))
    _put(out, ["BLUETOOTH_DEVICE"], target="core.connectivity.bluetooth_device", operation="MATCH_STATE_OR_EVENT", caps=("BLUETOOTH_HARDWARE",))
    _put(out, ["NETWORK_MODE"], target="core.connectivity.network_mode", operation="MATCH_STATE", caps=("TELEPHONY",))
    _put(out, ["AIRPLANE_MODE"], target="core.connectivity.airplane_mode", operation="MATCH_STATE")
    _put(out, ["BLUETOOTH_STATE"], target="core.connectivity.bluetooth", operation="MATCH_STATE", caps=("BLUETOOTH_HARDWARE",))
    _put(out, ["DATA_SAVER_STATE"], target="core.connectivity.data_saver", operation="MATCH_STATE")
    _put(out, ["WIFI_STATE"], target="core.connectivity.wifi", operation="MATCH_STATE", caps=("WIFI_HARDWARE",))
    _put(out, ["NFC_STATE"], target="core.connectivity.nfc", operation="MATCH_STATE", caps=("NFC_HARDWARE",))
    _put(out, ["WIFI_SIGNAL_STRENGTH"], target="core.connectivity.wifi_signal", operation="MATCH_THRESHOLD", caps=("WIFI_HARDWARE",))
    _put(out, ["CELL_SIGNAL_STRENGTH"], target="core.connectivity.cell_signal", operation="MATCH_THRESHOLD", caps=("TELEPHONY",))
    _put(out, ["ETHERNET_CONNECTED"], target="core.connectivity.ethernet", operation="MATCH_STATE")
    _put(out, ["VPN_CONNECTED"], target="core.connectivity.vpn", operation="MATCH_STATE")
    _put(out, ["DATA_ROAMING_STATE"], target="core.connectivity.data_roaming", operation="MATCH_STATE", caps=("TELEPHONY",))
    _put(out, ["NFC_TAG_SCANNED"], target="core.connectivity.nfc_tag", operation="MATCH_EVENT_FILTER", caps=("NFC_HARDWARE",))

    _put(out, ["LOCATION"], target="core.location.geofence", operation="MATCH_TRANSITION", caps=("LOCATION",))
    _put(out, ["LOCATION_STATE"], target="core.location.service", operation="MATCH_STATE", caps=("LOCATION",))

    _put(out, ["SMS"], target="core.communication.sms.incoming", operation="MATCH_EVENT_FILTER", caps=("SMS_RECEIVE",))
    _put(out, ["CALL_STATE"], target="core.communication.call", operation="MATCH_STATE_OR_TRANSITION", caps=("PHONE_STATE",))
    _put(out, ["INCOMING_CALL"], target="core.communication.call.incoming", operation="MATCH_EVENT_FILTER", caps=("PHONE_STATE",))

    _put(out, ["RINGER_MODE"], target="core.audio.ringer_mode", operation="MATCH_STATE")
    _put(out, ["HEADPHONE"], target="core.audio.headphone", operation="MATCH_STATE", caps=("AUDIO_DEVICE_STATE",))
    _put(out, ["VOLUME_CHANGED"], target="core.audio.volume", operation="MATCH_THRESHOLD")
    _put(out, ["DND_STATE"], target="core.audio.dnd", operation="MATCH_STATE")

    _put(out, ["MEDIA_PLAYING"], target="core.media.playback", operation="MATCH_STATE", caps=("AUDIO_PLAYBACK_STATE",))
    _put(
        out, ["NOTIFICATION"], target="core.notification.event", operation="MATCH_EVENT_FILTER",
        selection="MULTI", combination="ANY_OF", caps=("NOTIFICATION_LISTENER",)
    )
    _put(out, ["WEBHOOK"], target="core.external.webhook", operation="MATCH_EVENT_FILTER", caps=("LOCAL_WEBHOOK_SERVER",))
    _put(out, ["ROM_SETTING"], target="core.rom.setting", operation="MATCH_STATE", caps=("ROM_SETTING_READ",))
    _put(out, ["CLIPBOARD_CHANGED"], target="core.data.clipboard", operation="MATCH_EVENT_FILTER", caps=("CLIPBOARD_ACCESS",))
    _put(out, ["PLUGIN_EVENT"], target="plugin.event", operation="MATCH_EVENT_FILTER", caps=("PLUGIN_PROVIDER",))

    return out


def action_reviews() -> dict[str, Review]:
    out: dict[str, Review] = {}
    state = dict(side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    value = dict(side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    invoke = dict(side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    external = dict(side="EXTERNAL", idem="CONDITIONAL", retry="CONDITIONAL")

    # Display / screen state and values.
    _put(out, ["SYSTEM_BRIGHTNESS"], target="core.display.brightness", operation="SET_VALUE", caps=("DISPLAY_WRITE",), **value)
    _put(out, ["SYSTEM_SCREEN_ROTATION"], target="core.display.auto_rotate", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_SCREEN_TIMEOUT"], target="core.display.screen_timeout", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_STAY_AWAKE"], target="core.display.stay_awake", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_AUTO_BRIGHTNESS"], target="core.display.auto_brightness", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_DARK_MODE"], target="core.display.dark_mode", operation="SET_STATE", caps=("DISPLAY_WRITE",), **state)
    _put(out, ["SYSTEM_ANIMATIONS"], target="core.display.animations", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_WAKE_SCREEN"], target="core.display.screen", operation="WAKE", caps=("WAKE_LOCK",), **invoke)
    _put(out, ["SYSTEM_COLOR_INVERSION"], target="core.display.color_inversion", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_GRAYSCALE"], target="core.display.grayscale", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_EXTRA_DIM"], target="core.display.extra_dim", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_NIGHT_LIGHT"], target="core.display.night_light", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_FONT_SCALE"], target="core.display.font_scale", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_DISPLAY_DENSITY"], target="core.display.density", operation="SET_VALUE", caps=("PRIVILEGED_DISPLAY_WRITE",), **value)
    _put(out, ["SYSTEM_SCREENSAVER"], target="core.display.screensaver", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_ALWAYS_ON_DISPLAY"], target="core.display.always_on_display", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_SHOW_TAPS"], target="core.display.show_taps", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_POINTER_LOCATION"], target="core.display.pointer_location", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_SCREENSAVER_TIMEOUT"], target="core.display.screensaver_timeout", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_POINTER_SPEED"], target="core.input.pointer_speed", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_TOGGLE_PIP"], target="core.application.picture_in_picture", operation="INVOKE_TOGGLE", caps=("APP_CONTROL",), **invoke)

    # Audio and haptics.
    _put(out, ["SYSTEM_VOLUME"], target="core.audio.volume.music", operation="SET_VALUE", caps=("AUDIO_CONTROL",), **value)
    _put(out, ["SYSTEM_STREAM_VOLUME"], target="core.audio.volume", operation="SET_VALUE", caps=("AUDIO_CONTROL",), **value)
    _put(out, ["SYSTEM_RING_VOLUME"], target="core.audio.volume.ring", operation="SET_VALUE", caps=("AUDIO_CONTROL",), **value)
    _put(out, ["SYSTEM_DND"], target="core.audio.dnd", operation="SET_STATE", caps=("NOTIFICATION_POLICY_ACCESS",), **state)
    _put(out, ["SYSTEM_RINGER_MODE"], target="core.audio.ringer_mode", operation="SET_VALUE", caps=("AUDIO_CONTROL",), **value)
    _put(out, ["SYSTEM_SET_RINGTONE"], target="core.audio.ringtone", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_VIBRATE"], target="core.haptics.vibration", operation="INVOKE", caps=("VIBRATE",), **invoke)
    _put(out, ["SYSTEM_VIBRATE_PATTERN"], target="core.haptics.vibration", operation="INVOKE_PATTERN", caps=("VIBRATE",), **invoke)
    _put(out, ["SYSTEM_HAPTIC_FEEDBACK"], target="core.haptics.feedback", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_HAPTIC_INTENSITY"], target="core.haptics.intensity", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_SOUND_EFFECTS"], target="core.audio.sound_effects", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_CAMERA_SHUTTER_SOUND"], target="core.audio.camera_shutter", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_SET_NOTIFICATION_TONE"], target="core.audio.notification_tone", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_CALL_VIBRATION"], target="core.haptics.call_vibration", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)

    # Connectivity and radios.
    _put(out, ["SYSTEM_WIFI"], target="core.connectivity.wifi", operation="SET_STATE", caps=("WIFI_HARDWARE", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_BLUETOOTH"], target="core.connectivity.bluetooth", operation="SET_STATE", caps=("BLUETOOTH_HARDWARE", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_MOBILE_DATA"], target="core.connectivity.mobile_data", operation="SET_STATE", caps=("TELEPHONY", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_HOTSPOT"], target="core.connectivity.hotspot", operation="SET_STATE", caps=("WIFI_HARDWARE", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_NFC"], target="core.connectivity.nfc", operation="SET_STATE", caps=("NFC_HARDWARE", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_AIRPLANE_MODE"], target="core.connectivity.airplane_mode", operation="SET_STATE", caps=("ADAPTIVE_PRIVILEGED_WRITE",), **state)
    _put(out, ["SYSTEM_DATA_SAVER"], target="core.connectivity.data_saver", operation="SET_STATE", caps=("ADAPTIVE_PRIVILEGED_WRITE",), **state)
    _put(out, ["SYSTEM_DATA_ROAMING"], target="core.connectivity.data_roaming", operation="SET_STATE", caps=("TELEPHONY", "PRIVILEGED_SETTINGS_WRITE"), **state)
    _put(out, ["SYSTEM_NETWORK_MODE"], target="core.connectivity.network_mode", operation="SET_VALUE", caps=("TELEPHONY", "PRIVILEGED_TELEPHONY_WRITE"), **value)
    _put(out, ["SYSTEM_PRIVATE_DNS"], target="core.connectivity.private_dns", operation="SET_VALUE", caps=("PRIVILEGED_SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_WIFI_SLEEP_POLICY"], target="core.connectivity.wifi_sleep_policy", operation="SET_VALUE", caps=("SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_BLUETOOTH_DISCOVERABILITY"], target="core.connectivity.bluetooth_discoverability", operation="SET_VALUE", caps=("BLUETOOTH_HARDWARE",), **value)
    _put(out, ["SYSTEM_WIFI_SCANNING"], target="core.connectivity.wifi_scanning", operation="SET_STATE", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_WIFI_CONNECT"], target="core.connectivity.wifi_network", operation="CONNECT", caps=("WIFI_HARDWARE",), **external)
    _put(out, ["SYSTEM_WIFI_FORGET"], target="core.connectivity.wifi_network", operation="FORGET", caps=("WIFI_HARDWARE",), side="IRREVERSIBLE", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["SYSTEM_BLUETOOTH_SCAN"], target="core.connectivity.bluetooth", operation="SCAN", caps=("BLUETOOTH_HARDWARE",), **external)
    _put(out, ["SYSTEM_WIFI_SCAN_NOW"], target="core.connectivity.wifi", operation="SCAN", caps=("WIFI_HARDWARE",), **external)

    # Location.
    _put(out, ["SYSTEM_LOCATION"], target="core.location.service", operation="SET_STATE", caps=("LOCATION", "ADAPTIVE_PRIVILEGED_WRITE"), **state)
    _put(out, ["SYSTEM_LOCATION_MODE"], target="core.location.mode", operation="SET_VALUE", caps=("LOCATION", "PRIVILEGED_SETTINGS_WRITE"), **value)
    _put(out, ["SYSTEM_OPEN_MAPS"], target="core.location.maps", operation="OPEN", caps=("ANDROID_INTENT",), **external)

    # Media.
    for name, command in {
        "SYSTEM_MEDIA_PLAY_PAUSE": "PLAY_PAUSE",
        "SYSTEM_MEDIA_NEXT": "NEXT",
        "SYSTEM_MEDIA_PREVIOUS": "PREVIOUS",
        "SYSTEM_MEDIA_STOP": "STOP",
        "SYSTEM_MEDIA_FAST_FORWARD": "FAST_FORWARD",
        "SYSTEM_MEDIA_REWIND": "REWIND",
    }.items():
        _put(out, [name], target="core.media.active_session", operation="INVOKE", caps=("MEDIA_CONTROL",), notes=f"command={command}", **invoke)
    _put(out, ["SYSTEM_MEDIA_PLAY_FROM_SEARCH"], target="core.media.active_session", operation="SEARCH_AND_PLAY", caps=("MEDIA_CONTROL",), **invoke)

    # Notifications.
    _put(out, ["SYSTEM_SEND_NOTIFICATION"], target="core.notification", operation="SEND", caps=("POST_NOTIFICATIONS",), side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["SYSTEM_BLOCK_NOTIFICATION"], target="core.notification.app_policy", operation="SET_BLOCKED", selection="MULTI", combination="BATCH", caps=("NOTIFICATION_LISTENER",), **state)
    _put(out, ["SYSTEM_CLEAR_APP_NOTIFICATIONS"], target="core.notification.app", operation="CLEAR", selection="MULTI", combination="BATCH", caps=("NOTIFICATION_LISTENER",), side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    _put(out, ["SYSTEM_CLEAR_NOTIFICATIONS"], target="core.notification.all", operation="CLEAR", caps=("NOTIFICATION_LISTENER",), side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    _put(out, ["SYSTEM_SEND_REMINDER"], target="core.notification.reminder", operation="SCHEDULE", caps=("ALARMS", "POST_NOTIFICATIONS"), side="EXTERNAL", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["BATTERY_ALERTS"], target="core.notification.battery_alert", operation="SEND", caps=("POST_NOTIFICATIONS",), side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["BATTERY_CHARGING_NOTIFICATIONS"], target="core.notification.charging", operation="SEND", caps=("POST_NOTIFICATIONS",), side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")

    # Applications and package lifecycle.
    _put(out, ["SYSTEM_OPEN_APP"], target="core.application.package", operation="OPEN", selection="MULTI", combination="ORDERED", caps=("APP_LAUNCH",), notes="Legacy handler accepts comma-separated packages; preserve ordering during migration.", **external)
    _put(out, ["APPLICATION_LAUNCH_APP"], target="core.application.package", operation="OPEN", caps=("APP_LAUNCH",), **external)
    _put(out, ["APPLICATION_CLOSE_APP", "SYSTEM_FORCE_STOP_APP"], target="core.application.package", operation="FORCE_STOP", caps=("PRIVILEGED_PACKAGE_CONTROL",), side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    _put(out, ["SYSTEM_CLEAR_APP_DATA"], target="core.application.package", operation="CLEAR_DATA", caps=("PRIVILEGED_PACKAGE_CONTROL",), side="IRREVERSIBLE", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["SYSTEM_DISABLE_APP"], target="core.application.package", operation="SET_ENABLED", caps=("PRIVILEGED_PACKAGE_CONTROL",), notes="enabled=false", **state)
    _put(out, ["SYSTEM_ENABLE_APP"], target="core.application.package", operation="SET_ENABLED", caps=("PRIVILEGED_PACKAGE_CONTROL",), notes="enabled=true", **state)
    _put(out, ["APPLICATION_OPEN_APP_SETTINGS"], target="core.application.package_settings", operation="OPEN", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_INSTALL_APK"], target="core.application.package", operation="INSTALL", caps=("PACKAGE_INSTALL",), side="IRREVERSIBLE", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["SYSTEM_UNINSTALL_APP"], target="core.application.package", operation="UNINSTALL", caps=("PACKAGE_UNINSTALL",), side="IRREVERSIBLE", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["SYSTEM_UPDATE_GOOGLE_PLAY_APPS"], target="core.application.store_updates", operation="UPDATE_APPS", caps=("STORE_PROVIDER",), **external)
    _put(out, ["SYSTEM_OPEN_PLAY_UPDATES"], target="core.application.store_updates", operation="OPEN", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_OPEN_DEVICE_STORE"], target="core.application.store", operation="OPEN", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_OPEN_PLAY_STORE_APP"], target="core.application.store", operation="OPEN_APP_PAGE", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_OPEN_CAMERA"], target="core.application.camera", operation="OPEN", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_OPEN_CONTACTS"], target="core.application.contacts", operation="OPEN", caps=("ANDROID_INTENT",), **external)

    # Battery/power.
    _put(out, ["SYSTEM_POWER_SAVER"], target="core.power.saver", operation="SET_STATE", caps=("PRIVILEGED_SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_BATTERY_SAVER_THRESHOLD"], target="core.power.saver_threshold", operation="SET_VALUE", caps=("PRIVILEGED_SETTINGS_WRITE",), **value)
    _put(out, ["SYSTEM_CHARGING_LIMIT"], target="core.power.charging_limit", operation="SET_VALUE", caps=("PRIVILEGED_POWER_CONTROL",), **value)
    _put(out, ["SYSTEM_CHARGING_FEEDBACK"], target="core.power.charging_feedback", operation="SET_CONFIGURATION", caps=("SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_ADAPTIVE_BATTERY"], target="core.power.adaptive_battery", operation="SET_STATE", caps=("PRIVILEGED_SETTINGS_WRITE",), **state)

    # Scheduling and time.
    _put(out, ["SYSTEM_SET_ALARM"], target="core.schedule.alarm", operation="CREATE", caps=("ALARMS",), **external)
    _put(out, ["SYSTEM_SET_TIMER"], target="core.schedule.timer", operation="CREATE", caps=("ALARMS",), **external)
    _put(out, ["SYSTEM_AUTO_TIME"], target="core.system.auto_time", operation="SET_STATE", caps=("PRIVILEGED_SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_AUTO_TIMEZONE"], target="core.system.auto_timezone", operation="SET_STATE", caps=("PRIVILEGED_SETTINGS_WRITE",), **state)
    _put(out, ["SYSTEM_SET_TIMEZONE"], target="core.system.timezone", operation="SET_VALUE", caps=("PRIVILEGED_TIME_WRITE",), **value)

    # Network/external.
    _put(out, ["SYSTEM_OPEN_URL"], target="core.external.url", operation="OPEN", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_HTTP_REQUEST"], target="core.external.http", operation="SEND", caps=("INTERNET",), side="EXTERNAL", idem="CONDITIONAL", retry="CONDITIONAL", notes="Idempotency/retry safety depends on HTTP method and user retry policy.")
    _put(out, ["PLUGIN_FIRE"], target="plugin.action", operation="INVOKE", caps=("PLUGIN_PROVIDER",), side="EXTERNAL", idem="CONDITIONAL", retry="UNSAFE")

    # Data and clipboard/input.
    _put(out, ["SYSTEM_CLIPBOARD_SET"], target="core.data.clipboard", operation="SET_VALUE", caps=("CLIPBOARD_ACCESS",), **value)
    _put(out, ["SYSTEM_PASTE"], target="core.input.paste", operation="INVOKE", caps=("ACCESSIBILITY_OR_PRIVILEGED_INPUT",), **invoke)
    for name, category in {
        "DATA_TEXT": "TEXT",
        "DATA_ENCODING": "ENCODING",
        "DATA_HASH": "HASH",
        "DATA_MATH": "MATH",
        "DATA_JSON": "JSON",
        "DATA_ARRAY": "ARRAY",
    }.items():
        _put(out, [name], target="core.data.transform", operation="TRANSFORM", notes=f"category={category}", side="NONE", idem="IDEMPOTENT", retry="SAFE")
    _put(out, ["DATA_RANDOM"], target="core.data.transform", operation="GENERATE_RANDOM", side="NONE", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["DATA_DATE_TIME"], target="core.data.transform", operation="DATE_TIME", side="NONE", idem="CONDITIONAL", retry="CONDITIONAL", notes="NOW is time-dependent; deterministic date/time transforms are idempotent.")

    # Flow.
    _put(out, ["SYSTEM_WAIT"], target="core.flow.delay", operation="WAIT", side="NONE", idem="IDEMPOTENT", retry="SAFE")

    # ROM customization.
    _put(out, ["ROM_CUSTOM_SETTING"], target="core.rom.setting", operation="SET_VALUE", caps=("ROM_SETTING_WRITE",), side="REVERSIBLE", idem="CONDITIONAL", retry="CONDITIONAL")
    for name, section in {
        "ROM_QS_TILES": "QUICK_SETTINGS",
        "ROM_STATUS_BAR": "STATUS_BAR",
        "ROM_LOCKSCREEN": "LOCKSCREEN",
        "ROM_NAVIGATION": "NAVIGATION",
        "ROM_THEME": "THEME",
        "ROM_AMBIENT_AOD": "AMBIENT_AOD",
        "ROM_NOTIFICATIONS": "NOTIFICATIONS",
    }.items():
        _put(out, [name], target="core.rom.customization", operation="SET_CONFIGURATION", caps=("ROM_SETTING_WRITE",), notes=f"section={section}", side="REVERSIBLE", idem="CONDITIONAL", retry="CONDITIONAL")
    _put(out, ["ROM_BATCH"], target="core.rom.customization", operation="BATCH_WRITE", selection="MULTI", combination="BATCH", caps=("ROM_SETTING_WRITE",), side="REVERSIBLE", idem="CONDITIONAL", retry="CONDITIONAL")

    # Advanced execution/input.
    _put(out, ["ADVANCED_SHIZUKU"], target="core.advanced.command", operation="EXECUTE", caps=("SHIZUKU",), side="EXTERNAL", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["ADVANCED_ROOT"], target="core.advanced.command", operation="EXECUTE", caps=("ROOT",), side="EXTERNAL", idem="CONDITIONAL", retry="UNSAFE")
    _put(out, ["SYSTEM_SET_SETTING"], target="core.system.setting", operation="SET_VALUE", caps=("ADAPTIVE_PRIVILEGED_WRITE",), side="REVERSIBLE", idem="IDEMPOTENT", retry="SAFE")
    _put(out, ["SYSTEM_INPUT_TEXT"], target="core.input.text", operation="INPUT", caps=("ACCESSIBILITY_OR_PRIVILEGED_INPUT",), **invoke)
    _put(out, ["SYSTEM_KEY_EVENT"], target="core.input.key", operation="INPUT", caps=("ACCESSIBILITY_OR_PRIVILEGED_INPUT",), **invoke)
    _put(out, ["SYSTEM_INPUT_TAP"], target="core.input.pointer", operation="TAP", caps=("ACCESSIBILITY_OR_PRIVILEGED_INPUT",), **invoke)
    _put(out, ["SYSTEM_INPUT_SWIPE"], target="core.input.pointer", operation="SWIPE", caps=("ACCESSIBILITY_OR_PRIVILEGED_INPUT",), **invoke)

    # Device/system invocations.
    _put(out, ["SYSTEM_FLASHLIGHT"], target="core.device.flashlight", operation="SET_STATE", caps=("CAMERA_FLASH",), **state)
    _put(out, ["SYSTEM_LOCK_SCREEN"], target="core.device.lock", operation="INVOKE", caps=("DEVICE_LOCK_CONTROL",), **invoke)
    _put(out, ["SYSTEM_SCREENSHOT"], target="core.device.screenshot", operation="CAPTURE", caps=("SCREEN_CAPTURE",), side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["SYSTEM_REBOOT"], target="core.device.power", operation="REBOOT", caps=("PRIVILEGED_POWER_CONTROL",), **invoke)
    _put(out, ["SYSTEM_SHUTDOWN"], target="core.device.power", operation="SHUTDOWN", caps=("PRIVILEGED_POWER_CONTROL",), **invoke)
    _put(out, ["SYSTEM_RESTART_SYSTEM_UI"], target="core.system.ui", operation="RESTART", caps=("PRIVILEGED_SYSTEM_UI_CONTROL",), **invoke)
    _put(out, ["SYSTEM_SOFT_RESTART"], target="core.device.power", operation="SOFT_RESTART", caps=("PRIVILEGED_POWER_CONTROL",), **invoke)

    # Communication/calls.
    _put(out, ["SYSTEM_SEND_SMS"], target="core.communication.sms", operation="SEND", caps=("SMS_SEND",), side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["SYSTEM_SEND_EMAIL"], target="core.communication.email", operation="COMPOSE_OR_SEND", caps=("ANDROID_INTENT",), **external)
    _put(out, ["SYSTEM_DIAL_NUMBER"], target="core.communication.phone", operation="DIAL", caps=("ANDROID_INTENT",), **external)
    _put(out, ["CALL_BLOCK"], target="core.communication.call", operation="REJECT", caps=("CALL_CONTROL",), **invoke)
    _put(out, ["CALL_SILENCE"], target="core.communication.call", operation="SILENCE", caps=("AUDIO_CONTROL",), **invoke)

    # Navigation and status UI.
    for name, command in {
        "SYSTEM_OPEN_RECENTS": "RECENTS",
        "SYSTEM_GO_HOME": "HOME",
        "SYSTEM_OPEN_APP_DRAWER": "APP_DRAWER",
        "SYSTEM_OPEN_NOTIFICATIONS": "NOTIFICATIONS",
        "SYSTEM_OPEN_QUICK_SETTINGS": "QUICK_SETTINGS",
        "SYSTEM_EXPAND_STATUS_BAR": "EXPAND_STATUS_BAR",
        "SYSTEM_COLLAPSE_STATUS_BAR": "COLLAPSE_STATUS_BAR",
    }.items():
        _put(out, [name], target="core.system.navigation", operation="INVOKE", caps=("SYSTEM_NAVIGATION",), notes=f"command={command}", **invoke)
    _put(out, ["SYSTEM_STATUS_BAR_TOGGLE"], target="core.system.status_bar", operation="SET_STATE", caps=("PRIVILEGED_SYSTEM_UI_CONTROL",), **state)

    # Settings-page aliases all converge on one canonical target/operation.
    settings_pages = {
        "SYSTEM_OPEN_SETTINGS": "WIFI",
        "SYSTEM_OPEN_WIFI_SETTINGS": "WIFI",
        "SYSTEM_OPEN_BLUETOOTH_SETTINGS": "BLUETOOTH",
        "SYSTEM_OPEN_LOCATION_SETTINGS": "LOCATION",
        "SYSTEM_OPEN_DATA_USAGE_SETTINGS": "DATA_USAGE",
        "SYSTEM_OPEN_BATTERY_SETTINGS": "BATTERY",
        "SYSTEM_OPEN_DISPLAY_SETTINGS": "DISPLAY",
        "SYSTEM_OPEN_SOUND_SETTINGS": "SOUND",
        "SYSTEM_OPEN_STORAGE_SETTINGS": "STORAGE",
        "SYSTEM_OPEN_SECURITY_SETTINGS": "SECURITY",
        "SYSTEM_OPEN_ACCESSIBILITY_SETTINGS": "ACCESSIBILITY",
        "SYSTEM_OPEN_APP_SETTINGS_LIST": "APPLICATIONS",
        "SYSTEM_OPEN_ABOUT_PHONE": "ABOUT_PHONE",
        "SYSTEM_OPEN_SYSTEM_UPDATE_SETTINGS": "SYSTEM_UPDATE",
        "SYSTEM_OPEN_NETWORK_SETTINGS": "NETWORK",
        "SYSTEM_OPEN_NFC_SETTINGS": "NFC",
        "SYSTEM_OPEN_DATA_SAVER_SETTINGS": "DATA_SAVER",
        "SYSTEM_OPEN_DEVELOPER_SETTINGS": "DEVELOPER",
        "SYSTEM_OPEN_NOTIFICATION_SETTINGS": "NOTIFICATION_LIST",
        "SYSTEM_OPEN_PRIVACY_SETTINGS": "PRIVACY",
        "SYSTEM_OPEN_CAST_SETTINGS": "CAST",
        "SYSTEM_OPEN_INPUT_METHOD_SETTINGS": "INPUT_METHOD",
        "SYSTEM_OPEN_DEFAULT_APPS_SETTINGS": "DEFAULT_APPS",
        "SYSTEM_OPEN_VPN_SETTINGS": "VPN",
        "SYSTEM_OPEN_DATE_SETTINGS": "DATE",
        "SYSTEM_OPEN_PRINT_SETTINGS": "PRINT",
        "SYSTEM_OPEN_DEVICE_ADMIN_SETTINGS": "DEVICE_ADMIN",
        "SYSTEM_OPEN_USAGE_ACCESS_SETTINGS": "USAGE_ACCESS",
        "SYSTEM_OPEN_AIRPLANE_MODE_SETTINGS": "AIRPLANE_MODE",
    }
    for name, page in settings_pages.items():
        _put(out, [name], target="core.system.settings", operation="OPEN", caps=("ANDROID_INTENT",), notes=f"page={page}", **external)

    # Misc system actions not covered above.
    _put(out, ["SYSTEM_TOAST"], target="core.notification.transient_message", operation="SHOW", side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")
    _put(out, ["SYSTEM_ALERT"], target="core.notification.alert", operation="SHOW", side="EXTERNAL", idem="NON_IDEMPOTENT", retry="UNSAFE")

    return out


TRIGGER_REVIEWS = trigger_reviews()
ACTION_REVIEWS = action_reviews()
