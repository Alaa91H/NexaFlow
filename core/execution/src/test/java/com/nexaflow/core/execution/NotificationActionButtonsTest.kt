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
import org.robolectric.annotation.Config

/**
 * Verifies the notification action button protocol:
 *  - `action_buttons` config JSON round-trips through [NotificationActionButton]
 *  - malformed / blank / partially-invalid values degrade to safe lists
 *  - [NotificationActionButtons] builds PendingIntents that route to the
 *    [NotificationActionReceiver] class with the right automation id extra
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
}
