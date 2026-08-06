package com.nexaflow.core.execution.compat

import com.nexaflow.core.execution.workflow.WorkflowNode
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationWorkflowMapperTest {

    private fun automation(
        actions: List<Action> = emptyList(),
        exitActions: List<Action> = emptyList(),
        revertOnExit: Boolean = false,
        triggers: List<Trigger> = emptyList()
    ) = Automation(
        id = "auto-1",
        name = "Test task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 2,
        enabled = true,
        triggers = triggers,
        actions = actions,
        exitActions = exitActions,
        revertOnExit = revertOnExit,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun actions_mapToSequenceOfActionNodes() {
        val mapped = AutomationWorkflowMapper.map(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "80")),
                    Action(ActionType.SYSTEM_VOLUME, mapOf("value" to "50")),
                    Action(ActionType.SYSTEM_WAIT, mapOf("seconds" to "5"))
                )
            )
        )
        val root = mapped.runWorkflow.root
        assertTrue(root is WorkflowNode.SequenceNode)
        val children = (root as WorkflowNode.SequenceNode).children
        assertEquals(3, children.size)
        children.forEachIndexed { index, child ->
            assertTrue("child $index must be an ActionNode", child is WorkflowNode.ActionNode)
            assertEquals(
                listOf(ActionType.SYSTEM_BRIGHTNESS, ActionType.SYSTEM_VOLUME, ActionType.SYSTEM_WAIT)[index],
                (child as WorkflowNode.ActionNode).action.type
            )
        }
    }

    @Test
    fun emptyActions_produceEmptySequence() {
        val mapped = AutomationWorkflowMapper.map(automation())
        val root = mapped.runWorkflow.root
        assertTrue(root is WorkflowNode.SequenceNode)
        assertTrue((root as WorkflowNode.SequenceNode).children.isEmpty())
    }

    @Test
    fun exitActions_mapToExitWorkflow() {
        val mapped = AutomationWorkflowMapper.map(
            automation(exitActions = listOf(Action(ActionType.SYSTEM_DARK_MODE, mapOf("enabled" to "false"))))
        )
        assertNotNull(mapped.exitWorkflow)
        val root = mapped.exitWorkflow!!.root
        assertTrue(root is WorkflowNode.SequenceNode)
        val children = (root as WorkflowNode.SequenceNode).children
        assertEquals(1, children.size)
        assertEquals(
            ActionType.SYSTEM_DARK_MODE,
            (children.first() as WorkflowNode.ActionNode).action.type
        )
    }

    @Test
    fun noExitActions_produceNullExitWorkflow() {
        val mapped = AutomationWorkflowMapper.map(automation())
        assertNull(mapped.exitWorkflow)
    }

    @Test
    fun revertOnExit_isPreserved() {
        val mapped = AutomationWorkflowMapper.map(automation(revertOnExit = true))
        assertTrue(mapped.revertOnExit)
        assertFalse(AutomationWorkflowMapper.map(automation()).revertOnExit)
    }

    @Test
    fun triggers_areKeptAsEntryMetadata() {
        val triggers = listOf(
            Trigger(TriggerType.TIME, mapOf("time" to "08:00")),
            Trigger(TriggerType.BATTERY, mapOf("level" to "20"))
        )
        val mapped = AutomationWorkflowMapper.map(automation(triggers = triggers))
        assertEquals(2, mapped.entryTriggers.size)
        assertEquals(TriggerType.TIME, mapped.entryTriggers[0].type)
        assertEquals(TriggerType.BATTERY, mapped.entryTriggers[1].type)
    }

    @Test
    fun metadata_isCarriedOver() {
        val mapped = AutomationWorkflowMapper.map(automation())
        assertEquals("auto-1", mapped.automationId)
        assertEquals("Test task", mapped.automationName)
        assertEquals(2, mapped.priority)
        assertEquals("auto-1", mapped.runWorkflow.id)
    }

    @Test
    fun nodeIds_areStableAndUnique() {
        val mapped = AutomationWorkflowMapper.map(
            automation(
                actions = listOf(
                    Action(ActionType.SYSTEM_BRIGHTNESS, emptyMap()),
                    Action(ActionType.SYSTEM_VOLUME, emptyMap())
                )
            )
        )
        val ids = (mapped.runWorkflow.root as WorkflowNode.SequenceNode).children.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("run:auto-1") })
    }
}
