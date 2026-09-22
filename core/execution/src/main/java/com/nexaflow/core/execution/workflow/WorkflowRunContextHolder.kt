package com.nexaflow.core.execution.workflow

import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.domain.workflow.RuntimeValueV1
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Coroutine-scoped accessor for the ambient [WorkflowRunContext] so compiled
 * [ConditionExpr][com.nexaflow.domain.workflow.ConditionExpr] conditions can
 * resolve `$.`-path references without the persisted model ever carrying a
 * context pointer. The interpreter installs the context for the duration of a
 * run; reads outside a run return null and comparisons fail closed.
 */
object WorkflowRunContextHolder {

    private class Element(val runContext: WorkflowRunContext) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Element>
    }

    /** Installs [runContext] for the duration of [block]. */
    suspend fun <T> withRunContext(runContext: WorkflowRunContext, block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Element(runContext)) { block() }

    /**
     * Reads a `$.`-path from the ambient context. Unsupported outside a run
     * (returns null). Unreadable values also become null — conditions fail
     * closed rather than guessing. Suspend: reads the coroutine context.
     */
    suspend fun read(path: String): RuntimeValueV1? {
        val element = try {
            coroutineContext[Element.Key]
        } catch (_: IllegalStateException) {
            return null
        } ?: return null
        return try {
            element.runContext.get(path)?.toRuntimeValueV1()
        } catch (_: Throwable) {
            null
        }
    }

    private fun Any?.toRuntimeValueV1(): RuntimeValueV1? = when (this) {
        null -> null
        is RuntimeValueV1 -> this
        is String -> RuntimeValueV1.StringValue(this)
        is Boolean -> RuntimeValueV1.BooleanValue(this)
        is Int -> RuntimeValueV1.IntValue(this)
        is Long -> RuntimeValueV1.LongValue(this)
        is Double -> RuntimeValueV1.DoubleValue(this)
        else -> null
    }
}
