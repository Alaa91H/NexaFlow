package com.nexaflow.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderDefinitionRegistryTest {

    @Test
    fun `definition and preset ids are unique`() {
        val definitions = AiProviderDefinitionRegistry.definitions
        val presets = AiProviderDefinitionRegistry.presets

        assertEquals(definitions.size, definitions.map { it.id }.toSet().size)
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
    }

    @Test
    fun `every preset resolves to a compatible definition`() {
        AiProviderDefinitionRegistry.presets.forEach { preset ->
            val definition = AiProviderDefinitionRegistry.definitionForPreset(preset.id)

            assertNotNull("Missing definition for " + preset.id, definition)
            requireNotNull(definition)
            assertEquals(preset.providerKind, definition.kind)
            assertTrue(preset.dialect in definition.supportedDialects)
            assertTrue(preset.authScheme in definition.authSchemes)
        }
    }

    @Test
    fun `canonical providers expose the intended native dialects`() {
        assertEquals(
            AiApiDialect.OPENAI_RESPONSES,
            AiProviderDefinitionRegistry.definition("openai")?.defaultDialect
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            AiProviderDefinitionRegistry.definition("anthropic")?.defaultDialect
        )
        assertEquals(
            AiApiDialect.GEMINI_GENERATE_CONTENT,
            AiProviderDefinitionRegistry.definition("google")?.defaultDialect
        )
    }

    @Test
    fun `legacy protocol remains a fallback only when preset metadata is absent`() {
        assertEquals(
            AiApiDialect.OPENAI_RESPONSES,
            AiProviderDefinitionRegistry.resolveDialect(
                presetId = "openai",
                storedProtocol = AiProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                modelId = "gpt-5.6"
            )
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            AiProviderDefinitionRegistry.resolveDialect(
                presetId = null,
                storedProtocol = AiProviderProtocol.ANTHROPIC_MESSAGES,
                modelId = "claude-custom"
            )
        )
    }

    @Test
    fun `custom endpoint and authentication policy come from definitions`() {
        assertTrue(AiProviderDefinitionRegistry.isCustomEndpointAllowed("custom"))
        assertTrue(
            AiProviderDefinitionRegistry.supportsAuth(
                "openai",
                AiAuthScheme.BEARER_TOKEN
            )
        )
        assertTrue(
            AiProviderDefinitionRegistry.supportsDialect(
                "gemini",
                AiApiDialect.GEMINI_GENERATE_CONTENT
            )
        )
    }
}
