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
