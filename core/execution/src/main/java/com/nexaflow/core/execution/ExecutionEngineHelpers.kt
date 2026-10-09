package com.nexaflow.core.execution

import com.nexaflow.domain.models.ConditionResult

/** Makes Tasker outputs available only to actions in the current run. */
internal fun publishPluginOutputVariables(metadata: Map<String, String>, runContext: WorkflowRunContext?) {
    val context = runContext ?: return
    val outputs = metadata.asSequence()
        .filter { (key, _) -> key.startsWith("pluginOutput.") }
        .associate { (key, value) -> key.removePrefix("pluginOutput.") to value }
    if (outputs.isEmpty()) return
    val merged = LinkedHashMap<String, Any?>()
    (context.get("$.pluginOutputs") as? Map<*, *>)
        ?.forEach { (key, value) -> if (key is String) merged[key] = value }
    merged.putAll(outputs)
    // The run context enforces its own size budget; this best-effort export
    // must not turn a successful external action into a failure.
    runCatching { context.put("$.pluginOutputs", merged) }
}

internal fun ConditionResult.toGateMessage(): String = when (this) {
    ConditionResult.Satisfied -> "constraints satisfied"
    ConditionResult.Unsatisfied -> "constraints not met"
    ConditionResult.Unknown -> "constraint state is unknown"
    ConditionResult.Unavailable -> "constraint provider is unavailable"
    is ConditionResult.Error -> "constraint evaluation error: $reason"
}
