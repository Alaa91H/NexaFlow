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
 * Package-operation contract tests for both privileged typed strategies: every
 * dispatch must be a closed [PrivilegedOperation] (force-stop / clear-data /
 * pm enable / pm disable-user / pm list packages -d), a missing package must
 * fail with INVALID_CONFIGURATION before any transport call, and an uncertain
 * package dispatch must surface as UNKNOWN so the router reconciles through
 * the bounded read instead of re-executing.
 */
class PackageOperationsStrategyTest {

    private class RecordingSink {
        var lastOperation: PrivilegedOperation? = null
        var nextResult: SystemControlResult = SystemControlResult.ok("ok")
        fun run(operation: PrivilegedOperation): SystemControlResult {
            lastOperation = operation
            return nextResult
        }
    }

    private fun shizukuStrategy(sink: RecordingSink) = ShizukuTypedStrategy(
        shizukuGranted = { true },
        userServiceReady = { true },
        execute = sink::run
    )

    private fun rootStrategy(sink: RecordingSink) = RootTypedStrategy(
        rootAvailable = { true },
        execute = sink::run
    )

    private fun request(
        operation: SemanticOperationId,
        pkg: String? = "com.example.app",
        enabled: Boolean? = null
    ): TypedOperationRequest {
        val parameters = buildMap {
            pkg?.let { put("packageName", it) }
            enabled?.let { put("enabled", it.toString()) }
        }
        return TypedOperationRequest(operation = operation, parameters = parameters)
    }

    @Test
    fun shizukuForceStopDispatchesClosedAmForceStop() = runTest {
        val sink = RecordingSink()
        val outcome = shizukuStrategy(sink).execute(request(SemanticOperationId.PACKAGE_FORCE_STOP), SemanticOperationId.PACKAGE_FORCE_STOP)
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(
            listOf("am", "force-stop", "com.example.app"),
            sink.lastOperation?.argv()
        )
        assertTrue(outcome.metadata.isEmpty())
    }

    @Test
    fun shizukuClearDataDispatchesPmClear() = runTest {
        val sink = RecordingSink()
        shizukuStrategy(sink).execute(request(SemanticOperationId.PACKAGE_CLEAR_DATA), SemanticOperationId.PACKAGE_CLEAR_DATA)
        assertEquals(
            listOf("pm", "clear", "com.example.app"),
            sink.lastOperation?.argv()
        )
    }

    @Test
    fun shizukuEnableAndDisableUseTheReviewedArgvShapes() = runTest {
        val sink = RecordingSink()
        val strategy = shizukuStrategy(sink)
        strategy.execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = true), SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        assertEquals(
            listOf("pm", "enable", "com.example.app"),
            sink.lastOperation?.argv()
        )
        assertTrue(sink.lastOperation is PrivilegedOperation.SetPackageEnabled)
        strategy.execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = false), SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        assertEquals(
            listOf("pm", "disable-user", "--user", "0", "com.example.app"),
            sink.lastOperation?.argv()
        )
    }

    @Test
    fun missingPackageFailsBeforeAnyTransportCall() = runTest {
        for (strategy in listOf(shizukuStrategy(RecordingSink()), rootStrategy(RecordingSink()))) {
            val sink = RecordingSink()
            val noPackage = if (strategy is ShizukuTypedStrategy) {
                strategy.execute(request(SemanticOperationId.PACKAGE_FORCE_STOP, pkg = null), SemanticOperationId.PACKAGE_FORCE_STOP)
            } else {
                rootStrategy(sink).execute(request(SemanticOperationId.PACKAGE_FORCE_STOP, pkg = null), SemanticOperationId.PACKAGE_FORCE_STOP)
            }
            assertEquals(
                com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
                noPackage.errorCode
            )
            assertNull("No transport dispatch may happen for an invalid request", sink.lastOperation)
        }
    }

    @Test
    fun setEnabledWithoutBooleanFailsAsInvalidConfiguration() = runTest {
        val sink = RecordingSink()
        val strategy = shizukuStrategy(sink)
        val outcome = strategy.execute(
            TypedOperationRequest(
                operation = SemanticOperationId.PACKAGE_SET_ENABLED_STATE,
                parameters = mapOf("packageName" to "com.example.app")
            ),
            SemanticOperationId.PACKAGE_SET_ENABLED_STATE
        )
        assertEquals(
            com.nexaflow.domain.capability.CapabilityErrorCode.INVALID_CONFIGURATION,
            outcome.errorCode
        )
        assertNull(sink.lastOperation)
    }

    @Test
    fun uncertainPackageDispatchSurfacesUnknownForReconciliation() = runTest {
        val sink = RecordingSink()
        sink.nextResult = SystemControlResult.fail("Shizuku UserService failed: binder died")
        val outcome = shizukuStrategy(sink).execute(request(SemanticOperationId.PACKAGE_FORCE_STOP), SemanticOperationId.PACKAGE_FORCE_STOP)
        assertEquals(OperationOutcomeStatus.UNKNOWN, outcome.status)
        assertFalse(outcome.transportFailure)
    }

    @Test
    fun readBackDistinguishesEnabledDisabledAndUnexpectedShapes() = runTest {
        val sink = RecordingSink()
        val strategy = shizukuStrategy(sink)

        // Disabled package: the bounded probe prints exactly one line.
        sink.nextResult = SystemControlResult.ok("package:com.example.app")
        assertEquals(
            false,
            strategy.readState(request(SemanticOperationId.PACKAGE_GET_ENABLED_STATE), SemanticOperationId.PACKAGE_GET_ENABLED_STATE)
        )
        assertEquals(
            listOf("pm", "list", "packages", "-d", "com.example.app"),
            sink.lastOperation?.argv()
        )

        // Enabled package: empty output (not in the disabled list).
        sink.nextResult = SystemControlResult.ok("")
        assertEquals(
            true,
            strategy.readState(request(SemanticOperationId.PACKAGE_GET_ENABLED_STATE), SemanticOperationId.PACKAGE_GET_ENABLED_STATE)
        )

        // Unexpected shape: honest null, never a guess.
        sink.nextResult = SystemControlResult.ok("something unexpected")
        assertNull(
            strategy.readState(request(SemanticOperationId.PACKAGE_GET_ENABLED_STATE), SemanticOperationId.PACKAGE_GET_ENABLED_STATE)
        )
    }

    @Test
    fun rootStrategyServesAllPrivilegedStateWritesWithClosedOperations() = runTest {
        val sink = RecordingSink()
        val strategy = rootStrategy(sink)

        strategy.execute(
            request(SemanticOperationId.MOBILE_DATA_SET_STATE, enabled = true),
            SemanticOperationId.MOBILE_DATA_SET_STATE
        )
        assertEquals(
            listOf("svc", "data", "enable"),
            sink.lastOperation?.argv()
        )

        strategy.execute(
            request(SemanticOperationId.LOCATION_SET_STATE, enabled = false),
            SemanticOperationId.LOCATION_SET_STATE
        )
        assertEquals(
            listOf("cmd", "location", "set-location-enabled", "false"),
            sink.lastOperation?.argv()
        )

        strategy.execute(
            request(SemanticOperationId.DATA_SAVER_SET_STATE, enabled = true),
            SemanticOperationId.DATA_SAVER_SET_STATE
        )
        assertEquals(
            listOf("cmd", "netpolicy", "set", "restrict-background", "true"),
            sink.lastOperation?.argv()
        )

        strategy.execute(
            request(SemanticOperationId.DND_SET_STATE, enabled = true),
            SemanticOperationId.DND_SET_STATE
        )
        assertEquals(
            listOf("settings", "put", "global", "zen_mode", "2"),
            sink.lastOperation?.argv()
        )
    }

    @Test
    fun rootValueWriteReadBackUsesBoundedSettingsQueries() = runTest {
        val sink = RecordingSink()
        val strategy = rootStrategy(sink)

        sink.nextResult = SystemControlResult.ok("123")
        assertEquals(
            "123",
            strategy.readStateValue(
                TypedOperationRequest(SemanticOperationId.BRIGHTNESS_GET),
                SemanticOperationId.BRIGHTNESS_GET
            )
        )
        assertEquals(
            listOf("settings", "get", "system", "screen_brightness"),
            sink.lastOperation?.argv()
        )

        sink.nextResult = SystemControlResult.ok("30000")
        assertEquals(
            "30",
            strategy.readStateValue(
                TypedOperationRequest(SemanticOperationId.SCREEN_TIMEOUT_GET),
                SemanticOperationId.SCREEN_TIMEOUT_GET
            )
        )
        assertEquals(
            listOf("settings", "get", "system", "screen_off_timeout"),
            sink.lastOperation?.argv()
        )
    }

    @Test
    fun rootStrategyAlsoServesPackageOperations() = runTest {
        val sink = RecordingSink()
        val outcome = rootStrategy(sink).execute(request(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enabled = false), SemanticOperationId.PACKAGE_SET_ENABLED_STATE)
        assertEquals(OperationOutcomeStatus.SUCCESS, outcome.status)
        assertEquals(
            listOf("pm", "disable-user", "--user", "0", "com.example.app"),
            sink.lastOperation?.argv()
        )
    }
}
