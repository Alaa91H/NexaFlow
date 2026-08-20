package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDependencyScannerTest {

    @Test
    fun scansRunAndExitPluginActionsWithoutDuplicatingConfiguration() {
        val automation = automation(
            actions = listOf(pluginAction("plugin:run", approved = true)),
            exitActions = listOf(pluginAction(instance = null, approved = false))
        )

        val dependencies = PluginDependencyScanner.scan(listOf(automation))

        assertEquals(2, dependencies.size)
        assertEquals(PluginDependencySource.RUN_ACTION, dependencies[0].source)
        assertFalse(dependencies[0].requiresReconfiguration)
        assertEquals(PluginDependencySource.EXIT_ACTION, dependencies[1].source)
        assertTrue(dependencies[1].requiresReconfiguration)
        assertEquals("com.example.plugin", dependencies[0].packageName)
    }

    @Test
    fun ignoresIncompletePluginActionInsteadOfCreatingUnusableDependency() {
        val automation = automation(
            actions = listOf(Action(ActionType.PLUGIN_FIRE, mapOf("package" to "com.example.plugin")))
        )

        assertTrue(PluginDependencyScanner.scan(listOf(automation)).isEmpty())
    }

    private fun pluginAction(instance: String?, approved: Boolean): Action = Action(
        ActionType.PLUGIN_FIRE,
        buildMap {
            put("package", "com.example.plugin")
            put("receiver", "com.example.plugin.FireReceiver")
            put("editActivity", "com.example.plugin.EditActivity")
            put("bundleJson", "{\"unused\":true}")
            instance?.let { put("pluginInstance", it) }
            if (approved) put("pluginApproval", "approved")
        }
    )

    private fun automation(
        actions: List<Action> = emptyList(),
        exitActions: List<Action> = emptyList()
    ) = Automation(
        id = "plugin-workflow",
        name = "Plugin workflow",
        description = "",
        icon = "",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "Test",
        priority = 0,
        enabled = false,
        triggers = emptyList(),
        actions = actions,
        exitActions = exitActions,
        createdAt = 0L,
        updatedAt = 0L
    )
}
