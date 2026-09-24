package com.nexaflow.domain.capability

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The authorization surface that produced an observation. This is deliberately
 * broader than Android runtime permissions: a task may depend on AppOps,
 * special access, Shizuku, Root, or managed-device authority instead.
 */
@Serializable
enum class PrivilegeSurface {
    /** Manifest permission checked against NexaFlow's own UID (normal/signature/etc). */
    ANDROID_PERMISSION,
    RUNTIME_PERMISSION,
    SPECIAL_ACCESS,
    APP_OP,
    SHIZUKU,
    ROOT,
    DEVICE_OWNER
}

/**
 * Normalized live state for one authorization surface. Only [GRANTED] means
 * the observed authorization is currently usable without additional user
 * action. PARTIAL is intentionally not promoted to granted.
 */
@Serializable
enum class PrivilegeGrantState {
    GRANTED,
    PARTIAL,
    NOT_GRANTED,
    PERMISSION_REQUIRED,
    NOT_INSTALLED,
    NOT_RUNNING,
    SERVICE_UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN
}

/**
 * One non-secret authorization observation. [key] is either an Android
 * permission/app-op name or a stable NexaFlow special-access identifier.
 */
@Immutable
@Serializable
data class PrivilegeObservation(
    val surface: PrivilegeSurface,
    val key: String,
    val state: PrivilegeGrantState,
    val detailCode: String
) {
    val granted: Boolean
        get() = state == PrivilegeGrantState.GRANTED
}

/**
 * One coherent permission/privilege snapshot used by settings, capability
 * admission and diagnostics. Missing observations remain unknown; callers must
 * never interpret an absent key as a denial.
 */
@Immutable
@Serializable
data class PrivilegeSnapshot(
    val observations: List<PrivilegeObservation> = emptyList(),
    val observedAtMs: Long = 0L
) {
    val neverObserved: Boolean
        get() = observedAtMs == 0L

    fun observation(surface: PrivilegeSurface, key: String): PrivilegeObservation? =
        observations.firstOrNull { it.surface == surface && it.key == key }

    fun stateOf(surface: PrivilegeSurface, key: String): PrivilegeGrantState? =
        observation(surface, key)?.state

    fun isGranted(surface: PrivilegeSurface, key: String): Boolean? =
        observation(surface, key)?.granted

    fun grantedRuntimePermission(permission: String): Boolean? =
        isGranted(PrivilegeSurface.RUNTIME_PERMISSION, permission)

    companion object {
        const val SPECIAL_ACCESSIBILITY_SERVICE = "accessibility_service"
        const val SPECIAL_NOTIFICATION_LISTENER = "notification_listener"
        const val SPECIAL_WRITE_SETTINGS = "write_settings"
        const val SPECIAL_DRAW_OVERLAYS = "draw_overlays"
        const val SPECIAL_DND_POLICY = "notification_policy"
        const val SPECIAL_EXACT_ALARM = "exact_alarm"
        const val SPECIAL_INSTALL_PACKAGES = "install_unknown_apps"
        const val SPECIAL_BATTERY_OPTIMIZATION = "battery_optimization_exemption"

        const val ENV_SHIZUKU = "shizuku"
        const val ENV_ROOT = "root"
        const val ENV_DEVICE_OWNER = "device_owner"
    }
}
