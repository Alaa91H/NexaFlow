package com.nexaflow.sample.nfctoggle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.Executors

/**
 * Executes the plugin when a task runs. Potentially blocking NFC/reflection/
 * shell work is moved off BroadcastReceiver.onReceive via [goAsync].
 */
class NfcToggleFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocaleProtocol.ACTION_FIRE_SETTING) return

        val config = PluginConfig.fromBundle(
            intent.getBundleExtra(LocaleProtocol.EXTRA_BUNDLE)
        )
        val enabled = config["enabled"] as? Boolean
        if (enabled == null) {
            if (isOrderedBroadcast) {
                resultCode = LocaleProtocol.RESULT_CODE_FAILED
                resultExtras = errorExtras("Missing 'enabled' configuration")
            }
            return
        }

        val ordered = isOrderedBroadcast
        val pending = goAsync()
        WORKER.execute {
            try {
                val failure = NfcController.setNfcEnabled(context.applicationContext, enabled)
                if (ordered) {
                    pending.setResultCode(
                        if (failure == null) {
                            LocaleProtocol.RESULT_CODE_OK
                        } else {
                            LocaleProtocol.RESULT_CODE_FAILED
                        }
                    )
                    if (failure != null) {
                        pending.setResultExtras(errorExtras(failure))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun errorExtras(message: String): Bundle = Bundle().apply {
        putInt(LocaleProtocol.EXTRA_TASKER_ERR, 1)
        putString(LocaleProtocol.EXTRA_TASKER_ERRMSG, message)
    }

    private companion object {
        val WORKER = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "NexaFlow-sample-nfc").apply { isDaemon = true }
        }
    }
}
