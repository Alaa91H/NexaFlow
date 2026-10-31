package com.nexaflow.feature.builder

import com.nexaflow.core.agentruntime.AgentChoiceSource
import com.nexaflow.core.agentruntime.EnabledAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric supplies the Android main looper that `Dispatchers.setMain` replaces. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunChoiceViewModelTest {

    @Test
    fun exposesTheProvidedAgentsInCaseInsensitiveNameOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = AgentRunChoiceViewModel(
                object : AgentChoiceSource {
                    override fun observeEnabled() = flowOf(
                        listOf(
                            EnabledAgent(id = "b", name = "beta"),
                            EnabledAgent(id = "a", name = "Alpha")
                        )
                    )
                }
            )

            backgroundScope.launch { viewModel.agents.collect { } }
            advanceUntilIdle()

            assertEquals(listOf("Alpha", "beta"), viewModel.agents.value.map { it.name })
        } finally {
            Dispatchers.resetMain()
        }
    }
}
