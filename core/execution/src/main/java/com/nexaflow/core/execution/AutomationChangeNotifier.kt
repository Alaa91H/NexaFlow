package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent

/** Single package-scoped automation-change broadcast boundary. */
internal class AutomationChangeNotifier(context: Context) {
    private val appContext = context.applicationContext

    fun notifyChanged() {
        appContext.sendBroadcast(
            Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(appContext.packageName)
        )
    }
}
