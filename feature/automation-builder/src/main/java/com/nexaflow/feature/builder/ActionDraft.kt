package com.nexaflow.feature.builder

import androidx.compose.runtime.Immutable
import com.nexaflow.domain.models.Action
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
    val config: Map<String, String> = emptyMap(),
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
