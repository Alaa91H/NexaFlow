package com.nexaflow.sample.nfctoggle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Executes the plugin when a task runs. The host sends an explicit
 * [LocaleProtocol.ACTION_FIRE_SETTING] broadcast with the saved config bundle.
 *
 * Ordered-broadcast result contract (recommended, Tasker-compatible):
 *  - success → [LocaleProtocol.RESULT_CODE_OK]
 *  - failure → [LocaleProtocol.RESULT_CODE_FAILED] + %err / %errmsg extras so
 *    the host can show WHY the action failed.
 *
 * Keep [onReceive] fast — hosts may fire while the app is backgrounded. The
 * NFC toggle here is a quick synchronous call; for long work use [goAsync].
 */
class NfcToggleFireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocaleProtocol.ACTION_FIRE_SETTING) return

        val config = PluginConfig.fromBundle(
            intent.getBundleExtra(LocaleProtocol.EXTRA_BUNDLE)
        )
        val enabled = config["enabled"] as? Boolean
        val failure = when {
            enabled == null -> "Missing 'enabled' configuration"
            else -> NfcController.setNfcEnabled(context, enabled)
        }

        if (!isOrderedBroadcast) return
        if (failure == null) {
            resultCode = LocaleProtocol.RESULT_CODE_OK
        } else {
            resultCode = LocaleProtocol.RESULT_CODE_FAILED
            // Tasker-compatible error extras — hosts render these in the UI.
            val extras = Bundle()
            extras.putInt(LocaleProtocol.EXTRA_TASKER_ERR, 1)
            extras.putString(LocaleProtocol.EXTRA_TASKER_ERRMSG, failure)
            setResultExtras(extras)
        }
    }
}
