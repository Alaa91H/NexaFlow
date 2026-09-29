package com.nexaflow.domain.canonical

/**
 * Product-side T26 cutover boundary.
 *
 * Every side-effecting legacy action is translated to a canonical node and an
 * atomic command immediately before execution. The legacy action object may
 * still be handed to a compatibility provider during T39 retirement, but the
 * runtime admission/retry contract is owned by this canonical command.
 */
class CanonicalProductRuntime(
    private val adapter: LegacyCanonicalAdapter = CanonicalRuntimePipeline.defaultAdapter(),
    private val planner: CanonicalExecutionPlanner =
        CanonicalExecutionPlanner.of(productCommandSemantics()),
) {

    data class PreparedAction(
        val sourceType: String,
        val node: CanonicalActionNode,
        val command: AtomicCommand,
        val preservedConfig: List<LegacyConfigEntry>,
    )

    data class PreparedTrigger(
        val sourceType: String,
        val node: CanonicalConditionNode,
        val preservedConfig: List<LegacyConfigEntry>,
    )

    fun prepareAction(
        sourceType: String,
        config: Map<String, String>,
        instanceId: String,
    ): PreparedAction {
        val canonicalized = canonicalize(
            sourceType = sourceType,
            kind = LegacyNodeKind.ACTION,
            config = config,
        )
        val node = canonicalized.node.withProductRuntimeId(CanonicalNodeId(instanceId))
        require(node is CanonicalActionNode) {
            "Canonical action $sourceType did not produce an action node"
        }
        val plan = planner.plan(
            root = node,
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )
        val commands = plan.allCommands
        require(commands.size == 1) {
            "Canonical action $sourceType must plan to exactly one atomic command"
        }
        return PreparedAction(
            sourceType = sourceType,
            node = node,
            command = commands.single(),
            preservedConfig = canonicalized.preservedConfig,
        )
    }

    fun prepareTrigger(
        sourceType: String,
        config: Map<String, String>,
        instanceId: String,
    ): PreparedTrigger {
        val canonicalized = canonicalize(
            sourceType = sourceType,
            kind = LegacyNodeKind.TRIGGER,
            config = config,
        )
        val node = canonicalized.node.withProductRuntimeId(CanonicalNodeId(instanceId))
        require(node is CanonicalConditionNode) {
            "Canonical trigger $sourceType did not produce a condition node"
        }
        return PreparedTrigger(
            sourceType = sourceType,
            node = node,
            preservedConfig = canonicalized.preservedConfig,
        )
    }

    private fun canonicalize(
        sourceType: String,
        kind: LegacyNodeKind,
        config: Map<String, String>,
    ): LegacyAdapterOutcome.Canonicalized {
        val outcome = adapter.canonicalize(
            LegacyNodeInput(
                legacyType = sourceType,
                kind = kind,
                config = config.entries
                    .sortedBy { it.key }
                    .map { LegacyConfigEntry(it.key, it.value) },
            ),
        )
        return outcome as? LegacyAdapterOutcome.Canonicalized
            ?: throw IllegalArgumentException(
                "canonical runtime refused $kind/$sourceType: " +
                    (outcome as LegacyAdapterOutcome.Rejected).reason,
            )
    }

    companion object {
        /**
         * Explicit semantics for every reviewed operation currently emitted by
         * the 233-entry mapping table. Conservative classifications are used
         * for user-visible/external effects so retries can never be inferred.
         */
        fun productCommandSemantics(): List<CommandSemantics> = listOf(
            semantic("batch_write", CommandIdempotency.IDEMPOTENT, true),
            semantic("capture", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("clear", CommandIdempotency.IDEMPOTENT, false),
            semantic("clear_data", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("compose_or_send", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("connect", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("create", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("date_time", CommandIdempotency.IDEMPOTENT, false),
            semantic("dial", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("execute", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("force_stop", CommandIdempotency.IDEMPOTENT, true),
            semantic("forget", CommandIdempotency.IDEMPOTENT, false),
            semantic("generate_random", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("input", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("install", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("invoke", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("invoke_pattern", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("invoke_toggle", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("open", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("open_app_page", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("reboot", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("reject", CommandIdempotency.IDEMPOTENT, false),
            semantic("restart", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("scan", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("schedule", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("search_and_play", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("send", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("set_blocked", CommandIdempotency.IDEMPOTENT, true),
            semantic("set_configuration", CommandIdempotency.IDEMPOTENT, true),
            semantic("set_enabled", CommandIdempotency.IDEMPOTENT, true),
            semantic("set_state", CommandIdempotency.IDEMPOTENT, true),
            semantic("set_value", CommandIdempotency.IDEMPOTENT, true),
            semantic("show", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("shutdown", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("silence", CommandIdempotency.IDEMPOTENT, true),
            semantic("soft_restart", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("swipe", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("tap", CommandIdempotency.NON_IDEMPOTENT, false),
            semantic("transform", CommandIdempotency.IDEMPOTENT, true),
            semantic("uninstall", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("update_apps", CommandIdempotency.CONDITIONALLY_IDEMPOTENT, false),
            semantic("wait", CommandIdempotency.IDEMPOTENT, false),
            semantic("wake", CommandIdempotency.IDEMPOTENT, false),
        )

        private fun semantic(
            suffix: String,
            idempotency: CommandIdempotency,
            reversible: Boolean,
        ): CommandSemantics = CommandSemantics(
            operation = OperationId("core.operation.$suffix"),
            idempotency = idempotency,
            reversible = reversible,
        )
    }
}

private fun CanonicalNode.withProductRuntimeId(id: CanonicalNodeId): CanonicalNode = when (this) {
    is ObserveNode -> copy(id = id)
    is CompareNode -> copy(id = id)
    is SetStateNode -> copy(id = id)
    is SetValueNode -> copy(id = id)
    is InvokeNode -> copy(id = id)
    is OpenNode -> copy(id = id)
    is SendNode -> copy(id = id)
    is TransformNode -> copy(id = id)
    is InputNode -> copy(id = id)
    is WaitNode -> copy(id = id)
    is RestoreNode -> copy(id = id)
    is SequenceNode -> copy(id = id)
    is BranchNode -> copy(id = id)
    is ObservedConditionNode -> copy(id = id, observation = observation.copy(id = id))
    is ComparisonConditionNode -> copy(id = id, comparison = comparison.copy(id = id))
}
