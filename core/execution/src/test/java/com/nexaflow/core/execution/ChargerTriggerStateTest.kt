package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.domain.models.ConditionResult
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for issue #6: the battery monitor dispatches charger
 * transitions from ACTION_BATTERY_CHANGED, so the ALL-trigger gate must read
 * the same sticky state rather than racing BatteryManager.isCharging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChargerTriggerStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Suppress("DEPRECATION")
    private fun setStickyBatteryState(status: Int, plugged: Int) {
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 50)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        )
    }

    private fun charger(event: String) =
        Trigger(TriggerType.CHARGER, mapOf("event" to event))

    @Test
    fun connectedConditionUsesStickyChargingBroadcast() = runBlocking {
        setStickyBatteryState(
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_PLUGGED_USB
        )

        assertEquals(
            ConditionResult.Satisfied,
            TriggerStateEvaluator.evaluateTriggerState(context, charger("CONNECTED"))
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            TriggerStateEvaluator.evaluateTriggerState(context, charger("DISCONNECTED"))
        )
    }

    @Test
    fun fullBatteryStillCountsAsConnectedLikeBatteryMonitor() = runBlocking {
        setStickyBatteryState(
            BatteryManager.BATTERY_STATUS_FULL,
            BatteryManager.BATTERY_PLUGGED_AC
        )

        assertEquals(
            ConditionResult.Satisfied,
            TriggerStateEvaluator.evaluateTriggerState(context, charger("CONNECTED"))
        )
    }

    @Test
    fun disconnectedConditionUsesStickyDischargingBroadcast() = runBlocking {
        setStickyBatteryState(
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            0
        )

        assertEquals(
            ConditionResult.Satisfied,
            TriggerStateEvaluator.evaluateTriggerState(context, charger("DISCONNECTED"))
        )
        assertEquals(
            ConditionResult.Unsatisfied,
            TriggerStateEvaluator.evaluateTriggerState(context, charger("CONNECTED"))
        )
    }
}
