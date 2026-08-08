package com.nexaflow.core.pluginsdk

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the constants to the published Locale plugin protocol. */
class LocaleContractTest {

    @Test
    fun `intent actions match the published spec`() {
        assertEquals("com.twofortyfouram.locale.intent.action.EDIT_SETTING", LocaleContract.ACTION_EDIT_SETTING)
        assertEquals("com.twofortyfouram.locale.intent.action.FIRE_SETTING", LocaleContract.ACTION_FIRE_SETTING)
        assertEquals("com.twofortyfouram.locale.intent.action.EDIT_CONDITION", LocaleContract.ACTION_EDIT_CONDITION)
        assertEquals("com.twofortyfouram.locale.intent.action.QUERY_CONDITION", LocaleContract.ACTION_QUERY_CONDITION)
    }

    @Test
    fun `extra keys match the published spec`() {
        assertEquals("com.twofortyfouram.locale.intent.extra.BUNDLE", LocaleContract.EXTRA_BUNDLE)
        assertEquals("com.twofortyfouram.locale.intent.extra.STRING_BLURB", LocaleContract.EXTRA_STRING_BLURB)
        assertEquals("com.twofortyfouram.locale.intent.extra.STRING_BREADCRUMB", LocaleContract.EXTRA_STRING_BREADCRUMB)
    }

    @Test
    fun `result codes match the ecosystem convention`() {
        assertEquals(0, LocaleContract.RESULT_CODE_OK)
        assertEquals(1, LocaleContract.RESULT_CODE_PENDING)
        assertEquals(2, LocaleContract.RESULT_CODE_CANCELED)
        assertEquals(-1, LocaleContract.RESULT_CODE_FAILED)
    }

    @Test
    fun `plugin results map to the contract codes`() {
        assertEquals(0, PluginResult.Ok.toResultCode())
        assertEquals(1, PluginResult.Pending.toResultCode())
        assertEquals(2, PluginResult.Canceled.toResultCode())
        assertEquals(-1, PluginResult.Failed(5, "boom").toResultCode())
    }
}
