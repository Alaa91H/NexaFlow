package com.nexaflow.sample.nfctoggle

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the sample edit activity's protocol contract: saving finishes with
 * RESULT_OK + the config bundle + blurb (including the legacy blurb extra),
 * and backing out without saving leaves the default RESULT_CANCELED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NfcToggleEditActivityTest {

    @Test
    fun save_finishesWithProtocolExtras() {
        val controller = Robolectric.buildActivity(NfcToggleEditActivity::class.java).create()
        val activity = controller.get()

        // Flip the switch to "on" and click Save.
        val switch = activity.findViewById<android.widget.Switch>(R.id.switch_nfc)
        switch.isChecked = true
        activity.findViewById<android.widget.Button>(R.id.button_save).performClick()

        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_OK, shadow.resultCode)
        val result = requireNotNull(shadow.resultIntent)
        assertEquals(
            activity.getString(R.string.blurb_enable),
            result.getStringExtra(LocaleProtocol.EXTRA_STRING_BLURB)
        )
        // Legacy hosts still read the plain BLURB extra.
        assertEquals(
            activity.getString(R.string.blurb_enable),
            result.getStringExtra(LocaleProtocol.EXTRA_BLURB)
        )
        val back = PluginConfig.fromBundle(result.getBundleExtra(LocaleProtocol.EXTRA_BUNDLE))
        assertEquals(true, back["enabled"])
        assertTrue(activity.isFinishing)
    }

    @Test
    fun backWithoutSave_leavesDefaultCanceled() {
        val controller = Robolectric.buildActivity(NfcToggleEditActivity::class.java).create()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
        controller.destroy()
    }

    @Test
    fun create_appliesBreadcrumbTitle() {
        val intent = Intent().putExtra(
            LocaleProtocol.EXTRA_STRING_BREADCRUMB,
            "NexaFlow > NFC Toggle"
        )
        val controller = Robolectric.buildActivity(NfcToggleEditActivity::class.java, intent).create()

        assertEquals("NexaFlow > NFC Toggle", controller.get().title?.toString())
    }

    @Test
    fun create_prefillsSwitchFromSavedBundle() {
        // Reconfiguring an existing action: the host passes the previously
        // saved bundle and the switch must reflect the stored "enabled" value.
        val intent = Intent().putExtra(
            LocaleProtocol.EXTRA_BUNDLE,
            PluginConfig.toBundle(mapOf("enabled" to true))
        )
        val controller = Robolectric.buildActivity(NfcToggleEditActivity::class.java, intent).create()

        val switch = controller.get().findViewById<android.widget.Switch>(R.id.switch_nfc)
        assertTrue(switch.isChecked)
    }
}
