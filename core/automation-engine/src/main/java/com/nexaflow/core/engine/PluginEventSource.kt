package com.nexaflow.core.engine

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.nexaflow.core.execution.compat.EventSource
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.core.pluginsdk.LocaleContract
import kotlinx.coroutines.CoroutineScope

/**
 * Service-lifecycle source for externally emitted plugin events.
 *
 * It deliberately remains disabled before API 34 because `BroadcastReceiver`
 * cannot authenticate an implicit broadcast sender there. Existing plugin
 * actions/conditions remain available on supported earlier Android versions.
 */
class PluginEventSource(
    context: Context,
    scope: CoroutineScope,
    ingress: PluginEventIngress
) : EventSource {
    override val sourceId: String = TriggerSource.PLUGIN.sourceId
    override val description: String = "Verified external plugin requery events (Android 14+ only)"

    private val appContext = context.applicationContext
    private val receiver = PluginEventReceiver(scope, ingress)
    private val receiverFilter = IntentFilter(LocaleContract.ACTION_REQUEST_QUERY)
    private val lock = Any()
    private var registered = false

    override fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        synchronized(lock) {
            if (registered) return
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                receiverFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!registered) return
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
    }
}
