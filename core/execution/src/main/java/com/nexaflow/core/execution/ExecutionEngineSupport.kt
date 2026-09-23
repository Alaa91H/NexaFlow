package com.nexaflow.core.execution

import android.content.Context
import android.os.PowerManager
import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.models.ActionExecutionResult

internal enum class SnapshotFreshness {
    FRESH,
    STALE,
    NEVER_OBSERVED
}

internal fun classifySnapshotFreshness(
    snapshot: CapabilitySnapshot,
    nowMs: Long,
    freshnessMs: Long
): SnapshotFreshness = when {
    snapshot.neverObserved -> SnapshotFreshness.NEVER_OBSERVED
    nowMs - snapshot.observedAtMs > freshnessMs -> SnapshotFreshness.STALE
    else -> SnapshotFreshness.FRESH
}

internal fun acquireExecutionWakeLock(
    context: Context,
    tag: String
): PowerManager.WakeLock? = try {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val safeTag = if (tag.length > 60) tag.take(60) else tag
    powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, safeTag)?.apply {
        setReferenceCounted(false)
        acquire(10 * 60 * 1000L)
    }
} catch (_: Throwable) {
    null
}

internal fun buildExecutionMessage(results: List<ActionExecutionResult>): String =
    if (results.isEmpty()) {
        "No actions configured"
    } else {
        results.joinToString(" | ") { it.message }
    }
