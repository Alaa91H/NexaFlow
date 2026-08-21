package com.nexaflow.core.execution.plugin

import android.os.Looper
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.pluginsdk.PluginResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Full protocol round-trip for [PluginFireClient]: the fake plugin receiver
 * declared in the test manifest answers the client's explicit ordered
 * FIRE_SETTING broadcast, and OK / Pending / Failed must flow back to the
 * caller — the acceptance gate for the H2 plugin action.
 *
 * The client sends the broadcast from a background thread and waits on a
 * latch, while Robolectric only dispatches receivers when the main looper is
 * idled — so the test idles it repeatedly until the client's fire completes.
 */
@RunWith(RobolectricTestRunner::class)
class PluginFireClientRoundTripTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val client = PluginFireClient(timeoutMs = 3_000)

    @Before
    fun setUp() {
        FakePluginReceiverForTest.reset()
    }

    private fun fire(): PluginFireResult {
        val deferred = CoroutineScope(Dispatchers.Default).async {
            client.fire(
                context,
                packageName = context.packageName,
                receiverClass = FakePluginReceiverForTest::class.java.name,
                bundle = PluginConfigParser.toBundle(mapOf("k" to "v"))
            )
        }
        val deadline = System.currentTimeMillis() + 6_000
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        return runBlocking { deferred.await() }
    }

    @Test
    fun ok_deliversSuccessAndConfig() {
        FakePluginReceiverForTest.nextResult = PluginResult.Ok

        val result = fire()

        assertTrue(result.isSuccess)
        assertEquals(LocaleContract.RESULT_CODE_OK, result.resultCode)
        // The fake plugin also received the JSON config.
        assertEquals("v", FakePluginReceiverForTest.lastConfig?.get("k"))
    }

    @Test
    fun ok_returnsBoundedTaskerOutputVariablesAndAdvertisesHostCapability() {
        FakePluginReceiverForTest.nextResult = PluginResult.Ok
        FakePluginReceiverForTest.nextOutputVariables = buildMap {
            put("city", "Berlin")
            put("UPPER", "ignored")
            put("bad-name", "ignored")
            repeat(33) { put("value$it", "v$it") }
        }

        val result = fire()

        assertEquals("Berlin", result.outputVariables["city"])
        assertFalse("UPPER" in result.outputVariables)
        assertFalse("bad-name" in result.outputVariables)
        assertEquals(32, result.outputVariables.size)
        assertTrue(
            FakePluginReceiverForTest.lastHostCapabilities and
                LocaleContract.HOST_CAPABILITY_SETTING_OUTPUT_VARIABLES != 0
        )
    }

    @Test
    fun pending_deliversPendingCode() {
        FakePluginReceiverForTest.nextResult = PluginResult.Pending

        val result = fire()

        assertEquals(LocaleContract.RESULT_CODE_PENDING, result.resultCode)
        assertFalse(result.isSuccess)
    }

    @Test
    fun failed_deliversFailedCode() {
        FakePluginReceiverForTest.nextResult = PluginResult.Failed(7, "boom")

        val result = fire()

        assertEquals(LocaleContract.RESULT_CODE_FAILED, result.resultCode)
        assertFalse(result.isSuccess)
    }
}
