package com.nexaflow.core.engine

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.SmsManager

/** System entry point for the default-SMS "respond via message" action. */
class SmsRespondViaMessageService : Service() {
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart.orEmpty().trim()
        val message = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        if (destination.isNotEmpty() && message.isNotEmpty() &&
            checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                val manager = getSystemService(SmsManager::class.java)
                manager.sendMultipartTextMessage(destination, null, manager.divideMessage(message), null, null)
            }
        }
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
