package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationDagCompilerTest {

    private fun automation(
        triggers: List<Trigger> = listOf(Trigger(TriggerType.TIME, mapOf("time" to "08:00"))),
        constraints: List<Constraint> = emptyList(),
        actions: List<Action> = emptyList(),
        exitActions: List<Action> = emptyList(),
    ) = Automation(
        id = "a1",
        name = "Test",
        description = "",
        icon = "star",
        iconColor = 0,
        backgroundColor = 0,
        category = "general",
        priority = 0,
        enabled = true,
        triggers = triggers,
        actions = actions,
        constraints = constraints,
        exitActions = exitActions,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun `automation without triggers is rejected`() {
        val result = AutomationDagCompiler.compile(automation(triggers = emptyList()))
        assertTrue(result is DagCompilationResult.Failure)
        assertTrue((result as DagCompilationResult.Failure).reason.contains("no triggers"))
    }

    @Test
    fun `empty automation compiles to a single trigger node`() {
        val result = AutomationDagCompiler.compile(automation())
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertEquals(1, success.graph.nodes.size)
        assertEquals(DagNodeType.TRIGGER, success.graph.nodes.first().type)
        assertEquals(listOf("trigger-0"), success.executionOrder)
        assertEquals(listOf("trigger-0"), success.graph.entryNodeIds)
        assertEquals("TIME", success.graph.nodes.first().input["type"])
    }

    @Test
    fun `trigger constraint action exit compile into one verified chain`() {
        val automation = automation(
            triggers = listOf(Trigger(TriggerType.BATTERY, mapOf("direction" to "ABOVE"))),
            constraints = listOf(Constraint(ConstraintType.BATTERY, mapOf("level" to "80"))),
            actions = listOf(Action(ActionType.SYSTEM_VOLUME, mapOf("level" to "10"))),
            exitActions = listOf(Action(ActionType.SYSTEM_VOLUME, mapOf("level" to "5"))),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")

        // 1 trigger + 1 constraint + 1 action + 1 exit = 4 nodes.
        assertEquals(4, success.graph.nodes.size)
        val byType = success.graph.nodes.groupBy { it.type }
        assertEquals(1, byType[DagNodeType.TRIGGER]?.size)
        assertEquals(1, byType[DagNodeType.CONDITION]?.size)
        assertEquals(1, byType[DagNodeType.ACTION]?.size)
        assertEquals(1, byType[DagNodeType.EXIT]?.size)

        // The graph is acyclic and fully ordered.
        assertNull("graph must be acyclic", success.graph.let { if (it.isCyclic) "cycle" else null })
        assertEquals(4, success.executionOrder.size)
        assertEquals(listOf("trigger-0"), success.graph.entryNodeIds)

        // Trigger config (direction) is carried onto the node input.
        assertEquals("ABOVE", success.graph.nodes.first { it.type == DagNodeType.TRIGGER }.input["direction"])
    }

    @Test
    fun `multiple triggers are all entries and fan into the constraint head`() {
        val automation = automation(
            triggers = listOf(
                Trigger(TriggerType.TIME, mapOf("time" to "08:00")),
                Trigger(TriggerType.BATTERY, mapOf("direction" to "ABOVE")),
            ),
            constraints = listOf(Constraint(ConstraintType.WIFI, emptyMap())),
            actions = listOf(Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap())),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertEquals(listOf("trigger-0", "trigger-1"), success.graph.entryNodeIds)
        // Both triggers point at the constraint head.
        assertEquals(setOf("constraint-0"), success.graph.edges["trigger-0"])
        assertEquals(setOf("constraint-0"), success.graph.edges["trigger-1"])
        // Constraint → action.
        assertEquals(setOf("action-0"), success.graph.edges["constraint-0"])
    }

    @Test
    fun `multiple constraints form a chain`() {
        val automation = automation(
            constraints = listOf(
                Constraint(ConstraintType.WIFI, emptyMap()),
                Constraint(ConstraintType.HEADSET, emptyMap()),
                Constraint(ConstraintType.SCREEN_LOCKED, emptyMap()),
            ),
            actions = listOf(Action(ActionType.SYSTEM_WIFI, mapOf("state" to "ON"))),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertEquals(setOf("constraint-1"), success.graph.edges["constraint-0"])
        assertEquals(setOf("constraint-2"), success.graph.edges["constraint-1"])
        assertEquals(setOf("action-0"), success.graph.edges["constraint-2"])
    }

    @Test
    fun `actions and exits form their own chains`() {
        val automation = automation(
            actions = listOf(
                Action(ActionType.SYSTEM_DND, mapOf("mode" to "ON")),
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap()),
            ),
            exitActions = listOf(
                Action(ActionType.SYSTEM_DND, mapOf("mode" to "OFF")),
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, emptyMap()),
            ),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertEquals(setOf("action-1"), success.graph.edges["action-0"])
        assertEquals(setOf("exit-0"), success.graph.edges["action-1"])
        assertEquals(setOf("exit-1"), success.graph.edges["exit-0"])
        // Exit node carries its own config (mode=OFF), not the action's.
        val exit0 = success.graph.nodes.first { it.id == "exit-0" }
        assertEquals("OFF", exit0.input["mode"])
        assertEquals(DagNodeType.EXIT, exit0.type)
    }

    @Test
    fun `execution order is topological - every node before its successors`() {
        val automation = automation(
            triggers = listOf(
                Trigger(TriggerType.TIME, emptyMap()),
                Trigger(TriggerType.WEBHOOK, emptyMap()),
            ),
            constraints = listOf(
                Constraint(ConstraintType.BATTERY, emptyMap()),
                Constraint(ConstraintType.WIFI, emptyMap()),
            ),
            actions = listOf(
                Action(ActionType.SYSTEM_VOLUME, emptyMap()),
                Action(ActionType.SYSTEM_DND, emptyMap()),
            ),
            exitActions = listOf(Action(ActionType.SYSTEM_DND, emptyMap())),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        val order = success.executionOrder
        // 2 triggers + 2 constraints + 2 actions + 1 exit.
        assertEquals(7, order.size)

        val position = order.withIndex().associate { it.value to it.index }
        // Triggers first (both entries), then constraints, actions, exit.
        assert(position["trigger-0"]!! < position["constraint-0"]!!)
        assert(position["trigger-1"]!! < position["constraint-0"]!!)
        assert(position["constraint-0"]!! < position["constraint-1"]!!)
        assert(position["constraint-1"]!! < position["action-0"]!!)
        assert(position["action-0"]!! < position["action-1"]!!)
        assert(position["action-1"]!! < position["exit-0"]!!)
    }

    @Test
    fun `compiled graph is never cyclic`() {
        // Exercise the full pipeline with a rich automation: the compiler must
        // always return a Success with a non-null order (its shape is a DAG by
        // construction — segments fan in, never loop back).
        val automation = automation(
            triggers = List(3) { Trigger(TriggerType.SENSOR, mapOf("sensor" to "PROXIMITY")) },
            constraints = List(4) { Constraint(ConstraintType.BATTERY, emptyMap()) },
            actions = List(3) { Action(ActionType.SYSTEM_FLASHLIGHT, emptyMap()) },
            exitActions = List(2) { Action(ActionType.SYSTEM_FLASHLIGHT, emptyMap()) },
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertNotNull(success.executionOrder)
        assertTrue(!success.graph.isCyclic)
        assertEquals(3 + 4 + 3 + 2, success.executionOrder.size)
    }

    @Test
    fun `exit-only automation chains triggers into exits`() {
        val automation = automation(
            triggers = listOf(Trigger(TriggerType.LOCATION, emptyMap())),
            exitActions = listOf(Action(ActionType.SYSTEM_LOCATION, mapOf("state" to "OFF"))),
        )
        val result = AutomationDagCompiler.compile(automation)
        val success = result as? DagCompilationResult.Success ?: error("expected success, got $result")
        assertEquals(setOf("exit-0"), success.graph.edges["trigger-0"])
        assertEquals(listOf("trigger-0", "exit-0"), success.executionOrder)
    }
}
