package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexaflow.core.pluginsdk.LocaleContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dynamic receiver for a configured plugin's Locale/Tasker-style requery hint.
 *
 * Android exposes a trustworthy sender package only from API 34. Earlier API
 * levels therefore reject this event path rather than accepting a spoofable
 * implicit broadcast. The receiver performs validation/publishing only; the
 * EventBus subscriber is responsible for indexed workflow routing.
 */
class PluginEventReceiver(
    private val scope: CoroutineScope,
    private val ingress: PluginEventIngress
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocaleContract.ACTION_REQUEST_QUERY || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                val senderPackage = sentFromPackage ?: return@launch
                val eventComponent = intent.getStringExtra(LocaleContract.EXTRA_STRING_ACTIVITY_CLASS_NAME)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@launch
                val payload = when (val conversion = PluginEventPayloadAdapter.toJson(
                    intent.getBundleExtra(LocaleContract.EXTRA_BUNDLE)
                )) {
                    is PluginEventPayloadConversion.Accepted -> conversion.payload
                    is PluginEventPayloadConversion.Rejected -> return@launch
                }
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_EVENT_ID
                val correlationId = intent.getStringExtra(EXTRA_CORRELATION_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: eventComponent
                ingress.publish(
                    senderPackage = senderPackage,
                    eventComponent = eventComponent,
                    eventId = eventId,
                    correlationId = correlationId,
                    payload = payload
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Optional Tasker-extension event id; absent events are deduplicated as requery hints. */
        const val EXTRA_EVENT_ID = "com.nexaflow.plugin.event.ID"
        /** Optional stable correlation id; never treated as a credential. */
        const val EXTRA_CORRELATION_ID = "com.nexaflow.plugin.event.CORRELATION_ID"
        private const val DEFAULT_EVENT_ID = "request-query"
    }
}
