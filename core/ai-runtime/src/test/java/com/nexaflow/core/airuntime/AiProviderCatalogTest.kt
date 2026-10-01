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
        assertEquals(AiProviderProtocol.OPENAI_CHAT_COMPLETIONS, presets.getValue("openai").protocol)
        assertEquals(AiProviderProtocol.ANTHROPIC_MESSAGES, presets.getValue("claude").protocol)
        assertEquals(AiProviderProtocol.OPENAI_CHAT_COMPLETIONS, presets.getValue("gemini").protocol)
        assertEquals(AiProviderProtocol.OPENAI_CHAT_COMPLETIONS, presets.getValue("opencode_zen").protocol)
        assertTrue(presets.values.all { it.baseUrl.startsWith("https://") })
        assertTrue(presets.values.all { it.defaultModelId.isNotBlank() })
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
