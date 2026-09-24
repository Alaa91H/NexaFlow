package com.nexaflow.core.execution.compat

import com.nexaflow.domain.capability.CapabilityAvailability
import com.nexaflow.domain.capability.CapabilityAvailabilityReport
import com.nexaflow.domain.capability.CapabilityId
import com.nexaflow.domain.capability.CapabilitySnapshot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCapabilityValidatorTest {
    @Test
    fun `accepts workflow whose public command requirements are available`() {
        val automation = automation(ActionType.SYSTEM_OPEN_URL)
        val snapshot = CapabilitySnapshot(
            reports = mapOf(
                CapabilityId.INTENT_LAUNCH to CapabilityAvailabilityReport(
                    CapabilityId.INTENT_LAUNCH, CapabilityAvailability.AVAILABLE, emptyList()
                )
            )
        )

        assertTrue(WorkflowCapabilityValidator.validate(automation, snapshot).admissible)
    }

    @Test
    fun `blocks workflow when documented public capability is observed absent`() {
        val result = WorkflowCapabilityValidator.validate(
            automation(ActionType.SYSTEM_OPEN_URL),
            CapabilitySnapshot(observedAtMs = 1L)
        )

        assertFalse(result.admissible)
        assertTrue(CapabilityId.INTENT_LAUNCH in result.missingCapabilities)
    }

    @Test
    fun `admits workflow when first capability scan has not completed yet`() {
        val snapshot = CapabilitySnapshot()

        assertTrue(snapshot.neverObserved)

        val result = WorkflowCapabilityValidator.validate(automation(ActionType.SYSTEM_OPEN_URL), snapshot)

        assertTrue(result.admissible)
        assertTrue(result.missingCapabilities.isEmpty())
    }

    @Test
    fun `admits elevated action while privilege state has not been observed yet`() {
        val result = WorkflowCapabilityValidator.validate(
            automation(ActionType.SYSTEM_REBOOT),
            CapabilitySnapshot(observedAtMs = 1L),
            PrivilegeSnapshot()
        )

        assertTrue(result.admissible)
        assertTrue(result.state == ExecutionRequirementState.UNKNOWN)
    }

    @Test
    fun `blocks elevated action only after both Shizuku and Root are observed unavailable`() {
        val result = WorkflowCapabilityValidator.validate(
            automation(ActionType.SYSTEM_REBOOT),
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU,
                    PrivilegeGrantState.NOT_RUNNING,
                    "SHIZUKU_SERVER_NOT_RUNNING"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.NOT_GRANTED,
                    "ROOT_NOT_GRANTED"
                )
            )
        )

        assertFalse(result.admissible)
        assertTrue(result.missingPrivileges.isNotEmpty())
        assertTrue("action:0:SYSTEM_REBOOT" in result.blockedOwners)
        assertTrue("trigger:0:TIME" in result.unknownOwners)
    }

    @Test
    fun `reports an unavailable exit action as the exact blocked workflow node`() {
        val base = automation(ActionType.SYSTEM_SEND_NOTIFICATION)
        val withExit = base.copy(
            triggers = emptyList(),
            exitActions = listOf(Action(ActionType.SYSTEM_REBOOT, emptyMap()))
        )
        val result = WorkflowCapabilityValidator.validate(
            withExit,
            CapabilitySnapshot(observedAtMs = 1L),
            privilegeSnapshot(
                PrivilegeObservation(
                    PrivilegeSurface.SHIZUKU,
                    PrivilegeSnapshot.ENV_SHIZUKU,
                    PrivilegeGrantState.NOT_RUNNING,
                    "SHIZUKU_SERVER_NOT_RUNNING"
                ),
                PrivilegeObservation(
                    PrivilegeSurface.ROOT,
                    PrivilegeSnapshot.ENV_ROOT,
                    PrivilegeGrantState.NOT_GRANTED,
                    "ROOT_NOT_GRANTED"
                )
            )
        )

        assertFalse(result.admissible)
        assertTrue("exitAction:0:SYSTEM_REBOOT" in result.blockedOwners)
    }

    @Test
    fun `root satisfies hybrid write-settings action without Android special access`() {
        val result = WorkflowCapabilityValidator.validate(
            automation(ActionType.SYSTEM_BRIGHTNESS),
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

        assertTrue(result.admissible)
        assertTrue(result.missingPrivileges.isEmpty())
    }

    private fun privilegeSnapshot(
        vararg observations: PrivilegeObservation
    ) = PrivilegeSnapshot(
        observations = observations.toList(),
        observedAtMs = 1L
    )

    private fun automation(action: ActionType) = Automation(
        id = "validator",
        name = "Validator",
        description = "Test workflow",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = listOf(Trigger(TriggerType.TIME, emptyMap())),
        actions = listOf(Action(action, emptyMap())),
        createdAt = 1L,
        updatedAt = 1L
    )
}
