package com.nexaflow.core.execution.capability.semantic.strategies

import com.nexaflow.core.rom.PrivilegedOperation
import com.nexaflow.core.rom.model.SystemControlResult
import com.nexaflow.core.execution.capability.semantic.OperationOutcomeStatus
import com.nexaflow.core.execution.capability.semantic.TypedOperationRequest
import com.nexaflow.domain.capability.operation.SemanticOperationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the Shizuku typed strategy (Phase B): every execution
 * must be a closed [PrivilegedOperation] (workflow input can never become a
 * shell expression), permission-granted-but-unbound must NOT be executable,
 * and a failed radio toggle after a possible side effect must surface as
 * UNKNOWN so the router reconciles instead of re-executing.
 */
class ShizukuTypedStrategyTest {

    private class RecordingSink {
        var lastOperation: PrivilegedOperation? = null
        var nextResult: SystemControlResult = SystemControlResult.ok("ok")
        fun run(operation: PrivilegedOperation): SystemControlResult {
            lastOperation = operation
            return nextResult
        }
    }

    private fun strategy(
        granted: Boolean,
        bound: Boolean,
        sink: RecordingSink
    ): ShizukuTypedStrategy {
        @Suppress("UNUSED_PARAMETER")
        val strategy = ShizukuTypedStrategy(
            packageName = "com.nexaflow.app",
            shizukuGranted = { granted },
            userServiceReady = { bound },
            execute = sink::run
        )
        return strategy
    }

    private fun request(operation: SemanticOperationId, enabled: Boolean) =
        TypedOperationRequest(operation = operation, parameters = mapOf("enabled" to enabled.toString()))

    @Test
    fun grantedButUnboundServiceIsNotExecutable() = runTest {
        // Permission alone is never readiness: the typed AIDL endpoint must be
        // live. This is the GRANTED_NOT_BOUND state from the lifecycle contract.
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = false, sink = sink)
        val availability = strategy.availability(request(SemanticOperationId.WIFI_SET_STATE, true), SemanticOperationId.WIFI_SET_STATE)
        assertFalse("GRANTED_NOT_BOUND must not be executable", availability.available)
        assertTrue(availability.permissionRequired)
    }

    @Test
    fun boundAndGrantedStrategiesExecutesClosedWriteOperation() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        val outcome = strategy.execute(request(SemanticOperationId.WIFI_SET_STATE, true), SemanticOperationId.WIFI_SET_STATE)
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        // The write must be a closed allowlisted setting write, never a shell string.
        val operation = sink.lastOperation
        assertTrue(operation is PrivilegedOperation.WriteSetting)
        assertEquals("wifi_on", (operation as PrivilegedOperation.WriteSetting).key)
        assertEquals("1", operation.value)
        assertEquals(listOf("settings", "put", "global", "wifi_on", "1"), operation.argv())
        assertEquals("true", outcome.metadata["requestedEnabled"])
    }

    @Test
    fun radioToggleFailureWithPossibleSideEffectIsUnknownNotFailed() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        sink.nextResult = SystemControlResult.fail("Shizuku UserService failed: binder died")
        val outcome = strategy.execute(request(SemanticOperationId.BLUETOOTH_SET_STATE, true), SemanticOperationId.BLUETOOTH_SET_STATE)
        // The side effect may have landed before the transport dropped: UNKNOWN
        // forces the router to reconcile by reading state, never re-execute.
        assertEquals(OperationOutcomeStatus.UNKNOWN, outcome.status)
        assertFalse(outcome.transportFailure)
    }

    @Test
    fun transportFailureWithoutSideEffectIsSafeFallback() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        sink.nextResult = SystemControlResult.fail("Shizuku is not granted. Open the Shizuku app and grant NexaFlow")
        val outcome = strategy.execute(request(SemanticOperationId.DND_SET_STATE, true), SemanticOperationId.DND_SET_STATE)
        assertEquals(OperationOutcomeStatus.FAILED, outcome.status)
        assertTrue("Definite transport unavailability must allow router fallback", outcome.transportFailure)
    }

    @Test
    fun readBackUsesBoundedSettingRead() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        sink.nextResult = SystemControlResult.ok("1")
        assertEquals(true, strategy.readState(request(SemanticOperationId.WIFI_GET_STATE, true), SemanticOperationId.WIFI_GET_STATE))
        assertEquals(
            listOf("settings", "get", "global", "wifi_on"),
            sink.lastOperation?.argv()
        )
        sink.nextResult = SystemControlResult.ok("null")
        assertNull(strategy.readState(request(SemanticOperationId.WIFI_GET_STATE, true), SemanticOperationId.WIFI_GET_STATE))
        // Hotspot has no reliable single read: honest null, never a guess.
        sink.nextResult = SystemControlResult.ok("1")
        assertNull(strategy.readState(request(SemanticOperationId.HOTSPOT_GET_STATE, true), SemanticOperationId.HOTSPOT_GET_STATE))
    }

    @Test
    fun dndDisableWritesZenModeAndDndEnableGrantsPolicyAccessOnly() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        sink.nextResult = SystemControlResult.ok("Operation executed")
        strategy.execute(request(SemanticOperationId.DND_SET_STATE, false), SemanticOperationId.DND_SET_STATE)
        assertTrue(sink.lastOperation is PrivilegedOperation.WriteSetting)
        assertEquals("zen_mode", (sink.lastOperation as PrivilegedOperation.WriteSetting).key)
        strategy.execute(request(SemanticOperationId.DND_SET_STATE, true), SemanticOperationId.DND_SET_STATE)
        // Enabling DND needs only notification-policy access for the app; it
        // must not write the user's interruption filter directly.
        assertTrue(sink.lastOperation is PrivilegedOperation.GrantNotificationPolicyAccess)
        assertEquals(
            "com.nexaflow.app",
            (sink.lastOperation as PrivilegedOperation.GrantNotificationPolicyAccess).packageName
        )
    }

    @Test
    fun hotspotUsesClosedWifiShellCommand() = runTest {
        val sink = RecordingSink()
        val strategy = strategy(granted = true, bound = true, sink = sink)
        strategy.execute(request(SemanticOperationId.HOTSPOT_SET_STATE, true), SemanticOperationId.HOTSPOT_SET_STATE)
        assertEquals(
            listOf("cmd", "wifi", "start-softap"),
            sink.lastOperation?.argv()
        )
    }

}
