package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the per-family settings-key layout the bridge uses to enumerate/write ROM keys. */
class RomSettingSchemaTest {

    @Test
    fun `lineage derived families share the lineage prefixes plus their fork prefix`() {
        assertTrue("evo_" in RomSettingSchema.prefixes(RomFamily.EVOLUTION_X))
        assertTrue("lineage_" in RomSettingSchema.prefixes(RomFamily.EVOLUTION_X))
        assertTrue("lineage_" in RomSettingSchema.prefixes(RomFamily.LINEAGE_OS))
        assertTrue("lineage_" in RomSettingSchema.prefixes(RomFamily.CR_DROID))
        assertTrue("lineage_" in RomSettingSchema.prefixes(RomFamily.ARROW_OS))
        assertTrue("lineage_" in RomSettingSchema.prefixes(RomFamily.PIXEL_OS))
    }

    @Test
    fun `oem skins use their vendor prefixes`() {
        assertTrue("miui_" in RomSettingSchema.prefixes(RomFamily.MIUI))
        assertTrue("hyper_" in RomSettingSchema.prefixes(RomFamily.HYPER_OS))
        assertTrue("sec_" in RomSettingSchema.prefixes(RomFamily.ONE_UI))
        assertTrue("oplus_" in RomSettingSchema.prefixes(RomFamily.COLOR_OS))
        assertTrue("oplus_" in RomSettingSchema.prefixes(RomFamily.OXYGEN_OS))
        assertTrue("vivo_" in RomSettingSchema.prefixes(RomFamily.VIVO_ORIGIN_OS))
        assertTrue("hw_" in RomSettingSchema.prefixes(RomFamily.EMUI))
        assertTrue("nothing_" in RomSettingSchema.prefixes(RomFamily.NOTHING_OS))
    }

    @Test
    fun `aosp and other have no rom prefixes`() {
        assertTrue(RomSettingSchema.prefixes(RomFamily.AOSP).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.PIXEL).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.MOTOROLA).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.OTHER).isEmpty())
    }

    @Test
    fun `lineage derived families default to the secure namespace`() {
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.EVOLUTION_X))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.LINEAGE_OS))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.CR_DROID))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.PIXEL_OS))
    }

    @Test
    fun `oem skins default to the system namespace`() {
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.ONE_UI))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.MIUI))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.COLOR_OS))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.EMUI))
    }

    @Test
    fun `lineage derived classification covers the fork set`() {
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.EVOLUTION_X))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.CR_DROID))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.ARROW_OS))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.SUPERIOR_OS))
        assertFalse(RomSettingSchema.isLineageDerived(RomFamily.ONE_UI))
        assertFalse(RomSettingSchema.isLineageDerived(RomFamily.AOSP))
    }

    @Test
    fun `supported and oem classification`() {
        assertTrue(RomSettingSchema.isSupported(RomFamily.ONE_UI))
        assertTrue(RomSettingSchema.isSupported(RomFamily.EVOLUTION_X))
        assertFalse(RomSettingSchema.isSupported(RomFamily.AOSP))
        assertFalse(RomSettingSchema.isSupported(RomFamily.OTHER))

        assertTrue(RomSettingSchema.isOemSkin(RomFamily.MIUI))
        assertTrue(RomSettingSchema.isOemSkin(RomFamily.NOTHING_OS))
        assertTrue(RomSettingSchema.isOemSkin(RomFamily.HARMONY_OS))
        assertFalse(RomSettingSchema.isOemSkin(RomFamily.LINEAGE_OS))
        assertFalse(RomSettingSchema.isOemSkin(RomFamily.PIXEL))
    }
}
