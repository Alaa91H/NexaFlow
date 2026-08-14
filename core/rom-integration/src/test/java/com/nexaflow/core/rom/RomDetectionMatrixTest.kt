package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for the full-ROM detection matrix: custom-ROM forks before
 * bases, OEM skin disambiguation on shared ColorOS-family properties, and the
 * manufacturer fallback for stock builds without a version property.
 */
class RomDetectionMatrixTest {

    private fun family(
        props: Map<String, String> = emptyMap(),
        brand: String = "Google",
        manufacturer: String = "Google"
    ): RomFamily = RomDetectionMatrix.detectFamily(props, brand, manufacturer)

    // --- Custom ROMs: forks before bases -------------------------------------

    @Test
    fun `evolution x wins over inherited lineage version`() {
        assertEquals(
            RomFamily.EVOLUTION_X,
            family(
                mapOf("ro.evolution.version" to "12.0", "ro.lineage.version" to "21.0"),
                brand = "Xiaomi", manufacturer = "Xiaomi"
            )
        )
    }

    @Test
    fun `crdroid wins over inherited lineage version`() {
        assertEquals(
            RomFamily.CR_DROID,
            family(
                mapOf("ro.crdroid.version" to "10.1", "ro.lineage.version" to "21.0"),
                brand = "OnePlus", manufacturer = "OnePlus"
            )
        )
    }

    @Test
    fun `arrow pixelos elixir derpfest superior are detected`() {
        assertEquals(RomFamily.ARROW_OS, family(mapOf("ro.arrow.version" to "14.0")))
        assertEquals(RomFamily.PIXEL_OS, family(mapOf("ro.pixelos.version" to "5.2")))
        assertEquals(RomFamily.PROJECT_ELIXIR, family(mapOf("ro.elixir.version" to "4.2")))
        assertEquals(RomFamily.DERPFEST, family(mapOf("ro.derp.version" to "13.5")))
        assertEquals(RomFamily.SUPERIOR_OS, family(mapOf("ro.superior.version" to "14.0")))
    }

    @Test
    fun `lineage version alone maps to lineage`() {
        assertEquals(RomFamily.LINEAGE_OS, family(mapOf("ro.lineage.version" to "21.0")))
    }

    @Test
    fun `graphene os is detected from its build properties`() {
        assertEquals(
            RomFamily.GRAPHENE_OS,
            family(mapOf("ro.grapheneos.build_type" to "stable"))
        )
        assertEquals(
            RomFamily.GRAPHENE_OS,
            family(mapOf("ro.grapheneos.version" to "2026080100"))
        )
    }

    // --- Custom ROM on OEM hardware beats the stock skin ---------------------

    @Test
    fun `lineage on samsung hardware is lineage not one ui`() {
        assertEquals(
            RomFamily.LINEAGE_OS,
            family(
                mapOf("ro.lineage.version" to "21.0", "ro.build.version.oneui" to "6.1"),
                brand = "samsung", manufacturer = "samsung"
            )
        )
    }

    // --- Xiaomi ---------------------------------------------------------------

    @Test
    fun `hyperos wins over inherited miui property`() {
        assertEquals(
            RomFamily.HYPER_OS,
            family(
                mapOf("ro.mi.os.version.name" to "2.0.1", "ro.miui.ui.version.name" to "V140.0"),
                brand = "Xiaomi", manufacturer = "Xiaomi"
            )
        )
    }

    @Test
    fun `miui alone maps to miui on xiaomi brand`() {
        assertEquals(
            RomFamily.MIUI,
            family(mapOf("ro.miui.ui.version.name" to "V140.0"), brand = "Redmi", manufacturer = "Xiaomi")
        )
    }

    // --- ColorOS family: brand tiebreak --------------------------------------

    @Test
    fun `oppo with oplus property is color os`() {
        assertEquals(
            RomFamily.COLOR_OS,
            family(mapOf("ro.oplus.version" to "14.0"), brand = "OPPO", manufacturer = "OPPO")
        )
    }

    @Test
    fun `realme with oplus property is realme ui via brand tiebreak`() {
        assertEquals(
            RomFamily.REALME_UI,
            family(mapOf("ro.oplus.version" to "14.0"), brand = "realme", manufacturer = "realme")
        )
    }

    @Test
    fun `realme version property maps to realme ui`() {
        assertEquals(
            RomFamily.REALME_UI,
            family(mapOf("ro.build.version.realme" to "RMX3771_14.0"), brand = "realme", manufacturer = "realme")
        )
    }

    @Test
    fun `oneplus on coloros base without oxygen prop is oxygen os via manufacturer`() {
        // OnePlus 9+ OxygenOS 12/13 is ColorOS-based: ro.oplus.version is set,
        // ro.oxygen.version is not. The ColorOS rule is brand-constrained to
        // OPPO, so the manufacturer fallback resolves OnePlus → OxygenOS.
        assertEquals(
            RomFamily.OXYGEN_OS,
            family(mapOf("ro.oplus.version" to "13.1"), brand = "OnePlus", manufacturer = "OnePlus")
        )
    }

    @Test
    fun `oxygen version property maps to oxygen os`() {
        assertEquals(
            RomFamily.OXYGEN_OS,
            family(mapOf("ro.oxygen.version" to "11.0"), brand = "OnePlus", manufacturer = "OnePlus")
        )
    }

    // --- Huawei ---------------------------------------------------------------

    @Test
    fun `harmony os wins over inherited emui property`() {
        assertEquals(
            RomFamily.HARMONY_OS,
            family(
                mapOf("ro.build.version.harmonyos" to "4.0.0", "ro.build.version.emui" to "Emui_14.0"),
                brand = "HUAWEI", manufacturer = "HUAWEI"
            )
        )
    }

    @Test
    fun `emui property maps to emui on huawei or honor brand`() {
        assertEquals(
            RomFamily.EMUI,
            family(mapOf("ro.build.version.emui" to "Emui_14.0"), brand = "HUAWEI", manufacturer = "HUAWEI")
        )
        assertEquals(
            RomFamily.EMUI,
            family(mapOf("ro.build.hw_emui_api_level" to "21"), brand = "HONOR", manufacturer = "HONOR")
        )
    }

    // --- Other OEM skins ------------------------------------------------------

    @Test
    fun `one ui property maps to one ui`() {
        assertEquals(
            RomFamily.ONE_UI,
            family(mapOf("ro.build.version.oneui" to "6.1"), brand = "samsung", manufacturer = "samsung")
        )
    }

    @Test
    fun `vivo origin os property maps to origin os`() {
        assertEquals(
            RomFamily.VIVO_ORIGIN_OS,
            family(mapOf("ro.vivo.os.build.display.id" to "OriginOS 4.0"), brand = "vivo", manufacturer = "vivo")
        )
    }

    @Test
    fun `asus and nothing properties map to their families`() {
        assertEquals(
            RomFamily.ASUS_ZEN_UI,
            family(mapOf("ro.build.asus.version" to "14.0.0.31"), brand = "asus", manufacturer = "asus")
        )
        assertEquals(
            RomFamily.NOTHING_OS,
            family(mapOf("ro.nothing.version" to "2.5.5"), brand = "Nothing", manufacturer = "Nothing")
        )
    }

    // --- Manufacturer fallback (stock builds) ---------------------------------

    @Test
    fun `manufacturer fallback resolves stock builds without version props`() {
        assertEquals(RomFamily.MOTOROLA, family(brand = "motorola", manufacturer = "motorola"))
        assertEquals(RomFamily.SONY_XPERIA, family(brand = "Sony", manufacturer = "Sony"))
        assertEquals(RomFamily.PIXEL, family(brand = "google", manufacturer = "Google"))
        assertEquals(RomFamily.NOTHING_OS, family(brand = "Nothing", manufacturer = "Nothing"))
        assertEquals(RomFamily.ONE_UI, family(brand = "samsung", manufacturer = "samsung"))
        assertEquals(RomFamily.ASUS_ZEN_UI, family(brand = "asus", manufacturer = "asus"))
        assertEquals(RomFamily.COLOR_OS, family(brand = "OPPO", manufacturer = "OPPO"))
    }

    // --- Unknown --------------------------------------------------------------

    @Test
    fun `unknown device with no markers is other`() {
        assertEquals(RomFamily.OTHER, family(brand = "BrandX", manufacturer = "MakerY"))
    }

    // --- Version-aware build info --------------------------------------------

    @Test
    fun `detect captures android sdk and rom versions`() {
        val info = RomDetectionMatrix.detect(
            props = mapOf(
                "ro.evolution.version" to "12.0",
                "ro.lineage.version" to "21.0",
                "ro.evolution.buildtype" to "OFFICIAL"
            ),
            brand = "Xiaomi",
            manufacturer = "Xiaomi",
            device = "marble",
            model = "POCO X6 Pro",
            androidVersion = "17",
            securityPatch = "2026-08-01",
            buildId = "TP1A",
            buildDisplay = "evolution_marble-17",
            sdkInt = 36
        )
        assertEquals(RomFamily.EVOLUTION_X, info.family)
        assertEquals(36, info.androidSdk)
        assertEquals("12.0", info.evolutionVersion)
        assertEquals("21.0", info.lineageVersion)
        assertEquals("OFFICIAL", info.evolutionBuildType)
        assertEquals("Xiaomi", info.manufacturer)
    }
}
