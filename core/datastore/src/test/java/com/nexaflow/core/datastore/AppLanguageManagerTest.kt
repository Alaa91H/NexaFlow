package com.nexaflow.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageManagerTest {

    @Test
    fun normalizeSupportedLanguageTag_keepsSupportedTag() {
        assertEquals("ar", AppLanguageManager.normalizeSupportedLanguageTag("ar"))
        assertEquals("zh-CN", AppLanguageManager.normalizeSupportedLanguageTag("zh-CN"))
    }

    @Test
    fun normalizeSupportedLanguageTag_mapsFrameworkChineseVariant() {
        assertEquals(
            "zh-CN",
            AppLanguageManager.normalizeSupportedLanguageTag("zh-Hans-CN")
        )
    }

    @Test
    fun normalizeSupportedLanguageTag_acceptsSupportedRegionalVariant() {
        assertEquals("pt", AppLanguageManager.normalizeSupportedLanguageTag("pt-BR"))
    }

    @Test
    fun normalizeSupportedLanguageTag_rejectsUnsupportedLanguage() {
        assertNull(AppLanguageManager.normalizeSupportedLanguageTag("it"))
        assertNull(AppLanguageManager.normalizeSupportedLanguageTag(null))
    }
}
