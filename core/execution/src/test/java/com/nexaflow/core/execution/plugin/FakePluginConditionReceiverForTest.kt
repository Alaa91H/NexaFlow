package com.nexaflow.core.execution.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexaflow.core.pluginsdk.LocaleContract

/** Test-only explicit receiver that simulates a Locale condition plugin. */
class FakePluginConditionReceiverForTest : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        lastAction = intent.action
        setResultCode(nextResultCode)
    }

    companion object {
        @Volatile
        var nextResultCode: Int = LocaleContract.RESULT_CONDITION_SATISFIED

        @Volatile
        var lastAction: String? = null

        fun reset() {
            nextResultCode = LocaleContract.RESULT_CONDITION_SATISFIED
            lastAction = null
        }
    }
}
