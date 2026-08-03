package com.nexaflow.core.rom.model

enum class RomCapability(
    val displayName: String,
    val description: String
) {
    WRITE_SETTINGS(
        "Write system settings",
        "Modify android.provider.Settings.System (brightness, timeout, rotation, ...)."
    ),
    WRITE_SECURE_SETTINGS(
        "Write secure settings",
        "Modify Settings.Secure and Settings.Global. Privileged-only on stock ROMs."
    ),
    SYSTEM_ALERT_WINDOW(
        "Draw over apps",
        "Render overlay windows above other applications."
    ),
    PACKAGE_USAGE_STATS(
        "Usage statistics",
        "Read installed apps and their usage data."
    ),
    READ_LOGS(
        "Read system logs",
        "Access logcat output. Privileged-only on stock ROMs."
    ),
    MODIFY_PHONE_STATE(
        "Modify phone state",
        "Control radio and phone operations. Privileged-only on stock ROMs."
    ),
    STATUS_BAR_CONTROL(
        "Status bar control",
        "Expand and collapse the status bar. Privileged-only."
    ),
    FORCE_STOP_PACKAGES(
        "Force-stop packages",
        "Stop running applications. Privileged-only."
    ),
    DND_ACCESS(
        "Do Not Disturb access",
        "Control the notification interruption policy."
    ),
    KILL_BACKGROUND_PROCESSES(
        "Kill background processes",
        "Stop background processes and services."
    ),
    LINEAGEOS_SDK(
        "LineageOS SDK",
        "Full LineageOS privileged API access (power profiles, battery, ...)."
    ),
    LINEAGEOS_HARDWARE(
        "LineageOS hardware",
        "Vendor hardware features exposed by LineageOS (LED, vibration, display, ...)."
    ),
    MIUI_HIDDEN_API(
        "MIUI / HyperOS APIs",
        "Xiaomi system APIs exposed when running as a system component."
    ),
    COLOROS_HIDDEN_API(
        "ColorOS / OxygenOS APIs",
        "OPPO/OnePlus system APIs exposed when running as a system component."
    ),
    ONE_UI_HIDDEN_API(
        "One UI APIs",
        "Samsung system APIs exposed when running as a system component."
    ),
    ROOT_SHELL(
        "Root shell",
        "Execute shell commands through su."
    ),
    SHIZUKU(
        "Shizuku service",
        "Execute elevated commands through the Shizuku service."
    )
}
