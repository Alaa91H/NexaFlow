package com.nexaflow.core.execution

import java.util.UUID

/**
 * Per-run payload context threaded through [ExecutionEngine.runAutomation].
 *
 * Implements the Phase-2 contract: **JSON Merge Patch delta** (RFC 7386-style)
 * with a hard memory budget, plus JSONPath read/write access:
 *
 * ```
 * node A: output → context.put("$.weather.temp", 21.4)
 * node B: input  ← context.get("$.weather")          // {temp: 21.4, ...}
 * storage: only the changed paths are kept (no full-context copy per node)
 * budget:  256KB per run — a write that would exceed it is rejected BEFORE
 *          any mutation, so a failed put never leaves partial state
 * access:  read-only inside a node; writes only via explicit paths
 * ```
 *
 * Supported JSONPath subset: `$` (root), `$.a.b` (map keys), `$.a[0].b`
 * (list indexes), `$[0]` (root list). Keys match `[A-Za-z_][A-Za-z0-9_-]*`.
 *
 * Pure JVM (no Android imports) so every behavior is atomically testable.
 *
 * @param runId unique id per execution — surfaced in history and OTel in later phases.
 * @param automationId the task being run.
 * @param triggeredAt wall-clock time the run started (epoch millis).
 */
class WorkflowRunContext(
    val runId: String,
    val automationId: String,
    val triggeredAt: Long,
) {

    /**
     * The delta document. Starts empty and only ever contains what nodes wrote
     * — merging into it IS the patch. Functional updates keep reads consistent
     * and let the budget check happen before any mutation.
     */
    private var root: Any? = emptyMap<String, Any?>()

    companion object {
        /** Hard cap on the delta's estimated serialized size. */
        const val MAX_BYTES: Long = 256 * 1024

        /** Creates a fresh context with a generated run id. */
        fun create(automationId: String, triggeredAt: Long): WorkflowRunContext =
            WorkflowRunContext(UUID.randomUUID().toString(), automationId, triggeredAt)
    }

    /** Current estimated byte size of the delta (diagnostics / tests). */
    val size: Long get() = estimate(root)

    /**
     * Writes [value] at [path], merging into the delta. An existing subtree at
     * that path is replaced (JSON Merge Patch semantics); a scalar at an
     * intermediate key is replaced by a map rather than failing. Rejects the
     * write — before touching state — when the delta would exceed [MAX_BYTES].
     *
     * @throws IllegalArgumentException on a malformed path or an out-of-range list index.
     * @throws IllegalStateException when the write would exceed the 256KB budget.
     */
    fun put(path: String, value: Any?) {
        val segments = parsePath(path)
        if (segments.isEmpty()) {
            // Root write: replace the whole document.
            val delta = estimate(value)
            check(delta <= MAX_BYTES) { "PayloadContext exceeded ${MAX_BYTES / 1024}KB limit" }
            root = value
            return
        }
        val merged = mergeInto(root, segments, 0, value)
        check(estimate(merged) <= MAX_BYTES) { "PayloadContext exceeded ${MAX_BYTES / 1024}KB limit" }
        root = merged
    }

    /**
     * Reads the value at [path]. A stored `null` value is returned as null, the
     * same as a missing path — use [paths] to distinguish when it matters.
     * Returns null for any unknown segment.
     */
    fun get(path: String): Any? {
        var node: Any? = root
        for (seg in parsePath(path)) {
            node = readChild(node, seg)
            if (node === MISSING) return null
        }
        return node
    }

    /** Leaf JSONPaths currently stored, sorted for deterministic output. */
    fun paths(): List<String> {
        val result = mutableListOf<String>()
        collectPaths(root, "\$", result)
        return result.sorted()
    }

    /** Deep copy of the whole delta document — safe for external consumers. */
    fun snapshot(): Any? = deepCopy(root)

    // --- path parsing -------------------------------------------------------

    private sealed interface Segment {
        data class Key(val name: String) : Segment
        data class Index(val index: Int) : Segment
    }

    private val TOKEN = Regex("""([A-Za-z_][A-Za-z0-9_-]*)|\[(\d+)]""")

    private fun parsePath(path: String): List<Segment> {
        require(path.startsWith("$")) { "JSONPath must start with '\$': '$path'" }
        if (path == "$") return emptyList()
        val body = path.removePrefix("$")
        val segments = mutableListOf<Segment>()
        var pos = 0
        for (match in TOKEN.findAll(body)) {
            // The gap between tokens must be a single dot separator (or empty
            // for adjacent brackets like $[0][1]); anything else is invalid.
            val gap = body.substring(pos, match.range.first)
            require(gap == "" || gap == ".") { "Invalid JSONPath: '$path'" }
            segments += when {
                match.groups[1] != null -> Segment.Key(match.groups[1]!!.value)
                else -> Segment.Index(match.groups[2]!!.value.toInt())
            }
            pos = match.range.last + 1
        }
        // Nothing may trail the last token (rejects "$.a.") and at least one
        // segment is required (rejects "$.").
        require(body.substring(pos).isEmpty() && segments.isNotEmpty()) {
            "Invalid JSONPath: '$path'"
        }
        return segments
    }

    // --- merge (functional, JSON Merge Patch) --------------------------------

    /** Returns a NEW tree with [value] merged at [segments][index]. */
    private fun mergeInto(node: Any?, segments: List<Segment>, index: Int, value: Any?): Any? {
        val seg = segments[index]
        if (index == segments.lastIndex) return setChild(node, seg, value)
        return setChild(node, seg, mergeInto(readChild(node, seg), segments, index + 1, value))
    }

    private fun setChild(node: Any?, seg: Segment, value: Any?): Any? = when (node) {
        null, MISSING -> {
            require(seg is Segment.Key) { "Cannot index into a missing value" }
            mapOf(seg.name to value)
        }
        is Map<*, *> -> {
            val out = HashMap<String, Any?>(node.size)
            node.forEach { (k, v) ->
                require(k is String) { "Delta map keys must be strings" }
                out[k] = v
            }
            require(seg is Segment.Key) { "Cannot index into a map: '$seg'" }
            out[seg.name] = value
            out
        }
        is List<*> -> {
            val out = ArrayList<Any?>(node)
            require(seg is Segment.Index) { "Cannot key into a list: '$seg'" }
            require(seg.index in node.indices) { "List index out of bounds: ${seg.index}" }
            out[seg.index] = value
            out
        }
        // JSON Merge Patch replaces a non-object target with the patch object.
        else -> {
            require(seg is Segment.Key) { "Cannot index into a scalar" }
            mapOf(seg.name to value)
        }
    }

    // --- reads ---------------------------------------------------------------

    private fun readChild(node: Any?, seg: Segment): Any? = when (node) {
        is Map<*, *> ->
            if (seg is Segment.Key && node.containsKey(seg.name)) node[seg.name] else MISSING
        is List<*> ->
            if (seg is Segment.Index && seg.index in node.indices) node[seg.index] else MISSING
        else -> MISSING
    }

    private fun collectPaths(node: Any?, prefix: String, out: MutableList<String>) {
        when (node) {
            is Map<*, *> -> node.forEach { (k, v) ->
                val child = "$prefix.${k as String}"
                if (v is Map<*, *> || v is List<*>) collectPaths(v, child, out) else out += child
            }
            is List<*> -> node.forEachIndexed { i, v ->
                val child = "$prefix[$i]"
                if (v is Map<*, *> || v is List<*>) collectPaths(v, child, out) else out += child
            }
        }
    }

    // --- size estimation / deep copy ----------------------------------------

    private fun estimate(value: Any?): Long = when (value) {
        null -> 4L
        is String -> (value.length + 16).toLong()
        is Number -> 16L
        is Boolean -> 4L
        is Map<*, *> -> value.entries.sumOf { estimate(it.key) + estimate(it.value) }
        is Iterable<*> -> value.sumOf { estimate(it) }
        else -> 64L
    }

    private fun deepCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (k, v) -> (k as String) to deepCopy(v) }
        is List<*> -> value.map { deepCopy(it) }
        else -> value
    }

    /** Sentinel distinguishing "no child" from a stored null. */
    private object MISSING
}
