package com.nexaflow.feature.ai

import androidx.lifecycle.ViewModelStore
import com.nexaflow.core.airuntime.AiModelProvider
import com.nexaflow.core.airuntime.AiProviderDescriptor
import com.nexaflow.core.airuntime.AiConversationEngine
import com.nexaflow.core.airuntime.AiProviderRegistry
import com.nexaflow.core.airuntime.AiProviderRequest
import com.nexaflow.core.airuntime.AiProviderEvent
import com.nexaflow.core.airuntime.AiConversationMessage
import com.nexaflow.core.airuntime.AiProviderCapabilities
import com.nexaflow.core.airuntime.AiRoutingMode
import com.nexaflow.core.airuntime.AiRoutingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun blankMessageIsIgnoredAndUnavailableProviderIsShown() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.send("  \n")
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertFalse(viewModel.state.value.running)

        viewModel.send("hello")
        runCurrent()

        assertEquals("hello", viewModel.state.value.messages.single().text)
        assertFalse(viewModel.state.value.running)
        assertEquals("no_provider", viewModel.state.value.errorCode)
    }

    @Test
    fun clearErrorRemovesOnlyErrorAndClearConversationResetsTranscript() = runTest(mainDispatcher) {
        val viewModel = createViewModel()
        runCurrent()
        viewModel.send("hello")
        runCurrent()

        viewModel.clearError()
        assertEquals(1, viewModel.state.value.messages.size)
        assertEquals(null, viewModel.state.value.errorCode)

        viewModel.clearConversation()
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertEquals(null, viewModel.state.value.errorCode)
    }

    @Test
    fun failedReplyDoesNotKeepPartialAssistantTextAsACompletedMessage() = runTest(mainDispatcher) {
        val registry = AiProviderRegistry(listOf(PartialThenFailedProvider()))
        registry.updateRoutingPolicy(AiRoutingPolicy(mode = AiRoutingMode.CLOUD_ONLY))
        val viewModel = AiChatViewModel(
            engine = AiConversationEngine(registry),
            providerRegistry = registry
        ).also { viewModelStore.put("ai-chat-failure", it) }
        runCurrent()

        viewModel.send("hello")
        runCurrent()

        assertEquals(listOf("hello"), viewModel.state.value.messages.map { it.text })
        assertEquals("provider_failure", viewModel.state.value.errorCode)
        assertEquals("", viewModel.state.value.assistantDraft)
        assertFalse(viewModel.state.value.running)
    }

    private fun createViewModel() = AiChatViewModel(
        engine = AiConversationEngine(AiProviderRegistry()),
        providerRegistry = AiProviderRegistry()
    ).also { viewModelStore.put("ai-chat", it) }

    private class PartialThenFailedProvider : AiModelProvider {
        private val _descriptor = MutableStateFlow(
            AiProviderDescriptor(
                id = "test",
                displayName = "Test",
                modelId = "test-model",
                capabilities = AiProviderCapabilities(),
                available = true
            )
        )
        override val descriptor: StateFlow<AiProviderDescriptor> = _descriptor.asStateFlow()

        override fun stream(request: AiProviderRequest): Flow<AiProviderEvent> = flow {
            emit(AiProviderEvent.TextDelta("misleading partial"))
            error("provider failure")
        }
    }
}
