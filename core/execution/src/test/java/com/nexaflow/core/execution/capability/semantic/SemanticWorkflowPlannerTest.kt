package com.nexaflow.core.execution.capability.semantic

import com.nexaflow.core.rom.model.RomFamily
import com.nexaflow.domain.capability.operation.SemanticOperationId
import com.nexaflow.domain.capability.operation.StrategyId
import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticWorkflowPlannerTest {

    private class FakeStrategy(
        override val id: StrategyId,
        override val supportedOperations: Set<SemanticOperationId>,
        private val available: Boolean = true
    ) : CapabilityStrategy {
        var executions = 0
            private set

        override suspend fun availability(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): StrategyAvailability = StrategyAvailability(
            available = available,
            reason = if (available) null else "unavailable"
        )

        override suspend fun execute(
            request: TypedOperationRequest,
            operation: SemanticOperationId
        ): OperationOutcome {
            executions++
            return OperationOutcome(
                operation = operation,
                status = OperationOutcomeStatus.SUCCESS,
                strategy = id,
                message = "executed",
                metadata = mapOf("requestedEnabled" to "true")
            )
        }
    }

    private val fingerprint = DeviceFingerprint(
        manufacturer = "test",
        model = "test",
        device = "test",
        androidApi = 37,
        securityPatch = "2026-09-01",
        romFamily = RomFamily.AOSP
    )

    private fun planner(
        strategies: List<CapabilityStrategy>,
        privileged: Boolean
    ): SemanticWorkflowPlanner {
        val router = CapabilityRouter(
            registry = OperationRegistry.default(),
            strategies = strategies,
            evidenceStore = CapabilityEvidenceStore(),
            healthTracker = StrategyHealthTracker(),
            fingerprint = fingerprint
        )
        return SemanticWorkflowPlanner(
            SemanticActionRouter(
                router = router,
                privilegedPolicyEnabled = { privileged }
            )
        )
    }

    @Test
    fun \`settings-only route blocks automatic workflow without side effects\`() = runTest {
        val settings = FakeStrategy(
            StrategyId.SETTINGS_USER_ACTION,
            setOf(SemanticOperationId.WIFI_SET_STATE)
        )
        val plan = planner(listOf(settings), privileged = false)
            .plan(automation(actions = listOf(toggle(ActionType.SYSTEM_WIFI))))

        assertFalse(plan.executable)
        assertEquals(setOf("action:0:SYSTEM_WIFI"), plan.pendingUserActionOwners)
        assertEquals(0, settings.executions)
    }

    @Test
    fun \`verified privileged route makes automatic workflow executable\`() = runTest {
        val root = FakeStrategy(
            StrategyId.ROOT_SHELL,
            setOf(SemanticOperationId.WIFI_SET_STATE)
        )
        val plan = planner(listOf(root), privileged = true)
            .plan(automation(actions = listOf(toggle(ActionType.SYSTEM_WIFI))))

        assertTrue(plan.executable)
        assertEquals(
            StrategyId.ROOT_SHELL,
            plan.nodes.single().plan.selectedStrategy
        )
        assertEquals(0, root.executions)
    }

    @Test
    fun \`planner includes configured end values and explicit exit actions\`() = runTest {
        val root = FakeStrategy(
            StrategyId.ROOT_SHELL,
            setOf(
                SemanticOperationId.WIFI_SET_STATE,
                SemanticOperationId.NFC_SET_STATE
            )
        )
        val main = toggle(ActionType.SYSTEM_WIFI).copy(
            endBehavior = EndBehavior(
                mode = EndMode.SET_VALUE,
                config = mapOf("enabled" to "false", "configVersion" to "2")
            )
        )
        val plan = planner(listOf(root), privileged = true).plan(
            automation(
                actions = listOf(main),
                exitActions = listOf(toggle(ActionType.SYSTEM_NFC))
            )
        )

        assertTrue(plan.executable)
        assertEquals(
            listOf(
                "action:0:SYSTEM_WIFI",
                "endBehavior:0:SYSTEM_WIFI",
                "exitAction:0:SYSTEM_NFC"
            ),
            plan.nodes.map { it.owner }
        )
    }

    private fun toggle(type: ActionType) = Action(
        type,
        mapOf("enabled" to "true", "configVersion" to "2")
    )

    private fun automation(
        actions: List<Action>,
        exitActions: List<Action> = emptyList()
    ) = Automation(
        id = "semantic-plan",
        name = "Semantic plan",
        description = "",
        icon = "bolt",
        iconColor = 0L,
        backgroundColor = 0L,
        category = "test",
        priority = 1,
        enabled = true,
        triggers = emptyList(),
        actions = actions,
        exitActions = exitActions,
        createdAt = 1L,
        updatedAt = 1L
    )
}
