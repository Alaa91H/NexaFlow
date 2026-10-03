package com.nexaflow.core.airuntime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderRoutingTest {

    @Test
    fun automaticPrefersLocalAndRequiresExplicitCloudFallback() {
        val local = FakeProvider("local", local = true, available = true)
        val cloud = FakeProvider("cloud", local = false, available = true)
        val registry = AiProviderRegistry(listOf(cloud, local))

        registry.updateRoutingPolicy(AiRoutingPolicy())
        assertEquals("local", registry.routeProvider(true)?.descriptor?.value?.id)

        local.descriptor.value = local.descriptor.value.copy(available = false)
        registry.refreshDescriptors()
        assertNull(registry.routeProvider(true))

        registry.updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.AUTOMATIC,
                allowCloudFallback = true
            )
        )
        assertEquals("cloud", registry.routeProvider(true)?.descriptor?.value?.id)
    }

    @Test
    fun localAndCloudModesNeverCrossBoundary() {
        val local = FakeProvider("local", local = true, available = true)
        val cloud = FakeProvider("cloud", local = false, available = true)
        val registry = AiProviderRegistry(listOf(cloud, local))

        registry.updateRoutingPolicy(AiRoutingPolicy(AiRoutingMode.LOCAL_ONLY))
        assertEquals("local", registry.routeProvider(true)?.descriptor?.value?.id)

        registry.updateRoutingPolicy(AiRoutingPolicy(AiRoutingMode.CLOUD_ONLY))
        assertEquals("cloud", registry.routeProvider(true)?.descriptor?.value?.id)
    }

    @Test
    fun selectedProviderNeverFallsBackSilently() {
        val first = FakeProvider("first", local = true, available = true)
        val second = FakeProvider("second", local = false, available = true)
        val registry = AiProviderRegistry(listOf(first, second))

        registry.updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.SELECTED_PROVIDER,
                selectedProviderId = "second"
            )
        )
        assertEquals("second", registry.routeProvider(true)?.descriptor?.value?.id)

        second.descriptor.value = second.descriptor.value.copy(available = false)
        registry.refreshDescriptors()
        assertNull(registry.routeProvider(true))
        assertEquals(
            AiRouteReason.SELECTED_UNAVAILABLE,
            registry.state.value.routeDecision.reason
        )
    }

    @Test
    fun toolCapableProviderWinsDeterministically() {
        val plain = FakeProvider(
            "a-plain",
            local = true,
            available = true,
            toolCalling = false,
            structuredOutput = false
        )
        val tools = FakeProvider(
            "z-tools",
            local = true,
            available = true,
            toolCalling = true,
            structuredOutput = false
        )
        val registry = AiProviderRegistry(listOf(plain, tools))

        registry.updateRoutingPolicy(AiRoutingPolicy(AiRoutingMode.LOCAL_ONLY))

        assertEquals("z-tools", registry.routeProvider(true)?.descriptor?.value?.id)
    }

    @Test
    fun registryCanReplaceAdaptersForStoredProviderProfiles() {
        val registry = AiProviderRegistry()
        val cloud = FakeProvider("claude-main", local = false, available = true)

        registry.replaceProviders(listOf(cloud))
        registry.updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.SELECTED_PROVIDER,
                selectedProviderId = "claude-main"
            )
        )

        assertEquals("claude-main", registry.routeProvider(true)?.descriptor?.value?.id)
        registry.replaceProviders(emptyList())
        assertNull(registry.routeProvider(true))
    }


    @Test
    fun rateLimitedLocalProviderFallsBackToCloudUntilCooldownExpires() {
        val local = FakeProvider("local", local = true, available = true)
        val cloud = FakeProvider("cloud", local = false, available = true)
        val registry = AiProviderRegistry(listOf(local, cloud))
        registry.updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.AUTOMATIC,
                allowCloudFallback = true
            )
        )

        registry.recordProviderFailure(
            providerId = "local",
            failure = AiConnectionFailure.RATE_LIMITED,
            retryAfterMs = 10_000L,
            nowMillis = 1_000L
        )

        val duringCooldown = registry.routeDecision(
            AiRoutingRequirements(requireTools = true),
            nowMillis = 2_000L
        )
        assertEquals("cloud", duringCooldown.providerId)
        assertEquals(AiRouteReason.AUTOMATIC_HEALTH_FALLBACK, duringCooldown.reason)

        val afterCooldown = registry.routeDecision(
            AiRoutingRequirements(requireTools = true),
            nowMillis = 12_000L
        )
        assertEquals("local", afterCooldown.providerId)
        assertEquals(AiRouteReason.AUTOMATIC_LOCAL, afterCooldown.reason)
    }

    @Test
    fun verifiedRoutingRejectsUnknownProviderUntilSuccessfulVerification() {
        val cloud = FakeProvider("cloud", local = false, available = true)
        val registry = AiProviderRegistry(listOf(cloud))
        registry.updateRoutingPolicy(AiRoutingPolicy(AiRoutingMode.CLOUD_ONLY))
        val requirements = AiRoutingRequirements(
            requireTools = true,
            requireVerified = true
        )

        assertNull(registry.routeProvider(requirements, nowMillis = 1_000L))
        assertEquals(
            AiRouteReason.HEALTH_UNAVAILABLE,
            registry.routeDecision(requirements, nowMillis = 1_000L).reason
        )

        registry.recordConnectionTest(
            AiConnectionTestResult(
                success = true,
                providerId = "cloud",
                dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
                httpStatus = 200,
                latencyMs = 20
            ),
            nowMillis = 2_000L
        )

        assertEquals(
            "cloud",
            registry.routeProvider(requirements, nowMillis = 2_001L)
                ?.descriptor?.value?.id
        )
        assertEquals(
            AiProviderHealthState.HEALTHY,
            registry.state.value.providerHealth.getValue("cloud").state
        )
    }

    @Test
    fun fatalVerificationFailureBlocksSelectedProviderWithoutSilentFallback() {
        val selected = FakeProvider("selected", local = false, available = true)
        val backup = FakeProvider("backup", local = false, available = true)
        val registry = AiProviderRegistry(listOf(selected, backup))
        registry.updateRoutingPolicy(
            AiRoutingPolicy(
                mode = AiRoutingMode.SELECTED_PROVIDER,
                selectedProviderId = "selected"
            )
        )

        registry.recordConnectionTest(
            AiConnectionTestResult(
                success = false,
                providerId = "selected",
                dialect = AiApiDialect.OPENAI_CHAT_COMPLETIONS,
                httpStatus = 401,
                failure = AiConnectionFailure.AUTHENTICATION,
                latencyMs = 10
            ),
            nowMillis = 5_000L
        )

        assertNull(registry.routeProvider(true))
        assertEquals(
            AiRouteReason.SELECTED_UNHEALTHY,
            registry.routeDecision(AiRoutingRequirements(requireTools = true)).reason
        )
        assertEquals(
            AiProviderHealthState.UNAVAILABLE,
            registry.state.value.providerHealth.getValue("selected").state
        )
    }

    private class FakeProvider(
        id: String,
        local: Boolean,
        available: Boolean,
        toolCalling: Boolean = true,
        structuredOutput: Boolean = true
    ) : AiModelProvider {
        override val descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = id,
                displayName = id,
                capabilities = AiProviderCapabilities(
                    toolCalling = toolCalling,
                    structuredOutput = structuredOutput,
                    streaming = true,
                    local = local
                ),
                available = available
            )
        )

        override fun stream(request: AiProviderRequest) =
            emptyFlow<AiProviderEvent>()
    }
}
