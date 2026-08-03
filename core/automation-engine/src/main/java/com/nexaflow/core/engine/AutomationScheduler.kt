package com.nexaflow.core.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nexaflow.core.engine.di.ApplicationScope
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.repositories.AutomationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AutomationRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scheduledIds = mutableSetOf<String>()

    fun initialize() {
        scope.launch {
            repository.getAutomations().collect { automations ->
                rescheduleAll(automations)
            }
        }
    }

    fun rescheduleAll(automations: List<Automation>) {
        val desired = mutableSetOf<String>()
        automations.forEach { automation ->
            if (automation.enabled && automation.triggers.any { it.type == TriggerType.TIME }) {
                schedule(automation)
                desired.add(automation.id)
            }
        }
        val toCancel = scheduledIds - desired
        toCancel.forEach { cancel(it) }
        scheduledIds.clear()
        scheduledIds.addAll(desired)
    }

    fun schedule(automation: Automation) {
        val time = automation.triggers
            .firstOrNull { it.type == TriggerType.TIME }
            ?.config?.get("time")
        if (time.isNullOrBlank()) return
        val triggerAt = nextTriggerAt(time)
        val pendingIntent = buildPendingIntent(automation.id)
        setAlarm(triggerAt, pendingIntent)
    }

    fun scheduleNext(automationId: String) {
        scope.launch {
            val automation = repository.getAutomationById(automationId) ?: return@launch
            if (!automation.enabled) return@launch
            val time = automation.triggers
                .firstOrNull { it.type == TriggerType.TIME }
                ?.config?.get("time")
            if (time.isNullOrBlank()) return@launch
            val triggerAt = nextTriggerAt(time, fromMillis = System.currentTimeMillis() + 60_000L)
            setAlarm(triggerAt, buildPendingIntent(automationId))
        }
    }

    fun cancel(automationId: String) {
        alarmManager.cancel(buildPendingIntent(automationId))
    }

    private fun setAlarm(triggerAt: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun buildPendingIntent(automationId: String): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java)
            .setAction(ACTION_RUN_AUTOMATION)
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        return PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerAt(time: String, fromMillis: Long = System.currentTimeMillis()): Long {
        val localTime = LocalTime.parse(time)
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        var candidate = ZonedDateTime.of(today, localTime, zone).toInstant().toEpochMilli()
        if (candidate <= fromMillis) {
            candidate = ZonedDateTime.of(today.plusDays(1), localTime, zone).toInstant().toEpochMilli()
        }
        return candidate
    }

    companion object {
        const val ACTION_RUN_AUTOMATION = "com.nexaflow.core.engine.action.RUN_AUTOMATION"
        const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.engine.extra.AUTOMATION_ID"
    }
}
