package com.nexaflow.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationRuntimeStoreTest {

    private lateinit var context: Context
    private lateinit var store: AutomationRuntimeStore

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            store = AutomationRuntimeStore(context)
            store.clear("automation-a")
            store.clearSchedule("automation-a")
        }
    }

    private fun state(
        occurrenceId: String = "occurrence-1",
        lifecycle: AutomationRuntimeLifecycleState = AutomationRuntimeLifecycleState.ACTIVE
    ) = AutomationRuntimeState(
        automationId = "automation-a",
        occurrenceId = occurrenceId,
        source = "connectivity",
        sourceKey = "automation-a|CONNECTED",
        lifecycleState = lifecycle,
        activatedAt = 10L
    )

    @Test
    fun `activation never overwrites a previously active occurrence`() = runBlocking {
        assertTrue(store.activate(state("old-occurrence")))
        assertFalse(store.activate(state("new-occurrence")))

        assertEquals("old-occurrence", store.current("automation-a")?.occurrenceId)
    }

    @Test
    fun `only one caller claims an active occurrence for exit`() = runBlocking {
        assertTrue(store.activate(state()))

        val first = store.claimExit("automation-a", "occurrence-1", ExitReason.TRIGGER_FALSE, 20L)
        val second = store.claimExit("automation-a", "occurrence-1", ExitReason.TIME_WINDOW_ENDED, 21L)

        assertTrue(first is ExitClaim.Claimed)
        assertEquals(1, (first as ExitClaim.Claimed).state.exitAttempt)
        assertTrue(second is ExitClaim.AlreadyExiting)
        assertTrue(store.completeExit("automation-a", "occurrence-1"))
        assertTrue(store.statesForTest().isEmpty())
    }

    @Test
    fun `concurrent callers still receive exactly one claimed exit`() = runBlocking {
        assertTrue(store.activate(state()))

        val claims = awaitAll(
            async(Dispatchers.Default) {
                store.claimExit("automation-a", "occurrence-1", ExitReason.TRIGGER_FALSE, 20L)
            },
            async(Dispatchers.Default) {
                store.claimExit("automation-a", "occurrence-1", ExitReason.TIME_WINDOW_ENDED, 21L)
            }
        )

        assertEquals(1, claims.count { it is ExitClaim.Claimed })
        assertEquals(1, claims.count { it is ExitClaim.AlreadyExiting })
    }

    @Test
    fun `stale occurrence cannot claim or clear a newer lifecycle`() = runBlocking {
        assertTrue(store.activate(state("new-occurrence")))

        assertTrue(
            store.claimExit("automation-a", "old-occurrence", ExitReason.TIME_WINDOW_ENDED, 20L)
                is ExitClaim.OccurrenceMismatch
        )
        assertFalse(store.completeExit("automation-a", "old-occurrence"))
        assertEquals("new-occurrence", store.current("automation-a")?.occurrenceId)
    }

    @Test
    fun `failed exit remains observable and requires explicit recovery claim`() = runBlocking {
        assertTrue(store.activate(state()))
        assertTrue(store.claimExit("automation-a", "occurrence-1", ExitReason.TRIGGER_FALSE, 20L) is ExitClaim.Claimed)
        assertTrue(store.failExit("automation-a", "occurrence-1", ExitReason.TRIGGER_FALSE, "permission denied", 21L))

        val failed = checkNotNull(store.current("automation-a"))
        assertEquals(AutomationRuntimeLifecycleState.EXIT_FAILED, failed.lifecycleState)
        assertEquals("permission denied", failed.lastError)
        assertTrue(store.claimExit("automation-a", "occurrence-1", ExitReason.TRIGGER_FALSE, 22L) is ExitClaim.RecoveryRequired)
        assertTrue(
            store.claimFailedExitForRecovery(
                "automation-a",
                "occurrence-1",
                ExitReason.PROCESS_RECOVERY,
                23L
            ) is ExitClaim.Claimed
        )
    }

    @Test
    fun `multiple schedule occurrences retain identity and stale generation is rejected`() = runBlocking {
        val first = ScheduledAutomationOccurrence(
            automationId = "automation-a",
            occurrenceId = "time:100:200",
            generation = "first-generation",
            windowStartAt = 100L,
            windowEndAt = 200L
        )
        val next = ScheduledAutomationOccurrence(
            automationId = "automation-a",
            occurrenceId = "time:300:400",
            generation = "next-generation",
            windowStartAt = 300L,
            windowEndAt = 400L
        )
        assertTrue(store.registerSchedule(first))
        assertTrue(store.registerSchedule(next))

        assertEquals(2, store.schedulesFor("automation-a").size)
        assertTrue(store.matchesSchedule("automation-a", first.occurrenceId, first.generation, 200L))
        assertFalse(store.matchesSchedule("automation-a", first.occurrenceId, first.generation, 201L))
        assertFalse(store.matchesSchedule("automation-a", first.occurrenceId, "stale-generation", 200L))
        store.clearScheduleOccurrence("automation-a", first.occurrenceId)
        assertFalse(store.matchesSchedule("automation-a", first.occurrenceId, first.generation, 200L))
        assertTrue(store.matchesSchedule("automation-a", next.occurrenceId, next.generation, 400L))
    }
}
