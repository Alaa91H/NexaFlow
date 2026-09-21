package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Legacy-action → typed package operation mapping contract: the five migrated
 * action types (two force-stop aliases, clear-data, enable, disable) must map
 * to exactly three semantic operations, must resolve the historical
 * `package`/`packageName` config aliases, and must never guess a package name
 * or an enabled flag.
 */
class PackageActionMapperTest {

    private fun action(type: ActionType, config: Map<String, String>) =
        Action(type = type, config = config)

    @Test
    fun forceStopAliasesMapToTheSameOperation() {
        for (type in listOf(ActionType.APPLICATION_CLOSE_APP, ActionType.SYSTEM_FORCE_STOP_APP)) {
            val request = SemanticActionMapper.requestFor(
                action(type, mapOf("package" to "com.example.app")),
                workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
            )
            assertEquals(SemanticOperationId.PACKAGE_FORCE_STOP, request?.operation)
            assertEquals(
                mapOf("packageName" to "com.example.app"),
                request?.parameters
            )
        }
    }

    @Test
    fun packageNameAliasIsResolved() {
        val request = SemanticActionMapper.requestFor(
            action(ActionType.SYSTEM_CLEAR_APP_DATA, mapOf("packageName" to "com.example.app")),
            workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
        )
        assertEquals(SemanticOperationId.PACKAGE_CLEAR_DATA, request?.operation)
        assertEquals("com.example.app", request?.parameters?.get("packageName"))
    }

    @Test
    fun missingPackageNeverMaps() {
        // A blank/absent package keeps the legacy handler reporting in its own
        // terms — the router must not fabricate a request.
        assertNull(
            SemanticActionMapper.requestFor(
                action(ActionType.APPLICATION_CLOSE_APP, emptyMap()),
                workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
            )
        )
        assertNull(
            SemanticActionMapper.requestFor(
                action(ActionType.SYSTEM_FORCE_STOP_APP, mapOf("package" to "  ")),
                workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
            )
        )
    }

    @Test
    fun enableDisableMapToSetEnabledStateWithExplicitFlag() {
        val enable = SemanticActionMapper.requestFor(
            action(ActionType.SYSTEM_ENABLE_APP, mapOf("package" to "com.example.app")),
            workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
        )
        assertEquals(SemanticOperationId.PACKAGE_SET_ENABLED_STATE, enable?.operation)
        assertEquals("true", enable?.parameters?.get("enabled"))

        val disable = SemanticActionMapper.requestFor(
            action(ActionType.SYSTEM_DISABLE_APP, mapOf("package" to "com.example.app")),
            workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
        )
        assertEquals("false", disable?.parameters?.get("enabled"))
    }

    @Test
    fun disableWithUnparseableExplicitFlagIsRejected() {
        assertNull(
            SemanticActionMapper.requestFor(
                action(
                    ActionType.SYSTEM_DISABLE_APP,
                    mapOf("package" to "com.example.app", "enabled" to "maybe")
                ),
                workflowId = "wf", executionId = "run", allowPrivilegedStrategies = true
            )
        )
    }

    @Test
    fun privilegedPolicyGatesThePackageWrites() {
        val gated = SemanticActionMapper.requestFor(
            action(ActionType.APPLICATION_CLOSE_APP, mapOf("package" to "com.example.app")),
            workflowId = "wf", executionId = "run", allowPrivilegedStrategies = false
        )
        assertTrue(gated != null && !gated.allowPrivilegedStrategies)
    }
}
