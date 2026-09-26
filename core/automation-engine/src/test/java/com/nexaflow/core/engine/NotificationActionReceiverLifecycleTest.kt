package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveExecutionStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationActionReceiverLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            runtimeStore.clear("notification-revert")
            runtimeStore.clear("notification-legacy")
            ActiveExecutionStore(context).clear("notification-revert")
            ActiveExecutionStore(context).clear("notification-legacy")
        }
    }

    private fun receiverFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): NotificationActionReceiver {
        val engine = testEngine(context, history)
        return NotificationActionReceiver().apply {
            executionEngine = engine
            runtimeStore = this@NotificationActionReceiverLifecycleTest.runtimeStore
            exitCoordinator = ExitCoordinator(
                this@NotificationActionReceiverLifecycleTest.runtimeStore,
                engine,
                repository,
                history
            )
        }
    }

    @Test
    fun `manual revert consumes durable occurrence exactly once`() = runBlocking {
        val automation = testAutomation("notification-revert", emptyList())
            .copy(actions = emptyList())
        val repository = FakeRepository(listOf(automation))
        val history = RecordingHistory()
        val receiver = receiverFor(repository, history)

        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = automation.id,
                occurrenceId = "notification-revert:occurrence",
                source = "sensor",
                sourceKey = "notification-revert|PROXIMITY",
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )

        receiver.executeResolvedAction(automation, revert = true)
        receiver.executeResolvedAction(automation, revert = true)

        assertNull(runtimeStore.current(automation.id))
        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `manual revert preserves legacy active-run fallback`() = runBlocking {
        val automation = testAutomation("notification-legacy", emptyList())
            .copy(actions = emptyList())
        val repository = FakeRepository(listOf(automation))
        val history = RecordingHistory()
        val receiver = receiverFor(repository, history)
        ActiveExecutionStore(context).markStarted(automation.id)

        receiver.executeResolvedAction(automation, revert = true)

        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }
}
