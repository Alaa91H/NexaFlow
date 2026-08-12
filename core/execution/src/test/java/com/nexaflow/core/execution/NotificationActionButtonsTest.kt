package com.nexaflow.core.execution

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the notification action button protocol:
 *  - `action_buttons` config JSON round-trips through [NotificationActionButton]
 *  - malformed / blank / partially-invalid values degrade to safe lists
 *  - [NotificationActionButtons] builds PendingIntents that route to the
 *    [NotificationActionReceiver] class with the right automation id extra
 */
@RunWith(RobolectricTestRunner::class)
class NotificationActionButtonsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun roundTrip_preservesLabelAndId() {
        val buttons = listOf(
            NotificationActionButton("Turn on Wi-Fi", "task-1"),
            NotificationActionButton("Silent mode", "task-2")
        )
        val json = NotificationActionButton.toConfig(buttons)
        assertEquals(buttons, NotificationActionButton.fromConfig(json))
    }

    @Test
    fun roundTrip_preservesReplyVariable() {
        val buttons = listOf(
            NotificationActionButton("Ask for input", "task-1", replyVariable = "MyReply"),
            NotificationActionButton("Plain run", "task-2")
        )
        val json = NotificationActionButton.toConfig(buttons)
        val parsed = NotificationActionButton.fromConfig(json)
        assertEquals(buttons, parsed)
        assertEquals("MyReply", parsed[0].replyVariable)
        assertEquals(null, parsed[1].replyVariable)
    }

    @Test
    fun fromConfig_legacyEntriesWithoutReplyVariableParseAsNull() {
        // Old saved buttons (no replyVariable key) must still load: the field
        // defaults to null so existing notifications keep working unchanged.
        val json = """[{"label": "Run", "id": "a"}]"""
        val buttons = NotificationActionButton.fromConfig(json)
        assertEquals(1, buttons.size)
        assertEquals(null, buttons.first().replyVariable)
    }

    @Test
    fun buildPendingIntent_carriesReplyVariableWhenConfigured() {
        val plain = NotificationActionButtons.buildPendingIntent(context, "task-1", 0)
        assertEquals(null, shadowOf(plain).savedIntent.getStringExtra(EXTRA_REPLY_VARIABLE))

        val reply = NotificationActionButtons.buildPendingIntent(context, "task-1", 0, "MyReply")
        assertEquals("MyReply", shadowOf(reply).savedIntent.getStringExtra(EXTRA_REPLY_VARIABLE))
    }

    @Test
    fun toNotificationActions_addsRemoteInputOnlyForReplyButtons() {
        val buttons = listOf(
            NotificationActionButton("Ask", "a", replyVariable = "MyReply"),
            NotificationActionButton("Run", "b")
        )
        val actions = NotificationActionButtons.toNotificationActions(context, buttons)
        assertEquals(2, actions.size)
        // The reply button exposes a RemoteInput with the pinned key; the plain
        // run button does not.
        assertEquals(1, actions[0].remoteInputs?.size ?: 0)
        assertEquals(REMOTE_INPUT_REPLY_KEY, actions[0].remoteInputs?.get(0)?.resultKey)
        assertTrue((actions[1].remoteInputs?.size ?: 0) == 0)
    }

    @Test
    fun fromConfig_blankReturnsEmpty() {
        assertTrue(NotificationActionButton.fromConfig(null).isEmpty())
        assertTrue(NotificationActionButton.fromConfig("").isEmpty())
        assertTrue(NotificationActionButton.fromConfig("   ").isEmpty())
    }

    @Test
    fun fromConfig_malformedJsonReturnsEmpty() {
        assertTrue(NotificationActionButton.fromConfig("not-json{{{").isEmpty())
        assertTrue(NotificationActionButton.fromConfig("[\"just a string\"]").isEmpty())
    }

    @Test
    fun fromConfig_skipsEntriesWithoutLabelOrId() {
        // One valid + one missing id + one missing label: only the valid survives.
        val json = """[
            {"label": "Run", "id": "a"},
            {"label": "No id"},
            {"id": "b", "label": ""}
        ]"""
        val buttons = NotificationActionButton.fromConfig(json)
        assertEquals(1, buttons.size)
        assertEquals(NotificationActionButton("Run", "a"), buttons.first())
    }

    @Test
    fun buildPendingIntent_targetsReceiverWithAutomationId() {
        val pending = NotificationActionButtons.buildPendingIntent(context, "task-42", 0)
        assertNotNull(pending)

        // Robolectric exposes the wrapped Intent through the shadow: the action,
        // the explicit receiver component, and the automation id extra must all
        // match what NotificationActionReceiver expects.
        val intent = shadowOf(pending).savedIntent
        assertEquals(ACTION_RUN_TASK_FROM_NOTIFICATION, intent.action)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(NotificationActionButtons.RECEIVER_CLASS, intent.component?.className)
        assertEquals("task-42", intent.getStringExtra(EXTRA_AUTOMATION_ID))
    }

    @Test
    fun buildPendingIntent_immutableFlagSet() {
        val pending = NotificationActionButtons.buildPendingIntent(context, "task-1", 1)
        assertTrue(shadowOf(pending).isImmutable)
        assertTrue((shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE) != 0)
    }

    @Test
    fun toNotificationActions_buildsOneActionPerButton() {
        val buttons = listOf(
            NotificationActionButton("Run A", "a"),
            NotificationActionButton("Run B", "b")
        )
        val actions = NotificationActionButtons.toNotificationActions(context, buttons)
        assertEquals(2, actions.size)
        assertEquals("Run A", actions[0].title)
        assertEquals("Run B", actions[1].title)
        // Each action carries a working PendingIntent.
        assertNotNull(actions[0].actionIntent)
        assertNotNull(actions[1].actionIntent)
    }

    @Test
    fun toNotificationActions_skipsBlankAutomationIds() {
        val buttons = listOf(
            NotificationActionButton("Valid", "a"),
            NotificationActionButton("Blank", "  ")
        )
        val actions = NotificationActionButtons.toNotificationActions(context, buttons)
        assertEquals(1, actions.size)
        assertEquals("Valid", actions.first().title)
    }

    @Test
    fun buildRevertPendingIntent_targetsReceiverWithRevertAction() {
        val pending = NotificationActionButtons.buildRevertPendingIntent(context, "task-9")
        assertNotNull(pending)

        val intent = shadowOf(pending).savedIntent
        assertEquals(ACTION_REVERT_TASK_FROM_NOTIFICATION, intent.action)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(NotificationActionButtons.RECEIVER_CLASS, intent.component?.className)
        assertEquals("task-9", intent.getStringExtra(EXTRA_AUTOMATION_ID))
    }

    @Test
    fun buildRevertPendingIntent_immutableAndStable() {
        val pending = NotificationActionButtons.buildRevertPendingIntent(context, "task-1")
        assertTrue(shadowOf(pending).isImmutable)
        assertTrue((shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE) != 0)
        // Same automation id → same PendingIntent (stable across notifications).
        val again = NotificationActionButtons.buildRevertPendingIntent(context, "task-1")
        assertEquals(pending, again)
    }

    @Test
    fun buildRevertPendingIntent_distinctPerAutomation() {
        // Different tasks must yield independent PendingIntents: a fixed request
        // code + FLAG_UPDATE_CURRENT would let one task's button overwrite
        // another's and restore the wrong task when tapped.
        val a = NotificationActionButtons.buildRevertPendingIntent(context, "task-a")
        val b = NotificationActionButtons.buildRevertPendingIntent(context, "task-b")
        assertTrue(a != b)
        assertEquals("task-a", shadowOf(a).savedIntent.getStringExtra(EXTRA_AUTOMATION_ID))
        assertEquals("task-b", shadowOf(b).savedIntent.getStringExtra(EXTRA_AUTOMATION_ID))
    }

    @Test
    fun dismissAction_literalConstantPinned() {
        // Pin the literal so a rename of both the constant and the builder
        // can't slip through (same gap the run-action contract test closes).
        assertEquals(
            "com.nexaflow.core.execution.action.DISMISS_NOTIFICATION",
            ACTION_DISMISS_NOTIFICATION
        )
    }

    @Test
    fun buildDismissPendingIntent_targetsDismissReceiverWithId() {
        val pending = NotificationActionButtons.buildDismissPendingIntent(context, 3001)
        assertNotNull(pending)

        val intent = shadowOf(pending).savedIntent
        assertEquals(ACTION_DISMISS_NOTIFICATION, intent.action)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(NotificationActionButtons.DISMISS_RECEIVER_CLASS, intent.component?.className)
        assertEquals(3001, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
    }

    @Test
    fun buildDismissPendingIntent_distinctPerNotificationId() {
        // Different notification ids must yield independent PendingIntents so
        // dismissing one notification never cancels another.
        val reminder = NotificationActionButtons.buildDismissPendingIntent(context, 3001)
        val battery = NotificationActionButtons.buildDismissPendingIntent(context, 1001)
        assertTrue(reminder != battery)
        assertEquals(3001, shadowOf(reminder).savedIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
        assertEquals(1001, shadowOf(battery).savedIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
    }

    @Test
    fun buildDismissPendingIntent_immutable() {
        val pending = NotificationActionButtons.buildDismissPendingIntent(context, 1001)
        assertTrue(shadowOf(pending).isImmutable)
        assertTrue((shadowOf(pending).flags and PendingIntent.FLAG_IMMUTABLE) != 0)
    }

    @Test
    fun toRunNowAndDismissActions_includesBothButtonsWithTaskId() {
        val actions = NotificationActionButtons.toRunNowAndDismissActions(context, "task-5", 1001)
        assertEquals(2, actions.size)
        assertEquals("Run task now", actions[0].title)
        assertEquals("Dismiss", actions[1].title)

        val runIntent = shadowOf(actions[0].actionIntent!!).savedIntent
        assertEquals(ACTION_RUN_TASK_FROM_NOTIFICATION, runIntent.action)
        assertEquals(NotificationActionButtons.RECEIVER_CLASS, runIntent.component?.className)
        assertEquals("task-5", runIntent.getStringExtra(EXTRA_AUTOMATION_ID))

        val dismissIntent = shadowOf(actions[1].actionIntent!!).savedIntent
        assertEquals(ACTION_DISMISS_NOTIFICATION, dismissIntent.action)
        assertEquals(1001, dismissIntent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
    }

    @Test
    fun toRunNowAndDismissActions_omitsRunNowWithoutTaskId() {
        // Standalone notification (no enclosing task): only Dismiss makes sense.
        val actions = NotificationActionButtons.toRunNowAndDismissActions(context, null, 1001)
        assertEquals(1, actions.size)
        assertEquals("Dismiss", actions[0].title)
    }

    @Test
    fun toRunNowAndDismissActions_runNowDistinctPerTask() {
        // Run-now PendingIntents must be independent per task: a fixed request
        // code + FLAG_UPDATE_CURRENT would let task B's button overwrite task
        // A's and run the wrong task from an old notification.
        val a = NotificationActionButtons.buildRunNowPendingIntent(context, "task-a")
        val b = NotificationActionButtons.buildRunNowPendingIntent(context, "task-b")
        assertTrue(a != b)
        assertEquals("task-a", shadowOf(a).savedIntent.getStringExtra(EXTRA_AUTOMATION_ID))
        assertEquals("task-b", shadowOf(b).savedIntent.getStringExtra(EXTRA_AUTOMATION_ID))
    }

    @Test
    fun withRevertActionIfNeeded_appendsOnlyWhenTaskReverts() {
        val existing = NotificationActionButtons.toNotificationActions(
            context,
            listOf(NotificationActionButton("Run", "a"))
        )

        // No revert on exit → untouched (user buttons only).
        val plain = NotificationActionButtons.withRevertActionIfNeeded(
            context, existing, "a", revertOnExit = false, label = "Restore original state"
        )
        assertEquals(existing, plain)

        // No automation id available → untouched (defensive).
        val noId = NotificationActionButtons.withRevertActionIfNeeded(
            context, existing, null, revertOnExit = true, label = "Restore original state"
        )
        assertEquals(existing, noId)

        // Revert on exit + id → the restore button is appended last.
        val reverted = NotificationActionButtons.withRevertActionIfNeeded(
            context, existing, "a", revertOnExit = true, label = "Restore original state"
        )
        assertEquals(2, reverted.size)
        assertEquals("Run", reverted[0].title)
        assertEquals("Restore original state", reverted[1].title)
        val revertIntent = shadowOf(reverted[1].actionIntent!!).savedIntent
        assertEquals(ACTION_REVERT_TASK_FROM_NOTIFICATION, revertIntent.action)
        assertEquals("a", revertIntent.getStringExtra(EXTRA_AUTOMATION_ID))
    }
}
