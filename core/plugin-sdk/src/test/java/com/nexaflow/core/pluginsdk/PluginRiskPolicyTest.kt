package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRiskPolicyTest {

    @Test
    fun requiresExplicitApproval_only_for_reviewed_privileged_plugins() {
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.termux.tasker"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.ADBPlugin"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.joaomgcd.autotoolsroot"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("eu.chainfire.lumen"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("mobi.omegacentauri.red"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.oasisfeng.greenify"))
        assertTrue(PluginRiskPolicy.requiresHighRiskApproval("com.catchingnow.icebox"))

        assertFalse(PluginRiskPolicy.requiresHighRiskApproval("com.joaomgcd.autonotification"))
        assertFalse(PluginRiskPolicy.requiresHighRiskApproval("com.joaomgcd.autocast"))
        assertFalse(PluginRiskPolicy.requiresHighRiskApproval("com.example.localeplugin"))
    }
}
