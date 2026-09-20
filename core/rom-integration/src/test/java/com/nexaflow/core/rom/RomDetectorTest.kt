package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomBuildInfo
import com.nexaflow.core.rom.model.RomFamily
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards the build-tier detection ordering: fork evidence precedes the base
 * and sets BOTH `ro.evolution.version` and `ro.lineage.version`, so it must
 * be classified as EVOLUTION_X — never as LINEAGE_OS. This matters because
 * the deep-integration card and the LineageOS-derived capabilities only
 * render/apply for the correct family.
 */
class RomDetectorTest {

    private val originalBuildValues = RomDetector.buildValues

    @Before
    fun setUp() {
        // android.os.Build is empty on the pure JVM; inject stable values.
        RomDetector.buildValues = {
            RomBuildInfo(
                family = RomFamily.OTHER,
                brand = "Xiaomi",
                device = "marble",
                model = "POCO X6 Pro",
                androidVersion = "17",
                securityPatch = "2026-08-01",
                buildId = "TP1A",
                buildDisplay = "evolution_marble-17"
            )
        }
    }

    @After
    fun tearDown() {
        SystemPropertyProvider.injectedProperties = null
        RomDetector.buildValues = originalBuildValues
    }

    @Test
    fun `evolution version wins over lineage version`() {
        SystemPropertyProvider.injectedProperties = mapOf(
            "ro.evolution.version" to "12.0",
            "ro.lineage.version" to "21.0"
        )
        assertEquals(RomFamily.CUSTOM_ROM_PRIVILEGED, RomDetector.detect().family)
    }

    @Test
    fun `lineage version alone maps to lineage`() {
        SystemPropertyProvider.injectedProperties = mapOf(
            "ro.lineage.version" to "21.0"
        )
        assertEquals(RomFamily.CUSTOM_ROM_PRIVILEGED, RomDetector.detect().family)
    }

    @Test
    fun `vendor version property is captured in build info`() {
        SystemPropertyProvider.injectedProperties = mapOf(
            "ro.lineage.version" to "21.0"
        )
        val info = RomDetector.detect()
        assertEquals("21.0", info.vendorVersion)
    }

    @Test
    fun `no rom properties map to other`() {
        SystemPropertyProvider.injectedProperties = emptyMap()
        assertEquals(RomFamily.OTHER, RomDetector.detect().family)
    }

    @Test
    fun `miui is still detected when no evolution property`() {
        SystemPropertyProvider.injectedProperties = mapOf(
            "ro.miui.ui.version.name" to "V140.0"
        )
        assertEquals(RomFamily.OEM_SKIN_PRIVILEGED, RomDetector.detect().family)
    }
}
