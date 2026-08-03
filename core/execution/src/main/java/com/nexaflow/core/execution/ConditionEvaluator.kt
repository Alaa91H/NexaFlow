package com.nexaflow.core.execution

import android.content.Context
import android.os.BatteryManager
import com.nexaflow.domain.models.Condition
import com.nexaflow.domain.models.ConditionType
import java.time.LocalTime

class ConditionEvaluator(
    private val batteryLevelProvider: () -> Int,
    private val clock: () -> LocalTime = LocalTime::now
) {

    constructor(context: Context) : this(
        batteryLevelProvider = { readBatteryLevel(context) }
    )

    fun isMet(conditions: List<Condition>): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { isMet(it) }
    }

    private fun isMet(condition: Condition): Boolean {
        return when (condition.type) {
            ConditionType.AND -> condition.nestedConditions?.all { isMet(it) } ?: true
            ConditionType.OR -> condition.nestedConditions?.any { isMet(it) } ?: false
            ConditionType.NOT ->
                !(condition.nestedConditions?.firstOrNull()?.let { isMet(it) } ?: false)
            ConditionType.BATTERY_PERCENTAGE -> {
                val above = condition.config["above"]?.toIntOrNull() ?: 20
                batteryLevelProvider() >= above
            }
            ConditionType.TIME_RANGE -> {
                val start = condition.config["start"]
                val end = condition.config["end"]
                if (start.isNullOrBlank() || end.isNullOrBlank()) return true
                withinTimeRange(start, end)
            }
        }
    }

    private fun withinTimeRange(start: String, end: String): Boolean {
        return try {
            val now = clock()
            val startTime = LocalTime.parse(start)
            val endTime = LocalTime.parse(end)
            if (startTime <= endTime) {
                !now.isBefore(startTime) && !now.isAfter(endTime)
            } else {
                !now.isBefore(startTime) || !now.isAfter(endTime)
            }
        } catch (_: Throwable) {
            true
        }
    }

    private companion object {
        fun readBatteryLevel(context: Context): Int {
            return try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            } catch (_: Throwable) {
                100
            }
        }
    }
}
