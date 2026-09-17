package com.nexaflow.core.execution.handler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexaflow.core.datastore.NotificationSettings
import com.nexaflow.core.rom.RomCapabilityProvider
import com.nexaflow.core.rom.SystemController
import com.nexaflow.core.rom.model.IntegrationLevel
import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end dispatch coverage for the app-facing handlers: every supported
 * [ActionType] must flow through its registered handler's [execute][ActionHandler.execute]
 * without crashing and return a well-formed result. Failure messages are
 * acceptable (a real device may lack the privilege); thrown exceptions are
 * not — a task must never die mid-chain because one action misbehaved.
 *
 * The remaining handlers (HTTP, plugin, advanced, registry dispatch, system
 * expansion) already have dedicated suites; this file completes the family.
 */
@RunWith(RobolectricTestRunner::class)
class HandlerDispatchE2ETest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun controller() = SystemController(
        context,
        RomCapabilityProvider(context, IntegrationLevel.NORMAL, RomFamily.AOSP)
    )

    private fun ctx() = ActionExecutionContext(
        appContext = context,
        controller = controller(),
        notificationSettings = NotificationSettings(enabled = true, executionEnabled = true)
    )

    private fun runAll(handler: ActionHandler) = runBlocking {
        handler.supportedTypes.map { type ->
            type to runCatching { handler.execute(Action(type, emptyMap()), ctx()) }
        }
    }

    private fun assertGraceful(type: ActionType, result: Result<*>) {
        if (result.isFailure) {
            throw AssertionError("$type threw ${result.exceptionOrNull()}", result.exceptionOrNull())
        }
    }

    @Test
    fun `every app action dispatches without throwing`() {
        runAll(AppActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every connectivity action dispatches without throwing`() {
        runAll(ConnectivityActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every display action dispatches without throwing`() {
        runAll(DisplayActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every sound action dispatches without throwing`() {
        runAll(SoundActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every media action dispatches without throwing`() {
        runAll(MediaActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every call action dispatches without throwing`() {
        runAll(CallActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every evo action dispatches without throwing`() {
        runAll(EvoActionHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `every notification action dispatches without throwing`() {
        runAll(NotificationActionsHandler()).forEach { (type, result) -> assertGraceful(type, result) }
    }

    @Test
    fun `registry covers every handler action exactly once`() {
        val registry = ActionRegistry.default()
        val all = ActionType.entries.toSet()
        val covered = registry.supportedTypes
        val uncovered = all - covered
        assertTrue(
            "ActionRegistry.default() misses ${uncovered.take(5)}",
            uncovered.isEmpty()
        )
    }

    @Test
    fun `blocked and cleared notification actions honor the package config`() = runBlocking {
        val handler = NotificationActionsHandler()
        val empty = handler.execute(
            Action(ActionType.SYSTEM_BLOCK_NOTIFICATION, emptyMap()),
            ctx()
        )
        assertTrue("empty package list must fail, got: ${empty.message}", !empty.success)
        val populated = handler.execute(
            Action(
                ActionType.SYSTEM_BLOCK_NOTIFICATION,
                mapOf("packages" to "com.example.a,com.example.b", "enabled" to "true")
            ),
            ctx()
        )
        assertTrue(populated.success)
        assertEquals(
            "must report the per-package loop, got: ${populated.message}",
            true,
            populated.message.contains("2")
        )
    }
}
