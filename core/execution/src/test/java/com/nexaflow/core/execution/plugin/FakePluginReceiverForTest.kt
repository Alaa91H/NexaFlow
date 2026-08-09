package com.nexaflow.core.execution.plugin

import android.content.Context
import com.nexaflow.core.pluginsdk.PluginFireReceiver
import com.nexaflow.core.pluginsdk.PluginResult

/**
 * Fake Locale plugin declared in the test manifest (see
 * `src/test/AndroidManifest.xml`) so the client's explicit FIRE_SETTING
 * broadcast resolves to it, exactly like an installed third-party plugin.
 * The result is configured per test through [nextResult].
 */
class FakePluginReceiverForTest : PluginFireReceiver() {

    override fun onFire(context: Context, config: Map<String, Any?>): PluginResult {
        lastConfig = config
        return nextResult
    }

    companion object {
        // Written by the test thread, read by the receiver on the main looper.
        /** Result the next fired broadcast returns. */
        @Volatile
        var nextResult: PluginResult = PluginResult.Ok

        /** Config the last fired broadcast delivered. */
        @Volatile
        var lastConfig: Map<String, Any?>? = null

        fun reset() {
            nextResult = PluginResult.Ok
            lastConfig = null
        }
    }
}
