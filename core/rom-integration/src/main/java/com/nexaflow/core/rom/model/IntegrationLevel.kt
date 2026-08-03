package com.nexaflow.core.rom.model

enum class IntegrationLevel(
    val displayName: String,
    val description: String
) {
    NORMAL(
        "Standard app",
        "No system privileges. Only user-grantable runtime permissions are available."
    ),
    ROOT(
        "Root available",
        "su binary detected. Full control via a root shell is possible."
    ),
    SHIZUKU(
        "Shizuku active",
        "Shizuku server is running. Elevated commands are possible via ADB or root."
    ),
    SYSTEM_APP(
        "System app",
        "Installed as a system app in /system/app."
    ),
    PRIVILEGED_SYSTEM_APP(
        "Privileged system app",
        "Installed as a privileged app in /system/priv-app with a granted whitelist."
    ),
    PLATFORM_SIGNED_SYSTEM_APP(
        "Platform-signed system app",
        "Privileged app signed with the platform key. Full ROM control without root."
    )
}
