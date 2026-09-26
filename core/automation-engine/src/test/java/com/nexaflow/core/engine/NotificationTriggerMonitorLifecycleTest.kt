package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationTriggerMonitorLifecycleTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking {
            listOf("posted-task", "removed-task", "restart-task").forEach {
                runtimeStore.clear(it)
            }
        }
    }

    private fun automation(
        id: String,
        event: String
    ) = testAutomation(
        id = id,
        triggers = listOf(
            Trigger(
                TriggerType.NOTIFICATION,
                mapOf(
                    "packages" to "com.example.app",
                    "event" to event
                )
            )
        )
    ).copy(actions = emptyList())

    private fun monitorFor(
        repository: FakeRepository,
        history: RecordingHistory
    ): NotificationTriggerMonitor {
        val engine = testEngine(context, history)
        return NotificationTriggerMonitor(
            repository = repository,
            executionEngine = engine,
            runtimeStore = runtimeStore,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    @Test
    fun `posted occurrence exits only when its exact notification is removed`() = runBlocking {
        val task = automation("posted-task", "POSTED")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.onNotificationPosted(
            notificationKey = "key-1",
            packageName = "com.example.app",
            title = "hello",
            text = "body"
        )
        waitUntil { runtimeStore.current(task.id) != null }

        monitor.onNotificationRemoved(
            notificationKey = "key-2",
            packageName = "com.example.app",
            title = "other",
            text = null
        )
        kotlinx.coroutines.delay(50)
        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(task.id)?.lifecycleState
        )

        monitor.onNotificationRemoved(
            notificationKey = "key-1",
            packageName = "com.example.app",
            title = "hello",
            text = "body"
        )
        waitUntil { runtimeStore.current(task.id) == null }

        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `removed occurrence ends when a matching notification is posted again`() = runBlocking {
        val task = automation("removed-task", "REMOVED")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.onNotificationRemoved(
            notificationKey = "removed-key",
            packageName = "com.example.app",
            title = "gone",
            text = null
        )
        waitUntil { runtimeStore.current(task.id) != null }

        monitor.onNotificationPosted(
            notificationKey = "new-key",
            packageName = "com.example.app",
            title = "back",
            text = null
        )
        waitUntil { runtimeStore.current(task.id) == null }

        assertEquals(1, history.exits.count { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `listener reconnect closes posted occurrence when exact notification vanished`() = runBlocking {
        val task = automation("restart-task", "POSTED")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.onNotificationPosted(
            notificationKey = "restart-key",
            packageName = "com.example.app",
            title = "hello",
            text = null
        )
        waitUntil { runtimeStore.current(task.id) != null }

        monitor.reconcileActiveNotifications(emptyList())

        waitUntil { runtimeStore.current(task.id) == null }
        assertTrue(history.exits.any { it == EXIT_NOOP_MARKER })
    }

    @Test
    fun `listener reconnect preserves exact active posted notification`() = runBlocking {
        val task = automation("restart-task", "POSTED")
        val history = RecordingHistory()
        val monitor = monitorFor(FakeRepository(listOf(task)), history)

        monitor.onNotificationPosted(
            notificationKey = "restart-key",
            packageName = "com.example.app",
            title = "hello",
            text = null
        )
        waitUntil { runtimeStore.current(task.id) != null }

        monitor.reconcileActiveNotifications(
            listOf(
                NotificationTriggerMonitor.ActiveNotification(
                    key = "restart-key",
                    packageName = "com.example.app",
                    title = "hello",
                    text = null
                )
            )
        )
        kotlinx.coroutines.delay(50)

        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(task.id)?.lifecycleState
        )
        assertTrue(history.exits.none { it == EXIT_NOOP_MARKER })
    }
}
