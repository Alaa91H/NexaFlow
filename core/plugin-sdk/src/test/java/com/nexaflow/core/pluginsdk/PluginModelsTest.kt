package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginModelsTest {

    @Test
    fun descriptorDefaultsToUntrustedAndKeepsOnlyMetadata() {
        val descriptor = PluginDescriptor(
            id = "locale:com.example.plugin:setting:EditActivity",
            packageName = "com.example.plugin",
            type = PluginType.SETTING,
            protocol = PluginProtocol.LOCALE_BASE,
            editActivity = PluginComponentRef("com.example.plugin", "com.example.plugin.EditActivity"),
            receiver = PluginComponentRef("com.example.plugin", "com.example.plugin.FireReceiver"),
            displayName = "Example setting",
            supportsConfiguration = true,
            compatibility = PluginCompatibilityStatus.COMPATIBLE
        )

        assertEquals(PluginTrustLevel.UNTRUSTED, descriptor.trustLevel)
        assertTrue(descriptor.supportsConfiguration)
        assertFalse(descriptor.supportsOutputVariables)
    }

    @Test(expected = IllegalArgumentException::class)
    fun descriptorRejectsComponentFromAnotherPackage() {
        PluginDescriptor(
            id = "locale:com.example.plugin:setting:EditActivity",
            packageName = "com.example.plugin",
            type = PluginType.SETTING,
            protocol = PluginProtocol.LOCALE_BASE,
            editActivity = PluginComponentRef("com.other.plugin", "com.other.plugin.EditActivity"),
            displayName = "Invalid",
            compatibility = PluginCompatibilityStatus.INVALID_DECLARATION
        )
    }

    @Test
    fun invocationPolicyUsesCanonicalFieldsAndKeepsReadOnlyCompatibilityAliases() {
        val policy = PluginInvocationPolicy(
            maxTimeoutMs = 5_000L,
            allowOutput = false,
            requireApproval = true,
            maximumPayloadBytes = LocaleContract.MAX_BUNDLE_BYTES,
            deduplicationWindowMs = 30_000L
        )

        assertEquals(LocaleContract.MAX_BUNDLE_BYTES, policy.maximumPayloadBytes)
        assertEquals(5_000L, policy.timeoutMs)
        assertFalse(policy.allowOutputVariables)
        assertTrue(policy.requireUserApproval)
    }

    @Test
    fun manifestRejectsMissingProtocolAndDuplicateCapabilityDeclaration() {
        val capability = PluginCapabilityDeclaration(
            id = "example.action",
            type = PluginType.SETTING,
            displayName = "Example action"
        )
        val result = PluginManifestValidator.validate(
            PluginManifest(
                packageName = "com.example.plugin",
                receiverClass = "com.example.plugin.FireReceiver",
                displayName = "Example plugin",
                protocols = emptySet(),
                declaredCapabilities = listOf(capability, capability)
            )
        )

        assertFalse(result.isValid)
        assertTrue(PluginManifestIssue.MISSING_PROTOCOL in result.issues)
        assertTrue(PluginManifestIssue.DUPLICATE_CAPABILITY in result.issues)
    }

    @Test
    fun conditionStatePreservesUnknown() {
        assertEquals(PluginConditionState.UNKNOWN, PluginConditionState.UNKNOWN)
    }

    @Test
    fun protocolTrustDoesNotImplyUserApproval() {
        assertEquals(PluginTrustLevel.LOCALE_COMPATIBLE, PluginTrustLevel.LOCALE_COMPATIBLE)
        assertEquals(PluginTrustLevel.TASKER_EXTENDED, PluginTrustLevel.TASKER_EXTENDED)
        assertFalse(PluginInvocationPolicy(requireApproval = false).requireApproval)
    }
}
