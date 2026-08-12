package com.nexaflow.core.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 5 (Appendix A.4.1): `%CTX.<jsonpath>` selectors are resolved against a
 * [WorkflowRunContext] — a node can consume the output an earlier node
 * published at its `outputPath`.
 */
class ContextVariableResolverTest {

    private fun contextWith(pairs: List<Pair<String, Any?>>): WorkflowRunContext {
        val ctx = WorkflowRunContext.create("auto-ctx", 42L)
        pairs.forEach { (path, value) -> ctx.put(path, value) }
        return ctx
    }

    @Test
    fun resolvesScalarAtNestedPath() {
        val ctx = contextWith(listOf("$.fetch.result" to mapOf("status" to 200, "body" to "hello")))
        assertEquals(
            "status=200 body=hello",
            ContextVariableResolver.resolve("status=%CTX.$.fetch.result.status body=%CTX.$.fetch.result.body", ctx)
        )
    }

    @Test
    fun resolvesListIndexPath() {
        val ctx = contextWith(listOf("$.items" to listOf(mapOf("name" to "a"), mapOf("name" to "b"))))
        assertEquals(
            "b",
            ContextVariableResolver.resolve("%CTX.$.items[1].name", ctx)
        )
    }

    @Test
    fun rendersMapAndListAsCompactJson() {
        val ctx = contextWith(listOf("$.obj" to mapOf("a" to 1, "b" to "x"), "$.arr" to listOf(1, "two")))
        assertEquals("""{"a":1,"b":"x"}""", ContextVariableResolver.resolve("%CTX.$.obj", ctx))
        assertEquals("""[1,"two"]""", ContextVariableResolver.resolve("%CTX.$.arr", ctx))
    }

    @Test
    fun missingPathKeepsPlaceholderUntouched() {
        val ctx = contextWith(emptyList())
        assertEquals(
            "call %CTX.$.fetch.result.body",
            ContextVariableResolver.resolve("call %CTX.$.fetch.result.body", ctx)
        )
    }

    @Test
    fun storedNullKeepsPlaceholderUntouched() {
        val ctx = contextWith(listOf("$.maybe" to null))
        assertEquals("%CTX.$.maybe", ContextVariableResolver.resolve("%CTX.$.maybe", ctx))
    }

    @Test
    fun noPercentReturnsTextUntouched() {
        val ctx = contextWith(listOf("$.a" to "b"))
        assertEquals("plain text", ContextVariableResolver.resolve("plain text", ctx))
    }

    @Test
    fun referenceIsCaseInsensitive() {
        val ctx = contextWith(listOf("$.a" to "b"))
        assertEquals("b", ContextVariableResolver.resolve("%ctx.$.a", ctx))
        assertEquals("b", ContextVariableResolver.resolve("%CtX.$.a", ctx))
    }

    @Test
    fun multipleReferencesInOneText() {
        val ctx = contextWith(listOf("$.r" to mapOf("status" to 200, "body" to "ok")))
        assertEquals(
            "200-ok-200",
            ContextVariableResolver.resolve("%CTX.$.r.status-%CTX.$.r.body-%CTX.$.r.status", ctx)
        )
    }

    @Test
    fun unknownVariableStylePercentIsUntouched() {
        val ctx = contextWith(listOf("$.a" to "b"))
        // %NAME style belongs to VariableResolver — ContextVariableResolver
        // must not consume it.
        assertEquals("%DATE", ContextVariableResolver.resolve("%DATE", ctx))
        // Nor a bare %CTX without a path.
        assertEquals("x %CTX y", ContextVariableResolver.resolve("x %CTX y", ctx))
    }

    @Test
    fun referencedPathsListsOnlyContextSelectorsInOrder() {
        val text = "a %CTX.$.x.b %DATE %ctx.$.y %CTX.$.x.c"
        assertEquals(
            listOf("$.x.b", "$.y", "$.x.c"),
            ContextVariableResolver.referencedPaths(text)
        )
    }

    @Test
    fun resolvesWholeNumberAndBooleanScalars() {
        val ctx = contextWith(listOf("$.n" to 21, "$.ok" to true))
        assertEquals("21-true", ContextVariableResolver.resolve("%CTX.$.n-%CTX.$.ok", ctx))
    }

    @Test
    fun escapedPercentSentinelIsNotResolved() {
        // In the engine chain, VariableResolver runs FIRST and swaps `%%` for
        // its sentinel, so ContextVariableResolver never sees a double percent.
        // Feed the post-VariableResolver form: the sentinel must pass through
        // untouched (it is restored to `%` by VariableResolver afterwards).
        val ctx = contextWith(listOf("$.a" to "v"))
        assertEquals("\u0000CTX.$.a", ContextVariableResolver.resolve("\u0000CTX.$.a", ctx))
    }

    @Test
    fun emptyContextRootResolvesToStringsOnly() {
        val ctx = contextWith(listOf("$.s" to ""))
        assertEquals("got=[]", ContextVariableResolver.resolve("got=[%CTX.$.s]", ctx))
        assertTrue(ContextVariableResolver.referencedPaths("%CTX.$.s").isNotEmpty())
    }
}
