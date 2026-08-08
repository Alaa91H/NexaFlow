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
    val headsetConnected: Boolean = false
)
