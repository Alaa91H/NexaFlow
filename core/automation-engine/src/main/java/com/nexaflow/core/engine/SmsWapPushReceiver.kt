package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Declared for SMS-role eligibility. NexaFlow's automation engine currently
 * processes SMS, not MMS/WAP push payloads; it deliberately does not consume
 * or acknowledge the payload as handled.
 */
class SmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
