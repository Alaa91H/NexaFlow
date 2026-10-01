package com.nexaflow.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderDomainV2Test {

    @Test
    fun `connection model and credential reference are independent concepts`() {
        val credential = AiCredentialReference("secret://ai/provider/openai-main")
        val connection = AiConnectionProfile(
            id = "openai-main",
            providerKind = AiProviderKind.OPENAI,
            dialect = AiApiDialect.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            local = false,
            credentialRef = credential
        )
        val model = AiModelDescriptorV2(
            id = "gpt-5.6",
            connectionId = connection.id,
            providerKind = connection.providerKind,
            dialect = connection.dialect,
            contextWindowTokens = 128_000,
            maxOutputTokens = 32_000
        )

        assertEquals("openai-main", model.connectionId)
        assertEquals(AiProviderKind.OPENAI, model.providerKind)
        assertEquals(AiApiDialect.OPENAI_RESPONSES, model.dialect)
        assertEquals(credential, connection.credentialRef)
        assertFalse(connection.toString().contains("api-key", ignoreCase = true))
    }

    @Test
    fun `legacy protocol name resolves to dialect type`() {
        val legacy: AiProviderProtocol = AiProviderProtocol.ANTHROPIC_MESSAGES
        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, legacy)
    }

    @Test
    fun `provider identity does not imply one dialect`() {
        val opencode = AiProviderCatalog.definition("opencode")!!
        assertTrue(opencode.supportedDialects.size > 1)
        assertTrue(AiApiDialect.ANTHROPIC_MESSAGES in opencode.supportedDialects)
        assertTrue(AiApiDialect.GEMINI_GENERATE_CONTENT in opencode.supportedDialects)
    }
}
