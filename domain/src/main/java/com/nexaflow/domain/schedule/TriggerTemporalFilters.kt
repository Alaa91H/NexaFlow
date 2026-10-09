package com.nexaflow.domain.schedule

import com.nexaflow.domain.models.ConditionResult
import java.util.ArrayDeque
import java.util.LinkedHashMap

/** Optional per-trigger filters, stored in the existing string config map. */
data class TriggerTemporalFilterConfig(
    val debounceMs: Long? = null,
    val minIntervalMs: Long? = null,
    val cooldownMs: Long? = null,
    val rateLimitCount: Int? = null,
    val rateLimitWindowMs: Long? = null,
    val stableForMs: Long? = null,
    val hysteresis: Double? = null,
)

sealed interface TriggerFilterConfigParse {
    data object Unconfigured : TriggerFilterConfigParse
    data class Valid(val config: TriggerTemporalFilterConfig) : TriggerFilterConfigParse
    data class Invalid(val reason: String) : TriggerFilterConfigParse
}

/** Strict, locale-independent parser for the versioned trigger filter keys. */
object TriggerTemporalFilterConfigParser {
    const val KEY_DEBOUNCE_MS = "debounceMs"
    const val KEY_MIN_INTERVAL_MS = "minIntervalMs"
    const val KEY_COOLDOWN_MS = "cooldownMs"
    const val KEY_RATE_LIMIT_COUNT = "rateLimitCount"
    const val KEY_RATE_LIMIT_WINDOW_MS = "rateLimitWindowMs"
    const val KEY_STABLE_FOR_MS = "stableForMs"
    const val KEY_HYSTERESIS = "hysteresis"

    private val FILTER_KEYS = setOf(
        KEY_DEBOUNCE_MS,
        KEY_MIN_INTERVAL_MS,
        KEY_COOLDOWN_MS,
        KEY_RATE_LIMIT_COUNT,
        KEY_RATE_LIMIT_WINDOW_MS,
        KEY_STABLE_FOR_MS,
        KEY_HYSTERESIS,
    )

    fun parse(raw: Map<String, String>): TriggerFilterConfigParse {
        if (FILTER_KEYS.none(raw::containsKey)) return TriggerFilterConfigParse.Unconfigured

        fun duration(key: String): Long? {
            val value = raw[key] ?: return null
            val parsed = value.toLongOrNull()
                ?: throw IllegalArgumentException("INVALID_DURATION:$key")
            if (parsed !in 0L..MAX_DURATION_MS) {
                throw IllegalArgumentException("INVALID_DURATION:$key")
            }
            return parsed
        }

        fun integer(key: String): Int? {
            val value = raw[key] ?: return null
            val parsed = value.toIntOrNull()
                ?: throw IllegalArgumentException("INVALID_COUNT:$key")
            if (parsed !in 1..MAX_RATE_LIMIT_COUNT) {
                throw IllegalArgumentException("INVALID_COUNT:$key")
            }
            return parsed
        }

        return try {
            val rateLimitCount = integer(KEY_RATE_LIMIT_COUNT)
            val rateLimitWindowMs = duration(KEY_RATE_LIMIT_WINDOW_MS)
            if ((rateLimitCount == null) != (rateLimitWindowMs == null)) {
                throw IllegalArgumentException("INCOMPLETE_RATE_LIMIT")
            }
            val hysteresis = raw[KEY_HYSTERESIS]?.let { value ->
                val parsed = value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("INVALID_HYSTERESIS")
                if (!parsed.isFinite() || parsed !in 0.0..MAX_HYSTERESIS) {
                    throw IllegalArgumentException("INVALID_HYSTERESIS")
                }
                parsed
            }
            TriggerFilterConfigParse.Valid(
                TriggerTemporalFilterConfig(
                    debounceMs = duration(KEY_DEBOUNCE_MS),
                    minIntervalMs = duration(KEY_MIN_INTERVAL_MS),
                    cooldownMs = duration(KEY_COOLDOWN_MS),
                    rateLimitCount = rateLimitCount,
                    rateLimitWindowMs = rateLimitWindowMs,
                    stableForMs = duration(KEY_STABLE_FOR_MS),
                    hysteresis = hysteresis,
                ),
            )
        } catch (invalid: IllegalArgumentException) {
            TriggerFilterConfigParse.Invalid(invalid.message ?: "INVALID_FILTER_CONFIG")
        }
    }

    const val MAX_DURATION_MS = 604_800_000L // Seven days.
    const val MAX_RATE_LIMIT_COUNT = 1_000
    const val MAX_HYSTERESIS = 1_000_000.0
}

enum class TriggerFilterReason {
    DEBOUNCED,
    DEBOUNCE_PENDING,
    MIN_INTERVAL,
    COOLDOWN,
    RATE_LIMITED,
    CLOCK_RESET,
    STABILITY_PENDING,
    INVALID_STATE,
}

sealed interface TriggerFilterDecision {
    data object Allowed : TriggerFilterDecision
    data class Blocked(val reason: TriggerFilterReason) : TriggerFilterDecision
    data class Unknown(val reason: TriggerFilterReason) : TriggerFilterDecision
}

data class TriggerStateDecision(
    val result: ConditionResult,
    val reason: TriggerFilterReason? = null,
)

/**
 * Bounded in-memory reducer state. Timestamps are monotonic elapsed milliseconds,
 * never wall-clock event timestamps. Missing config is deliberately a no-op.
 */
class TriggerTemporalFilterState(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private data class Entry(
        var lastSeenAtMs: Long? = null,
        var lastOccurrenceAtMs: Long? = null,
        var debouncedAdmissionReady: Boolean = false,
        var lastAdmittedAtMs: Long? = null,
        val admittedEvents: ArrayDeque<Long> = ArrayDeque(),
        var stableSinceMs: Long? = null,
        var hysteresisSatisfied: Boolean? = null,
    )

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {}

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    @Synchronized
    fun evaluateEvent(
        key: String,
        config: TriggerTemporalFilterConfig,
        elapsedRealtimeMs: Long,
    ): TriggerFilterDecision {
        require(key.isNotBlank()) { "key must not be blank" }
        val hasEventFilter = config.debounceMs != null || config.minIntervalMs != null || config.cooldownMs != null ||
            config.rateLimitCount != null
        if (!hasEventFilter) return TriggerFilterDecision.Allowed
        if (elapsedRealtimeMs < 0L) return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)

        val entry = entry(key)
        val lastSeen = entry.lastSeenAtMs
        if (lastSeen != null && elapsedRealtimeMs < lastSeen) {
            entries.remove(key)
            return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)
        }
        entry.lastSeenAtMs = elapsedRealtimeMs
        if (config.debounceMs != null) {
            entry.lastOccurrenceAtMs = elapsedRealtimeMs
            return TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING)
        }

        val lastAdmitted = entry.lastAdmittedAtMs
        val minIntervalMs = config.minIntervalMs
        if (lastAdmitted != null && minIntervalMs != null &&
            elapsedRealtimeMs - lastAdmitted < minIntervalMs
        ) {
            return TriggerFilterDecision.Blocked(TriggerFilterReason.MIN_INTERVAL)
        }
        val cooldownMs = config.cooldownMs
        if (lastAdmitted != null && cooldownMs != null &&
            elapsedRealtimeMs - lastAdmitted < cooldownMs
        ) {
            return TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN)
        }

        val count = config.rateLimitCount
        val windowMs = config.rateLimitWindowMs
        if (count != null && windowMs != null) {
            val cutoff = elapsedRealtimeMs - windowMs
            while (entry.admittedEvents.isNotEmpty() && entry.admittedEvents.first() <= cutoff) {
                entry.admittedEvents.removeFirst()
            }
            if (entry.admittedEvents.size >= count) {
                return TriggerFilterDecision.Blocked(TriggerFilterReason.RATE_LIMITED)
            }
            entry.admittedEvents.addLast(elapsedRealtimeMs)
        }

        entry.lastAdmittedAtMs = elapsedRealtimeMs
        return TriggerFilterDecision.Allowed
    }

    @Synchronized
    fun observeDebounced(key: String, config: TriggerTemporalFilterConfig, elapsedRealtimeMs: Long): TriggerFilterDecision {
        require(key.isNotBlank())
        if (elapsedRealtimeMs < 0L) return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)
        val entry = entry(key)
        val previous = entry.lastSeenAtMs
        if (previous != null && elapsedRealtimeMs < previous) {
            entries.remove(key)
            return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)
        }
        entry.lastSeenAtMs = elapsedRealtimeMs
        entry.lastOccurrenceAtMs = elapsedRealtimeMs
        entry.debouncedAdmissionReady = false
        return TriggerFilterDecision.Allowed
    }

    /** Reserves one trailing-edge occurrence after its quiet window has elapsed. */
    @Synchronized
    fun admitDebounced(
        key: String,
        config: TriggerTemporalFilterConfig,
        elapsedRealtimeMs: Long,
    ): TriggerFilterDecision {
        require(key.isNotBlank()) { "key must not be blank" }
        val debounceMs = config.debounceMs ?: return TriggerFilterDecision.Allowed
        if (elapsedRealtimeMs < 0L) return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)
        val entry = entry(key)
        val lastOccurrence = entry.lastOccurrenceAtMs ?: return TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING)
        if (elapsedRealtimeMs < lastOccurrence) {
            entries.remove(key)
            return TriggerFilterDecision.Unknown(TriggerFilterReason.CLOCK_RESET)
        }
        if (elapsedRealtimeMs - lastOccurrence < debounceMs) {
            return TriggerFilterDecision.Blocked(TriggerFilterReason.DEBOUNCE_PENDING)
        }
        val lastAdmitted = entry.lastAdmittedAtMs
        if (lastAdmitted != null && config.minIntervalMs != null && elapsedRealtimeMs - lastAdmitted < config.minIntervalMs) {
            return TriggerFilterDecision.Blocked(TriggerFilterReason.MIN_INTERVAL)
        }
        if (lastAdmitted != null && config.cooldownMs != null && elapsedRealtimeMs - lastAdmitted < config.cooldownMs) {
            return TriggerFilterDecision.Blocked(TriggerFilterReason.COOLDOWN)
        }
        val count = config.rateLimitCount
        val window = config.rateLimitWindowMs
        if (count != null && window != null) {
            val cutoff = elapsedRealtimeMs - window
            while (entry.admittedEvents.isNotEmpty() && entry.admittedEvents.first() <= cutoff) entry.admittedEvents.removeFirst()
            if (entry.admittedEvents.size >= count) return TriggerFilterDecision.Blocked(TriggerFilterReason.RATE_LIMITED)
            entry.admittedEvents.addLast(elapsedRealtimeMs)
        }
        entry.lastAdmittedAtMs = elapsedRealtimeMs
        entry.lastOccurrenceAtMs = null
        entry.debouncedAdmissionReady = true
        return TriggerFilterDecision.Allowed
    }

    @Synchronized
    fun consumeDebouncedAdmission(key: String): Boolean {
        val entry = entries[key] ?: return false
        if (!entry.debouncedAdmissionReady) return false
        entry.debouncedAdmissionReady = false
        return true
    }

    /**
     * Requires a known satisfied observation to remain satisfied for the full
     * configured interval. UNKNOWN and errors break continuity and reset the interval; confirmed false also resets it.
     */
    @Synchronized
    fun evaluateStability(
        key: String,
        observation: ConditionResult,
        config: TriggerTemporalFilterConfig,
        elapsedRealtimeMs: Long,
    ): TriggerStateDecision {
        require(key.isNotBlank()) { "key must not be blank" }
        val stableForMs = config.stableForMs ?: return TriggerStateDecision(observation)
        if (elapsedRealtimeMs < 0L) {
            entries.remove(key)
            return TriggerStateDecision(ConditionResult.Unknown, TriggerFilterReason.CLOCK_RESET)
        }
        val entry = entry(key)
        val stableSince = entry.stableSinceMs
        if (stableSince != null && elapsedRealtimeMs < stableSince) {
            entries.remove(key)
            return TriggerStateDecision(ConditionResult.Unknown, TriggerFilterReason.CLOCK_RESET)
        }
        return when (observation) {
            ConditionResult.Satisfied -> {
                if (stableForMs == 0L) {
                    entry.stableSinceMs = elapsedRealtimeMs
                    TriggerStateDecision(ConditionResult.Satisfied)
                } else if (stableSince == null) {
                    entry.stableSinceMs = elapsedRealtimeMs
                    TriggerStateDecision(ConditionResult.Unknown, TriggerFilterReason.STABILITY_PENDING)
                } else if (elapsedRealtimeMs - stableSince >= stableForMs) {
                    TriggerStateDecision(ConditionResult.Satisfied)
                } else {
                    TriggerStateDecision(ConditionResult.Unknown, TriggerFilterReason.STABILITY_PENDING)
                }
            }

            ConditionResult.Unsatisfied -> {
                entry.stableSinceMs = null
                TriggerStateDecision(ConditionResult.Unsatisfied)
            }

            ConditionResult.Unknown, ConditionResult.Unavailable, is ConditionResult.Error -> {
                entry.stableSinceMs = null
                TriggerStateDecision(observation)
            }
        }
    }

    /** Threshold latch: on crossing the threshold, release only past its hysteresis band. */
    @Synchronized
    fun evaluateThreshold(
        key: String,
        value: Double,
        threshold: Double,
        above: Boolean,
        config: TriggerTemporalFilterConfig,
        elapsedRealtimeMs: Long,
    ): ConditionResult {
        require(key.isNotBlank()) { "key must not be blank" }
        val band = config.hysteresis ?: 0.0
        if (!value.isFinite() || !threshold.isFinite() || !band.isFinite() || elapsedRealtimeMs < 0L) {
            entries.remove(key)
            return ConditionResult.Unknown
        }
        val entry = entry(key)
        val lastSeen = entry.lastSeenAtMs
        if (lastSeen != null && elapsedRealtimeMs < lastSeen) {
            entries.remove(key)
            return ConditionResult.Unknown
        }
        entry.lastSeenAtMs = elapsedRealtimeMs
        val latched = entry.hysteresisSatisfied
        val satisfied = when {
            above && latched == true -> value >= threshold - band
            !above && latched == true -> value <= threshold + band
            above -> value >= threshold
            else -> value <= threshold
        }
        entry.hysteresisSatisfied = satisfied
        return if (satisfied) ConditionResult.Satisfied else ConditionResult.Unsatisfied
    }

    @Synchronized
    fun clear(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun clearPrefix(prefix: String) {
        entries.keys.removeIf { it.startsWith(prefix) }
    }

    @Synchronized
    internal fun entryCountForTest(): Int = entries.size

    private fun entry(key: String): Entry {
        entries[key]?.let { return it }
        val created = Entry()
        entries[key] = created
        while (entries.size > maxEntries) {
            val eldestKey = entries.entries.iterator().next().key
            entries.remove(eldestKey)
        }
        return created
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 4_096
    }
}



