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
        val triggerAt = nextTriggerAt(config, fromMillis = System.currentTimeMillis())
        if (triggerAt == null) return
        val pendingIntent = buildPendingIntent(automation.id)
        setAlarm(triggerAt, pendingIntent)
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
            if (config["repeat"] == REPEAT_ONCE) return@launch
            val triggerAt = nextTriggerAt(config, fromMillis = System.currentTimeMillis() + 60_000L)
            if (triggerAt == null) return@launch
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

    /**
     * Computes the next fire time for a TIME trigger honouring the repeat mode:
     * ONCE, DAILY, WEEKDAYS, WEEKENDS, SPECIFIC_DAYS, MONTHLY or DATE_RANGE.
     * Returns null when no future occurrence exists (e.g. past one-shot or
     * finished date range).
     */
    private fun nextTriggerAt(config: Map<String, String>, fromMillis: Long): Long? {
        val time = config["time"] ?: return null
        val localTime = LocalTime.parse(time)
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone).toLocalDate()
        val repeat = config["repeat"] ?: REPEAT_DAILY

        // Date-range window bounds (yyyy-MM-dd).
        val startDate = config["startDate"]?.let(::parseDate)
        val endDate = config["endDate"]?.let(::parseDate)
        if (endDate != null && today.isAfter(endDate)) return null

        var daysChecked = 0
        var candidate = ZonedDateTime.of(today, localTime, zone)
        while (daysChecked < MAX_SEARCH_DAYS) {
            val day = candidate.toLocalDate()
            if (startDate != null && day.isBefore(startDate)) {
                candidate = ZonedDateTime.of(startDate, localTime, zone)
                continue
            }
            if (endDate != null && day.isAfter(endDate)) return null
            if (matchesRepeat(repeat, config, day)) {
                val millis = candidate.toInstant().toEpochMilli()
                if (millis > fromMillis) return millis
            }
            candidate = candidate.plusDays(1)
            daysChecked++
        }
        return null
    }

    private fun matchesRepeat(repeat: String, config: Map<String, String>, day: java.time.LocalDate): Boolean {
        val dayOfWeek = day.dayOfWeek.value // 1=MON .. 7=SUN
        return when (repeat) {
            REPEAT_WEEKDAYS -> dayOfWeek in 1..5
            REPEAT_WEEKENDS -> dayOfWeek == 6 || dayOfWeek == 7
            REPEAT_SPECIFIC_DAYS -> {
                val days = config["days"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
                dayOfWeek in days
            }
            REPEAT_MONTHLY -> {
                val monthDay = config["monthDay"]?.toIntOrNull() ?: 1
                day.dayOfMonth == monthDay
            }
            else -> true // ONCE and DAILY
        }
    }

    private fun parseDate(value: String): java.time.LocalDate {
        return runCatching { java.time.LocalDate.parse(value) }.getOrNull()
            ?: java.time.LocalDate.parse(value.replace('/', '-'))
    }

    companion object {
        const val ACTION_RUN_AUTOMATION = "com.nexaflow.core.engine.action.RUN_AUTOMATION"
        const val EXTRA_AUTOMATION_ID = "com.nexaflow.core.engine.extra.AUTOMATION_ID"
        const val REPEAT_ONCE = "ONCE"
        const val REPEAT_DAILY = "DAILY"
        const val REPEAT_WEEKDAYS = "WEEKDAYS"
        const val REPEAT_WEEKENDS = "WEEKENDS"
        const val REPEAT_SPECIFIC_DAYS = "SPECIFIC_DAYS"
        const val REPEAT_MONTHLY = "MONTHLY"
        const val REPEAT_DATE_RANGE = "DATE_RANGE"
        private const val MAX_SEARCH_DAYS = 370
    }
}
