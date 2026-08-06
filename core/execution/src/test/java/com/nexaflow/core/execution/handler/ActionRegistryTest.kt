package com.nexaflow.core.execution.handler

import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRegistryTest {

    @Test
    fun default_registersEveryActionType() {
        val registry = ActionRegistry.default()
        ActionType.entries.forEach { type ->
            assertNotNull("No handler registered for $type", registry.handlerFor(type))
        }
    }

    @Test
    fun default_coversAllKnownActionTypes() {
        val registry = ActionRegistry.default()
        assertEquals(ActionType.entries.toSet(), registry.supportedTypes)
    }

    @Test
    fun from_buildsMapFromHandlers() {
        val registry = ActionRegistry.from(listOf(MediaActionsHandler()))
        assertNotNull(registry.handlerFor(ActionType.SYSTEM_MEDIA_NEXT))
        assertNull(registry.handlerFor(ActionType.SYSTEM_DND))
    }

    @Test
    fun from_rejectsDuplicateRegistration() {
        val duplicate = object : ActionHandler {
            override val supportedTypes: Set<ActionType> = setOf(ActionType.SYSTEM_DND)
            override suspend fun execute(
                action: com.nexaflow.domain.models.Action,
                ctx: ActionExecutionContext
            ): com.nexaflow.core.rom.model.SystemControlResult {
                return com.nexaflow.core.rom.model.SystemControlResult.ok("dup")
            }
        }
        val conflicting = listOf(SoundActionsHandler(), duplicate)
        try {
            ActionRegistry.from(conflicting)
            throw AssertionError("Expected IllegalArgumentException for duplicate registration")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun allHandlers_returnsUniqueHandlers() {
        val registry = ActionRegistry.default()
        val handlers = registry.allHandlers()
        assertTrue(handlers.size >= 8)
        assertEquals(ActionType.entries.size, registry.supportedTypes.size)
    }
}
