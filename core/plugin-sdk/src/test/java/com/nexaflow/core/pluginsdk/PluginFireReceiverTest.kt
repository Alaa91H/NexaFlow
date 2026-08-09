package com.nexaflow.core.pluginsdk

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
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [PluginFireReceiver]'s ordered-broadcast protocol
 * behavior: the receiver must write the [PluginResult] as the broadcast result
 * code and — on failure — the Tasker-compatible %err / %errmsg extras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginFireReceiverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private class TestReceiver : PluginFireReceiver() {
        var seenConfig: Map<String, Any?>? = null
        var result: PluginResult = PluginResult.Ok

        override fun onFire(context: Context, config: Map<String, Any?>): PluginResult {
            seenConfig = config
            return result
        }
    }

    private data class BroadcastResult(
        val code: Int,
        val data: String?,
        val extras: Bundle?
    )

    /** Sends an ordered FIRE_SETTING broadcast and returns the final result. */
    private fun fire(receiver: TestReceiver, config: Map<String, Any?>): BroadcastResult {
        // RECEIVER_EXPORTED: a real Locale host fires the plugin from another
        // app; on API 33+ an explicit flag is mandatory for registerReceiver.
        context.registerReceiver(
            receiver,
            IntentFilter(LocaleContract.ACTION_FIRE_SETTING),
            Context.RECEIVER_EXPORTED
        )
        var captured: BroadcastResult? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                captured = BroadcastResult(getResultCode(), getResultData(), getResultExtras(true))
            }
        }
        try {
            val intent = Intent(LocaleContract.ACTION_FIRE_SETTING)
                .putExtra(LocaleContract.EXTRA_BUNDLE, PluginConfigParser.toBundle(config))
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
    fun orderedBroadcast_ok_setsResultCodeAndPassesConfig() {
        val receiver = TestReceiver()

        val result = fire(receiver, mapOf("k" to "v"))

        assertEquals("v", receiver.seenConfig?.get("k"))
        assertEquals(LocaleContract.RESULT_CODE_OK, result.code)
    }

    @Test
    fun orderedBroadcast_pending_setsResultCode() {
        val receiver = TestReceiver().apply { result = PluginResult.Pending }

        val result = fire(receiver, emptyMap())

        assertEquals(LocaleContract.RESULT_CODE_PENDING, result.code)
    }

    @Test
    fun orderedBroadcast_failed_setsCodeAndTaskerErrorExtras() {
        val receiver = TestReceiver().apply { result = PluginResult.Failed(42, "boom") }

        val result = fire(receiver, emptyMap())

        assertEquals(LocaleContract.RESULT_CODE_FAILED, result.code)
        // The fire contract carries errors via %err/%errmsg extras; the data
        // field is deliberately left at the initial value (RESULT_OK data null).
        val extras = requireNotNull(result.extras)
        assertEquals(42, extras.getInt("net.dinglisch.android.tasker.extras.ERR"))
        assertEquals("boom", extras.getString("net.dinglisch.android.tasker.extras.ERRMSG"))
        assertTrue(extras.containsKey("net.dinglisch.android.tasker.extras.ERR"))
    }
}
