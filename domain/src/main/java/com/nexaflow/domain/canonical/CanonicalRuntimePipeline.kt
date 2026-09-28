package com.nexaflow.domain.canonical

/**
 * T26 — Canonical Runtime Cutover (plan §26.1, §T26).
 *
 * The single runtime path after cutover:
 *
 * ```
 * Legacy input
 *   → Adapter (T14 framework + T15 table + T17–T25 family overrides)
 *   → Canonical AST
 *   → Validation (T09 pipeline, schema explicit)
 *   → Execution Plan (T10 planner)
 * ```
 *
 * Closure condition: no legacy type reaches canonical execution handlers —
 * [planLegacy] refuses legacy inputs whose mapping was Rejected, whose
 * verdict is invalid, or whose family contract is missing. The schema is an
 * explicit parameter: the caller (family host) supplies the reviewed schema,
 * so no hidden name-based resolution exists.
 *
 * Preserved legacy config rides through [CanonicalizedPlan] untouched — it
 * is lossless but is never part of the validated/executable surface.
 */
class CanonicalRuntimePipeline(
    /** The full adapter table (T15 skeleton + T17–T25 family overrides). */
    val adapter: LegacyCanonicalAdapter = CanonicalRuntimePipeline.defaultAdapter(),
    /** The planner semantics table (default + family declarations). */
    private val planner: CanonicalExecutionPlanner = CanonicalRuntimePipeline.defaultPlanner(),
) {

    /** Canonicalizes one legacy node; deterministic and lossless. */
    fun canonicalize(input: LegacyNodeInput): LegacyAdapterOutcome =
        adapter.canonicalize(input)

    /**
     * Full path: legacy → canonical → validated → planned. Fails closed on
     * any rejection or invalid verdict; the returned plan is executable.
     */
    fun planLegacy(
        runId: String,
        legacyType: String,
        kind: LegacyNodeKind,
        schema: NodeSchema,
        config: List<LegacyConfigEntry>,
        semantics: NodeSelectionSemantics,
        capabilityRequirement: com.nexaflow.domain.capability.CapabilityRequirement,
        selectedTargetCount: Int = 1,
        cardinality: OperationCardinality? = null,
        executionPolicy: PlanExecutionPolicy = PlanExecutionPolicy.SEQUENTIAL,
        failurePolicy: FailurePolicy = FailurePolicy.FAIL_FAST,
    ): CanonicalizedPlan {
        val outcome = adapter.canonicalize(LegacyNodeInput(legacyType, kind, config))
        val canonicalized = when (outcome) {
            is LegacyAdapterOutcome.Canonicalized -> outcome
            is LegacyAdapterOutcome.Rejected -> throw IllegalArgumentException(
                "cutover refused: ${outcome.message}",
            )
        }

        val contract = CanonicalWorkflowContract(
            schema = schema,
            semantics = semantics,
            capabilityRequirement = capabilityRequirement,
            selectedTargetCount = selectedTargetCount,
            cardinality = cardinality,
        )
        val ast = CanonicalWorkflowAst(root = canonicalized.node)

        // The validated surface is the schema-typed view of the SAME config
        // the adapter consumed: consumed keys become typed NodeFieldValues,
        // and schema defaults fill the rest. Unconsumed legacy keys stay out
        // of the validated surface (they ride in preservedConfig only).
        val suppliedValues = nodeArguments(canonicalized.node).entries.mapNotNull { argument ->
            val type = schema.field(argument.id)?.type ?: return@mapNotNull null
            if (argument.value.kind != expectedValueKind(type)) return@mapNotNull null
            NodeFieldValue(argument.id, argument.value)
        }
        val suppliedFields = suppliedValues.mapTo(mutableSetOf()) { it.field }
        val values = suppliedValues + defaultsOf(schema).filter { it.field !in suppliedFields }

        val verdict = validate(ast, contract, values)
        require(verdict.isValid) {
            "cutover refused: ${verdict.findings.first().message}"
        }

        val plan = planner.plan(ast.root, executionPolicy, failurePolicy)
        return CanonicalizedPlan(
            runId = runId,
            legacyType = legacyType,
            preservedConfig = canonicalized.preservedConfig,
            verdict = verdict,
            plan = plan,
        )
    }

    /** The result of a successful cutover path. */
    data class CanonicalizedPlan(
        val runId: String,
        val legacyType: String,
        val preservedConfig: List<LegacyConfigEntry>,
        val verdict: ValidationVerdict,
        val plan: ExecutionPlan,
    ) {
        /** Preserved config is carried, never executed. */
        val preservedKeyCount: Int
            get() = preservedConfig.size
    }

    companion object {

        /**
         * Typed argument bag of a node regardless of its concrete primitive:
         * actions and observations carry arguments; structural nodes do not.
         */
        private fun nodeArguments(node: CanonicalNode): CanonicalArguments = when (node) {
            is CanonicalActionNode -> node.arguments
            is ObserveNode -> node.arguments
            else -> CanonicalArguments.EMPTY
        }

        /** Full adapter: T15 table + every implemented family override. */
        fun defaultAdapter(): LegacyCanonicalAdapter {
            val table = LegacyMappingTable.all()
            val overridden = buildMap {
                (PilotOpenFamily.ruleOverrides(table) +
                    FamilyPhase18MediaNavigation.ruleOverrides(table) +
                    FamilyPhase19Connectivity.ruleOverrides(table) +
                    FamilyPhase20DisplaySound.ruleOverrides(table) +
                    FamilyPhase21Applications.ruleOverrides(table) +
                    FamilyPhase22Communication.ruleOverrides(table) +
                    FamilyPhase23PowerSensors.ruleOverrides(table) +
                    FamilyPhase24TimeLocation.ruleOverrides(table) +
                    FamilyPhase25AdvancedExternal.ruleOverrides(table))
                    .forEach { put(it.legacyType, it) }
            }
            return LegacyCanonicalAdapter(table.filter { it.legacyType !in overridden } + overridden.values)
        }

        /**
         * The cutover planner: the default table plus every family-declared
         * semantics. Operation-keyed; a family may only refine a declaration,
         * never invent a conflicting one.
         */
        fun defaultPlanner(): CanonicalExecutionPlanner {
            val declarations = CanonicalExecutionPlanner.defaultSemantics() +
                FamilyPhase21Applications.commandSemantics() +
                FamilyPhase22Communication.commandSemantics() +
                FamilyPhase25AdvancedExternal.commandSemantics()
            val byOperation = declarations.associateBy { it.operation }
            val conflicting = declarations.groupBy { it.operation }
                .filterValues { it.size > 1 }
                .filterValues { group -> group.distinct().size > 1 }
            require(conflicting.isEmpty()) {
                "conflicting command semantics: " + conflicting.keys.joinToString { it.value }
            }
            return CanonicalExecutionPlanner.of(byOperation.values.toList())
        }
    }
}
