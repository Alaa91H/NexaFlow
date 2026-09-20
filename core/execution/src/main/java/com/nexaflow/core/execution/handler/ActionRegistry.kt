package com.nexaflow.core.execution.handler

import com.nexaflow.domain.models.ActionType

/**
 * Resolves an [ActionType] to its registered [ActionHandler].
 *
 * The registry is the single lookup point the engine uses for dispatch, so new
 * actions (including plugin-provided ones) plug in without engine changes.
 */
class ActionRegistry private constructor(
    private val handlers: Map<ActionType, ActionHandler>
) {

    fun handlerFor(type: ActionType): ActionHandler? = handlers[type]

    val supportedTypes: Set<ActionType> get() = handlers.keys

    fun allHandlers(): Set<ActionHandler> = handlers.values.toSet()

    companion object {
        /** The built-in handlers covering every [ActionType] today. */
        fun default(): ActionRegistry = from(
            listOf(
                DisplayActionsHandler(),
                SoundActionsHandler(),
                ConnectivityActionsHandler(),
                MediaActionsHandler(),
                NotificationActionsHandler(),
                AppActionsHandler(),
                SystemActionsHandler(),
                CallActionsHandler(),
                EvoActionHandler(),
                AdvancedActionsHandler(),
                HttpRequestHandler(),
                DataActionsHandler(),
                PluginFireHandler()
            )
        )

        /**
         * The built-in handlers with the semantic capability router attached
         * to migrated state operations. Unrouted actions fall through to the
         * exact legacy handler behavior, so construction is always safe.
         */
        fun withSemanticRouter(
            router: com.nexaflow.core.execution.capability.semantic.SemanticActionRouter
        ): ActionRegistry = from(
            listOf(
                DisplayActionsHandler().apply { semanticRouter = router },
                SoundActionsHandler().apply { semanticRouter = router },
                ConnectivityActionsHandler().apply { semanticRouter = router },
                MediaActionsHandler(),
                NotificationActionsHandler(),
                AppActionsHandler(),
                SystemActionsHandler(),
                CallActionsHandler(),
                EvoActionHandler(),
                AdvancedActionsHandler(),
                HttpRequestHandler(),
                DataActionsHandler(),
                PluginFireHandler()
            )
        )

        /** Builds a registry from a handler list, failing fast on conflicts. */
        fun from(handlers: List<ActionHandler>): ActionRegistry {
            val map = mutableMapOf<ActionType, ActionHandler>()
            handlers.forEach { handler ->
                handler.supportedTypes.forEach { type ->
                    require(type !in map) {
                        "ActionType $type registered twice (${map[type]} vs $handler)"
                    }
                    map[type] = handler
                }
            }
            return ActionRegistry(map)
        }
    }
}
