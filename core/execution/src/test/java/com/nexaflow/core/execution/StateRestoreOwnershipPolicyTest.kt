package com.nexaflow.core.execution

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateRestoreOwnershipPolicyTest {
    @Test
    fun restoreOnlyWhenTheCurrentValueStillMatchesTheAutomationWrite() {
        assertTrue(StateRestoreOwnershipPolicy.mayRestore("0.65", "0.65"))
        assertFalse(StateRestoreOwnershipPolicy.mayRestore("0.65", "0.8"))
        assertFalse(StateRestoreOwnershipPolicy.mayRestore("0.65", null))
        assertFalse(StateRestoreOwnershipPolicy.mayRestore(null, "0.65"))
    }
}
