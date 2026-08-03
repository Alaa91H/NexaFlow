package com.nexaflow.core.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.core.execution.ExecutionEngine
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    private val executionEngine: ExecutionEngine,
    @ApplicationScope private val scope: CoroutineScope
) {

    @Volatile
    private var registered = false

    private var alertedLow = false
    private var alertedChargeComplete = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            handleBatteryChange(level, status)
        }
    }

    fun initialize() {
        if (registered) return
        registered = true
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun handleBatteryChange(level: Int, status: Int) {
        if (level <= 0) return
        scope.launch {
            val automations = repository.getAutomations().first()
            automations.filter { it.enabled }.forEach { automation ->
                val alertAction = automation.actions.firstOrNull {
                    it.type == ActionType.BATTERY_ALERTS
                }
                val chargeAction = automation.actions.firstOrNull {
                    it.type == ActionType.BATTERY_CHARGING_NOTIFICATIONS
                }

                if (alertAction != null) {
                    val threshold = alertAction.config["below"]?.toIntOrNull() ?: 20
                    if (level <= threshold) {
                        if (!alertedLow) {
                            alertedLow = true
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedLow = false
                    }
                }

                if (chargeAction != null) {
                    if (status == BatteryManager.BATTERY_STATUS_FULL) {
                        if (!alertedChargeComplete) {
                            alertedChargeComplete = true
                            executionEngine.runAutomation(automation)
                        }
                    } else {
                        alertedChargeComplete = false
                    }
                }
            }
        }
    }
}
