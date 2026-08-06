package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-7 gate: [ChannelStatusMapper] over the simulated device matrix — every
 * channel the selector can pick maps to the right tier, badge label and shell
 * flag, and unsupported combinations degrade to NONE.
 */
class ChannelStatusMapperTest {

    private val selector = ProviderSelector.default()

    private fun profile(
        level: IntegrationLevel = IntegrationLevel.NORMAL,
        family: RomFamily = RomFamily.AOSP,
        capabilities: Set<RomCapability> = emptySet(),
        accessibility: Boolean = false,
        shizuku: Boolean = false,
        root: Boolean = false,
        adb: Boolean = false
    ) = DeviceProfile(
        integrationLevel = level,
        romFamily = family,
        capabilities = capabilities,
        accessibilityEnabled = accessibility,
        shizukuGranted = shizuku,
        rootAvailable = root,
        adbConnected = adb,
        androidSdk = 34
    )

    private fun map(profile: DeviceProfile, selector: ProviderSelector = this.selector): ChannelStatus {
        // Mirrors the production call path: the caller passes the provider the
        // selector picked — single source of truth.
        return ChannelStatusMapper.map(
            provider = selector.bestFor(profile),
            capabilityCount = profile.capabilities.size
        )
    }

    @Test
    fun stockDevice_mapsToAndroidStandard() {
        val status = map(profile())
        assertEquals(ExecutionProviderType.ANDROID, status.provider)
        assertEquals(ChannelTier.STANDARD, status.tier)
        assertFalse(status.shellAccess)
        assertEquals(0, status.capabilityCount)
    }

    @Test
    fun rootedDevice_mapsToRootElevated() {
        val status = map(profile(root = true))
        assertEquals(ExecutionProviderType.ROOT, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
        assertTrue(status.shellAccess)
    }

    @Test
    fun shizukuDevice_mapsToShizukuElevated() {
        val status = map(profile(shizuku = true))
        assertEquals(ExecutionProviderType.SHIZUKU, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
        assertTrue(status.shellAccess)
    }

    @Test
    fun systemAppDevice_mapsToSystemAppElevated() {
        val status = map(
            profile(level = IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP)
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
        assertTrue(status.shellAccess)
    }

    @Test
    fun adbDevice_mapsToAdbElevated() {
        val status = map(profile(adb = true))
        assertEquals(ExecutionProviderType.ADB, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
        assertTrue(status.shellAccess)
    }

    @Test
    fun accessibilityDevice_mapsToAccessibilityTier() {
        val status = map(profile(accessibility = true))
        assertEquals(ExecutionProviderType.ACCESSIBILITY, status.provider)
        assertEquals(ChannelTier.ACCESSIBILITY, status.tier)
        assertFalse(status.shellAccess)
    }

    @Test
    fun noProvider_mapsToNone() {
        // A selector with no usable providers on this profile degrades to NONE.
        val status = map(profile(), ProviderSelector(emptyList()))
        assertNull(status.provider)
        assertEquals(ChannelTier.NONE, status.tier)
        assertFalse(status.shellAccess)
    }

    @Test
    fun noneFactory_producesNeutralState() {
        val none = ChannelStatus.none()
        assertNull(none.provider)
        assertEquals(ChannelTier.NONE, none.tier)
        assertFalse(none.shellAccess)
        assertEquals(0, none.capabilityCount)
    }

    @Test
    fun capabilityCount_reflectsDetectedCapabilities() {
        val status = map(
            profile(
                capabilities = setOf(
                    RomCapability.WRITE_SETTINGS,
                    RomCapability.DND_ACCESS
                )
            )
        )
        assertEquals(2, status.capabilityCount)
    }

    @Test
    fun rootOutranksShizuku_bothAvailable() {
        val status = map(profile(root = true, shizuku = true))
        assertEquals(ExecutionProviderType.ROOT, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
    }

    @Test
    fun customRomSystemApp_reportsShellAccess() {
        val status = map(
            profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.LINEAGE_OS)
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, status.provider)
        assertEquals(ChannelTier.ELEVATED, status.tier)
        assertTrue(status.shellAccess)
    }
}
