package com.nexaflow.domain.canonical

/**
 * T29 — Safe consolidation optimizer (plan §T29).
 *
 * Pure, deterministic AST-to-AST rewrite over the canonical graph. Two safe
 * rules only, both applied to *adjacent* siblings inside a sequence:
 *
 *  1. Redundant desired-state rewrites collapse to the LAST write (keep-last
 *     is exactly what sequential execution would do): same primitive, same
 *     target, equal typed payload. Expression payloads are never collapsed —
 *     their value is only provable at runtime (T06 fail-closed posture).
 *  2. Adjacent waits merge into one wait whose duration is the exact sum
 *     (overflow fails closed instead of wrapping).
 *
 * Safety contract, pinned by tests:
 *  - keep-last preserves the final desired state of every collapsed run, so
 *    device outcomes are bit-identical to executing the original plan;
 *  - node ids stay unique (the surviving node keeps its id); the result
 *    re-validates against [validateCanonicalAst];
 *  - idempotent: optimizing an optimized tree is a no-op with a zero report;
 *  - ordering with respect to anything between two writes is never touched —
 *    non-adjacent duplicates survive untouched.
 */
object CanonicalConsolidationOptimizer {

    /** Bounded rewrite passes; the tree is tiny relative to this bound. */
    const val MAX_PASSES: Int = 64

    /** Exact counters of applied rewrites (deterministic, order-stable). */
    data class OptimizationReport(
        val eliminatedRedefinitions: Int,
        val mergedWaits: Int,
        val passesUsed: Int,
    ) {
        val removedNodes: Int get() = eliminatedRedefinitions + mergedWaits
    }

    /** The rewritten tree plus what was done to it. */
    data class OptimizationResult(
        val root: CanonicalNode,
        val report: OptimizationReport,
    )

    fun optimize(root: CanonicalNode, maxPasses: Int = MAX_PASSES): OptimizationResult {
        require(maxPasses in 1..MAX_PASSES) {
            "maxPasses must be in 1..$MAX_PASSES"
        }
        var current = root
        var eliminated = 0
        var merged = 0
        var passes = 0
        while (passes < maxPasses) {
            val pass = optimizeNode(current)
            current = pass.node
            eliminated += pass.eliminatedRedefinitions
            merged += pass.mergedWaits
            passes++
            if (pass.eliminatedRedefinitions == 0 && pass.mergedWaits == 0) break
        }
        return OptimizationResult(
            root = current,
            report = OptimizationReport(
                eliminatedRedefinitions = eliminated,
                mergedWaits = merged,
                passesUsed = passes,
            ),
        )
    }

    private class Pass(
        val node: CanonicalNode,
        val eliminatedRedefinitions: Int,
        val mergedWaits: Int,
    )

    private fun optimizeNode(node: CanonicalNode): Pass = when (node) {
        is SequenceNode -> optimizeChildren(node.id, node.children)
        is BranchNode -> {
            val ifTrue = optimizeNode(node.ifTrue)
            val ifFalse = node.ifFalse?.let(::optimizeNode)
            val eliminated = ifTrue.eliminatedRedefinitions +
                (ifFalse?.eliminatedRedefinitions ?: 0)
            val merged = ifTrue.mergedWaits + (ifFalse?.mergedWaits ?: 0)
            if (eliminated == 0 && merged == 0) {
                Pass(node, 0, 0)
            } else {
                Pass(
                    BranchNode(
                        id = node.id,
                        condition = node.condition,
                        ifTrue = ifTrue.node,
                        ifFalse = ifFalse?.node,
                    ),
                    eliminated,
                    merged,
                )
            }
        }
        else -> Pass(node, 0, 0)
    }

    private fun optimizeChildren(id: CanonicalNodeId, children: List<CanonicalNode>): Pass {
        val childPasses = children.map(::optimizeNode)
        var list = childPasses.map { it.node }
        var eliminated = childPasses.sumOf { it.eliminatedRedefinitions }
        var merged = childPasses.sumOf { it.mergedWaits }

        var changed = true
        while (changed) {
            changed = false
            val out = mutableListOf<CanonicalNode>()
            var index = 0
            while (index < list.size) {
                val current = list[index]
                val next = list.getOrNull(index + 1)
                val collapse = collapsePair(current, next)
                if (collapse != null) {
                    out += collapse
                    when (collapse) {
                        is WaitNode -> merged++
                        else -> eliminated++
                    }
                    index += 2
                    changed = true
                } else {
                    out += current
                    index += 1
                }
            }
            list = out
        }
        return Pass(SequenceNode(id = id, children = list), eliminated, merged)
    }

    /**
     * The rewritten survivor when the adjacent pair may safely collapse, or
     * null when the pair must stay untouched.
     */
    private fun collapsePair(current: CanonicalNode, next: CanonicalNode?): CanonicalNode? {
        if (next == null) return null

        // Rule 2 — adjacent waits merge into one exact-sum wait.
        if (current is WaitNode && next is WaitNode) {
            val total = Math.addExact(
                current.duration.milliseconds,
                next.duration.milliseconds,
            )
            return WaitNode(id = next.id, duration = DurationValue(total))
        }

        // Rule 1 — adjacent identical desired-state rewrites keep the last.
        if (current is SetStateNode && next is SetStateNode &&
            current.target == next.target &&
            current.state !is ExpressionValue &&
            current.state == next.state
        ) {
            return next
        }
        if (current is SetValueNode && next is SetValueNode &&
            current.target == next.target &&
            current.value !is ExpressionValue &&
            current.value == next.value
        ) {
            return next
        }
        return null
    }
}
