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
import com.nexaflow.domain.models.Automation
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
    fun `repair plan does not request write settings when Root already satisfies brightness`() {
        val automation = automation(
            actions = listOf(Action(ActionType.SYSTEM_BRIGHTNESS, mapOf("value" to "120")))
        )
        val privilegeSnapshot = privilegeSnapshot(
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

        val plan = WorkflowRequirementCatalog.repairPlan(
            automation = automation,
            capabilitySnapshot = CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot = privilegeSnapshot,
            sdk = 37
        )

        assertTrue(plan.specialPermissions.isEmpty())
        assertTrue(plan.runtimePermissions.isEmpty())
    }

    @Test
    fun `repair plan requests only missing runtime permission when elevation is already ready`() {
        val automation = automation(
            actions = listOf(
                Action(ActionType.SYSTEM_LOCATION, mapOf("enabled" to "true", "configVersion" to "2"))
            )
        )
        val privilegeSnapshot = privilegeSnapshot(
            PrivilegeObservation(
                PrivilegeSurface.RUNTIME_PERMISSION,
                "android.permission.ACCESS_FINE_LOCATION",
                PrivilegeGrantState.NOT_GRANTED,
                "ANDROID_RUNTIME_NOT_GRANTED"
            ),
            PrivilegeObservation(
                PrivilegeSurface.RUNTIME_PERMISSION,
                "android.permission.ACCESS_COARSE_LOCATION",
                PrivilegeGrantState.GRANTED,
                "ANDROID_RUNTIME_GRANTED"
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

        val plan = WorkflowRequirementCatalog.repairPlan(
            automation = automation,
            capabilitySnapshot = CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot = privilegeSnapshot,
            sdk = 37
        )

        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION"),
            plan.runtimePermissions
        )
        assertTrue(plan.specialPermissions.isEmpty())
        assertTrue("action:0:SYSTEM_LOCATION" in plan.blockedOwners)
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
    fun `command spec permissions automatically flow into the shared requirement catalog`() {
        assertEquals(
            listOf("android.permission.CALL_PHONE"),
            WorkflowRequirementCatalog.runtimePermissionsFor(
                ActionType.SYSTEM_DIAL_NUMBER,
                sdk = 37
            )
        )
        assertEquals(
            listOf("android.permission.READ_PHONE_STATE"),
            WorkflowRequirementCatalog.runtimePermissionsFor(
                TriggerType.CALL_STATE,
                sdk = 37
            )
        )
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

    private fun automation(
        actions: List<Action>,
        triggers: List<Trigger> = emptyList()
    ) = Automation(
        id = "requirements",
        name = "Requirements",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = triggers,
        actions = actions,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun privilegeSnapshot(
        vararg observations: PrivilegeObservation
    ) = PrivilegeSnapshot(
        observations = observations.toList(),
        observedAtMs = 1L
    )
}
