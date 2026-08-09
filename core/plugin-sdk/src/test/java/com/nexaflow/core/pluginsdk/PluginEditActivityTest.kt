package com.nexaflow.core.pluginsdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [PluginEditActivity]'s protocol contract: [save] must
 * finish with RESULT_OK + the config bundle + blurb (including the legacy
 * blurb extra), and backing out without saving leaves the default
 * RESULT_CANCELED for the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginEditActivityTest {

    private class TestEditActivity : PluginEditActivity() {
        // Public wrappers around the protected protocol helpers.
        fun saveForTest(config: Map<String, Any?>, blurb: String) = save(config, blurb)
        fun savedBundleForTest(): Bundle? = savedBundle()
    }

    @Test
    fun save_packsProtocolExtrasAndFinishes() {
        val controller = Robolectric.buildActivity(TestEditActivity::class.java).create()
        val activity = controller.get()

        activity.saveForTest(mapOf("enabled" to true, "name" to "x"), "Flashlight: on")

        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        val result = requireNotNull(shadow.resultIntent)
        assertEquals("Flashlight: on", result.getStringExtra(LocaleContract.EXTRA_STRING_BLURB))
        // Legacy hosts still read the plain BLURB extra.
        assertEquals("Flashlight: on", result.getStringExtra(LocaleContract.EXTRA_BLURB))
        val back = PluginConfigParser.fromBundle(
            result.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
        )
        assertEquals("x", back["name"])
        assertEquals(true, back["enabled"])
        assertTrue(activity.isFinishing)
    }

    @Test
    fun finishWithoutSave_leavesDefaultCanceledResult() {
        val controller = Robolectric.buildActivity(TestEditActivity::class.java).create()
        val activity = controller.get()

        // No [save] was called: the host must see the default canceled result.
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        controller.destroy()
    }

    @Test
    fun create_appliesBreadcrumbTitle() {
        val intent = Intent().putExtra(
            LocaleContract.EXTRA_STRING_BREADCRUMB,
            "NexaFlow > My plugin"
        )
        val controller = Robolectric.buildActivity(TestEditActivity::class.java, intent).create()

        assertEquals("NexaFlow > My plugin", controller.get().title?.toString())
    }

    @Test
    fun savedBundle_returnsPreviouslySavedConfig() {
        val first = PluginConfigParser.toBundle(mapOf("mode" to "night"))
        val intent = Intent().putExtra(LocaleContract.EXTRA_BUNDLE, first)
        val controller = Robolectric.buildActivity(TestEditActivity::class.java, intent).create()

        val back = PluginConfigParser.fromBundle(controller.get().savedBundleForTest())
        assertEquals("night", back["mode"])
    }
}
