package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRiskPolicyTest {

    @Test
    fun requiresExplicitApproval_only_for_reviewed_command_plugins() {
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.termux.tasker"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.ADBPlugin"))
        assertFalse(PluginRiskPolicy.requiresHighRiskApproval("com.joaomgcd.autocast"))
        assertFalse(PluginRiskPolicy.requiresHighRiskApproval("com.example.localeplugin"))
    }
}
