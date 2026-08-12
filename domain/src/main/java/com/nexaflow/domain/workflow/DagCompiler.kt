package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Automation

/**
 * Kahn's algorithm over an adjacency map (nodeId → successor nodeIds).
 *
 * Returns a topological ordering (a node always appears before every node it
 * points to) when the graph is acyclic, or null when it contains a cycle —
 * the caller must reject cyclic workflows before execution.
 *
 * ```
 * edges {A→[B], B→[C]}        → [A, B, C]        (valid)
 * edges {A→[B], B→[C], C→[A]} → null             (cycle → reject)
 * ```
 */
fun kahnTopologicalSort(edges: Map<String, Set<String>>): List<String>? {
    if (edges.isEmpty()) return emptyList()
    // in-degree: how many predecessors point at each node.
    val inDegree = HashMap<String, Int>(edges.size)
    edges.keys.forEach { inDegree[it] = 0 }
    edges.forEach { (_, successors) ->
        successors.forEach { successor ->
            if (inDegree.containsKey(successor)) {
                inDegree[successor] = (inDegree[successor] ?: 0) + 1
            }
        }
    }
    // Frontier: nodes with nothing pointing at them.
    val queue = ArrayDeque<String>().apply {
        inDegree.filterValues { it == 0 }.keys.forEach { add(it) }
    }
    val order = ArrayList<String>(inDegree.size)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        order.add(node)
        edges[node].orEmpty().forEach { successor ->
            // Skip successors that are not graph nodes (defensive: an edge to
            // an unknown id must not corrupt the degree bookkeeping).
            if (inDegree.containsKey(successor)) {
                val next = inDegree[successor]!! - 1
                inDegree[successor] = next
                if (next == 0) queue.addLast(successor)
            }
        }
    }
    return if (order.size == inDegree.size) order else null
}

/** Result of compiling an [Automation] into a verified DAG. */
sealed interface DagCompilationResult {
    /** The graph compiled successfully and passed acyclicity verification. */
    data class Success(
        val graph: DagGraph,
        /** The verified topological execution order (never null on success). */
        val executionOrder: List<String>,
    ) : DagCompilationResult

    /** The automation cannot be compiled into a valid DAG. */
    data class Failure(val reason: String) : DagCompilationResult
}

/**
 * Compiles an [Automation] (trigger → constraints → actions → exit) into a
 * verified [DagGraph].
 *
 * Graph shape:
 * - one [DagNodeType.TRIGGER] node per automation trigger (all are entries),
 * - one [DagNodeType.CONDITION] node per constraint, chained after the
 *   triggers (constraints are AND-ed, so they form a chain),
 * - one [DagNodeType.ACTION] node per action, chained after the constraints,
 * - one [DagNodeType.EXIT] node per exit action, chained after the actions.
 *
 * Every compiled graph is verified acyclic via [kahnTopologicalSort] before it
 * is returned — a cyclic automation is rejected, never executed.
 */
object AutomationDagCompiler {

    fun compile(automation: Automation): DagCompilationResult {
        if (automation.triggers.isEmpty()) {
            return DagCompilationResult.Failure("Automation has no triggers")
        }

        val nodes = mutableListOf<DagNode>()
        val edges = HashMap<String, MutableSet<String>>()
        val entryNodeIds = mutableListOf<String>()

        // Entry nodes: one per trigger. All triggers are entries — any of them
        // can fire the workflow, so none depends on the others.
        val triggerIds = automation.triggers.mapIndexed { index, trigger ->
            val id = "trigger-$index"
            nodes += DagNode(
                id = id,
                type = DagNodeType.TRIGGER,
                input = mapOf("type" to trigger.type.name) + trigger.config,
            )
            edges[id] = mutableSetOf()
            entryNodeIds += id
            id
        }

        // Constraint chain: AND-ed gates, so they run sequentially.
        val constraintIds = automation.constraints.mapIndexed { index, constraint ->
            val id = "constraint-$index"
            nodes += DagNode(
                id = id,
                type = DagNodeType.CONDITION,
                input = mapOf("type" to constraint.type.name) + constraint.config,
            )
            edges[id] = mutableSetOf()
            id
        }

        // Action chain: executed in order.
        val actionIds = automation.actions.mapIndexed { index, action ->
            val id = "action-$index"
            nodes += DagNode(
                id = id,
                type = DagNodeType.ACTION,
                input = mapOf("type" to action.type.name) + action.config,
            )
            edges[id] = mutableSetOf()
            id
        }

        // Exit chain: run when the condition stops being true.
        val exitIds = automation.exitActions.mapIndexed { index, action ->
            val id = "exit-$index"
            nodes += DagNode(
                id = id,
                type = DagNodeType.EXIT,
                input = mapOf("type" to action.type.name) + action.config,
            )
            edges[id] = mutableSetOf()
            id
        }

        // Wire the chain: triggers → constraints → actions → exits. Each node
        // points at the first node of the next segment (or its own successor
        // within a segment), forming a single forward path per segment.
        val firstConstraint = constraintIds.firstOrNull()
        val firstAction = actionIds.firstOrNull()
        val firstExit = exitIds.firstOrNull()

        triggerIds.forEach { trigger ->
            // Triggers fan into the constraint chain head (or action head, or
            // exit head when neither constraints nor actions exist).
            val target = firstConstraint ?: firstAction ?: firstExit
            target?.let { edges[trigger]?.add(it) }
        }
        if (constraintIds.isNotEmpty()) {
            constraintIds.zipWithNext { from, to -> edges[from]?.add(to) }
            constraintIds.last().let { last ->
                val target = firstAction ?: firstExit
                target?.let { edges[last]?.add(it) }
            }
        }
        if (actionIds.isNotEmpty()) {
            actionIds.zipWithNext { from, to -> edges[from]?.add(to) }
            actionIds.last().let { last ->
                firstExit?.let { edges[last]?.add(it) }
            }
        }
        if (exitIds.isNotEmpty()) {
            exitIds.zipWithNext { from, to -> edges[from]?.add(to) }
        }

        val graph = DagGraph(
            nodes = nodes,
            edges = edges,
            entryNodeIds = entryNodeIds,
        )
        val order = graph.topologicalOrder
        return if (order != null) {
            DagCompilationResult.Success(graph, order)
        } else {
            DagCompilationResult.Failure("Compiled graph contains a cycle")
        }
    }
}
