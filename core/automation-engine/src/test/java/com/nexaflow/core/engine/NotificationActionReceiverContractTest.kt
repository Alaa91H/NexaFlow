package com.nexaflow.core.engine

import com.nexaflow.core.execution.ACTION_RUN_TASK_FROM_NOTIFICATION
import com.nexaflow.core.execution.NotificationActionButtons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the string-based coupling between the PendingIntent builder (in
 * core/execution) and this receiver: `NotificationActionButtons.RECEIVER_CLASS`
 * names this class by string, so a rename/move would otherwise fail silently
 * at runtime (the button just does nothing). This test fails the build the
 * moment the class or the action constant drifts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
}
