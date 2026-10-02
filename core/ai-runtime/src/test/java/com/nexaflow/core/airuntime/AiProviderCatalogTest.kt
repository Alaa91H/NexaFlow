package com.nexaflow.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {

    @Test
    fun `built in catalog has openai claude gemini and opencode presets without secrets`() {
        val presets = AiProviderCatalog.presets.associateBy(AiProviderPreset::id)

        assertEquals(
            setOf("openai", "claude", "gemini", "opencode_zen"),
            presets.keys
        )
        assertEquals(AiProviderKind.OPENAI, presets.getValue("openai").providerKind)
        assertEquals(AiApiDialect.OPENAI_CHAT_COMPLETIONS, presets.getValue("openai").dialect)
        assertEquals(AiProviderKind.ANTHROPIC, presets.getValue("claude").providerKind)
        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, presets.getValue("claude").dialect)
        assertEquals(AiProviderKind.GOOGLE, presets.getValue("gemini").providerKind)
        assertEquals(
            AiApiDialect.GEMINI_GENERATE_CONTENT,
            presets.getValue("gemini").dialect
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            presets.getValue("gemini").baseUrl
        )
        assertEquals(AiAuthScheme.GOOGLE_API_KEY, presets.getValue("gemini").authScheme)
        assertEquals(
            AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
            presets.getValue("gemini").protocol
        )
        assertEquals(AiProviderKind.OPENCODE, presets.getValue("opencode_zen").providerKind)
        assertTrue(presets.values.all { it.baseUrl.startsWith("https://") })
        assertTrue(presets.values.all { it.defaultModelId.isNotBlank() })
    }

    @Test
    fun `provider definitions separate identity dialect and authentication`() {
        val definitions = AiProviderCatalog.definitions.associateBy(AiProviderDefinition::id)

        assertEquals(AiProviderKind.OPENAI, definitions.getValue("openai").kind)
        assertTrue(
            AiApiDialect.OPENAI_RESPONSES in
                definitions.getValue("openai").supportedDialects
        )
        assertEquals(
            setOf(AiAuthScheme.X_API_KEY),
            definitions.getValue("anthropic").authSchemes
        )
        assertTrue(
            AiApiDialect.GEMINI_GENERATE_CONTENT in
                definitions.getValue("google").supportedDialects
        )
        assertTrue(definitions.getValue("custom").supportsCustomEndpoint)
    }

    @Test
    fun `reasoning levels map to supported provider effort values`() {
        assertEquals("low", AiReasoningLevel.FAST.apiValue)
        assertEquals("medium", AiReasoningLevel.BALANCED.apiValue)
        assertEquals("high", AiReasoningLevel.DEEP.apiValue)
        assertEquals(AiReasoningLevel.FAST, AiReasoningLevel.fromStoredValue("low"))
        assertEquals(AiReasoningLevel.DEEP, AiReasoningLevel.fromStoredValue("DEEP"))
        assertEquals(AiReasoningLevel.BALANCED, AiReasoningLevel.fromStoredValue("unknown"))
    }
}
