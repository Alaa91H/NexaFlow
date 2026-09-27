package com.nexaflow.feature.builder

import androidx.compose.runtime.Immutable
import com.nexaflow.domain.catalog.AutomationNodeCatalog
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.EndBehavior
import java.util.UUID

/**
 * Editable execution card inside the builder.
 *
 * The persisted [Action] model deliberately remains list-based and unchanged.
 * This draft supplies the stable per-card identity required by the editor so
 * equal action types may coexist with independent configuration, end behavior,
 * picker targets and saved-instance state.
 */
@Immutable
data class ActionDraft(
    val id: String = UUID.randomUUID().toString(),
    val option: ActionOption,
    val config: Map<String, String> = defaultActionConfig(option.actionType),
    val endBehavior: EndBehavior? = null
) {
    init {
        require(id.isNotBlank()) { "Action draft id must not be blank" }
    }

    fun toAction(): Action = Action(
        type = option.actionType,
        config = config.toMap(),
        endBehavior = endBehavior
    )
}

/**
 * Canonical defaults come from the domain catalog so the builder, agents and
 * persisted workflows start from the same contract. Action-specific dynamic
 * defaults may extend/override that base here when they cannot be represented
 * as static schema values.
 */
internal fun defaultActionConfig(type: ActionType): Map<String, String> {
    val schemaDefaults = AutomationNodeCatalog.definitionFor(type)
        .configuration
        .fields
        .mapNotNull { field -> field.defaultValue?.let { field.key to it } }
        .toMap()

    val dynamicDefaults = when (type) {
        ActionType.SYSTEM_UPDATE_GOOGLE_PLAY_APPS -> mapOf(
            "includeGoogleApps" to "true",
            "includeUserApps" to "false",
            "wifiOnly" to "true",
            "chargingOnly" to "true",
            "maxConcurrentDownloads" to "1",
            "retryCount" to "0",
            "allowReboot" to "false",
            "requireSilentInstall" to "true",
            "dryRun" to "true"
        )
        else -> emptyMap()
    }
    return schemaDefaults + dynamicDefaults
}
