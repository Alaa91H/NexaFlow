package com.nexaflow.core.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.ActiveTriggerStore
import com.nexaflow.core.datastore.AutomationRuntimeLifecycleState
import com.nexaflow.core.datastore.AutomationRuntimeState
import com.nexaflow.core.datastore.AutomationRuntimeStore
import com.nexaflow.core.execution.compat.TriggerSource
import com.nexaflow.core.execution.events.InMemoryNexaFlowEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WearEventRouterRecoveryTest {

    private lateinit var context: Context
    private lateinit var runtimeStore: AutomationRuntimeStore
    private lateinit var activeStore: ActiveTriggerStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        runtimeStore = AutomationRuntimeStore(context)
        activeStore = ActiveTriggerStore(context)
        runtimeStore.clear(AUTOMATION_ID)
        activeStore.clearSource(TriggerSource.WEAR.sourceId)
    }

    @Test
    fun `missing automation definition preserves durable wear lifecycle evidence`() = runBlocking {
        val repository = FakeRepository(emptyList())
        val history = RecordingHistory()
        val engine = testEngine(context, history)
        val scope = CoroutineScope(Dispatchers.Default)
        val source = TriggerSource.WEAR.sourceId
        runtimeStore.activateStrict(
            AutomationRuntimeState(
                automationId = AUTOMATION_ID,
                occurrenceId = "wear-orphan-occurrence",
                source = source,
                sourceKey = AUTOMATION_ID,
                lifecycleState = AutomationRuntimeLifecycleState.ACTIVE,
                activatedAt = 1L
            )
        )
        activeStore.markActive(source, AUTOMATION_ID)

        val router = WearEventRouter(
            context = context,
            repository = repository,
            executionEngine = engine,
            exitCoordinator = ExitCoordinator(runtimeStore, engine, repository, history),
            runtimeStore = runtimeStore,
            activeStore = activeStore,
            triggerIndex = TriggerIndex(repository.getAutomations()),
            eventBus = InMemoryNexaFlowEventBus(scope),
            scope = scope
        )

        router.restoreAndPruneDurableState(emptyList())

        val retained = runtimeStore.current(AUTOMATION_ID)
        assertEquals(AutomationRuntimeLifecycleState.ACTIVE, retained?.lifecycleState)
        assertEquals("wear-orphan-occurrence", retained?.occurrenceId)
        assertTrue(activeStore.activeKeys(source).isEmpty())

        runtimeStore.clear(AUTOMATION_ID)
        Unit
    }

    private companion object {
        const val AUTOMATION_ID = "wear-orphan"
    }
}
