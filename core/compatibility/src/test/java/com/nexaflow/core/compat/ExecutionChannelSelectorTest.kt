package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.core.rom.model.SystemControlResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-6 gate: the production [ExecutionChannelSelector] over the simulated
 * device matrix — auto-selection, channel-aware execution reports and the
 * graceful fallback chain. The pure `selectFor`/`executeFor` paths are the
 * JVM-testable surface of the Context-bound bridge.
 */
class ExecutionChannelSelectorTest {

    // ---- Controllable providers so the report's channel/chain is deterministic ----

    private class FakeProvider(
        override val type: ExecutionProviderType,
        override val baseScore: Int,
        override val supportedCapabilities: Set<RomCapability>,
        private val availableOn: (DeviceProfile) -> Boolean,
        private val outcome: (String) -> SystemControlResult
    ) : ExecutionProvider {
        override fun isAvailable(profile: DeviceProfile): Boolean = availableOn(profile)
        override fun execute(command: String): SystemControlResult = outcome(command)
    }

    private val rootProvider = FakeProvider(
        type = ExecutionProviderType.ROOT,
        baseScore = 80,
        supportedCapabilities = setOf(
            RomCapability.ROOT_SHELL,
            RomCapability.WRITE_SECURE_SETTINGS,
            RomCapability.READ_LOGS,
            RomCapability.FORCE_STOP_PACKAGES
        ),
        availableOn = { it.rootAvailable },
        outcome = { command ->
            if (command == "root-ok") SystemControlResult.ok("root ran it")
            else SystemControlResult.fail("root rejected $command")
        }
    )

    private val shizukuProvider = FakeProvider(
        type = ExecutionProviderType.SHIZUKU,
        baseScore = 70,
        supportedCapabilities = setOf(
            RomCapability.SHIZUKU,
            RomCapability.WRITE_SECURE_SETTINGS,
            RomCapability.READ_LOGS,
            RomCapability.FORCE_STOP_PACKAGES
        ),
        availableOn = { it.shizukuGranted },
        outcome = { command ->
            if (command == "shizuku-ok") SystemControlResult.ok("shizuku ran it")
            else SystemControlResult.fail("shizuku rejected $command")
        }
    )

    private val androidProvider = FakeProvider(
        type = ExecutionProviderType.ANDROID,
        baseScore = 10,
        supportedCapabilities = setOf(
            RomCapability.WRITE_SETTINGS,
            RomCapability.SYSTEM_ALERT_WINDOW,
            RomCapability.DND_ACCESS,
            RomCapability.KILL_BACKGROUND_PROCESSES
        ),
        availableOn = { true },
        outcome = { _ -> SystemControlResult.fail("no shell access") }
    )

    private val selector = ProviderSelector(listOf(androidProvider, shizukuProvider, rootProvider))
    private val channelSelector = ExecutionChannelSelector(selector)

    private fun profile(
        root: Boolean = false,
        shizuku: Boolean = false
    ) = DeviceProfile(
        integrationLevel = IntegrationLevel.NORMAL,
        romFamily = RomFamily.AOSP,
        capabilities = emptySet(),
        rootAvailable = root,
        shizukuGranted = shizuku,
        androidSdk = 34
    )

    // ---- Auto-selection ----

    @Test
    fun selectFor_stockDevice_picksAndroid() {
        assertEquals(
            ExecutionProviderType.ANDROID,
            channelSelector.selectFor(profile())?.type
        )
    }

    @Test
    fun selectFor_rootedDevice_picksRoot() {
        assertEquals(
            ExecutionProviderType.ROOT,
            channelSelector.selectFor(profile(root = true))?.type
        )
    }

    @Test
    fun selectFor_capabilityRestricted_picksBestMatching() {
        val best = channelSelector.selectFor(
            profile(root = true),
            capability = RomCapability.WRITE_SECURE_SETTINGS
        )
        // Root and Android are available; only Root can write secure settings.
        assertEquals(ExecutionProviderType.ROOT, best?.type)
    }

    @Test
    fun selectFor_capabilityUnsatisfied_returnsNull() {
        assertNull(
            channelSelector.selectFor(
                profile(root = true, shizuku = true),
                capability = RomCapability.LINEAGEOS_SDK
            )
        )
    }

    // ---- Channel-aware execution reports ----

    @Test
    fun executeFor_success_reportsChannelAndChain() {
        val report = channelSelector.executeFor(profile(root = true), command = "root-ok")
        assertTrue(report.success)
        assertEquals(ExecutionProviderType.ROOT, report.channel)
        assertEquals(listOf(ExecutionProviderType.ROOT), report.attemptedChannels)
        assertTrue(report.message.contains("root ran it"))
    }

    @Test
    fun executeFor_fallback_walksChainAndReportsWinner() {
        // Root rejects, Shizuku succeeds — the report names Shizuku and shows
        // both channels were attempted in ranked order.
        val report = channelSelector.executeFor(
            profile(root = true, shizuku = true),
            command = "shizuku-ok"
        )
        assertTrue(report.success)
        assertEquals(ExecutionProviderType.SHIZUKU, report.channel)
        assertEquals(
            listOf(ExecutionProviderType.ROOT, ExecutionProviderType.SHIZUKU),
            report.attemptedChannels
        )
    }

    @Test
    fun executeFor_allReject_reportsNoChannel() {
        val report = channelSelector.executeFor(
            profile(root = true, shizuku = true),
            command = "boom"
        )
        assertFalse(report.success)
        assertNull(report.channel)
        assertEquals(
            listOf(
                ExecutionProviderType.ROOT,
                ExecutionProviderType.SHIZUKU,
                ExecutionProviderType.ANDROID
            ),
            report.attemptedChannels
        )
    }

    @Test
    fun executeFor_capabilityRestricted_onlyTriesMatchingProviders() {
        val report = channelSelector.executeFor(
            profile(root = true, shizuku = true),
            command = "shizuku-ok",
            capability = RomCapability.SHIZUKU
        )
        assertTrue(report.success)
        assertEquals(ExecutionProviderType.SHIZUKU, report.channel)
        // Root and Android were excluded — they do not support SHIZUKU.
        assertEquals(listOf(ExecutionProviderType.SHIZUKU), report.attemptedChannels)
    }

    @Test
    fun executeFor_noProviderSupportsCapability_failsClearly() {
        val report = channelSelector.executeFor(
            profile(root = true, shizuku = true),
            command = "x",
            capability = RomCapability.LINEAGEOS_SDK
        )
        assertFalse(report.success)
        assertNull(report.channel)
        assertTrue(report.message.contains("No execution provider supports this capability"))
    }
}
