package com.nexaflow.core.engine

/**
 * Decides whether an accessibility `TYPE_WINDOW_STATE_CHANGED` package is a
 * *real* foreground app or transparent system chrome that overlays the current
 * app without ending its "while open" session.
 *
 * Without this filter, opening the notification shade, a permission dialog or
 * the keyboard would look like a foreground switch — firing the task's exit
 * actions while the app is still open, then re-running (or not, if the cooldown
 * is still active) when the overlay closes. That churn is exactly what makes
 * "runs while the app is open" unreliable.
 */
object AppForegroundRules {

    /** Packages that never represent the foreground app (system windows). */
    private val SYSTEM_CHROME = setOf("android", "com.android.systemui")

    /**
     * Major OEM keyboards whose package names do not follow the conventional
     * `.inputmethod` / `.ime.` naming (they would otherwise look like a real
     * foreground switch and end the session while the user is just typing).
     */
    private val KNOWN_IMES = setOf(
        "com.samsung.android.honeyboard", // Samsung Keyboard
        "com.touchtype.swiftkey", // SwiftKey
        "com.miui.inputmethod"
    )

    /** True when [packageName] denotes an input-method (keyboard) package. */
    private fun isInputMethod(packageName: String): Boolean {
        if (packageName in KNOWN_IMES) return true
        val lower = packageName.lowercase()
        return lower.contains(".inputmethod") ||
            lower.contains("inputmethod.latin") ||
            lower.contains(".ime.")
    }

    /** True when [packageName] is a real app the user put in the foreground. */
    fun isForegroundPackage(packageName: String): Boolean =
        packageName !in SYSTEM_CHROME && !isInputMethod(packageName)
}
