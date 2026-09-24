package com.nexaflow.core.execution.capability

import com.nexaflow.domain.capability.PrivilegeGrantState
import com.nexaflow.domain.capability.PrivilegeObservation
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeStateStoreTest {

    @Test
    fun initialProbePublishesOneCoherentSnapshot() = runTest {
        val events = FakeEventSource()
        var captures = 0
        val store = PrivilegeStateStore(
            scope = this,
            probe = PrivilegeStateProbe { observedAt ->
                captures += 1
                snapshot(observedAt, rootGranted = false)
            },
            eventSource = events,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMs = { 1_000L },
            minRefreshIntervalMs = 0L
        )

        advanceUntilIdle()

        assertEquals(1, captures)
        assertFalse(store.snapshot.value.neverObserved)
        assertEquals(
            PrivilegeGrantState.PERMISSION_REQUIRED,
            store.snapshot.value.stateOf(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT
            )
        )
        store.close()
    }

    @Test
    fun eventInvalidationReReadsRealStateInsteadOfTrustingTheEvent() = runTest {
        val events = FakeEventSource()
        var rootGranted = false
        var now = 1_000L
        val store = PrivilegeStateStore(
            scope = this,
            probe = PrivilegeStateProbe { observedAt ->
                snapshot(observedAt, rootGranted)
            },
            eventSource = events,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMs = { now },
            minRefreshIntervalMs = 0L
        )
        advanceUntilIdle()

        rootGranted = true
        now = 2_000L
        events.emit()
        advanceUntilIdle()

        assertEquals(
            PrivilegeGrantState.GRANTED,
            store.snapshot.value.stateOf(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT
            )
        )
        assertEquals(2_000L, store.snapshot.value.observedAtMs)
        store.close()
    }

    @Test
    fun burstInvalidationsAreCoalescedWithoutLosingTheLatestState() = runTest {
        val events = FakeEventSource()
        var captures = 0
        var rootGranted = false
        var now = 1_000L
        val store = PrivilegeStateStore(
            scope = this,
            probe = PrivilegeStateProbe { observedAt ->
                captures += 1
                snapshot(observedAt, rootGranted)
            },
            eventSource = events,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowMs = { now },
            minRefreshIntervalMs = 500L
        )
        advanceUntilIdle()
        assertEquals(1, captures)

        rootGranted = true
        now = 2_000L
        repeat(20) { events.emit() }
        advanceUntilIdle()

        assertTrue(captures <= 3)
        assertEquals(
            PrivilegeGrantState.GRANTED,
            store.snapshot.value.stateOf(
                PrivilegeSurface.ROOT,
                PrivilegeSnapshot.ENV_ROOT
            )
        )
        store.close()
    }

    private fun snapshot(observedAt: Long, rootGranted: Boolean) = PrivilegeSnapshot(
        observations = listOf(
            PrivilegeObservation(
                surface = PrivilegeSurface.ROOT,
                key = PrivilegeSnapshot.ENV_ROOT,
                state = if (rootGranted) {
                    PrivilegeGrantState.GRANTED
                } else {
                    PrivilegeGrantState.PERMISSION_REQUIRED
                },
                detailCode = if (rootGranted) {
                    "ROOT_UID_ZERO_VERIFIED"
                } else {
                    "ROOT_GRANT_REQUIRED_OR_DENIED"
                }
            )
        ),
        observedAtMs = observedAt
    )

    private class FakeEventSource : PrivilegeStateEventSource {
        private var listener: (() -> Unit)? = null

        override fun start(onChanged: () -> Unit) {
            listener = onChanged
        }

        override fun stop() {
            listener = null
        }

        fun emit() {
            listener?.invoke()
        }
    }
}
