package com.nexaflow.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

object NexaFlowIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "bolt" to Icons.Filled.Bolt,
        "battery" to Icons.Filled.BatteryChargingFull,
        "sunny" to Icons.Filled.WbSunny,
        "dark" to Icons.Filled.DarkMode,
        "dnd" to Icons.Filled.DoNotDisturb,
        "wifi" to Icons.Filled.Wifi,
        "home" to Icons.Filled.Home,
        "schedule" to Icons.Filled.Schedule,
        "notifications" to Icons.Filled.Notifications,
        "volume" to Icons.AutoMirrored.Filled.VolumeUp,
        "flash" to Icons.Filled.FlashOn,
        "lock" to Icons.Filled.Lock,
        "palette" to Icons.Filled.Palette,
        "security" to Icons.Filled.Security,
        "settings" to Icons.Filled.Settings,
        "star" to Icons.Filled.Star
    )
}

fun iconVector(name: String): ImageVector {
    return NexaFlowIcons.all.find { it.first == name }?.second ?: Icons.Filled.Bolt
}
