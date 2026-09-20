package com.nexaflow.wear.presentation

import app.cash.turbine.test
import com.nexaflow.wear.data.WearAutomationDto
import com.nexaflow.wear.data.WearDataLayerClient
import com.nexaflow.wear.data.WearSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class WearViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val syncRepository = WearSyncRepository()
    private lateinit var dataLayerClient: WearDataLayerClient
    private lateinit var viewModel: WearViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataLayerClient = mock()
        viewModel = WearViewModel(syncRepository, dataLayerClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Connecting when no automations received`() = runTest {
        assertEquals(WearUiState.Connecting, viewModel.uiState.value)
    }

    @Test
    fun `state transitions to Loaded after automations received`() = runTest {
        val payload = """[{"id":"1","name":"Test","icon":"I","iconColor":0,"enabled":true}]"""
        syncRepository.handleIncomingPayload(payload)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Loaded but got $state", state is WearUiState.Loaded)
        assertEquals(1, (state as WearUiState.Loaded).automations.size)
    }

    @Test
    fun `state is Empty when phone pushes an empty automation list`() = runTest {
        // First push a real list so state is no longer Connecting
        val payload = """[{"id":"1","name":"Test","icon":"I","iconColor":0,"enabled":true}]"""
        syncRepository.handleIncomingPayload(payload)
        testDispatcher.scheduler.advanceUntilIdle()
        // Then push empty list
        syncRepository.handleIncomingPayload("[]")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WearUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `runNow delegates to dataLayerClient`() = runTest {
        whenever(dataLayerClient.sendRunCommand(any())).thenReturn(Unit)
        val automation = makeDto("a1")

        viewModel.runNow(automation)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(dataLayerClient).sendRunCommand("a1")
    }

    @Test
    fun `toggleEnabled delegates to dataLayerClient`() = runTest {
        whenever(dataLayerClient.sendToggleCommand(any(), any())).thenReturn(Unit)
        val automation = makeDto("b2")

        viewModel.toggleEnabled(automation, false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(dataLayerClient).sendToggleCommand("b2", false)
    }

    private fun makeDto(id: String) = WearAutomationDto(
        id = id, name = "Test", icon = "Icon", iconColor = 0L, enabled = true,
    )
}
