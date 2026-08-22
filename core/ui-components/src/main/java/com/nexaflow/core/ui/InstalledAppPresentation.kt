package com.nexaflow.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

/**
 * User-facing identity for an installed application. The package name remains
 * available as a safe fallback, but a launcher label and icon are preferred
 * whenever Android exposes them to this app.
 */
@Immutable
data class InstalledAppPresentation(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Resolves an installed application's current label and icon. Package visibility
 * and uninstalled-app failures intentionally fall back to the persisted package
 * name instead of reporting a misleading application identity.
 */
fun resolveInstalledAppPresentation(
    context: Context,
    packageName: String
): InstalledAppPresentation {
    val normalizedPackageName = packageName.trim()
    if (normalizedPackageName.isEmpty()) {
        return InstalledAppPresentation(packageName = "", label = "", icon = null)
    }
    return runCatching {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(normalizedPackageName, 0)
        val label = packageManager.getApplicationLabel(applicationInfo)
            .toString()
            .ifBlank { normalizedPackageName }
        InstalledAppPresentation(
            packageName = normalizedPackageName,
            label = label,
            icon = packageManager.getApplicationIcon(applicationInfo).toImageBitmapOrNull()
        )
    }.getOrElse {
        InstalledAppPresentation(
            packageName = normalizedPackageName,
            label = normalizedPackageName,
            icon = null
        )
    }
}

/** Loads a package identity once per package during composition. */
@Composable
fun rememberInstalledAppPresentation(packageName: String): InstalledAppPresentation {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context, packageName) {
        resolveInstalledAppPresentation(context, packageName)
    }
}
