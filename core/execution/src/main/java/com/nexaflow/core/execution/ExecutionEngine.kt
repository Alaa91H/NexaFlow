package com.nexaflow.core.execution

import android.content.Context
import android.media.AudioManager
import com.nexaflow.core.rom.RomIntegrationManager
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.repositories.HistoryRepository
import java.util.UUID

class ExecutionEngine(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {

    suspend fun runAutomation(automation: Automation): ExecutionRecord {
        val controller = RomIntegrationManager.controller(context)
        val results = automation.actions.map { executeAction(it, controller) }
        val record = ExecutionRecord(
            id = UUID.randomUUID().toString(),
            automationId = automation.id,
            automationName = automation.name,
            success = results.all { it.success },
            message = buildMessage(results),
            executedAt = System.currentTimeMillis()
        )
        historyRepository.recordExecution(record)
        return record
    }

    private fun executeAction(action: Action, controller: SystemController): SystemControlResult {
        return when (action.type) {
            ActionType.SYSTEM_BRIGHTNESS ->
                controller.setBrightness(action.config["value"]?.toIntOrNull() ?: 128)
            ActionType.SYSTEM_VOLUME ->
                controller.setVolume(AudioManager.STREAM_MUSIC, action.config["value"]?.toIntOrNull() ?: 50)
            ActionType.SYSTEM_DND ->
                controller.setDoNotDisturb(action.config["enabled"]?.toBoolean() ?: true)
            ActionType.SYSTEM_SCREEN_ROTATION ->
                controller.setScreenRotation(action.config["autoRotate"]?.toBoolean() ?: true)
            ActionType.SYSTEM_OPEN_APP ->
                controller.launchApp(action.config["package"] ?: "")
            ActionType.SYSTEM_SEND_NOTIFICATION ->
                controller.sendNotification(
                    action.config["title"] ?: "NexaFlow",
                    action.config["text"] ?: "Automation executed"
                )
            ActionType.APPLICATION_LAUNCH_APP ->
                controller.launchApp(action.config["package"] ?: "")
            ActionType.APPLICATION_CLOSE_APP ->
                controller.forceStopPackage(action.config["package"] ?: "")
            ActionType.BATTERY_ALERTS,
            ActionType.BATTERY_CHARGING_NOTIFICATIONS ->
                controller.sendNotification(
                    "Battery Alert",
                    action.config["message"] ?: "Battery alert triggered"
                )
            ActionType.ADVANCED_SHIZUKU,
            ActionType.ADVANCED_ROOT ->
                SystemControlResult.fail("${action.type.name} requires an external runtime that is not wired yet")
        }
    }

    private fun buildMessage(results: List<SystemControlResult>): String {
        if (results.isEmpty()) return "No actions configured"
        return results.joinToString(" | ") { it.message }
    }
}
