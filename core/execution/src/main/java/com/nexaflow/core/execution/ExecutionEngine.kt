package com.nexaflow.core.execution

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.rom.PrivilegedRunner
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.UUID

class ExecutionEngine(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val notificationPreferences: NotificationPreferences
) {

    /** Snapshots captured for automations with revertOnExit, keyed by automation id. */
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, DeviceStateSnapshot>()

    suspend fun runAutomation(automation: Automation): ExecutionRecord {
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        if (automation.revertOnExit) {
            snapshots[automation.id] = DeviceStateSnapshot.capture(context)
        }
        val results = automation.actions.map { executeAction(it, controller, notif) }
        // Actions are executed sequentially, so a SYSTEM_WAIT action placed anywhere
        // pauses the chain for the configured duration (counter mode).
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = buildMessage(results),
            executedAt = System.currentTimeMillis()
        )
        historyRepository.recordExecution(record)
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /**
     * Runs the exit behavior of a task when its condition stops being true:
     * either restores the device to its pre-run state (revertOnExit) or runs
     * the configured exit actions. Records the run in history as well.
     */
    suspend fun runExit(automation: Automation): ExecutionRecord {
        val controller = RomIntegrationManager.controller(context)
        val notif = notificationPreferences.settings.first()
        val results = if (automation.revertOnExit) {
            val snapshot = snapshots.remove(automation.id)
            if (snapshot != null) {
                snapshot.restore(context)
                listOf(SystemControlResult.ok("Restored original state"))
            } else {
                listOf(SystemControlResult.ok("Nothing to restore"))
            }
        } else {
            automation.exitActions.map { executeAction(it, controller, notif) }
        }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = buildMessage(results),
            executedAt = System.currentTimeMillis()
        )
        historyRepository.recordExecution(record)
        context.sendBroadcast(Intent(ACTION_AUTOMATIONS_CHANGED).setPackage(context.packageName))
        return record
    }

    /** Discards any stored snapshot (e.g. when the automation is deleted). */
    fun clearSnapshot(automationId: String) {
        snapshots.remove(automationId)
    }

    private suspend fun executeAction(
        action: Action,
        controller: SystemController,
        notif: NotificationSettings
    ): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_WAIT -> {
                val seconds = action.config["seconds"]?.toIntOrNull()?.coerceIn(1, 3600) ?: 5
                delay(seconds * 1000L)
                SystemControlResult.ok("Waited ${seconds}s")
            }
            ActionType.SYSTEM_BRIGHTNESS ->
                controller.setBrightness(action.config["value"]?.toIntOrNull() ?: 128)
            ActionType.SYSTEM_VOLUME ->
                controller.setVolume(AudioManager.STREAM_MUSIC, action.config["value"]?.toIntOrNull() ?: 50)
            ActionType.SYSTEM_STREAM_VOLUME ->
                controller.setVolume(
                    AudioStreams.streamId(action.config["stream"] ?: "MUSIC"),
                    action.config["value"]?.toIntOrNull() ?: 50
                )
            ActionType.SYSTEM_DND ->
                controller.setDoNotDisturb(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_SCREEN_ROTATION ->
                controller.setScreenRotation(action.config["autoRotate"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_APP -> {
                val packages = (action.config["packages"] ?: action.config["package"] ?: "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (packages.isEmpty()) {
                    SystemControlResult.fail("No app selected")
                } else {
                    packages.forEach { controller.launchApp(it) }
                    SystemControlResult.ok("Opened ${packages.size} app(s)")
                }
            }
            ActionType.SYSTEM_SEND_NOTIFICATION ->
                if (notif.enabled && notif.executionEnabled) {
                    controller.sendNotification(
                        action.config["title"] ?: "NexaFlow",
                        action.config["text"] ?: "Automation executed",
                        sound = action.config["sound"] ?: "DEFAULT"
                    )
                } else {
                    SystemControlResult.ok("Notifications disabled")
                }
            ActionType.SYSTEM_WIFI ->
                controller.setWifi(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_BLUETOOTH ->
                controller.setBluetooth(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_FLASHLIGHT ->
                controller.setFlashlight(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_AIRPLANE_MODE ->
                controller.setAirplaneMode(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_MEDIA_PLAY_PAUSE ->
                controller.mediaControl("PLAY_PAUSE")
            ActionType.SYSTEM_MEDIA_NEXT ->
                controller.mediaControl("NEXT")
            ActionType.SYSTEM_MEDIA_PREVIOUS ->
                controller.mediaControl("PREVIOUS")
            ActionType.SYSTEM_OPEN_URL ->
                controller.openUrl(action.config["url"] ?: "")
            ActionType.SYSTEM_CLEAR_NOTIFICATIONS ->
                controller.clearNotifications()
            ActionType.SYSTEM_EXPAND_STATUS_BAR ->
                controller.expandStatusBar()
            ActionType.SYSTEM_COLLAPSE_STATUS_BAR ->
                controller.collapseStatusBar()
            ActionType.SYSTEM_SCREEN_TIMEOUT ->
                controller.setScreenTimeout(action.config["seconds"]?.toIntOrNull() ?: 60)
            ActionType.SYSTEM_STAY_AWAKE ->
                controller.setStayAwake(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_AUTO_BRIGHTNESS ->
                controller.setAutoBrightness(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_RINGER_MODE ->
                controller.setRingerMode(action.config["mode"] ?: "NORMAL")
            ActionType.SYSTEM_MOBILE_DATA ->
                controller.setMobileData(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_HOTSPOT ->
                controller.setHotspot(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_NFC ->
                controller.setNfc(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_POWER_SAVER ->
                controller.setPowerSaver(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_ANIMATIONS ->
                controller.setAnimations(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_LOCK_SCREEN ->
                controller.lockScreenNow()
            ActionType.SYSTEM_SET_ALARM ->
                controller.setAlarm(
                    action.config["hour"]?.toIntOrNull() ?: 7,
                    action.config["minute"]?.toIntOrNull() ?: 0
                )
            ActionType.SYSTEM_DARK_MODE ->
                controller.setDarkMode(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_RECENTS ->
                controller.openRecents()
            ActionType.SYSTEM_GO_HOME ->
                controller.goHome()
            ActionType.SYSTEM_RING_VOLUME ->
                controller.setRingVolume(action.config["value"]?.toIntOrNull() ?: 50)
            ActionType.SYSTEM_LOCATION ->
                controller.setLocationEnabled(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_PLAY_UPDATES ->
                controller.openPlayStoreUpdates()
            ActionType.SYSTEM_OPEN_GALAXY_STORE ->
                controller.openGalaxyStore()
            ActionType.SYSTEM_SEND_SMS ->
                controller.sendSms(action.config["number"] ?: "", action.config["text"] ?: "")
            ActionType.SYSTEM_SEND_REMINDER ->
                if (notif.enabled && notif.remindersEnabled) {
                    scheduleReminder(
                        action.config["title"] ?: "Reminder",
                        action.config["text"] ?: "",
                        action.config["hour"]?.toIntOrNull() ?: 9,
                        action.config["minute"]?.toIntOrNull() ?: 0
                    )
                } else {
                    SystemControlResult.ok("Reminders disabled")
                }
            ActionType.SYSTEM_OPEN_SETTINGS ->
                controller.openSystemSettings(action.config["page"] ?: "")
            ActionType.APPLICATION_OPEN_APP_SETTINGS ->
                controller.openAppSettings(action.config["package"] ?: "")
            ActionType.APPLICATION_LAUNCH_APP ->
                controller.launchApp(action.config["package"] ?: "")
            ActionType.APPLICATION_CLOSE_APP ->
                controller.forceStopPackage(action.config["package"] ?: "")
            ActionType.BATTERY_ALERTS,
            ActionType.BATTERY_CHARGING_NOTIFICATIONS ->
                if (notif.enabled && notif.executionEnabled) {
                    controller.sendNotification(
                        "Battery Alert",
                        action.config["message"] ?: "Battery alert triggered",
                        sound = action.config["sound"] ?: "DEFAULT"
                    )
                } else {
                    SystemControlResult.ok("Notifications disabled")
                }
            ActionType.ADVANCED_SHIZUKU ->
                PrivilegedRunner.runShizuku(action.config["command"] ?: "echo nexaflow")
            ActionType.ADVANCED_ROOT ->
                PrivilegedRunner.runRoot(action.config["command"] ?: "echo nexaflow")
        }
    }

    private fun scheduleReminder(title: String, text: String, hour: Int, minute: Int): SystemControlResult {
        return try {
            val zone = java.time.ZoneId.systemDefault()
            var at = java.time.ZonedDateTime
                .now(zone)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli()
            if (at <= System.currentTimeMillis()) {
                at = java.time.ZonedDateTime
                    .now(zone)
                    .plusDays(1)
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
                    .toInstant()
                    .toEpochMilli()
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ReminderAlarmReceiver.ACTION_SHOW_REMINDER)
                .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title.ifBlank { "Reminder" })
                .putExtra(ReminderAlarmReceiver.EXTRA_TEXT, text.ifBlank { "You asked me to remind you." })
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                at.toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
            SystemControlResult.ok("Reminder scheduled")
        } catch (t: Throwable) {
            SystemControlResult.fail("Failed to schedule reminder: ${t.message}")
        }
    }

    private fun buildMessage(results: List<SystemControlResult>): String {
        if (results.isEmpty()) return "No actions configured"
        return results.joinToString(" | ") { it.message }
    }
}
