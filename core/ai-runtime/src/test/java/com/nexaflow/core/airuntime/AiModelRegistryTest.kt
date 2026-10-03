package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelRegistryTest {

    @Test
    fun `capability evidence merges static metadata and successful probe`() = runTest {
        var now = 1_000L
        val registry = AiModelRegistry(
            ttlMillis = 60_000,
            clock = { now }
        )
        val adapter = FakeAdapter(
            AiCapabilityResult(
                success = true,
                modelId = "model-a",
                capabilities = AiProviderCapabilities(
                    toolCalling = true,
                    reasoning = true
                )
            )
        )

        val snapshot = registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel(
                id = "model-a",
                contextTokens = 128_000
            ),
            providerRevision = "revision-1",
            staticEvidence = AiCapabilityEvidence(
                structuredOutput = true,
                maxOutputTokens = 16_000
            ),
            providerMetadataEvidence = AiCapabilityEvidence(
                vision = true
            ),
            adapter = adapter,
            probe = true
        )

        assertTrue(snapshot.descriptor.capabilities.toolCalling)
        assertTrue(snapshot.descriptor.capabilities.structuredOutput)
        assertTrue(snapshot.descriptor.capabilities.vision)
        assertTrue(snapshot.descriptor.capabilities.reasoning)
        assertEquals(128_000, snapshot.descriptor.contextWindowTokens)
        assertEquals(16_000, snapshot.descriptor.maxOutputTokens)
        assertEquals(
            setOf(
                AiCapabilityEvidenceSource.STATIC,
                AiCapabilityEvidenceSource.PROVIDER_METADATA,
                AiCapabilityEvidenceSource.PROBE
            ),
            snapshot.sources
        )
        assertEquals(1, adapter.probeCount)
        assertEquals(now, snapshot.testedAtEpochMillis)
    }

    @Test
    fun `cache respects ttl and provider revision`() = runTest {
        var now = 10_000L
        val registry = AiModelRegistry(
            ttlMillis = 1_000,
            clock = { now }
        )
        val adapter = FakeAdapter(
            AiCapabilityResult(
                success = true,
                modelId = "model-a",
                capabilities = AiProviderCapabilities(toolCalling = true)
            )
        )

        val first = registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-a"),
            providerRevision = "revision-1",
            adapter = adapter,
            probe = true
        )
        now += 500
        val cached = registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-a"),
            providerRevision = "revision-1",
            adapter = adapter,
            probe = true
        )

        assertEquals(first, cached)
        assertEquals(1, adapter.probeCount)
        assertNull(
            registry.cached(
                connectionId = "connection-a",
                modelId = "model-a",
                providerRevision = "revision-2"
            )
        )

        now += 600
        val refreshed = registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-a"),
            providerRevision = "revision-1",
            adapter = adapter,
            probe = true
        )

        assertEquals(2, adapter.probeCount)
        assertTrue(refreshed.testedAtEpochMillis > first.testedAtEpochMillis)
    }

    @Test
    fun `failed probe keeps provider metadata without claiming probe evidence`() = runTest {
        val registry = AiModelRegistry()
        val adapter = FakeAdapter(
            AiCapabilityResult(
                success = false,
                modelId = "model-a",
                failure = AiConnectionFailure.TIMEOUT
            )
        )

        val snapshot = registry.resolve(
            connection = connection(local = true),
            discoveredModel = AiDiscoveredModel(
                id = "model-a",
                contextTokens = 32_000
            ),
            providerRevision = "revision-1",
            staticEvidence = AiCapabilityEvidence(streaming = true),
            adapter = adapter,
            probe = true
        )

        assertTrue(snapshot.descriptor.capabilities.streaming)
        assertTrue(snapshot.descriptor.capabilities.local)
        assertEquals(32_000, snapshot.descriptor.capabilities.contextTokens)
        assertFalse(AiCapabilityEvidenceSource.PROBE in snapshot.sources)
        assertTrue(AiCapabilityEvidenceSource.PROVIDER_METADATA in snapshot.sources)
    }

    @Test
    fun `bounded registry evicts least recently used model`() = runTest {
        val registry = AiModelRegistry(maxEntries = 2)

        registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-1"),
            providerRevision = "revision-1"
        )
        registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-2"),
            providerRevision = "revision-1"
        )
        assertNotNull(registry.cached("connection-a", "model-1", "revision-1"))
        registry.resolve(
            connection = connection(),
            discoveredModel = AiDiscoveredModel("model-3"),
            providerRevision = "revision-1"
        )

        assertNotNull(registry.cached("connection-a", "model-1", "revision-1"))
        assertNull(registry.cached("connection-a", "model-2", "revision-1"))
        assertNotNull(registry.cached("connection-a", "model-3", "revision-1"))
        assertEquals(2, registry.size())
    }

    private fun connection(local: Boolean = false) = AiConnectionProfile(
        id = "connection-a",
        providerKind = AiProviderKind.OPENAI,
        dialect = AiApiDialect.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        local = local
    )

    private class FakeAdapter(
        private val capabilityResult: AiCapabilityResult
    ) : AiProviderAdapter {
        var probeCount: Int = 0
            private set

        private val state = MutableStateFlow(
            AiProviderDescriptor(
                id = "fake",
                displayName = "Fake",
                modelId = capabilityResult.modelId,
                available = true
            )
        )

        override val descriptor: StateFlow<AiProviderDescriptor> = state

        override suspend fun verifyConnection() = AiConnectionTestResult(
            success = true,
            providerId = "fake",
            dialect = AiApiDialect.OPENAI_RESPONSES
        )

        override suspend fun listModels() = AiModelDiscoveryResult(
            success = true,
            models = listOf(AiDiscoveredModel(capabilityResult.modelId))
        )

        override suspend fun discoverCapabilities(
            model: AiModelDescriptorV2
        ): AiCapabilityResult {
            probeCount += 1
            return capabilityResult
        }

        override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> =
            emptyFlow()
    }
}
