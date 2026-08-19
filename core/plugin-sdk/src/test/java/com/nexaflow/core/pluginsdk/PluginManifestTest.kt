package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestTest {

    @Test
    fun validatesCompatibleLocaleManifest() {
        val result = PluginManifestValidator.validate(
            PluginManifest(
                packageName = "com.example.plugin",
                receiverClass = "com.example.plugin.FireReceiver",
                displayName = "Example plugin",
                declaredActionIds = setOf("example.flash", "example.notify")
            )
        )

        assertTrue(result.isValid)
    }

    @Test
    fun rejectsIncompatibleOrUnsafeManifestFields() {
        val result = PluginManifestValidator.validate(
            PluginManifest(
                packageName = "not a package",
                receiverClass = "bad receiver",
                displayName = "",
                minimumHostProtocolVersion = PluginConfigParser.SDK_VERSION + 1,
                declaredActionIds = setOf("../../shell")
            )
        )

        assertFalse(result.isValid)
        assertTrue(PluginManifestIssue.UNSUPPORTED_PROTOCOL in result.issues)
        assertTrue(PluginManifestIssue.INVALID_ACTION_ID in result.issues)
    }
}
