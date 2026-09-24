package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilitySnapshot
import com.nexaflow.domain.capability.ExecutionRequirementResolver
import com.nexaflow.domain.capability.ExecutionRequirementState
import com.nexaflow.domain.capability.PrivilegeGrantState
import com.nexaflow.domain.capability.PrivilegeObservation
import com.nexaflow.domain.capability.PrivilegeSnapshot
import com.nexaflow.domain.capability.PrivilegeSurface
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowRequirementCatalogTest {

    @Test
    fun `legacy elevated command accepts either Shizuku or Root`() {
        val requirement = WorkflowRequirementCatalog.requirementFor(
            Action(ActionType.SYSTEM_REBOOT, emptyMap()),
            sdk = 37
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU,
                    PrivilegeGrantState.GRANTED,
                    "SHIZUKU_USER_SERVICE_READY"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.NOT_GRANTED,
                    "ROOT_NOT_GRANTED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.READY, resolution.state)
    }

    @Test
    fun `advanced root remains pinned to Root and does not accept Shizuku`() {
        val requirement = WorkflowRequirementCatalog.requirementFor(
            Action(ActionType.ADVANCED_ROOT, emptyMap()),
            sdk = 37
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU,
                    PrivilegeGrantState.GRANTED,
                    "SHIZUKU_USER_SERVICE_READY"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.NOT_GRANTED,
                    "ROOT_NOT_GRANTED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.BLOCKED, resolution.state)
        assertTrue(
            resolution.missingPrivileges.any {
                it.surface == PrivilegeSurface.ROOT
            }
        )
    }

    @Test
    fun `brightness can use Root when write settings special access is absent`() {
        val requirement = WorkflowRequirementCatalog.requirementFor(
            Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "120")),
            sdk = 37
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SPECIAL_ACCESS,
                    PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS,
                    PrivilegeGrantState.NOT_GRANTED,
                    "SPECIAL_ACCESS_NOT_GRANTED"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU,
                    PrivilegeGrantState.NOT_RUNNING,
                    "SHIZUKU_SERVER_NOT_RUNNING"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.GRANTED,
                    "ROOT_UID_ZERO_VERIFIED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.READY, resolution.state)
    }

    @Test
    fun `time trigger requires exact alarm special access`() {
        val requirement = WorkflowRequirementCatalog.requirementFor(
            Trigger(TriggerType.TIME, mapOf("time" to "08:00")),
            sdk = 37
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SPECIAL_ACCESS,
                    PrivilegeSnapshot.SPECIAL_EXACT_ALARM,
                    PrivilegeGrantState.NOT_GRANTED,
                    "SPECIAL_ACCESS_NOT_GRANTED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.BLOCKED, resolution.state)
    }

    @Test
    fun `private network HTTP permission is config and API aware`() {
        val action = Action(
            ActionType.SYSTEM_HTTP_REQUEST,
            mapOf(
                "url" to "https://192.168.1.10",
                "allowPrivateNetwork" to "true"
            )
        )

        assertEquals(
            listOf("android.permission.ACCESS_LOCAL_NETWORK"),
            WorkflowRequirementCatalog.runtimePermissionsFor(action, sdk = 37)
        )
        assertTrue(
            WorkflowRequirementCatalog.runtimePermissionsFor(action, sdk = 36).isEmpty()
        )
    }

    private fun privilegeSnapshot(
        vararg observations: PrivilegeObservation
    ) = PrivilegeSnapshot(
        observations = observations.toList(),
        observedAtMs = 1L
    )
}
