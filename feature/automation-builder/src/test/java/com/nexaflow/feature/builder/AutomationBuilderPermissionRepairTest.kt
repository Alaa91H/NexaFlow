package com.nexaflow.feature.builder

import com.nexaflow.core.execution.capability.semantic.OperationExecutionPlan
import com.nexaflow.core.execution.capability.semantic.OperationPlanStatus
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowExecutionPlan
import com.nexaflow.core.execution.capability.semantic.SemanticWorkflowNodePlan
import com.nexaflow.core.execution.capability.semantic.StrategyPlanCandidate
import com.nexaflow.core.execution.compat.WorkflowPermissionRepairPlan
import com.nexaflow.core.execution.compat.WorkflowSpecialPermission
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.domain.models.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationBuilderPermissionRepairTest {

    @Test
    fun `semantic privileged alternative adds elevated repair for otherwise unowned blocker`() {
        val merged = mergeSemanticRepairPlan(
            requirementPlan = WorkflowPermissionRepairPlan(),
            semanticPlan = semanticBlock("action:0:SYSTEM_WIFI", ActionType.SYSTEM_WIFI)
        )

        assertEquals(
            listOf(WorkflowSpecialPermission.ELEVATED),
            merged.specialPermissions
        )
        assertTrue("action:0:SYSTEM_WIFI" in merged.blockedOwners)
    }

    @Test
    fun `direct requirement repair wins over redundant elevated prompt`() {
        val direct = WorkflowPermissionRepairPlan(
            specialPermissions = listOf(WorkflowSpecialPermission.WRITE_SETTINGS),
            blockedOwners = setOf("action:0:SYSTEM_BRIGHTNESS")
        )

        val merged = mergeSemanticRepairPlan(
            requirementPlan = direct,
            semanticPlan = semanticBlock(
                "action:0:SYSTEM_BRIGHTNESS",
                ActionType.SYSTEM_BRIGHTNESS,
                SemanticOperationId.BRIGHTNESS_SET
            )
        )

        assertEquals(listOf(WorkflowSpecialPermission.WRITE_SETTINGS), merged.specialPermissions)
        assertFalse(WorkflowSpecialPermission.ELEVATED in merged.specialPermissions)
        assertEquals(direct.blockedOwners, merged.blockedOwners)
    }

    private fun semanticBlock(
        owner: String,
        actionType: ActionType,
        operation: SemanticOperationId = SemanticOperationId.WIFI_SET_STATE
    ) = SemanticWorkflowExecutionPlan(
        nodes = listOf(
            SemanticWorkflowNodePlan(
                owner = owner,
                actionType = actionType,
                plan = OperationExecutionPlan(
                    operation = operation,
                    status = OperationPlanStatus.PENDING_USER_ACTION,
                    selectedStrategy = StrategyId.SETTINGS_USER_ACTION,
                    candidates = listOf(
                        candidate(StrategyId.SETTINGS_USER_ACTION, available = true),
                        candidate(StrategyId.SHIZUKU_USER_SERVICE, available = false),
                        candidate(StrategyId.ROOT_SHELL, available = false)
                    ),
                    message = "Automatic execution requires a grant"
                )
            )
        )
    )

    private fun candidate(
        strategy: StrategyId,
        available: Boolean
    ) = StrategyPlanCandidate(
        strategy = strategy,
        available = available,
        selected = strategy == StrategyId.SETTINGS_USER_ACTION,
        interactive = strategy == StrategyId.SETTINGS_USER_ACTION,
        permissionRequired = !available,
        confidence = if (available) 50 else 0,
        reason = if (available) "available" else "unavailable",
        evidenceScore = 0L,
        privilegeCost = 0
    )
}
