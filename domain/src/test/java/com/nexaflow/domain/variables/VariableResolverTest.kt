package com.nexaflow.domain.variables

import org.junit.Assert.assertEquals
import org.junit.Test

class VariableResolverTest {

    @Test
    fun resolve_substitutesKnownPlaceholders() {
        assertEquals(
            "Hello World from 42",
            VariableResolver.resolve("Hello %name from %NUMBER", mapOf("name" to "World", "NUMBER" to "42"))
        )
    }

    @Test
    fun resolve_isCaseInsensitive() {
        assertEquals(
            "value",
            VariableResolver.resolve("%MY_VAR", mapOf("my_var" to "value"))
        )
        assertEquals(
            "value",
            VariableResolver.resolve("%my_var", mapOf("MY_VAR" to "value"))
        )
    }

    @Test
    fun resolve_keepsUnknownPlaceholders() {
        assertEquals(
            "%unknown stays",
            VariableResolver.resolve("%unknown stays", mapOf("known" to "x"))
        )
    }

    @Test
    fun resolve_escapedPercentStaysLiteral() {
        // %% is the escape for a literal %, while %DATE still resolves.
        assertEquals(
            "50% of 2026-08-07",
            VariableResolver.resolve("50%% of %DATE", mapOf("DATE" to "2026-08-07"))
        )
    }

    @Test
    fun resolve_multipleOccurrencesAndMixed() {
        assertEquals(
            "A A B %MISSING",
            VariableResolver.resolve("%a %A %b %MISSING", mapOf("a" to "A", "b" to "B"))
        )
    }

    @Test
    fun resolve_noPercentReturnsTextUntouched() {
        val text = "plain text without variables"
        assertEquals(text, VariableResolver.resolve(text, mapOf("a" to "b")))
    }

    @Test
    fun resolve_emptyVariablesMapKeepsEverything() {
        assertEquals("Keep %ME", VariableResolver.resolve("Keep %ME", emptyMap()))
    }

    @Test
    fun referencedPlaceholders_listsUniqueNamesInOrder() {
        assertEquals(
            listOf("date", "TIME"),
            VariableResolver.referencedPlaceholders("On %date at %TIME (%date)")
        )
        assertEquals(emptyList<String>(), VariableResolver.referencedPlaceholders("no vars"))
    }
}
