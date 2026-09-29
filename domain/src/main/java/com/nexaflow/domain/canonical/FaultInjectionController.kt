package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T32 — Deterministic fault injection (plan §T32).
 *
 * A pure, reproducible failure simulator used by tests and device-side
 * diagnostics to drive the canonical runtime's failure paths: command
 * failures, group aborts, retry storms and rollback exercises — without
 * touching real device services.
 *
 * Contracts pinned by tests:
 *  - Deterministic: outcomes derive from a stable [FaultSchedule] plus an
 *    attempt counter — no clocks, no randomness. Same script + same request
 *    sequence = same fault decisions, forever.
 *  - Fail closed: an expired, paused or unknown-spec schedule refuses with
 *    a typed [FaultRefusal]; it never silently passes a real invocation.
 *  - Bounded: every knob (attempt caps, counters, schedule size) has an
 *    explicit limit so a runaway test cannot spin forever.
 *
 * The runtime consults [decide] before each [AtomicCommand]; the returned
 * decision tells the harness to succeed, fail, or hang the command.
 */
object FaultInjectionController {

    /** Hard bound on schedule entries (keeps scripts auditable). */
    const val MAX_SCHEDULE_ENTRIES: Int = 128

    /** Hard bound on attempts recorded for one command id. */
    const val MAX_TRACKED_COMMANDS: Int = 4_096

    /** What the harness must do when the fault point is hit. */
    enum class FaultAction {
        /** The command fails with a typed synthetic error. */
        FAIL,

        /** The command never returns within its timeout. */
        HANG,

        /** The command reports progress forever without completing. */
        STALL,
    }

    /** When a fault triggers for a command. */
    enum class Trigger {
        /** The first attempt only. */
        FIRST_ATTEMPT,

        /** Every attempt (retry storm). */
        EVERY_ATTEMPT,

        /** Exactly the Nth attempt (1-based; handled by [nthAttempt]). */
        EXACT_ATTEMPT,
    }

    /** One scripted fault for one command id. */
    @Serializable
    data class FaultSpec(
        val commandId: String,
        val action: FaultAction,
        val trigger: Trigger = Trigger.FIRST_ATTEMPT,
        /** Only meaningful with [Trigger.EXACT_ATTEMPT]; 1-based. */
        val nthAttempt: Int = 1,
        /** Stable synthetic error label surfaced to the harness. */
        val errorLabel: String = "injected_fault",
    ) {
        init {
            require(commandId.isNotBlank()) { "commandId must not be blank" }
            require(nthAttempt in 1..10_000) { "nthAttempt must be in 1..10000" }
            require(errorLabel.length <= 120) { "errorLabel too long" }
            require(trigger != Trigger.EXACT_ATTEMPT || nthAttempt >= 1) {
                "EXACT_ATTEMPT requires nthAttempt >= 1"
            }
        }
    }

    /** An auditable fault script: deterministic and serializable. */
    @Serializable
    data class FaultSchedule(
        val specs: List<FaultSpec> = emptyList(),
        val paused: Boolean = false,
    ) {
        init {
            require(specs.size <= MAX_SCHEDULE_ENTRIES) {
                "schedule exceeds $MAX_SCHEDULE_ENTRIES entries"
            }
            require(specs.map { it.commandId }.distinct().size == specs.size) {
                "duplicate commandId in schedule"
            }
        }

        fun specFor(commandId: String): FaultSpec? =
            specs.firstOrNull { it.commandId == commandId }
    }

    /** Why fault injection refused to act; typed and stable. */
    enum class FaultRefusal {
        SCHEDULE_PAUSED,
        SCHEDULE_EXPIRED,
        UNKNOWN_COMMAND,
        TRACKING_LIMIT_EXCEEDED,
    }

    /** What the harness must do with the current attempt. */
    sealed class Decision {
        /** Run the command normally. */
        data object Pass : Decision()

        /** Inject the scripted fault. */
        data class Inject(
            val action: FaultAction,
            val errorLabel: String,
            val attempt: Int,
        ) : Decision()

        /** Injection itself refused: run the command, record the refusal. */
        data class Refused(val reason: FaultRefusal) : Decision()
    }

    /** The per-schedule attempt tracker (host supplies storage). */
    class AttemptTracker {
        private val attempts = linkedMapOf<String, Int>()

        /** Records and returns the 1-based attempt for [commandId]. */
        fun nextAttempt(commandId: String): Int {
            val next = (attempts[commandId] ?: 0) + 1
            require(attempts.size < MAX_TRACKED_COMMANDS || attempts.containsKey(commandId)) {
                "attempt tracking exceeds $MAX_TRACKED_COMMANDS commands"
            }
            attempts[commandId] = next
            return next
        }

        fun attemptCount(commandId: String): Int = attempts[commandId] ?: 0
    }

    /**
     * The fault decision for one attempt of [commandId]. Deterministic given
     * (schedule, tracker state): the same harness sequence replays the same
     * faults on every machine.
     */
    fun decide(
        schedule: FaultSchedule,
        tracker: AttemptTracker,
        commandId: String,
        scheduleExpired: Boolean = false,
    ): Decision {
        if (schedule.paused) return Decision.Refused(FaultRefusal.SCHEDULE_PAUSED)
        if (scheduleExpired) return Decision.Refused(FaultRefusal.SCHEDULE_EXPIRED)

        val spec = schedule.specFor(commandId)
            ?: return Decision.Refused(FaultRefusal.UNKNOWN_COMMAND)

        val attempt = tracker.nextAttempt(commandId)
        val hits = when (spec.trigger) {
            Trigger.FIRST_ATTEMPT -> attempt == 1
            Trigger.EVERY_ATTEMPT -> true
            Trigger.EXACT_ATTEMPT -> attempt == spec.nthAttempt
        }
        return if (hits) {
            Decision.Inject(spec.action, spec.errorLabel, attempt)
        } else {
            Decision.Pass
        }
    }

    // ------------------------------------------------------------------
    // Retry/replay helpers for harness assertions
    // ------------------------------------------------------------------

    /**
     * Simulates a retry loop: returns the injected labels per attempt for
     * [commandId] until a Pass decision occurs (or the cap is hit). The
     * returned list lets tests pin exact retry counts against policies.
     */
    fun replayUntilPass(
        schedule: FaultSchedule,
        maxAttempts: Int = 10,
        commandId: String = "command",
    ): List<String> {
        require(maxAttempts in 1..100) { "maxAttempts must be in 1..100" }
        val tracker = AttemptTracker()
        val injected = mutableListOf<String>()
        repeat(maxAttempts) {
            when (val decision = decide(schedule, tracker, commandId)) {
                is Decision.Inject -> injected += decision.errorLabel
                Decision.Pass, is Decision.Refused -> return injected
            }
        }
        return injected
    }
}
