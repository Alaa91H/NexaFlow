package com.nexaflow.feature.builder

import androidx.compose.runtime.Immutable
import com.nexaflow.domain.models.TriggerType

/**
 * Editable draft of a trigger inside the builder. Immutable by contract: every
 * edit produces a new instance via `copy()`, and the config map is frozen on
 * construction (defending against mutable Gson maps from a loaded automation)
 * so it can never be mutated in place. This lets the Compose compiler treat
 * the draft as stable and skip `TriggerEditorCard` when it is unchanged.
 *
 * Note: `copy()` bypasses the factory's freeze, so call sites must keep
 * passing freshly built maps (the editor always does via `config + pair`),
 * never an aliased mutable map.
 */
@Immutable
data class TriggerDraft private constructor(
    val type: TriggerType,
    val config: Map<String, String>
) {
    companion object {
        /**
         * Freezes the config on construction so the @Immutable contract is
         * enforceable, not just documented: a mutable Gson LinkedTreeMap from
         * a loaded automation can never be mutated in place afterwards.
         */
        operator fun invoke(
            type: TriggerType,
            config: Map<String, String> = emptyMap()
        ): TriggerDraft = TriggerDraft(type, config.toMap())
    }
}
