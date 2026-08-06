package com.nexaflow.core.compat

import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider-selector tests over a simulated device matrix (Phase-5 gate):
 * stock / rooted / shizuku / system-app / adb / accessibility devices, with
 * capability filtering, ROM-profile bonuses and graceful fallback.
 */
class ProviderSelectorTest {

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

    // ---- Basic availability matrix ----

    @Test
    fun stockDevice_usesAndroidProvider() {
        val best = selector.bestFor(profile())
        assertEquals(ExecutionProviderType.ANDROID, best?.type)
    }

    @Test
    fun rootedDevice_prefersRoot() {
        val best = selector.bestFor(profile(root = true))
        assertEquals(ExecutionProviderType.ROOT, best?.type)
    }

    @Test
    fun shizukuDevice_prefersShizuku() {
        val best = selector.bestFor(profile(shizuku = true))
        assertEquals(ExecutionProviderType.SHIZUKU, best?.type)
    }

    @Test
    fun rootOutranksShizukuWhenBothAvailable() {
        val best = selector.bestFor(profile(shizuku = true, root = true))
        assertEquals(ExecutionProviderType.ROOT, best?.type)
    }

    @Test
    fun systemAppDevice_prefersSystemApp() {
        val best = selector.bestFor(
            profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.AOSP)
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, best?.type)
    }

    @Test
    fun platformSigned_outranksRoot() {
        val best = selector.bestFor(
            profile(level = IntegrationLevel.PLATFORM_SIGNED_SYSTEM_APP, root = true)
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, best?.type)
    }

    @Test
    fun adbDevice_usesAdb() {
        val best = selector.bestFor(profile(adb = true))
        assertEquals(ExecutionProviderType.ADB, best?.type)
    }

    @Test
    fun accessibilityDevice_usesAccessibility() {
        val best = selector.bestFor(profile(accessibility = true))
        assertEquals(ExecutionProviderType.ACCESSIBILITY, best?.type)
    }

    @Test
    fun androidAlwaysAvailable() {
        val ranked = selector.rankedFor(profile())
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.any { it.type == ExecutionProviderType.ANDROID })
    }

    // ---- Capability filtering ----

    @Test
    fun capability_rootShell_onlyRootAndShizukuSatisfy() {
        val best = selector.bestFor(
            profile(root = true, shizuku = true),
            capability = RomCapability.ROOT_SHELL
        )
        assertEquals(ExecutionProviderType.ROOT, best?.type)
    }

    @Test
    fun capability_lineageSdk_onlySystemApp() {
        val best = selector.bestFor(
            profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.LINEAGE_OS),
            capability = RomCapability.LINEAGEOS_SDK
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, best?.type)
    }

    @Test
    fun capability_romSdk_rootSatisfies() {
        // isElevated() treats ROOT as elevated — a rooted LineageOS device can
        // drive LINEAGEOS_SDK even without being a system app.
        val best = selector.bestFor(
            profile(root = true, family = RomFamily.LINEAGE_OS),
            capability = RomCapability.LINEAGEOS_SDK
        )
        assertEquals(ExecutionProviderType.ROOT, best?.type)
    }

    @Test
    fun capability_romSdk_shizukuSatisfies() {
        val best = selector.bestFor(
            profile(shizuku = true, family = RomFamily.HYPER_OS),
            capability = RomCapability.MIUI_HIDDEN_API
        )
        assertEquals(ExecutionProviderType.SHIZUKU, best?.type)
    }

    @Test
    fun capability_statusBar_accessibilityDoesNotSatisfy() {
        // The accessibility service is a UI-automation channel, not a privileged
        // permission grant — it must not claim STATUS_BAR_CONTROL.
        val best = selector.bestFor(
            profile(accessibility = true),
            capability = RomCapability.STATUS_BAR_CONTROL
        )
        assertNull(best)
    }

    @Test
    fun capability_notSatisfied_returnsNull() {
        // A stock device has no provider that can write secure settings.
        val best = selector.bestFor(
            profile(),
            capability = RomCapability.WRITE_SECURE_SETTINGS
        )
        assertNull(best)
    }

    @Test
    fun capability_writeSettings_androidSatisfies() {
        val best = selector.bestFor(
            profile(),
            capability = RomCapability.WRITE_SETTINGS
        )
        assertEquals(ExecutionProviderType.ANDROID, best?.type)
    }

    // ---- ROM-profile bonuses ----

    @Test
    fun customRom_bonusFavorsSystemApp() {
        val lineage = profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.LINEAGE_OS)
        val aosp = profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.AOSP)
        val systemApp = selector.rankedFor(lineage).first { it.type == ExecutionProviderType.SYSTEM_APP }
        val baseline = selector.rankedFor(aosp).first { it.type == ExecutionProviderType.SYSTEM_APP }
        // +15 on LineageOS, +0 on AOSP.
        assertTrue(selector.score(systemApp, lineage) > selector.score(baseline, aosp))
    }

    @Test
    fun oemRom_bonusApplies() {
        val oneUi = profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.ONE_UI)
        val aosp = profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.AOSP)
        val onOneUi = selector.rankedFor(oneUi).first { it.type == ExecutionProviderType.SYSTEM_APP }
        val onAosp = selector.rankedFor(aosp).first { it.type == ExecutionProviderType.SYSTEM_APP }
        assertTrue(selector.score(onOneUi, oneUi) > selector.score(onAosp, aosp))
    }

    // ---- Graceful fallback ----

    @Test
    fun executeWithFallback_triesProvidersInOrder() {
        // Root and shizuku both "available" but neither can actually run in this
        // JVM test — the chain still completes without throwing and returns a
        // terminal failure rather than crashing.
        val result = selector.executeWithFallback(
            profile(root = true, shizuku = true),
            command = "echo hi"
        )
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun rankedOrder_isStableByScore() {
        val ranked = selector.rankedFor(profile(shizuku = true, root = true))
        val scores = ranked.map { selector.score(it, profile(shizuku = true, root = true)) }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun bestForAll_requiresEveryCapability() {
        val best = selector.bestForAll(
            profile(level = IntegrationLevel.SYSTEM_APP, family = RomFamily.LINEAGE_OS),
            capabilities = setOf(RomCapability.READ_LOGS, RomCapability.LINEAGEOS_SDK)
        )
        assertEquals(ExecutionProviderType.SYSTEM_APP, best?.type)
    }

    @Test
    fun bestForAll_emptySetFallsBackToAny() {
        val best = selector.bestForAll(profile(), capabilities = emptySet())
        assertEquals(ExecutionProviderType.ANDROID, best?.type)
    }
}
