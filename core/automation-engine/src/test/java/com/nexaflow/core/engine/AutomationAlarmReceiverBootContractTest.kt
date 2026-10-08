package com.nexaflow.core.engine

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationAlarmReceiverBootContractTest {
    @Test
    fun `only unlocked device boot performs boot recovery`() {
        assertTrue(AutomationAlarmReceiver.isBootRecoveryAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(AutomationAlarmReceiver.isBootRecoveryAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(AutomationAlarmReceiver.isBootRecoveryAction(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    }

    @Test
    fun `package replacement rebuilds schedules without firing boot triggers`() {
        assertTrue(AutomationAlarmReceiver.firesBootTriggers(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(AutomationAlarmReceiver.firesBootTriggers(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(AutomationAlarmReceiver.firesBootTriggers(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    }
}
