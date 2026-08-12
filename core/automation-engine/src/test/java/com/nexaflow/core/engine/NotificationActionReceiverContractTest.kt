package com.nexaflow.core.engine

import com.nexaflow.core.execution.ACTION_REVERT_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.ACTION_RUN_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.EXTRA_REPLY_VARIABLE
import com.nexaflow.core.execution.NotificationActionButtons
import com.nexaflow.core.execution.REMOTE_INPUT_REPLY_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the string-based coupling between the PendingIntent builder (in
 * core/execution) and this receiver: `NotificationActionButtons.RECEIVER_CLASS`
 * names this class by string, so a rename/move would otherwise fail silently
 * at runtime (the button just does nothing). This test fails the build the
 * moment the class or the action constant drifts.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationActionReceiverContractTest {

    @Test
    fun receiverClassName_resolvesToThisClass() {
        val resolved = Class.forName(NotificationActionButtons.RECEIVER_CLASS)
        assertNotNull(resolved)
        // The class the string names must actually be this receiver, not some
        // accidental same-name collision in another module.
        assertEquals(NotificationActionReceiver::class.java.name, resolved.name)
    }

    @Test
    fun receiverAction_matchesTheBuilderAction() {
        // The receiver guards on this constant; the builder sends this action.
        // A mismatch would make every notification button dead on arrival.
        assertEquals(
            "com.nexaflow.core.execution.action.RUN_TASK_FROM_NOTIFICATION",
            ACTION_RUN_TASK_FROM_NOTIFICATION
        )
        assertTrue(ACTION_RUN_TASK_FROM_NOTIFICATION.isNotBlank())
    }

    @Test
    fun receiverRevertAction_matchesTheBuilderAction() {
        // The "restore original state" button broadcasts this action; the
        // receiver must route it to runExit — a drift breaks the button.
        assertEquals(
            "com.nexaflow.core.execution.action.REVERT_TASK_FROM_NOTIFICATION",
            ACTION_REVERT_TASK_FROM_NOTIFICATION
        )
        assertTrue(ACTION_REVERT_TASK_FROM_NOTIFICATION.isNotBlank())
        // The two actions must stay distinct so run/revert never collide.
        assertTrue(ACTION_REVERT_TASK_FROM_NOTIFICATION != ACTION_RUN_TASK_FROM_NOTIFICATION)
    }

    @Test
    fun replyContract_keysArePinned() {
        // The reply extra and RemoteInput key are baked into the PendingIntent /
        // RemoteInput contract; drifting either one would silently drop replies.
        assertEquals(
            "com.nexaflow.core.execution.extra.REPLY_VARIABLE",
            EXTRA_REPLY_VARIABLE
        )
        assertEquals(
            "com.nexaflow.core.execution.remote_input.reply",
            REMOTE_INPUT_REPLY_KEY
        )
        // The receiver must accept the same extra the builder puts in, so it
        // reads the variable through this constant rather than a literal.
        assertTrue(EXTRA_REPLY_VARIABLE.isNotBlank())
        assertTrue(REMOTE_INPUT_REPLY_KEY.isNotBlank())
    }
}
