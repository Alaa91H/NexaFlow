package com.nexaflow.core.automationcontrol.risk

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRiskEvaluatorTest {

    @Test
    fun plainTogglesAreLow() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                triggers = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00"))),
                actions = listOf(Action(ActionType.SYSTEM_WIFI, mapOf("enabled" to "true")))
            )
        )

        assertEquals(AgentRiskLevel.LOW, report.level)
        assertTrue(report.reasons.isEmpty())
    }

    @Test
    fun arbitraryShellIsCritical() {
        val report = AgentRiskEvaluator.evaluate(
            automation(actions = listOf(Action(ActionType.ADVANCED_ROOT, mapOf("command" to "id"))))
        )

        assertEquals(AgentRiskLevel.CRITICAL, report.level)
        assertTrue(report.reasons.any { it.contains("ADVANCED_ROOT") })
    }

    @Test
    fun uninstallIsCritical() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_UNINSTALL_APP, mapOf("package" to "com.example"))
                )
            )
        )

        assertEquals(AgentRiskLevel.CRITICAL, report.level)
    }

    @Test
    fun remoteTriggerIsHigh() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                triggers = listOf(Trigger(TriggerType.WEBHOOK, mapOf("path" to "/run"))),
                actions = listOf(Action(ActionType.SYSTEM_WIFI, emptyMap()))
            )
        )

        assertEquals(AgentRiskLevel.HIGH, report.level)
        assertTrue(report.reasons.any { it.contains("WEBHOOK") })
    }

    @Test
    fun privacyTriggerIsMedium() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                triggers = listOf(Trigger(TriggerType.LOCATION, emptyMap())),
                actions = listOf(Action(ActionType.SYSTEM_WIFI, emptyMap()))
            )
        )

        assertEquals(AgentRiskLevel.MEDIUM, report.level)
    }

    @Test
    fun levelIsTheMaximumAcrossSignals() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                triggers = listOf(Trigger(TriggerType.SMS, emptyMap())),
                actions = listOf(
                    Action(ActionType.SYSTEM_SEND_SMS, mapOf("to" to "1")),
                    Action(ActionType.SYSTEM_WIFI, emptyMap())
                )
            )
        )

        assertEquals(AgentRiskLevel.HIGH, report.level)
    }

    @Test
    fun largeGraphsEscalateOneLevel() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                triggers = List(4) { Trigger(TriggerType.TIME, mapOf("time" to "08:00")) },
                actions = List(5) { Action(ActionType.SYSTEM_WIFI, emptyMap()) }
            )
        )

        assertEquals(AgentRiskLevel.MEDIUM, report.level)
        assertTrue(report.reasons.any { it.startsWith("node_count:") })
    }

    @Test
    fun exitActionsCountTowardRisk() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                actions = listOf(Action(ActionType.SYSTEM_WIFI, emptyMap())),
                exitActions = listOf(Action(ActionType.ADVANCED_SHIZUKU, mapOf("command" to "id")))
            )
        )

        assertEquals(AgentRiskLevel.CRITICAL, report.level)
    }

    @Test
    fun reasonsStayBounded() {
        val report = AgentRiskEvaluator.evaluate(
            automation(
                actions = List(20) { Action(ActionType.SYSTEM_HTTP_REQUEST, mapOf("url" to "https://x")) }
            )
        )

        assertEquals(AgentRiskLevel.CRITICAL, report.level)
        assertTrue(report.reasons.size <= 8)
    }

    private fun automation(
        triggers: List<Trigger> = emptyList(),
        actions: List<Action> = emptyList(),
        exitActions: List<Action> = emptyList()
    ) = Automation(
        id = "risk-task",
        name = "Risk task",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "custom",
        priority = 1,
        enabled = false,
        triggers = triggers,
        actions = actions,
        exitActions = exitActions,
        cooldownSeconds = 0,
        createdAt = 0L,
        updatedAt = 0L
    )
}
