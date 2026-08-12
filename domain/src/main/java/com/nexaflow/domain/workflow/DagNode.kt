package com.nexaflow.domain.workflow

/**
 * A node in a compiled automation DAG (the workflow-engineering layer above the
 * tree-based [com.nexaflow.core.execution.workflow.Workflow] runtime).
 *
 * Pure data — the compiler ([AutomationDagCompiler]) produces it from an
 * [com.nexaflow.domain.models.Automation], and the executor consumes it. The
 * DAG is verified acyclic before execution via Kahn's algorithm.
 *
 * @param id unique node id within the graph ("trigger-0", "action-1", ...).
 * @param type the node's role in the graph.
 * @param input type-specific parameters, mirroring the schema's config maps
 *   (e.g. `type` + trigger/action/constraint config) so no information is lost
 *   when compiling.
 * @param outputPath JSONPath where this node's output is stored on the payload
 *   context (Phase-2 wiring; null until the context is threaded through).
 * @param retry per-node retry policy (exponential backoff + jitter).
 * @param compensate reverse node executed when this node fails (Saga).
 */
data class DagNode(
    val id: String,
    val type: DagNodeType,
    val input: Map<String, String> = emptyMap(),
    val outputPath: String? = null,
    val retry: RetryPolicy = RetryPolicy(),
    val compensate: DagNode? = null,
)

/** The role of a node in a compiled automation DAG. */
enum class DagNodeType {
    /** Entry node: a trigger of the automation ("when"). */
    TRIGGER,

    /** A gate check that must pass before the actions run (AND-ed). */
    CONDITION,

    /** An action of the automation ("then"). */
    ACTION,

    /** An exit action run when the task's condition stops being true. */
    EXIT,

    /** A user-supplied custom code step (sandboxed in later phases). */
    CUSTOM_CODE,
}

/**
 * A directed acyclic graph of [DagNode]s.
 *
 * @param nodes every node of the graph.
 * @param edges adjacency: nodeId → successor nodeIds (the "A must run before B"
 *   relation). Every node must appear as a key, possibly with an empty set.
 * @param entryNodeIds nodes with no incoming edges — where execution starts.
 */
data class DagGraph(
    val nodes: List<DagNode>,
    val edges: Map<String, Set<String>>,
    val entryNodeIds: List<String>,
) {
    /** The topological execution order, or null when the graph contains a cycle. */
    val topologicalOrder: List<String>? get() = kahnTopologicalSort(edges)

    /** True when the graph contains at least one cycle (invalid workflow). */
    val isCyclic: Boolean get() = topologicalOrder == null
}

/**
 * Per-node retry policy (exponential backoff with jitter).
 *
 * Delay for attempt n (1-based): `min(capMs, baseDelayMs * 2^(n-1))` plus
 * uniform jitter in `±(delay * jitter)`. Transient failures (5xx, timeouts,
 * 429) are retried up to [maxAttempts]; permanent failures (4xx validation)
 * fail immediately to the dead-letter queue instead.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1000,
    val capMs: Long = 60_000,
    val jitter: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(baseDelayMs >= 0) { "baseDelayMs must be >= 0" }
        require(capMs >= 0) { "capMs must be >= 0" }
        require(jitter in 0.0..1.0) { "jitter must be in 0.0..1.0" }
    }
}
