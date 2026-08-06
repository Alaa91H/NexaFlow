package com.nexaflow.core.execution.handler

import com.nexaflow.core.compat.ExecutionProvider
import com.nexaflow.core.compat.ExecutionProviderType
import com.nexaflow.core.compat.DeviceProfile
import com.nexaflow.core.rom.model.RomCapability
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-6 wiring test: the engine passes the runtime-selected [ExecutionProvider]
 * into the action context, and the shell handler must prefer it over the legacy
 * hard-coded runtime — falling back only when no channel was selected or the
 * channel has no shell access.
 */
class AdvancedActionsHandlerRoutingTest {

    private val handler = AdvancedActionsHandler()

    private class FakeProvider(
        override val type: ExecutionProviderType,
        private val result: SystemControlResult
    ) : ExecutionProvider {
        override val baseScore: Int = 0
        override val supportedCapabilities: Set<RomCapability> = emptySet()
        override fun isAvailable(profile: DeviceProfile): Boolean = true
        override fun execute(command: String): SystemControlResult = result
    }

    @Test
    fun route_usesSelectedChannelWhenItHasShellAccess() {
        val channel = FakeProvider(
            ExecutionProviderType.ROOT,
            SystemControlResult.ok("root ran it")
        )
        val result = handler.route(channel, ActionType.ADVANCED_ROOT, "echo hi")
        assertTrue(result.success)
        assertEquals("root ran it", result.message)
    }

    @Test
    fun route_usesShizukuChannelForShizukuAction() {
        val channel = FakeProvider(
            ExecutionProviderType.SHIZUKU,
            SystemControlResult.ok("shizuku ran it")
        )
        val result = handler.route(channel, ActionType.ADVANCED_SHIZUKU, "echo hi")
        assertTrue(result.success)
        assertEquals("shizuku ran it", result.message)
    }

    @Test
    fun route_fallsBackToLegacyRuntimeWithoutChannel() {
        // No channel selected (legacy engine path): the explicit runtime runs,
        // which fails on a JVM without root/shizuku — proving the fallback
        // executed rather than crashing or silently succeeding.
        val result = handler.route(null, ActionType.ADVANCED_ROOT, "echo hi")
        assertFalse(result.success)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun route_fallsBackWhenChannelHasNoShellAccess() {
        // A pure-API channel (Android) cannot run shells — must fall back.
        val channel = FakeProvider(
            ExecutionProviderType.ANDROID,
            SystemControlResult.fail("no shell access")
        )
        val result = handler.route(channel, ActionType.ADVANCED_ROOT, "echo hi")
        assertFalse(result.success)
        assertTrue(result.message.isNotBlank())
    }


    @Test
    fun route_doesNotOverrideExplicitRuntimeWithDifferentChannel() {
        // A Shizuku-selected channel must NOT run a root action: auto-selection
        // never silently overrides the explicit runtime choice.
        val channel = FakeProvider(
            ExecutionProviderType.SHIZUKU,
            SystemControlResult.ok("shizuku would run this")
        )
        val result = handler.route(channel, ActionType.ADVANCED_ROOT, "echo hi")
        // Falls back to the legacy root runtime, which fails on a JVM without
        // root — proving the mismatched channel was not used.
        assertFalse(result.success)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun route_doesNotOverrideExplicitShizukuWithRootChannel() {
        val channel = FakeProvider(
            ExecutionProviderType.ROOT,
            SystemControlResult.ok("root would run this")
        )
        val result = handler.route(channel, ActionType.ADVANCED_SHIZUKU, "echo hi")
        assertFalse(result.success)
        assertTrue(result.message.isNotBlank())
    }
}
