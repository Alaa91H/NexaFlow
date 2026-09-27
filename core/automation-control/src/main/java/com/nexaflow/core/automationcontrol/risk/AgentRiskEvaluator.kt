package com.nexaflow.core.automationcontrol.risk

import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.TriggerType

/**
 * Plan §18 - agent-mutation risk classification.
 *
 * Scores an automation definition for audit, telemetry, routing and
 * debugging. It never gates mutations: full permanent access means no
 * per-task approval dialogs. The system-computed level (not any
 * caller-supplied hint) is what the service persists into API metadata.
 *
 * Rules are deliberately transparent and family-based so agents and users
 * can predict them; capability-level enforcement stays in the domain
 * [com.nexaflow.domain.risk.RiskEngine] at execution time.
 */
enum class AgentRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class AgentRiskReport(
    val level: AgentRiskLevel,
    val reasons: List<String>
)

object AgentRiskEvaluator {

    fun evaluate(automation: Automation): AgentRiskReport {
        var level = AgentRiskLevel.LOW
        val reasons = ArrayList<String>(8)

        fun raise(to: AgentRiskLevel, reason: String) {
            if (to.ordinal > level.ordinal) {
                level = to
            }
            if (reasons.size < MAX_REASONS && reason !in reasons) {
                reasons += reason
            }
        }

        val actions = automation.actions + automation.exitActions
        actions.forEach { action ->
            when (action.type) {
                in CRITICAL_ACTIONS -> raise(
                    AgentRiskLevel.CRITICAL,
                    "action:${action.type.name}=CRITICAL"
                )
                in HIGH_ACTIONS -> raise(
                    AgentRiskLevel.HIGH,
                    "action:${action.type.name}=HIGH"
                )
                in MEDIUM_ACTIONS -> raise(
                    AgentRiskLevel.MEDIUM,
                    "action:${action.type.name}=MEDIUM"
                )
                else -> Unit
            }
        }
        automation.triggers.forEach { trigger ->
            when (trigger.type) {
                TriggerType.WEBHOOK -> raise(
                    AgentRiskLevel.HIGH,
                    "trigger:WEBHOOK=HIGH"
                )
                TriggerType.SMS,
                TriggerType.NOTIFICATION,
                TriggerType.LOCATION,
                TriggerType.CALL_STATE,
                TriggerType.INCOMING_CALL -> raise(
                    AgentRiskLevel.MEDIUM,
                    "trigger:${trigger.type.name}=MEDIUM"
                )
                else -> Unit
            }
        }

        val nodeCount = actions.size + automation.triggers.size
        if (nodeCount > LARGE_AUTOMATION_NODES) {
            val escalated = when (level) {
                AgentRiskLevel.LOW -> AgentRiskLevel.MEDIUM
                AgentRiskLevel.MEDIUM -> AgentRiskLevel.HIGH
                AgentRiskLevel.HIGH,
                AgentRiskLevel.CRITICAL -> AgentRiskLevel.CRITICAL
            }
            if (escalated != level) {
                level = escalated
                if (reasons.size < MAX_REASONS) {
                    reasons += "node_count:$nodeCount"
                }
            }
        }
        return AgentRiskReport(level, reasons.toList())
    }

    private val CRITICAL_ACTIONS = setOf(
        ActionType.ADVANCED_ROOT,
        ActionType.ADVANCED_SHIZUKU,
        ActionType.SYSTEM_INSTALL_APK,
        ActionType.SYSTEM_UNINSTALL_APP,
        ActionType.SYSTEM_CLEAR_APP_DATA,
        ActionType.SYSTEM_REBOOT,
        ActionType.SYSTEM_SHUTDOWN,
        ActionType.SYSTEM_SOFT_RESTART
    )

    private val HIGH_ACTIONS = setOf(
        ActionType.SYSTEM_SET_SETTING,
        ActionType.ROM_CUSTOM_SETTING,
        ActionType.ROM_BATCH,
        ActionType.SYSTEM_SEND_SMS,
        ActionType.SYSTEM_DIAL_NUMBER,
        ActionType.SYSTEM_HTTP_REQUEST,
        ActionType.SYSTEM_INPUT_TEXT,
        ActionType.SYSTEM_KEY_EVENT,
        ActionType.SYSTEM_INPUT_TAP,
        ActionType.SYSTEM_INPUT_SWIPE,
        ActionType.SYSTEM_DISABLE_APP,
        ActionType.SYSTEM_ENABLE_APP,
        ActionType.SYSTEM_FORCE_STOP_APP,
        ActionType.SYSTEM_PRIVATE_DNS,
        ActionType.CALL_BLOCK,
        ActionType.CALL_SILENCE
    )

    private val MEDIUM_ACTIONS = setOf(
        ActionType.SYSTEM_LOCATION,
        ActionType.SYSTEM_LOCATION_MODE,
        ActionType.SYSTEM_NETWORK_MODE,
        ActionType.SYSTEM_SEND_EMAIL,
        ActionType.SYSTEM_SCREENSHOT,
        ActionType.SYSTEM_PASTE,
        ActionType.SYSTEM_CLIPBOARD_SET,
        ActionType.SYSTEM_WIFI_CONNECT,
        ActionType.SYSTEM_WIFI_FORGET,
        ActionType.PLUGIN_FIRE,
        ActionType.SYSTEM_SET_TIMEZONE,
        ActionType.SYSTEM_AUTO_TIME,
        ActionType.SYSTEM_AUTO_TIMEZONE,
        ActionType.SYSTEM_DISPLAY_DENSITY,
        ActionType.SYSTEM_FONT_SCALE
    )

    private const val LARGE_AUTOMATION_NODES = 8
    private const val MAX_REASONS = 8
}
