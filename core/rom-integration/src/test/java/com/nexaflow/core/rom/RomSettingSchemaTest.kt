package com.nexaflow.core.rom

import com.nexaflow.core.rom.model.RomFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the per-family settings-key layout the bridge uses to enumerate/write ROM keys. */
class RomSettingSchemaTest {

    @Test
    fun `privileged community tier shares base and fork prefixes`() {
        val prefixes = RomSettingSchema.prefixes(RomFamily.CUSTOM_ROM_PRIVILEGED)
        assertTrue("lineage_" in prefixes)
        assertTrue("evo_" in prefixes)
        assertTrue("sysui_" in prefixes)
    }

    @Test
    fun `oem skins use their vendor prefixes`() {
        assertTrue("miui_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue("hyper_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue("sec_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue("oplus_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue("oplus_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue("vivo_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN))
        assertTrue("hw_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN))
        assertTrue("nothing_" in RomSettingSchema.prefixes(RomFamily.OEM_SKIN))
    }

    @Test
    fun `aosp and other have no rom prefixes`() {
        assertTrue(RomSettingSchema.prefixes(RomFamily.AOSP).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.STOCK_GOOGLE).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.OEM_STOCK).isEmpty())
        assertTrue(RomSettingSchema.prefixes(RomFamily.OTHER).isEmpty())
    }

    @Test
    fun `lineage derived families default to the secure namespace`() {
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertEquals("secure", RomSettingSchema.defaultNamespaceName(RomFamily.CUSTOM_ROM_PRIVILEGED))
    }

    @Test
    fun `oem skins default to the system namespace`() {
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.OEM_SKIN_PRIVILEGED))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.OEM_SKIN_PRIVILEGED))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.OEM_SKIN_PRIVILEGED))
        assertEquals("system", RomSettingSchema.defaultNamespaceName(RomFamily.OEM_SKIN))
    }

    @Test
    fun `lineage derived classification covers the fork set`() {
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertTrue(RomSettingSchema.isLineageDerived(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertFalse(RomSettingSchema.isLineageDerived(RomFamily.OEM_SKIN_PRIVILEGED))
        assertFalse(RomSettingSchema.isLineageDerived(RomFamily.AOSP))
    }

    @Test
    fun `supported and oem classification`() {
        assertTrue(RomSettingSchema.isSupported(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue(RomSettingSchema.isSupported(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertFalse(RomSettingSchema.isSupported(RomFamily.AOSP))
        assertFalse(RomSettingSchema.isSupported(RomFamily.OTHER))

        assertTrue(RomSettingSchema.isOemSkin(RomFamily.OEM_SKIN_PRIVILEGED))
        assertTrue(RomSettingSchema.isOemSkin(RomFamily.OEM_SKIN))
        assertTrue(RomSettingSchema.isOemSkin(RomFamily.OEM_SKIN))
        assertFalse(RomSettingSchema.isOemSkin(RomFamily.CUSTOM_ROM_PRIVILEGED))
        assertFalse(RomSettingSchema.isOemSkin(RomFamily.STOCK_GOOGLE))
    }
}
