package com.nexaflow.sample.nfctoggle

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Verifies the sample's ordered-broadcast result contract end-to-end: the host
 * fires [LocaleProtocol.ACTION_FIRE_SETTING], the receiver writes the result
 * code and — on failure — the Tasker-compatible %err / %errmsg extras.
 *
 * Robolectric has no NFC hardware, so both cases here are deterministic:
 * missing config and hardware-unavailable both report FAILED with extras. The
 * OK path is hardware-dependent and verified on a real device.
 */
@RunWith(RobolectricTestRunner::class)
class NfcToggleFireReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private data class BroadcastResult(val code: Int, val extras: Bundle?)

    private fun fire(bundle: Bundle?): BroadcastResult {
        val receiver = NfcToggleFireReceiver()
        context.registerReceiver(
            receiver,
            IntentFilter(LocaleProtocol.ACTION_FIRE_SETTING),
            Context.RECEIVER_EXPORTED
        )
        var captured: BroadcastResult? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                captured = BroadcastResult(getResultCode(), getResultExtras(true))
            }
        }
        try {
            val intent = Intent(LocaleProtocol.ACTION_FIRE_SETTING)
            if (bundle != null) intent.putExtra(LocaleProtocol.EXTRA_BUNDLE, bundle)
            context.sendOrderedBroadcast(
                intent,
                null,
                resultReceiver,
                null,
                Activity.RESULT_OK,
                null,
                null
            )
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            context.unregisterReceiver(receiver)
        }
        return requireNotNull(captured) { "result receiver was never invoked" }
    }

    @Test
    fun missingConfig_reportsFailedWithTaskerExtras() {
        val result = fire(null)

        assertEquals(LocaleProtocol.RESULT_CODE_FAILED, result.code)
        val extras = requireNotNull(result.extras)
        assertTrue(extras.containsKey(LocaleProtocol.EXTRA_TASKER_ERR))
        assertEquals("Missing 'enabled' configuration", extras.getString(LocaleProtocol.EXTRA_TASKER_ERRMSG))
    }

    @Test
    fun missingEnabledKey_reportsFailed() {
        val result = fire(PluginConfig.toBundle(emptyMap()))

        assertEquals(LocaleProtocol.RESULT_CODE_FAILED, result.code)
        assertEquals(
            "Missing 'enabled' configuration",
            result.extras?.getString(LocaleProtocol.EXTRA_TASKER_ERRMSG)
        )
    }

    @Test
    fun hardwareUnavailable_reportsFailedWithClearMessage() {
        val result = fire(PluginConfig.toBundle(mapOf("enabled" to true)))

        assertEquals(LocaleProtocol.RESULT_CODE_FAILED, result.code)
        // Robolectric has no NFC adapter → the controller reports hardware
        // absence (never a fake success).
        val message = result.extras?.getString(LocaleProtocol.EXTRA_TASKER_ERRMSG)
        assertTrue(message?.contains("NFC", ignoreCase = true) == true)
    }
}
