package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.Trigger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Lossless bridge between the legacy [Automation] storage format and the
 * versioned [WorkflowDocumentV1] contract (roadmap P0.1 acceptance: every
 * legacy task reads unchanged; edit/save may upgrade through an explicit,
 * idempotent path; export always carries the schema version).
 *
 * Round-trip guarantee pinned by tests: Automation → Document → Automation
 * preserves every field that the document models, and
 * Document → Automation → Document is exact for documents produced by
 * [toDocument]. The legacy reader remains the storage truth for old tasks.
 */
object WorkflowDocumentMappers {

    val json: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    // ---------------------------------------------------------------------
    // Legacy Automation → WorkflowDocumentV1
    // ---------------------------------------------------------------------

    /**
     * Maps a legacy task into the vNext document. `Map<String,String>` configs
     * are transported verbatim (no interpretation, no defaults invented), so
     * no legacy behavior can change.
     */
    fun Automation.toDocument(revision: Long = 1L): WorkflowDocumentV1 {
        val runNodes = actions.mapIndexed { index, action ->
            PersistedWorkflowNodeV1.Action(
                nodeId = stableNodeId("run", id, index, action.type.name),
                action = action.toPersisted(nodeId = stableNodeId("action", id, index, action.type.name)),
            )
        }
        val root: PersistedWorkflowNodeV1 = when {
            runNodes.isEmpty() -> PersistedWorkflowNodeV1.Sequence(
                nodeId = stableNodeId("run", id, -1, "empty"),
                children = emptyList(),
            )
            else -> PersistedWorkflowNodeV1.Sequence(
                nodeId = stableNodeId("run", id, -1, "sequence"),
                children = runNodes,
                rollbackOnFailure = false,
            )
        }
        val exitActions = exitActions.mapIndexed { index, action ->
            PersistedActionV1(
                type = action.type.name,
                config = action.config,
                endBehavior = null,
                nodeId = stableNodeId("exit", id, index, action.type.name),
            )
        }
        return WorkflowDocumentV1(
            id = id,
            revision = revision,
            metadata = WorkflowMetadataV1(
                name = name,
                description = description,
                icon = icon,
                category = category,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            automationSettings = AutomationSettingsV1(
                iconColor = iconColor,
                backgroundColor = backgroundColor,
                priority = priority,
                enabled = enabled,
                showToastOnToggle = showToastOnToggle,
                triggerMatch = triggerMatch.name,
                cooldownSeconds = cooldownSeconds,
                workflowVersion = workflowVersion,
                maintenanceProfile = maintenanceProfile,
            ),
            triggers = triggers.map { it.toDefinition() },
            constraints = constraints.map { it.toDefinition() },
            root = root,
            exitPolicy = if (exitActions.isEmpty() && !revertOnExit) {
                null
            } else {
                ExitPolicyV1(actions = exitActions, revertOnExit = revertOnExit)
            },
            riskDescriptor = RiskDescriptorV1(level = riskLevel().name),
        )
    }

    private fun Trigger.toDefinition() = TriggerDefinitionV1(type = type.name, config = config)
    private fun Constraint.toDefinition() = ConstraintDefinitionV1(type = type.name, config = config)

    private fun Action.toPersisted(nodeId: String) = PersistedActionV1(
        type = type.name,
        config = config,
        endBehavior = endBehavior?.let { behavior ->
            PersistedEndBehaviorV1(mode = behavior.mode.name, config = behavior.config)
        },
        nodeId = nodeId,
    )

    /**
     * Document-level risk class derived from the action types the task carries
     * (capability-based classification; privilege escalation happens at run
     * time through the router, not statically here).
     */
    private fun Automation.riskLevel(): com.nexaflow.domain.risk.RiskLevel =
        when {
            actions.any { it.type.name.contains("SHELL") || it.type.name.contains("ROOT") } ->
                com.nexaflow.domain.risk.RiskLevel.HIGH
            actions.any { it.type.name.contains("DATA") || it.type.name.contains("DELETE") } ->
                com.nexaflow.domain.risk.RiskLevel.MEDIUM
            else -> com.nexaflow.domain.risk.RiskLevel.LOW
        }

    /** Deterministic, position-independent-enough node identity. */
    internal fun stableNodeId(kind: String, automationId: String, index: Int, type: String): String =
        "$kind:$automationId:$index:$type"

    // ---------------------------------------------------------------------
    // WorkflowDocumentV1 → legacy Automation
    // ---------------------------------------------------------------------

    /**
     * Down-maps a vNext document back to the legacy storage model. Used by the
     * editor save path so storage stays legacy-compatible while authoring moves
     * to the document. Throws [IllegalArgumentException] on documents that
     * carry graph shapes the legacy flat model cannot express — callers must
     * route those to the graph runtime, never flatten silently.
     */
    fun WorkflowDocumentV1.toAutomation(): Automation {
        val runActions = requireLinearRun(root)
        val exitPolicy = exitPolicy
        val settings = automationSettings
        return Automation(
            id = id,
            name = metadata.name,
            description = metadata.description,
            icon = metadata.icon,
            iconColor = settings.iconColor,
            backgroundColor = settings.backgroundColor,
            category = metadata.category.ifBlank { "general" },
            priority = settings.priority,
            enabled = settings.enabled,
            showToastOnToggle = settings.showToastOnToggle,
            triggers = triggers.map { it.toTrigger() },
            actions = runActions,
            constraints = constraints.map { it.toConstraint() },
            triggerMatch = com.nexaflow.domain.models.TriggerMatchMode.valueOf(settings.triggerMatch),
            exitActions = exitPolicy?.actions?.map { it.toAction() } ?: emptyList(),
            revertOnExit = exitPolicy?.revertOnExit ?: false,
            cooldownSeconds = settings.cooldownSeconds,
            createdAt = metadata.createdAt.takeIf { it > 0 } ?: 0L,
            updatedAt = metadata.updatedAt.takeIf { it > 0 } ?: 0L,
            workflowVersion = settings.workflowVersion,
            maintenanceProfile = settings.maintenanceProfile,
        )
    }

    /**
     * Legacy storage is flat: the run root must be a pure sequence of action
     * nodes. Complex graphs are supported by the runtime but not by the legacy
     * row, so this mapper refuses them loudly instead of dropping nodes.
     */
    private fun requireLinearRun(root: PersistedWorkflowNodeV1): List<Action> {
        val seq = root as? PersistedWorkflowNodeV1.Sequence
            ?: throw IllegalArgumentException(
                "Legacy Automation storage is flat: run root must be a sequence, got ${root::class.simpleName}"
            )
        return seq.children.map { node ->
            val action = node as? PersistedWorkflowNodeV1.Action
                ?: throw IllegalArgumentException(
                    "Legacy Automation storage is flat: nested ${node::class.simpleName} " +
                        "(${node.nodeId}) requires the graph runtime"
                )
            action.action.toAction()
        }
    }

    private fun TriggerDefinitionV1.toTrigger() = Trigger(
        type = com.nexaflow.domain.models.TriggerType.valueOf(type),
        config = config,
    )

    private fun ConstraintDefinitionV1.toConstraint() = Constraint(
        type = com.nexaflow.domain.models.ConstraintType.valueOf(type),
        config = config,
    )

    /**
     * Exposed for the execution-boundary compiler only — maps a persisted
     * action onto the legacy runtime model it executes through.
     */
    fun PersistedActionV1.toRuntimeAction(): Action = toAction()

    private fun PersistedActionV1.toAction(): Action {
        val endBehavior = endBehavior?.let { persisted ->
            com.nexaflow.domain.models.EndBehavior(
                mode = com.nexaflow.domain.models.EndMode.valueOf(persisted.mode),
                config = persisted.config,
            )
        }
        return Action(
            type = com.nexaflow.domain.models.ActionType.valueOf(type),
            config = config,
            endBehavior = endBehavior,
        )
    }

    // ---------------------------------------------------------------------
    // Structural validation
    // ---------------------------------------------------------------------

    /**
     * Bounded structural validator. Rejects empty graphs, duplicate/blank node
     * ids, runaway loops and impossible bounds before anything is persisted or
     * executed. Errors are stable machine-readable codes for the preflight UI.
     */
    sealed interface ValidationIssue {
        data class BlankNodeId(val parent: String) : ValidationIssue
        data class DuplicateNodeId(val nodeId: String) : ValidationIssue
        data class EmptyGraph(val where: String) : ValidationIssue
        data class UnboundedLoop(val nodeId: String) : ValidationIssue
        data class InvalidBound(val nodeId: String, val detail: String) : ValidationIssue
    }

    fun validateStructure(document: WorkflowDocumentV1): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val seen = HashSet<String>()
        visit(document.root, seen, issues)
        document.exitPolicy?.actions?.forEach { persisted ->
            when {
                persisted.nodeId.isBlank() ->
                    issues += ValidationIssue.BlankNodeId("exitPolicy")
                !seen.add(persisted.nodeId) ->
                    issues += ValidationIssue.DuplicateNodeId(persisted.nodeId)
            }
        }
        return issues
    }

    private fun visit(
        node: PersistedWorkflowNodeV1,
        seen: MutableSet<String>,
        issues: MutableList<ValidationIssue>,
    ) {
        if (node.nodeId.isBlank()) {
            issues += ValidationIssue.BlankNodeId(parent = "<root>")
            return
        }
        if (!seen.add(node.nodeId)) {
            issues += ValidationIssue.DuplicateNodeId(node.nodeId)
            return
        }
        when (node) {
            is PersistedWorkflowNodeV1.Sequence -> {
                if (node.children.isEmpty()) issues += ValidationIssue.EmptyGraph(node.nodeId)
                node.children.forEach { visit(it, seen, issues) }
            }
            is PersistedWorkflowNodeV1.Parallel -> {
                if (node.children.isEmpty()) issues += ValidationIssue.EmptyGraph(node.nodeId)
                node.children.forEach { visit(it, seen, issues) }
            }
            is PersistedWorkflowNodeV1.Race -> {
                if (node.children.isEmpty()) issues += ValidationIssue.EmptyGraph(node.nodeId)
                node.children.forEach { visit(it, seen, issues) }
            }
            is PersistedWorkflowNodeV1.Loop -> {
                if (node.iterations !in 0..WorkflowDocumentMappers.Bounds.MAX_LOOP_ITERATIONS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "iterations ${node.iterations} outside 0..${WorkflowDocumentMappers.Bounds.MAX_LOOP_ITERATIONS}",
                    )
                }
                visit(node.body, seen, issues)
            }
            is PersistedWorkflowNodeV1.While -> {
                if (node.maxIterations !in 0..WorkflowDocumentMappers.Bounds.MAX_LOOP_ITERATIONS) {
                    issues += ValidationIssue.UnboundedLoop(node.nodeId)
                }
                visit(node.body, seen, issues)
            }
            is PersistedWorkflowNodeV1.Retry -> {
                if (node.maxAttempts !in 1..WorkflowDocumentMappers.Bounds.MAX_RETRY_ATTEMPTS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "maxAttempts ${node.maxAttempts} outside 1..${WorkflowDocumentMappers.Bounds.MAX_RETRY_ATTEMPTS}",
                    )
                }
                if (node.backoffMs !in 0..WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "backoffMs ${node.backoffMs} outside 0..${WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS}",
                    )
                }
                visit(node.body, seen, issues)
            }
            is PersistedWorkflowNodeV1.Timeout -> {
                if (node.timeoutMs !in 1..WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "timeoutMs ${node.timeoutMs} outside 1..${WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS}",
                    )
                }
                visit(node.body, seen, issues)
            }
            is PersistedWorkflowNodeV1.WaitUntil -> {
                if (node.timeoutMs !in 1..WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "timeoutMs ${node.timeoutMs} outside 1..${WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS}",
                    )
                }
                if (node.pollIntervalMs !in 10..node.timeoutMs) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "pollIntervalMs ${node.pollIntervalMs} outside 10..timeoutMs",
                    )
                }
            }
            is PersistedWorkflowNodeV1.Branch -> {
                visit(node.whenTrue, seen, issues)
                node.whenFalse?.let { visit(it, seen, issues) }
            }
            is PersistedWorkflowNodeV1.Try -> {
                visit(node.body, seen, issues)
                node.catchNode?.let { visit(it, seen, issues) }
                node.finallyNode?.let { visit(it, seen, issues) }
            }
            is PersistedWorkflowNodeV1.Delay -> {
                if (node.delayMs !in 0..WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS) {
                    issues += ValidationIssue.InvalidBound(
                        node.nodeId,
                        "delayMs ${node.delayMs} outside 0..${WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS}",
                    )
                }
            }
            is PersistedWorkflowNodeV1.Action -> Unit
        }
    }

    object Bounds {
        /** Mirrors the runtime guards so persisted and executed bounds agree. */
        const val MAX_LOOP_ITERATIONS = 1_000
        const val MAX_RETRY_ATTEMPTS = 100
        const val MAX_TIMEOUT_MS = 300_000L
    }

    /** Decodes a persisted document, failing safely on unknown versions. */
    fun decode(encoded: String): WorkflowDocumentV1 = json.decodeFromString(encoded)

    /** Encodes a document with its schema version embedded. */
    fun encode(document: WorkflowDocumentV1): String = json.encodeToString(
        WorkflowDocumentV1.serializer(),
        document,
    )
}
