package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T32 — Fault injection tests: the same script and request sequence must
 * replay identical fault decisions, and refusals must be typed, never silent.
 */
class FaultInjectionControllerTest {

    private val tracker = FaultInjectionController.AttemptTracker()

    private fun schedule(
        vararg specs: FaultInjectionController.FaultSpec,
        paused: Boolean = false,
    ) = FaultInjectionController.FaultSchedule(specs.toList(), paused)

    // ------------------------------------------------------------------
    // Triggers
    // ------------------------------------------------------------------

    @Test
    fun firstAttemptTriggerInjectsExactlyOnce() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "cmd-1",
                action = FaultInjectionController.FaultAction.FAIL,
                trigger = FaultInjectionController.Trigger.FIRST_ATTEMPT,
            ),
        )

        val first = FaultInjectionController.decide(schedule, tracker, "cmd-1")
        val second = FaultInjectionController.decide(schedule, tracker, "cmd-1")

        assertTrue(first is FaultInjectionController.Decision.Inject)
        assertEquals(1, (first as FaultInjectionController.Decision.Inject).attempt)
        assertEquals(FaultInjectionController.Decision.Pass, second)
    }

    @Test
    fun everyAttemptTriggerInjectsOnEveryRetry() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "cmd-1",
                action = FaultInjectionController.FaultAction.FAIL,
                trigger = FaultInjectionController.Trigger.EVERY_ATTEMPT,
                errorLabel = "storm",
            ),
        )

        repeat(5) { index ->
            val decision = FaultInjectionController.decide(schedule, tracker, "cmd-1")
            assertTrue(decision is FaultInjectionController.Decision.Inject)
            assertEquals(index + 1, (decision as FaultInjectionController.Decision.Inject).attempt)
            assertEquals("storm", decision.errorLabel)
        }
    }

    @Test
    fun exactAttemptTriggerHitsOnlyTheNthAttempt() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "cmd-1",
                action = FaultInjectionController.FaultAction.HANG,
                trigger = FaultInjectionController.Trigger.EXACT_ATTEMPT,
                nthAttempt = 3,
            ),
        )

        assertEquals(FaultInjectionController.Decision.Pass, FaultInjectionController.decide(schedule, tracker, "cmd-1"))
        assertEquals(FaultInjectionController.Decision.Pass, FaultInjectionController.decide(schedule, tracker, "cmd-1"))
        val third = FaultInjectionController.decide(schedule, tracker, "cmd-1")
        assertTrue(third is FaultInjectionController.Decision.Inject)
        assertEquals(FaultInjectionController.FaultAction.HANG, (third as FaultInjectionController.Decision.Inject).action)
        assertEquals(FaultInjectionController.Decision.Pass, FaultInjectionController.decide(schedule, tracker, "cmd-1"))
    }

    // ------------------------------------------------------------------
    // Determinism
    // ------------------------------------------------------------------

    @Test
    fun identicalSequencesReplayIdenticalDecisions() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "a",
                action = FaultInjectionController.FaultAction.FAIL,
                trigger = FaultInjectionController.Trigger.EXACT_ATTEMPT,
                nthAttempt = 2,
            ),
            FaultInjectionController.FaultSpec(
                commandId = "b",
                action = FaultInjectionController.FaultAction.STALL,
                trigger = FaultInjectionController.Trigger.FIRST_ATTEMPT,
            ),
        )

        fun run(): List<String> {
            val tracker = FaultInjectionController.AttemptTracker()
            val commands = listOf("a", "b", "a", "b", "a")
            return commands.map { commandId ->
                when (val decision = FaultInjectionController.decide(schedule, tracker, commandId)) {
                    is FaultInjectionController.Decision.Inject -> "${decision.action}"
                    FaultInjectionController.Decision.Pass -> "pass"
                    is FaultInjectionController.Decision.Refused -> "refused"
                }
            }
        }

        assertEquals(run(), run())
        assertEquals(
            listOf("pass", "STALL", "FAIL", "pass", "pass"),
            run(),
        )
    }

    // ------------------------------------------------------------------
    // Refusals
    // ------------------------------------------------------------------

    @Test
    fun pausedScheduleRefusesWithoutTracking() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "cmd-1",
                action = FaultInjectionController.FaultAction.FAIL,
            ),
            paused = true,
        )

        val decision = FaultInjectionController.decide(schedule, tracker, "cmd-1")
        assertEquals(
            FaultInjectionController.Decision.Refused(FaultInjectionController.FaultRefusal.SCHEDULE_PAUSED),
            decision,
        )
        assertEquals(0, tracker.attemptCount("cmd-1"))
    }

    @Test
    fun expiredScheduleRefuses() {
        val decision = FaultInjectionController.decide(
            schedule = schedule(
                FaultInjectionController.FaultSpec(
                    commandId = "cmd-1",
                    action = FaultInjectionController.FaultAction.FAIL,
                ),
            ),
            tracker = tracker,
            commandId = "cmd-1",
            scheduleExpired = true,
        )

        assertEquals(
            FaultInjectionController.Decision.Refused(FaultInjectionController.FaultRefusal.SCHEDULE_EXPIRED),
            decision,
        )
    }

    @Test
    fun unknownCommandRefusesAndNeverInjects() {
        val decision = FaultInjectionController.decide(
            schedule = schedule(),
            tracker = tracker,
            commandId = "unscripted",
        )

        assertEquals(
            FaultInjectionController.Decision.Refused(FaultInjectionController.FaultRefusal.UNKNOWN_COMMAND),
            decision,
        )
    }

    @Test
    fun scheduleConstructorRefusesDuplicatesAndOversize() {
        try {
            schedule(
                FaultInjectionController.FaultSpec("a", FaultInjectionController.FaultAction.FAIL),
                FaultInjectionController.FaultSpec("a", FaultInjectionController.FaultAction.HANG),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("duplicate"))
        }

        val oversized = (1..200).map {
            FaultInjectionController.FaultSpec("cmd-$it", FaultInjectionController.FaultAction.FAIL)
        }
        try {
            FaultInjectionController.FaultSchedule(oversized)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("exceeds"))
        }
    }

    // ------------------------------------------------------------------
    // Replay helper
    // ------------------------------------------------------------------

    @Test
    fun replayUntilPassCountsExactRetryStorm() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "command",
                action = FaultInjectionController.FaultAction.FAIL,
                trigger = FaultInjectionController.Trigger.EVERY_ATTEMPT,
                errorLabel = "boom",
            ),
        )

        // 4 scripted failures then the harness's capped pass at attempt 5.
        val injected = FaultInjectionController.replayUntilPass(
            schedule = schedule,
            maxAttempts = 4,
            commandId = "command",
        )
        assertEquals(listOf("boom", "boom", "boom", "boom"), injected)
    }

    @Test
    fun replayStopsAtTheFirstPass() {
        val schedule = schedule(
            FaultInjectionController.FaultSpec(
                commandId = "command",
                action = FaultInjectionController.FaultAction.FAIL,
                trigger = FaultInjectionController.Trigger.EXACT_ATTEMPT,
                nthAttempt = 2,
            ),
        )

        val injected = FaultInjectionController.replayUntilPass(
            schedule = schedule,
            maxAttempts = 10,
            commandId = "command",
        )
        // Attempt 1 passes; the injected fault would land on attempt 2, which
        // the replay never reaches after a pass.
        assertEquals(emptyList<String>(), injected)
    }

    @Test
    fun attemptTrackingIsPerCommand() {
        val tracker = FaultInjectionController.AttemptTracker()
        assertEquals(1, tracker.nextAttempt("x"))
        assertEquals(2, tracker.nextAttempt("x"))
        assertEquals(1, tracker.nextAttempt("y"))
        assertEquals(2, tracker.attemptCount("x"))
        assertEquals(1, tracker.attemptCount("y"))
        assertEquals(0, tracker.attemptCount("z"))
    }
}
