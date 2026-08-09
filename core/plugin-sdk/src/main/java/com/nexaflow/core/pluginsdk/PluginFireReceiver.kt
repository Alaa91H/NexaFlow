package com.nexaflow.core.pluginsdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Base class for a Locale *setting* plugin's execution receiver. Subclasses
 * implement [onFire] with pure plugin logic and declare the receiver in their
 * manifest exactly like this:
 *
 * ```xml
 * <receiver android:name=".FireReceiver" android:exported="true">
 *     <intent-filter>
 *         <action android:name="com.twofortyfouram.locale.intent.action.FIRE_SETTING" />
 *     </intent-filter>
 * </receiver>
 * ```
 *
 * The class handles the whole protocol boilerplate: it parses the config
 * bundle, dispatches to [onFire], and — when the host fired an **ordered**
 * broadcast — writes the [PluginResult] (including %err / %errmsg for
 * Tasker-compatible error UI) back to the result.
 *
 * Keep [onFire] fast: hosts may fire the receiver while the app is in the
 * background. For genuinely long work use [BroadcastReceiver.goAsync] with a
 * coroutine and report a [PluginResult.Pending] immediately if desired.
 */
abstract class PluginFireReceiver : BroadcastReceiver() {

    final override fun onReceive(context: Context, intent: Intent) {
        val config = PluginConfigParser.fromBundle(
            intent.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
        )
        val result = onFire(context, config)
        // isOrderedBroadcast lives on the receiver (not the intent).
        if (isOrderedBroadcast) {
            resultCode = result.toResultCode()
            val failed = result as? PluginResult.Failed
            if (failed != null) {
                // Tasker-compatible error extras (net.dinglisch.android.tasker.extras).
                // resultData is deliberately left untouched: the fire contract
                // carries errors via %err/%errmsg extras, not the data field.
                val extras = Bundle()
                extras.putInt("net.dinglisch.android.tasker.extras.ERR", failed.code.coerceIn(0, 999))
                extras.putString("net.dinglisch.android.tasker.extras.ERRMSG", failed.message)
                setResultExtras(extras)
            }
        }
    }

    /**
     * The plugin's actual execution logic. [config] is the configuration map
     * the user saved in the edit activity (already JSON-decoded).
     */
    abstract fun onFire(context: Context, config: Map<String, Any?>): PluginResult
}
