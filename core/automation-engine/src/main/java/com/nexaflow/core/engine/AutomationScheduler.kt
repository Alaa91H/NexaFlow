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
import com.nexaflow.domain.schedule.TimeTriggerCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
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
        val config = automation.triggers
            .firstOrNull { it.type == TriggerType.TIME }
            ?.config ?: return
        val time = config["time"]
        if (time.isNullOrBlank()) return
        val triggerAt = TimeTriggerCalculator.nextFireTime(config, fromMillis = System.currentTimeMillis())
        if (triggerAt == null) return
        setAlarm(triggerAt, buildPendingIntent(automation.id, ACTION_RUN_AUTOMATION))
        // A time-range trigger also schedules an end-of-window alarm that runs
        // the exit/revert behavior when the range closes.
        if (config["timeMode"] == "RANGE") {
            val endAt = TimeTriggerCalculator.windowEndMillis(config, triggerAt)
            if (endAt != null && endAt > System.currentTimeMillis()) {
                setAlarm(endAt, buildPendingIntent(automation.id, ACTION_END_AUTOMATION))
            }
        }
    }

    fun scheduleNext(automationId: String) {
        scope.launch {
            val automation = repository.getAutomationById(automationId) ?: return@launch
            if (!automation.enabled) return@launch
            val config = automation.triggers
                .firstOrNull { it.type == TriggerType.TIME }
                ?.config ?: return@launch
            val time = config["time"]
            if (time.isNullOrBlank()) return@launch
            // One-shot automations must not be rescheduled after they fire.
            if (config["repeat"] == TimeTriggerCalculator.REPEAT_ONCE ||
                config["repeat"] == TimeTriggerCalculator.REPEAT_SPECIFIC_DATE
            ) return@launch
            val triggerAt = TimeTriggerCalculator.nextFireTime(config, fromMillis = System.currentTimeMillis() + 60_000L)
            if (triggerAt == null) return@launch
            setAlarm(triggerAt, buildPendingIntent(automationId, ACTION_RUN_AUTOMATION))
            // Keep the end-of-window alarm aligned with the rescheduled start.
            if (config["timeMode"] == "RANGE") {
                val endAt = TimeTriggerCalculator.windowEndMillis(config, triggerAt)
                if (endAt != null) {
                    setAlarm(endAt, buildPendingIntent(automationId, ACTION_END_AUTOMATION))
                }
            }
        }
    }

    fun cancel(automationId: String) {
        alarmManager.cancel(buildPendingIntent(automationId, ACTION_RUN_AUTOMATION))
        alarmManager.cancel(buildPendingIntent(automationId, ACTION_END_AUTOMATION))
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

    private fun buildPendingIntent(automationId: String, action: String): PendingIntent {
        val intent = Intent(context, AutomationAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_AUTOMATION_ID, automationId)
        return PendingIntent.getBroadcast(
            context,
            (automationId.hashCode() * 31 + action.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_RUN_AUTOMATION = "com.nexaflow.core.engine.action.RUN_AUTOMATION"
        const val ACTION_END_AUTOMATION = "com.nexaflow.core.engine.action.END_AUTOMATION"
        const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.engine.extra.AUTOMATION_ID"
    }
}
