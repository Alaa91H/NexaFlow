package com.nexaflow.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiGatewayRoutingTest {

    @Test
    fun explicitMetadataWinsOverAllOtherSources() {
        val route = AiGatewayDialectResolver.resolve(
            modelId = "gpt-5.6-sol",
            rules = AiGatewayCatalog.openCodeZen,
            explicitDialect = AiApiDialect.ANTHROPIC_MESSAGES,
            remoteMetadata = AiGatewayRemoteModelMetadata(
                modelId = "gpt-5.6-sol",
                apiStyle = "google"
            )
        )

        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, route.dialect)
        assertEquals(AiGatewayRouteSource.EXPLICIT_METADATA, route.source)
    }

    @Test
    fun remoteMetadataOverridesCuratedAndPrefixRouting() {
        val route = AiGatewayDialectResolver.resolve(
            modelId = "gemini-3.7-flash",
            rules = AiGatewayCatalog.openCodeZen.copy(
                curatedModels = mapOf(
                    "gemini-3.7-flash" to AiApiDialect.OPENAI_RESPONSES
                )
            ),
            remoteMetadata = AiGatewayRemoteModelMetadata(
                modelId = "gemini-3.7-flash",
                apiStyle = "anthropic"
            )
        )

        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, route.dialect)
        assertEquals(AiGatewayRouteSource.REMOTE_METADATA, route.source)
    }

    @Test
    fun curatedRegistryOverridesPrefixFallback() {
        val route = AiGatewayDialectResolver.resolve(
            modelId = "gpt-special",
            rules = AiGatewayCatalog.openCodeZen.copy(
                curatedModels = mapOf(
                    "gpt-special" to AiApiDialect.GEMINI_GENERATE_CONTENT
                )
            )
        )

        assertEquals(AiApiDialect.GEMINI_GENERATE_CONTENT, route.dialect)
        assertEquals(AiGatewayRouteSource.CURATED_REGISTRY, route.source)
    }

    @Test
    fun openCodeZenRoutesKnownFamiliesAcrossFourDialects() {
        val rules = AiGatewayCatalog.openCodeZen

        assertEquals(
            AiApiDialect.GEMINI_GENERATE_CONTENT,
            AiGatewayDialectResolver.resolve("gemini-3.7-flash", rules).dialect
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            AiGatewayDialectResolver.resolve("claude-sonnet-5", rules).dialect
        )
        assertEquals(
            AiApiDialect.ANTHROPIC_MESSAGES,
            AiGatewayDialectResolver.resolve("qwen3.6-plus", rules).dialect
        )
        assertEquals(
            AiApiDialect.OPENAI_RESPONSES,
            AiGatewayDialectResolver.resolve("gpt-5.6-sol", rules).dialect
        )
        assertEquals(
            AiApiDialect.OPENAI_RESPONSES,
            AiGatewayDialectResolver.resolve("grok-4.5", rules).dialect
        )
        assertEquals(
            AiApiDialect.OPENAI_CHAT_COMPLETIONS,
            AiGatewayDialectResolver.resolve("deepseek-v4-pro", rules).dialect
        )
    }

    @Test
    fun openCodeGoAndZenCanRouteTheSameModelDifferently() {
        val modelId = "minimax-m3"
        val zen = AiGatewayDialectResolver.resolve(modelId, AiGatewayCatalog.openCodeZen)
        val go = AiGatewayDialectResolver.resolve(modelId, AiGatewayCatalog.openCodeGo)

        assertEquals(AiApiDialect.OPENAI_CHAT_COMPLETIONS, zen.dialect)
        assertEquals(AiApiDialect.ANTHROPIC_MESSAGES, go.dialect)
        assertNotEquals(zen.dialect, go.dialect)
    }

    @Test
    fun unknownRemoteApiStyleFallsBackWithoutTrustingIt() {
        val route = AiGatewayDialectResolver.resolve(
            modelId = "gpt-5.6-sol",
            rules = AiGatewayCatalog.openCodeZen,
            remoteMetadata = AiGatewayRemoteModelMetadata(
                modelId = "gpt-5.6-sol",
                apiStyle = "unknown-style"
            )
        )

        assertEquals(AiApiDialect.OPENAI_RESPONSES, route.dialect)
        assertEquals(AiGatewayRouteSource.MODEL_PREFIX, route.source)
        assertNull(AiGatewayDialectResolver.apiStyleToDialect("unknown-style"))
    }

    @Test
    fun sessionIdsAreBoundedAndSanitized() {
        val raw = " thread / with spaces ? " + "x".repeat(200)
        val normalized = AiGatewaySessionPolicy.normalizedSessionId(raw)

        assertEquals(false, normalized.contains(' '))
        assertEquals(false, normalized.contains('/'))
        assertEquals(false, normalized.contains('?'))
        assertEquals(128, normalized.length)
    }
}
