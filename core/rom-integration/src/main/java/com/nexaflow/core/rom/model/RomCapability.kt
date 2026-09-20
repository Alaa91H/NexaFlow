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
    PRIVILEGED_ROM_SDK(
        "Privileged ROM SDK",
        "Full privileged ROM API access (power profiles, battery, ...) on community builds."
    ),
    PRIVILEGED_ROM_HARDWARE(
        "Privileged ROM hardware",
        "Vendor hardware features exposed by privileged community builds (LED, vibration, display, ...)."
    ),
    CUSTOM_ROM_SETTINGS(
        "Custom ROM settings",
        "Read and write the build's custom settings (vendor-defined keys) on privileged community builds."
    ),
    VENDOR_HIDDEN_API_PRIMARY(
        "Vendor hidden APIs",
        "Vendor system APIs exposed to system components on gated builds."
    ),
    VENDOR_HIDDEN_API_EXTENDED(
        "Extended vendor APIs",
        "Additional vendor system APIs exposed to system components on related builds."
    ),
    VENDOR_HIDDEN_API(
        "Vendor APIs",
        "Vendor system APIs exposed when running as a system component."
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
