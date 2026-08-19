package com.nexaflow.core.execution

import android.content.Context
import androidx.paging.PagingSource
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationPreferences
import com.nexaflow.core.execution.handler.ActionExecutionContext
import com.nexaflow.core.execution.handler.ActionHandler
import com.nexaflow.core.execution.handler.ActionRegistry
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.ExecutionRecord
import com.nexaflow.domain.models.GlobalVariable
import com.nexaflow.domain.repositories.HistoryRepository
import com.nexaflow.domain.repositories.VariableRepository
import com.nexaflow.domain.variables.RuntimeValue
import com.nexaflow.domain.variables.RuntimeValueCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the Tasker-style %variable resolution in [ExecutionEngine]: user
 * globals and device built-ins are substituted into every text-bearing config
 * value BEFORE the action handler sees it. A recording handler captures the
 * config exactly as dispatched, so the assertion does not depend on any
 * real device state.
 */
@RunWith(RobolectricTestRunner::class)
class ExecutionEngineVariableResolutionTest {

    private lateinit var context: Context

    private class RecordingHandler : ActionHandler {
        val receivedConfigs = mutableListOf<Map<String, String>>()
        val receivedTypedThresholds = mutableListOf<RuntimeValue?>()
        override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_SEND_NOTIFICATION)
        override suspend fun execute(
            action: Action,
            ctx: ActionExecutionContext
        ): SystemControlResult {
            receivedConfigs += action.config
            receivedTypedThresholds += ctx.dataRuntime?.resolve("threshold")?.value
            return SystemControlResult.ok("ok")
        }
    }

    private class FakeVariableRepository(
        private val variables: List<GlobalVariable>
    ) : VariableRepository {
        override fun getVariables(): Flow<List<GlobalVariable>> = flowOf(variables)
        override fun getVariablesPaging(): PagingSource<Int, GlobalVariable> = emptyPagingSource()
        override suspend fun getVariablesOnce(): List<GlobalVariable> = variables
        override suspend fun saveVariable(variable: GlobalVariable) = Unit
        override suspend fun deleteVariable(id: String) = Unit
    }

    private class FakeHistoryRepository : HistoryRepository {
        override fun getExecutionHistory(): Flow<List<ExecutionRecord>> = flowOf(emptyList())
        override fun getExecutionPaging(): PagingSource<Int, ExecutionRecord> =
            emptyPagingSource()
        override suspend fun getExecutionById(id: String): ExecutionRecord? = null
        override suspend fun recordExecution(record: ExecutionRecord) = Unit
    }

    private fun automationWith(action: Action): Automation = Automation(
        id = "auto-var",
        name = "Variable task",
        description = "",
        icon = "bolt",
        iconColor = 0xFF0000,
        backgroundColor = 0xFFEEEE,
        category = "general",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = listOf(action),
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun engine(
        handler: RecordingHandler,
        variables: List<GlobalVariable>
    ): ExecutionEngine = ExecutionEngine(
        context = context,
        historyRepository = FakeHistoryRepository(),
        notificationPreferences = NotificationPreferences(context),
        actionRegistry = ActionRegistry.from(listOf(handler)),
        variableRepository = FakeVariableRepository(variables)
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun runAutomation_substitutesUserGlobals() = runBlocking {
        val handler = RecordingHandler()
        val engine = engine(
            handler,
            variables = listOf(
                GlobalVariable("g1", "HomeAddress", "123 Main St", 1L),
                GlobalVariable("g2", "owner", "Alaa", 1L)
            )
        )

        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    mapOf("title" to "Hello %OWNER", "text" to "%homeaddress is home")
                )
            )
        )

        assertEquals(1, handler.receivedConfigs.size)
        val config = handler.receivedConfigs.first()
        assertEquals("Hello Alaa", config["title"])
        assertEquals("123 Main St is home", config["text"])
    }

    @Test
    fun runAutomation_exposesTypedGlobalsThroughDataRuntime() = runBlocking {
        val handler = RecordingHandler()
        val engine = engine(
            handler,
            variables = listOf(
                GlobalVariable(
                    id = "g1",
                    name = "threshold",
                    value = "85",
                    updatedAt = 1L,
                    version = 2L,
                    serializedValue = RuntimeValueCodec.encode(RuntimeValue.IntValue(85))
                )
            )
        )

        engine.runAutomation(
            automationWith(
                Action(ActionType.SYSTEM_SEND_NOTIFICATION, mapOf("title" to "Threshold %THRESHOLD"))
            )
        )

        assertEquals("Threshold 85", handler.receivedConfigs.single()["title"])
        assertEquals(RuntimeValue.IntValue(85), handler.receivedTypedThresholds.single())
    }

    @Test
    fun runAutomation_substitutesBuiltinVariables() = runBlocking {
        val handler = RecordingHandler()
        val engine = engine(handler, variables = emptyList())

        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    mapOf("title" to "Level %BATTERY", "text" to "Run on %DATE")
                )
            )
        )

        val config = handler.receivedConfigs.first()
        // Built-ins come from live device state: assert substitution happened
        // (no leftover placeholder) rather than pinning exact values.
        assertFalse(config["title"].orEmpty().contains("%"))
        assertTrue(config["title"].orEmpty().contains("Level"))
        assertFalse(config["text"].orEmpty().contains("%"))
    }

    @Test
    fun runAutomation_keepsUnknownPlaceholdersUntouched() = runBlocking {
        val handler = RecordingHandler()
        val engine = engine(handler, variables = emptyList())

        engine.runAutomation(
            automationWith(
                Action(
                    ActionType.SYSTEM_SEND_NOTIFICATION,
                    mapOf("title" to "Hi %UNKNOWN_VAR")
                )
            )
        )

        assertEquals("Hi %UNKNOWN_VAR", handler.receivedConfigs.first()["title"])
    }
}
