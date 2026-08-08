package com.nexaflow.core.execution.plugin

import com.nexaflow.core.pluginsdk.LocaleContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginFireClientTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `fire to an absent package never hangs and is not a success`() = runBlocking {
        val client = PluginFireClient(timeoutMs = 2_000)
        val result = client.fire(
            context,
            packageName = "com.example.does.not.exist",
            receiverClass = "com.example.does.not.exist.FireReceiver",
            bundle = null
        )
        // Either the broadcast completed with the initial "canceled" code,
        // timed out, or failed fast — but it must never report success.
        assertFalse("absent plugin must not succeed", result.isSuccess)
        assertNotNull(result)
    }

    @Test
    fun `blank receiver class is handled without crashing`() = runBlocking {
        val client = PluginFireClient(timeoutMs = 1_000)
        val result = client.fire(
            context,
            packageName = context.packageName,
            receiverClass = "No.Such.Receiver",
            bundle = null
        )
        assertFalse(result.isSuccess)
        assertTrue(
            "expected canceled/timeout/failure, got code=${result.resultCode} timedOut=${result.timedOut}",
            result.timedOut ||
                result.resultCode == LocaleContract.RESULT_CODE_CANCELED ||
                result.resultCode == LocaleContract.RESULT_CODE_FAILED
        )
    }
}
