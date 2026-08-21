package com.nexaflow.core.execution.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nexaflow.core.pluginsdk.LocaleContract
import com.nexaflow.core.pluginsdk.PluginConfigParser
import com.nexaflow.core.pluginsdk.PluginResult

/**
 * Fake Locale plugin declared in the test manifest (see
 * `src/test/AndroidManifest.xml`) so the client's explicit ordered
 * FIRE_SETTING broadcast resolves to it, exactly like an installed third-party
 * plugin. It also exposes Tasker output variables for host-side protocol tests.
 */
class FakePluginReceiverForTest : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        lastConfig = PluginConfigParser.fromBundle(
            intent.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
        )
        lastHostCapabilities = intent.getIntExtra(LocaleContract.EXTRA_HOST_CAPABILITIES, 0)
        if (!isOrderedBroadcast) return

        resultCode = nextResult.toResultCode()
        if (nextOutputVariables.isNotEmpty()) {
            setResultExtras(Bundle().apply {
                putBundle(LocaleContract.EXTRA_VARIABLES_BUNDLE, Bundle().apply {
                    nextOutputVariables.forEach { (name, value) -> putString(name, value) }
                })
            })
        }
    }

    companion object {
        /** Result the next fired broadcast returns. */
        @Volatile
        var nextResult: PluginResult = PluginResult.Ok

        /** Config the last fired broadcast delivered. */
        @Volatile
        var lastConfig: Map<String, Any?>? = null

        /** Tasker output variables returned with the next ordered result. */
        @Volatile
        var nextOutputVariables: Map<String, String> = emptyMap()

        /** Host capability bit-mask received with the last fire request. */
        @Volatile
        var lastHostCapabilities: Int = 0

        fun reset() {
            nextResult = PluginResult.Ok
            lastConfig = null
            nextOutputVariables = emptyMap()
            lastHostCapabilities = 0
        }
    }
}
