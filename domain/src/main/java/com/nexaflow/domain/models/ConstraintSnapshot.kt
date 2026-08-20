package com.nexaflow.domain.models

import androidx.compose.runtime.Immutable

/**
 * A pure, Android-free snapshot of the device conditions a [Constraint] can
 * check. Captured by the execution layer ([ConstraintStateReader]) right
 * before a task runs; evaluated purely by [ConstraintEvaluator].
 *
 * @param batteryLevel percentage 0..100, or `-1` when the device could not
 *   report it (battery constraints then fail closed: better skip than run
 *   against an unverifiable level).
 */
@Immutable
data class ConstraintSnapshot(
    val wifiConnected: Boolean = false,
    val batteryLevel: Int = -1,
    val screenLocked: Boolean = false,
    val headsetConnected: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val dndActive: Boolean = false,
    val airplaneModeOn: Boolean = false,
    val isCharging: Boolean = false,
    val locationEnabled: Boolean = false,
    /** True only when the active validated network is explicitly unmetered. */
    val unmeteredNetwork: Boolean = false,
    /** False when the screen is interactive; unknown callers must fail closed. */
    val screenOff: Boolean = false,
    /** PowerManager idle state; false when unavailable. */
    val deviceIdle: Boolean = false,
    /** PowerManager thermal status, or null when unsupported/unavailable. */
    val thermalStatus: Int? = null,
    /** Allocatable bytes in the app-visible storage volume, or null when unreadable. */
    val availableStorageBytes: Long? = null
)
