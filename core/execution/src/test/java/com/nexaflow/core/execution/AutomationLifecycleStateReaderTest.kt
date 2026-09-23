package com.nexaflow.core.execution

import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.datastore.ExitReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationLifecycleStateReaderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun activeRuntimeStateIsProjectedWithoutInternalOccurrenceData() {
        runBlocking {
        val id = "lifecycle-reader-active"
        val store = AutomationRuntimeStore(context)
        store.clear(id)
        assertTrue(
            store.activate(
                AutomationRuntimeState(
                    automationId = id,
                    occurrenceId = "private-occurrence",
                    source = "test",
                    sourceKey = "private-source-key",
                    lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                    activatedAt = 1_000L,
                    expectedEndAt = 2_000L
                )
            )
        )

        val state = AutomationLifecycleStateReader.read(context, id)

        assertEquals(AutomationLifecycleDisplayState.ACTIVE, state?.state)
        assertEquals(1_000L, state?.activatedAt)
        assertEquals(2_000L, state?.expectedEndAt)
        assertEquals(0, state?.exitAttempt)
        assertNull(state?.exitReason)

        store.clear(id)
        }
    }

    @Test
    fun failedExitIncludesTypedReasonAndAttempt() {
        runBlocking {
        val id = "lifecycle-reader-failed"
        val store = AutomationRuntimeStore(context)
        store.clear(id)
        assertTrue(
            store.activate(
                AutomationRuntimeState(
                    automationId = id,
                    occurrenceId = "occurrence",
                    source = "test",
                    sourceKey = "source-key",
                    lifecycleState = AutomationRuntimeLifecycleState.EXIT_FAILED,
                    activatedAt = 5_000L,
                    exitStartedAt = 6_000L,
                    exitAttempt = 2,
                    exitReason = ExitReason.TRIGGER_FALSE,
                    lastError = "internal failure text"
                )
            )
        )

        val state = AutomationLifecycleStateReader.read(context, id)

        assertEquals(AutomationLifecycleDisplayState.EXIT_FAILED, state?.state)
        assertEquals(2, state?.exitAttempt)
        assertEquals(AutomationExitReason.TRIGGER_FALSE, state?.exitReason)
        assertEquals(6_000L, state?.exitStartedAt)

        store.clear(id)
        }
    }

    @Test
    fun missingRuntimeStateReturnsNull() {
        runBlocking {
        val id = "lifecycle-reader-missing"
        AutomationRuntimeStore(context).clear(id)

        assertNull(AutomationLifecycleStateReader.read(context, id))
        }
    }
}
