package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable

/**
 * An installed external plugin discovered through the Locale protocol: an app
 * that declares an exported receiver for the FIRE_SETTING broadcast.
 * Icons are resolved by the UI layer via [android.content.pm.PackageManager]
 * (domain stays free of Android drawables).
 */
@Immutable
data class PluginInfo(
    /** Application package of the plugin, e.g. `com.example.flashplugin`. */
    val packageName: String,
    /** Flattened class name of the FIRE_SETTING receiver, e.g. `com.example.flashplugin.FireReceiver`. */
    val receiverClass: String,
    /** User-visible label of the plugin app. */
    val label: String,
    /**
     * Stable exported configuration activity selected during protocol-aware
     * discovery. Empty remains supported for legacy persisted picker entries.
     */
    val editActivityClass: String = ""
)
