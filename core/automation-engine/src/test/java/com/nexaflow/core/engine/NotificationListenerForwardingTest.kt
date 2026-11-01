package com.nexaflow.core.engine

import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.execution.NotificationAccess
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotificationListenerService

/**
 * The bound service is a thin forwarder; everything it decides (which active
 * notification matters, which posted event is silenced, what reaches the
 * monitor and with which category) lives in the internal functions these tests
 * drive, and the service methods themselves are exercised through a real
 * Robolectric-built instance. That is the contract the engine depends on when
 * the system hands it a notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationListenerForwardingTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        runBlocking { runtimeStore.clear(TASK_ID) }
        NotificationAccess.clearBlockedState()
    }

    @After
    fun tearDown() = runBlocking {
        runtimeStore.clear(TASK_ID)
        NotificationAccess.clearBlockedState()
    }

    private fun task() = testAutomation(
        id = TASK_ID,
        triggers = listOf(
            Trigger(
                TriggerType.NOTIFICATION,
                mapOf("packages" to OTHER_PACKAGE, "event" to "POSTED")
            )
        )
    ).copy(actions = emptyList())

    private fun monitorFor(): NotificationTriggerMonitor {
        val repository = FakeRepository(listOf(task()))
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        return NotificationTriggerMonitor(
            repository = repository,
            executionEngine = engine,
            runtimeStore = runtimeStore,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            scope = CoroutineScope(Dispatchers.Default)
        )
    }

    private fun notification(title: String, text: String, category: String? = null): Notification =
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (category != null) setCategory(category) }
            .build()

    // The system constructs these itself; on a test JVM the ten-argument
    // constructor (the only one besides Parcel) is the way to build one, and it
    // is deprecated in API 37 in favour of the internal builder.
    @Suppress("DEPRECATION")
    private fun statusBarNotification(
        packageName: String,
        id: Int,
        title: String,
        text: String,
        category: String? = null,
    ): StatusBarNotification = StatusBarNotification(
        packageName,
        packageName,
        id,
        "tag-$id",
        10_000 + id,
        1_000,
        0,
        notification(title, text, category),
        UserHandle.getUserHandleForUid(10_000 + id),
        0L,
    )

    /**
     * Builds the real service. `get()` skips `onCreate`, so the Hilt-generated
     * base class never asks an application for a component the test does not
     * have; the monitor is what Hilt would have injected anyway.
     */
    private fun serviceWith(monitor: NotificationTriggerMonitor): NotificationListener =
        Robolectric.buildService(NotificationListener::class.java).get().apply {
            this.monitor = monitor
        }

    private fun shadowOf(service: NotificationListener): ShadowNotificationListenerService =
        Shadow.extract(service)

    @Test
    fun `the projection keeps other apps' notifications and drops our own`() {
        val own = statusBarNotification(context.packageName, 1, "self", "self")
        val foreign = statusBarNotification(
            OTHER_PACKAGE,
            2,
            "noisy",
            "laundry is done",
            Notification.CATEGORY_MESSAGE,
        )

        val projected = arrayOf(own, foreign).toActiveNotifications(context.packageName)

        assertEquals(1, projected.size)
        val active = projected.single()
        assertEquals("noisy", active.title)
        assertEquals("laundry is done", active.text)
        assertEquals(Notification.CATEGORY_MESSAGE, active.category)
        assertTrue(active.key.endsWith("|$OTHER_PACKAGE|2|tag-2|10002"))
    }

    /**
     * After a listener restart the service re-reads Android's active list and
     * hands it to the monitor: an occurrence whose notification is no longer
     * there must exit instead of staying "active" forever.
     */
    @Test
    fun `connecting reconciles a stale occurrence against the active list`() = runBlocking {
        val monitor = monitorFor()
        val sbn = statusBarNotification(OTHER_PACKAGE, 3, "gone", "body")
        monitor.onNotificationPosted(sbn.key, sbn.packageName, "gone", "body", null)
        waitUntil { runtimeStore.current(TASK_ID) != null }

        // The shadow's active list is empty, so the durable occurrence has no
        // notification behind it any more.
        val service = serviceWith(monitor)
        service.onListenerConnected()

        waitUntil { runtimeStore.current(TASK_ID) == null }
    }

    @Test
    fun `a posted notification crosses the service into the monitor`() = runBlocking {
        val service = serviceWith(monitorFor())

        service.onNotificationPosted(statusBarNotification(OTHER_PACKAGE, 7, "posted", "first"))

        waitUntil { runtimeStore.current(TASK_ID) != null }
        assertEquals(
            AutomationRuntimeLifecycleState.ACTIVE,
            runtimeStore.current(TASK_ID)?.lifecycleState
        )
    }

    @Test
    fun `a removed notification ends the occurrence through the service`() = runBlocking {
        val service = serviceWith(monitorFor())
        val sbn = statusBarNotification(OTHER_PACKAGE, 11, "transient", "body")

        service.onNotificationPosted(sbn)
        waitUntil { runtimeStore.current(TASK_ID) != null }

        service.onNotificationRemoved(sbn)

        waitUntil { runtimeStore.current(TASK_ID) == null }
    }

    @Test
    fun `a blocked package is cancelled instead of being forwarded`() = runBlocking {
        val monitor = monitorFor()
        NotificationAccess.setBlocked(OTHER_PACKAGE, true)

        handleNotificationPosted(
            sbn = statusBarNotification(OTHER_PACKAGE, 8, "silenced", "never again"),
            ownPackage = context.packageName,
            monitor = monitor,
            cancel = {},
        )

        // Nothing reached the monitor: the task never started.
        assertTrue(runtimeStore.current(TASK_ID) == null)
    }

    @Test
    fun `our own notifications never reach the monitor`() = runBlocking {
        val monitor = monitorFor()
        val cancelled = mutableListOf<String>()

        handleNotificationPosted(
            sbn = statusBarNotification(context.packageName, 9, "reminder", "mine"),
            ownPackage = context.packageName,
            monitor = monitor,
            cancel = { key -> cancelled += key },
        )

        assertTrue(cancelled.isEmpty())
        assertTrue(runtimeStore.current(TASK_ID) == null)
    }

    @Test
    fun `removing the exact notification ends the occurrence it opened`() = runBlocking {
        val monitor = monitorFor()
        val sbn = statusBarNotification(OTHER_PACKAGE, 12, "transient", "body")

        handleNotificationPosted(sbn, context.packageName, monitor, cancel = {})
        waitUntil { runtimeStore.current(TASK_ID) != null }

        handleNotificationRemoved(sbn, monitor)

        waitUntil { runtimeStore.current(TASK_ID) == null }
    }

    private companion object {
        const val TASK_ID = "listener-forwarding"
        const val OTHER_PACKAGE = "com.example.messaging"
        const val CHANNEL = "messages"
    }
}
