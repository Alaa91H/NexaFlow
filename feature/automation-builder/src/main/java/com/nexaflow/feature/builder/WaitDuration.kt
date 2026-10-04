package com.nexaflow.feature.builder

import com.nexaflow.core.execution.canonical.CanonicalDelayDefinition
import com.nexaflow.domain.canonical.CanonicalWorkflowNode
import com.nexaflow.domain.canonical.NodeSchemaKind
import com.nexaflow.domain.canonical.WaitNode

internal object WaitDuration {
    const val MAX_SECONDS = 24L * 60L * 60L

    data class Parts(val hours: Int, val minutes: Int, val seconds: Int)

    fun fromSeconds(value: Long): Parts {
        val total = value.coerceIn(0L, MAX_SECONDS)
        return Parts(
            hours = (total / 3_600L).toInt(),
            minutes = ((total % 3_600L) / 60L).toInt(),
            seconds = (total % 60L).toInt()
        )
    }

    fun toSeconds(hours: Int, minutes: Int, seconds: Int): Long =
        (hours.coerceIn(0, 24) * 3_600L +
            minutes.coerceIn(0, 59) * 60L +
            seconds.coerceIn(0, 59).toLong())
            .coerceAtMost(MAX_SECONDS)

    fun configSeconds(config: Map<String, String>): Long =
        config["seconds"]?.toLongOrNull()?.coerceIn(0L, MAX_SECONDS) ?: 5L
}

internal data class MigratedBuilderActionSequence(
    val actions: List<ActionDraft>,
    val remainingCanonicalActions: List<CanonicalWorkflowNode>
)

internal object BuilderActionSequence {
    fun migrateCanonicalWaits(
        actions: List<ActionDraft>,
        canonicalActions: List<CanonicalWorkflowNode>,
        waitOption: ActionOption
    ): MigratedBuilderActionSequence {
        val waits = canonicalActions
            .filter { it.kind == NodeSchemaKind.ACTION && it.definitionId == CanonicalDelayDefinition.ID }
            .sortedBy { it.sequenceIndex }
            .mapNotNull { node ->
                val wait = node.node as? WaitNode ?: return@mapNotNull null
                ActionDraft(
                    id = node.node.id.value,
                    option = waitOption,
                    config = mapOf(
                        "seconds" to (wait.duration.milliseconds / 1_000L)
                            .coerceIn(0L, WaitDuration.MAX_SECONDS)
                            .toString()
                    )
                )
            }
        val remaining = canonicalActions.filterNot {
            it.kind == NodeSchemaKind.ACTION && it.definitionId == CanonicalDelayDefinition.ID
        }
        return MigratedBuilderActionSequence(actions + waits, remaining)
    }
}
