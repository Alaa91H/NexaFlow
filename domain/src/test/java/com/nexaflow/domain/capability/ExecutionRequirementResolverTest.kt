package com.nexaflow.domain.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRequirementResolverTest {

    @Test
    fun `all of requires both capability and permission`() {
        val requirement = ExecutionRequirement.AllOf(
            listOf(
                ExecutionRequirement.Capability(CapabilityId.INTENT_LAUNCH),
                ExecutionRequirement.AndroidPermission("android.permission.CAMERA")
            )
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            capabilitySnapshot(CapabilityId.INTENT_LAUNCH, CapabilityAvailability.AVAILABLE),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.RUNTIME_PERMISSION,
                    "android.permission.CAMERA",
                    PrivilegeGrantState.NOT_GRANTED,
                    "ANDROID_RUNTIME_NOT_GRANTED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.BLOCKED, resolution.state)
        assertTrue(
            PrivilegeRequirementRef(
                PrivilegeSurface.ANDROID_PERMISSION,
                "android.permission.CAMERA"
            ) in resolution.missingPrivileges
        )
    }

    @Test
    fun `any of accepts Shizuku when Root is unavailable`() {
        val requirement = ExecutionRequirement.AnyOf(
            listOf(
                ExecutionRequirement.Authority(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU
                ),
                ExecutionRequirement.Authority(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT
                )
            )
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
        assertTrue(resolution.missingPrivileges.isEmpty())
    }

    @Test
    fun `any of stays unknown while an unobserved alternative could still satisfy it`() {
        val requirement = ExecutionRequirement.AnyOf(
            listOf(
                ExecutionRequirement.Authority(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU
                ),
                ExecutionRequirement.Authority(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT
                )
            )
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.NOT_GRANTED,
                    "ROOT_NOT_GRANTED"
                )
            )
        )

        assertEquals(ExecutionRequirementState.UNKNOWN, resolution.state)
        assertTrue(
            PrivilegeRequirementRef(
                PrivilegeSurface.SHIZUKU,
                PrivilegeSnapshot.ENV_SHIZUKU
            ) in resolution.unknownPrivileges
        )
    }

    @Test
    fun `never observed snapshots never create a false blocker`() {
        val requirement = ExecutionRequirement.AllOf(
            listOf(
                ExecutionRequirement.Capability(CapabilityId.INTENT_LAUNCH),
                ExecutionRequirement.SpecialAccess(PrivilegeSnapshot.SPECIAL_WRITE_SETTINGS)
            )
        )
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(),
            PrivilegeSnapshot()
        )

        assertEquals(ExecutionRequirementState.UNKNOWN, resolution.state)
        assertTrue(resolution.missingCapabilities.isEmpty())
        assertTrue(resolution.missingPrivileges.isEmpty())
    }

    @Test
    fun `app op partial state is not promoted to ready`() {
        val op = "android:write_settings"
        val requirement = ExecutionRequirement.AppOp(op)
        val resolution = ExecutionRequirementResolver.resolve(
            requirement,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.APP_OP,
                    op,
                    PrivilegeGrantState.PARTIAL,
                    "APP_OP_FOREGROUND_ONLY"
                )
            )
        )

        assertEquals(ExecutionRequirementState.BLOCKED, resolution.state)
    }

    private fun capabilitySnapshot(
        id: CapabilityId,
        availability: CapabilityAvailability
    ) = CapabilitySnapshot(
        reports = mapOf(
            id to CapabilityAvailabilityReport(
                capability = id,
                availability = availability,
                backends = emptyList()
            )
        ),
        observedAtMs = 1L
    )

    private fun privilegeSnapshot(
        vararg observations: PrivilegeObservation
    ) = PrivilegeSnapshot(
        observations = observations.toList(),
        observedAtMs = 1L
    )
}
