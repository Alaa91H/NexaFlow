package com.nexaflow.domain.models

/**
 * A permission (runtime or special) a task needs to run a trigger or action.
 *
 * - [runtimePermissions] — dangerous permissions granted through the system
 *   dialog (`ActivityResultContracts.RequestMultiplePermissions`).
 * - [special] — settings-screen permissions (write settings, DND access,
 *   notification listener, accessibility, Shizuku/root) that cannot be granted
 *   by a dialog and need their dedicated system screen.
 */
data class PermissionRequirement(
    val owner: String,
    val runtimePermissions: List<String> = emptyList(),
    val special: String? = null
)
