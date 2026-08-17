package com.nexaflow.feature.builder

import androidx.compose.runtime.Immutable
import com.nexaflow.domain.models.TriggerType

/**
 * Editable draft of a trigger inside the builder. Every mutation produces a
 * fresh draft and freezes its configuration map, so a mutable map deserialized
 * from storage cannot invalidate the Compose stability contract in place.
 *
 * This deliberately uses an explicit [copy] rather than a `data class` copy:
 * Kotlin 2.5 rejects generated `copy()` functions that expose a non-public
 * primary constructor. The explicit copy retains the private construction
 * boundary and freezes its input on every edit.
 */
@Immutable
class TriggerDraft private constructor(
    val type: TriggerType,
    val config: Map<String, String>
) {
    companion object {
        operator fun invoke(
            type: TriggerType,
            config: Map<String, String> = emptyMap()
        ): TriggerDraft = TriggerDraft(type, config.toMap())
    }

    fun copy(
        type: TriggerType = this.type,
        config: Map<String, String> = this.config
    ): TriggerDraft = TriggerDraft(type, config.toMap())

    override fun equals(other: Any?): Boolean =
        other is TriggerDraft && type == other.type && config == other.config

    override fun hashCode(): Int = 31 * type.hashCode() + config.hashCode()

    override fun toString(): String = "TriggerDraft(type=$type, config=$config)"
}
