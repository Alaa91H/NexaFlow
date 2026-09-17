package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.execution.WorkflowRunContext
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class DataActionsHandlerTest {
    @Test fun registeredActionsPassTypedOutputToTheNextAction() = runBlocking {
        val app: Context = ApplicationProvider.getApplicationContext()
        val context = WorkflowRunContext.create("data-chain", 0)
        val ctx = ActionExecutionContext(app, SystemController(app, RomCapabilityProvider(app, IntegrationLevel.NORMAL, RomFamily.AOSP)), NotificationSettings(), runContext = context)
        val registry = ActionRegistry.default()
        val first = Action(ActionType.DATA_JSON, mapOf("input" to "{\"values\":[3,1,2]}", "operation" to "POINTER", "argument" to "/values", "outputPath" to "$.values"))
        val second = Action(ActionType.DATA_ARRAY, mapOf("inputPath" to "$.values", "operation" to "SORT_NUMERIC", "outputPath" to "$.sorted"))
        assertTrue(registry.handlerFor(first.type)!!.execute(first, ctx).success)
        assertTrue(registry.handlerFor(second.type)!!.execute(second, ctx).success)
        assertEquals(listOf(BigDecimal.ONE, BigDecimal(2), BigDecimal(3)), context.get("$.sorted"))
        val invalid = Action(ActionType.DATA_MATH, mapOf("input" to "4", "argument" to "0", "operation" to "DIVIDE", "outputPath" to "$.sorted"))
        assertFalse(registry.handlerFor(invalid.type)!!.execute(invalid, ctx).success)
        assertEquals(listOf(BigDecimal.ONE, BigDecimal(2), BigDecimal(3)), context.get("$.sorted"))
        assertFalse(registry.handlerFor(first.type)!!.execute(first, ctx.copy(runContext = null)).success)
    }

    @Test fun defaultsNullInputsAndInvalidPathsAreHandledWithoutPartialWrites() = runBlocking {
        val app: Context = ApplicationProvider.getApplicationContext()
        val context = WorkflowRunContext.create("data-validation", 0)
        val ctx = ActionExecutionContext(app, SystemController(app, RomCapabilityProvider(app, IntegrationLevel.NORMAL, RomFamily.AOSP)), NotificationSettings(), runContext = context)
        val handler = DataActionsHandler()
        for (type in handler.supportedTypes) {
            assertTrue("Default $type", handler.execute(Action(type, emptyMap()), ctx).success)
        }
        context.put("$.nullable", null)
        val nullable = Action(ActionType.DATA_JSON, mapOf("inputPath" to "$.nullable", "outputPath" to "$.copy"))
        assertTrue(handler.execute(nullable, ctx).success)
        assertTrue("$.copy" in context.paths())
        val before = context.snapshot()
        for (path in listOf("missing", "\$bad", "$.a.".repeat(100), "$" + ".a".repeat(33))) {
            assertFalse(handler.execute(nullable.copy(config = nullable.config + ("outputPath" to path)), ctx).success)
            assertEquals(before, context.snapshot())
        }
        assertFalse(handler.execute(nullable.copy(config = nullable.config + ("inputPath" to "$.absent")), ctx).success)
        assertTrue(handler.execute(Action(ActionType.DATA_RANDOM, mapOf("inputPath" to "$.absent")), ctx).success)
    }
}
