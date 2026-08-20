package com.nexaflow.data.backup

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import kotlinx.serialization.Serializable

/**
 * Portable, non-secret dependency record for one external plugin action. The
 * configuration Bundle itself remains in the workflow action and is never
 * duplicated here; this record exists solely for preflight and user review.
 */
@Serializable
data class PluginDependency(
    val workflowId: String,
    val packageName: String,
    val receiverClass: String,
    val editActivityClass: String? = null,
    val protocol: String = "LOCALE_BASE",
    val source: PluginDependencySource,
    val requiresReconfiguration: Boolean
)

@Serializable
enum class PluginDependencySource {
    RUN_ACTION,
    EXIT_ACTION
}

/** Deterministic extractor shared by export and non-mutating import preflight. */
object PluginDependencyScanner {
    fun scan(automations: List<Automation>): List<PluginDependency> = automations
        .flatMap { automation ->
            scanActions(automation.id, automation.actions, PluginDependencySource.RUN_ACTION) +
                scanActions(automation.id, automation.exitActions, PluginDependencySource.EXIT_ACTION)
        }
        .distinct()
        .sortedWith(
            compareBy<PluginDependency> { it.workflowId }
                .thenBy { if (it.source == PluginDependencySource.RUN_ACTION) 0 else 1 }
                .thenBy { it.packageName }
                .thenBy { it.receiverClass }
        )

    private fun scanActions(
        workflowId: String,
        actions: List<Action>,
        source: PluginDependencySource
    ): List<PluginDependency> = actions.mapNotNull { action ->
        if (action.type != ActionType.PLUGIN_FIRE) return@mapNotNull null
        val packageName = action.config[KEY_PACKAGE]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val receiver = action.config[KEY_RECEIVER]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PluginDependency(
            workflowId = workflowId,
            packageName = packageName,
            receiverClass = receiver,
            editActivityClass = action.config[KEY_EDIT_ACTIVITY]?.takeIf { it.isNotBlank() },
            source = source,
            // Legacy actions execute through their compatibility handler until
            // the user reconfigures them with an explicit opaque instance.
            requiresReconfiguration = action.config[KEY_INSTANCE].isNullOrBlank() ||
                action.config[KEY_APPROVAL] != APPROVAL_VALUE
        )
    }

    private const val KEY_PACKAGE = "package"
    private const val KEY_RECEIVER = "receiver"
    private const val KEY_EDIT_ACTIVITY = "editActivity"
    private const val KEY_INSTANCE = "pluginInstance"
    private const val KEY_APPROVAL = "pluginApproval"
    private const val APPROVAL_VALUE = "approved"
}
