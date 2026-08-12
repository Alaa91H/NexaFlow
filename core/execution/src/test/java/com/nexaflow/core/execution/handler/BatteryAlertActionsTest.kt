package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.ACTION_DISMISS_NOTIFICATION
import com.nexaflow.core.execution.ACTION_RUN_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.EXTRA_AUTOMATION_ID
import com.nexaflow.core.execution.EXTRA_NOTIFICATION_ID
import com.nexaflow.core.execution.NotificationActionButtons
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.runBlocking

/**
 * Verifies battery alert notifications carry the interactive action buttons:
 * \"Run task now\" (routes to the engine with the enclosing task id) and
 * \"Dismiss\" (cancels the posted alert through NotificationDismissReceiver).
 */
@RunWith(RobolectricTestRunner::class)
class BatteryAlertActionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    private val handler = NotificationActionsHandler()

    @Before
    fun setUp() {
        manager.cancelAll()
    }

    private fun controller(): SystemController = SystemController(
        context,
        RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun ctx(
        automationId: String?,
        revertOnExit: Boolean = false
    ) = ActionExecutionContext(
        appContext = context,
        controller = controller(),
        notificationSettings = NotificationSettings(enabled = true, executionEnabled = true),
        automationId = automationId,
        revertOnExit = revertOnExit
    )

    private val alertAction = Action(
        type = ActionType.BATTERY_ALERTS,
        config = mapOf("message" to "Battery low")
    )

    @Test
    fun batteryAlert_withTaskId_showsRunNowAndDismiss() = runBlocking {
        handler.execute(alertAction, ctx("task-11"))
        val notification = shadowOf(manager).getNotification(SystemController.ACTION_NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(2, notification!!.actions.size)
        assertEquals("Run task now", notification.actions[0].title.toString())
        assertEquals("Dismiss", notification.actions[1].title.toString())

        val runIntent = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertEquals(ACTION_RUN_TASK_FROM_NOTIFICATION, runIntent.action)
        assertEquals(NotificationActionButtons.RECEIVER_CLASS, runIntent.component?.className)
        assertEquals("task-11", runIntent.getStringExtra(EXTRA_AUTOMATION_ID))

        val dismissIntent = shadowOf(notification.actions[1].actionIntent).savedIntent
        assertEquals(ACTION_DISMISS_NOTIFICATION, dismissIntent.action)
        assertEquals(SystemController.ACTION_NOTIFICATION_ID, dismissIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
    }

    @Test
    fun batteryAlert_withoutTaskId_showsOnlyDismiss() = runBlocking {
        handler.execute(alertAction, ctx(null))
        val notification = shadowOf(manager).getNotification(SystemController.ACTION_NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(1, notification!!.actions.size)
        assertEquals("Dismiss", notification.actions[0].title.toString())
    }

    @Test
    fun batteryAlert_withRevertTask_appendsRevertButton() = runBlocking {
        handler.execute(alertAction, ctx("task-11", revertOnExit = true))
        val notification = shadowOf(manager).getNotification(SystemController.ACTION_NOTIFICATION_ID)
        assertNotNull(notification)
        // run-now + dismiss + restore-original-state
        assertEquals(3, notification!!.actions.size)
        assertEquals("Restore original state", notification.actions[2].title.toString())
    }
}
