package com.nexaflow.domain.canonical

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CanonicalSelectionSemanticsTest {

    private val json = Json {
        classDiscriminator = "_type"
        encodeDefaults = true
    }

    @Test
    fun singleSelectionRequiresExactlyOneActionAndSingleExecution() {
        val action = SetStateNode(
            id = CanonicalNodeId("wifi.on"),
            target = TargetId("core.connectivity.wifi"),
            state = BooleanValue(true)
        )
        val node = ActionSelectionNode(
            id = CanonicalNodeId("selection.single"),
            actions = listOf(action),
            semantics = ActionSelectionSemantics.SINGLE
        )

        assertEquals(TargetSelectionMode.SINGLE, node.semantics.targetSelectionMode)
        assertEquals(ExecutionMode.SINGLE, node.semantics.executionMode)
        assertEquals(FailurePolicy.FAIL_FAST, node.semantics.failurePolicy)

        expectIllegalArgument {
            ActionSelectionNode(
                id = CanonicalNodeId("selection.invalid.empty"),
                actions = emptyList(),
                semantics = ActionSelectionSemantics.SINGLE
            )
        }
    }

    @Test
    fun multiSelectionRequiresBatchOrOrderedExecution() {
        expectIllegalArgument {
            ActionSelectionSemantics(
                targetSelectionMode = TargetSelectionMode.MULTI,
                executionMode = ExecutionMode.SINGLE
            )
        }

        expectIllegalArgument {
            ActionSelectionSemantics(
                targetSelectionMode = TargetSelectionMode.SINGLE,
                executionMode = ExecutionMode.BATCH
            )
        }

        expectIllegalArgument {
            ActionSelectionSemantics(
                targetSelectionMode = TargetSelectionMode.SINGLE,
                executionMode = ExecutionMode.SINGLE,
                failurePolicy = FailurePolicy.CONTINUE_ON_ERROR
            )
        }
    }

    @Test
    fun batchAndOrderedSelectionsKeepDistinctExecutionSemantics() {
        val actions = listOf(
            SetStateNode(
                id = CanonicalNodeId("wifi.enable"),
                target = TargetId("core.connectivity.wifi"),
                state = BooleanValue(true)
            ),
            SetStateNode(
                id = CanonicalNodeId("bluetooth.enable"),
                target = TargetId("core.connectivity.bluetooth"),
                state = BooleanValue(true)
            )
        )

        val batch = ActionSelectionNode(
            id = CanonicalNodeId("connectivity.batch"),
            actions = actions,
            semantics = ActionSelectionSemantics.batch(
                failurePolicy = FailurePolicy.CONTINUE_ON_ERROR
            )
        )
        val ordered = ActionSelectionNode(
            id = CanonicalNodeId("connectivity.ordered"),
            actions = actions,
            semantics = ActionSelectionSemantics.ordered(
                failurePolicy = FailurePolicy.FAIL_FAST
            )
        )

        assertEquals(ExecutionMode.BATCH, batch.semantics.executionMode)
        assertEquals(ExecutionMode.ORDERED, ordered.semantics.executionMode)
        assertEquals(FailurePolicy.CONTINUE_ON_ERROR, batch.semantics.failurePolicy)
        assertEquals(FailurePolicy.FAIL_FAST, ordered.semantics.failurePolicy)
    }

    @Test
    fun multiSelectionRequiresAtLeastTwoUniqueActions() {
        val action = SetStateNode(
            id = CanonicalNodeId("wifi.enable"),
            target = TargetId("core.connectivity.wifi"),
            state = BooleanValue(true)
        )

        expectIllegalArgument {
            ActionSelectionNode(
                id = CanonicalNodeId("selection.one"),
                actions = listOf(action),
                semantics = ActionSelectionSemantics.batch()
            )
        }

        expectIllegalArgument {
            ActionSelectionNode(
                id = CanonicalNodeId("selection.duplicate"),
                actions = listOf(action, action),
                semantics = ActionSelectionSemantics.batch()
            )
        }
    }

    @Test
    fun eventAlternativesUseAnyOfOnly() {
        val events = listOf(
            ObserveNode(
                id = CanonicalNodeId("event.installed"),
                target = TargetId("core.application.lifecycle"),
                predicate = PredicateId("core.predicate.match_event")
            ),
            ObserveNode(
                id = CanonicalNodeId("event.updated"),
                target = TargetId("core.application.lifecycle"),
                predicate = PredicateId("core.predicate.match_event_filter")
            )
        )

        val node = EventSelectionNode(
            id = CanonicalNodeId("events.apps"),
            events = events
        )

        assertEquals(EventLogic.ANY_OF, node.logic)
        assertEquals(listOf(EventLogic.ANY_OF), EventLogic.entries)
    }

    @Test
    fun conditionGroupsSupportAnyAndAllWithoutChangingEventLogic() {
        fun condition(id: String, target: String): CanonicalConditionNode {
            val nodeId = CanonicalNodeId(id)
            return ObservedConditionNode(
                id = nodeId,
                observation = ObserveNode(
                    id = nodeId,
                    target = TargetId(target),
                    predicate = PredicateId("core.predicate.match_state")
                )
            )
        }

        val conditions = listOf(
            condition("condition.wifi", "core.connectivity.wifi"),
            condition("condition.vpn", "core.connectivity.vpn")
        )

        val any = ConditionGroupNode(
            id = CanonicalNodeId("conditions.any"),
            conditions = conditions,
            logic = ConditionLogic.ANY
        )
        val all = ConditionGroupNode(
            id = CanonicalNodeId("conditions.all"),
            conditions = conditions,
            logic = ConditionLogic.ALL
        )

        assertEquals(ConditionLogic.ANY, any.logic)
        assertEquals(ConditionLogic.ALL, all.logic)
        assertEquals(setOf(ConditionLogic.ANY, ConditionLogic.ALL), ConditionLogic.entries.toSet())
    }

    @Test
    fun selectionNodesRoundTripInsideCanonicalWorkflow() {
        val eventSelection = EventSelectionNode(
            id = CanonicalNodeId("event.selection"),
            events = listOf(
                ObserveNode(
                    id = CanonicalNodeId("event.one"),
                    target = TargetId("core.device.lifecycle"),
                    predicate = PredicateId("core.predicate.match_event")
                ),
                ObserveNode(
                    id = CanonicalNodeId("event.two"),
                    target = TargetId("core.device.boot"),
                    predicate = PredicateId("core.predicate.match_event")
                )
            )
        )

        val workflow = CanonicalWorkflowAst(
            root = BranchNode(
                id = CanonicalNodeId("branch"),
                condition = eventSelection,
                ifTrue = ActionSelectionNode(
                    id = CanonicalNodeId("actions"),
                    actions = listOf(
                        SetStateNode(
                            id = CanonicalNodeId("action.wifi"),
                            target = TargetId("core.connectivity.wifi"),
                            state = BooleanValue(true)
                        ),
                        SetStateNode(
                            id = CanonicalNodeId("action.bluetooth"),
                            target = TargetId("core.connectivity.bluetooth"),
                            state = BooleanValue(true)
                        )
                    ),
                    semantics = ActionSelectionSemantics.batch(
                        FailurePolicy.CONTINUE_ON_ERROR
                    )
                )
            )
        )

        val encoded = json.encodeToString(workflow)
        val decoded = json.decodeFromString<CanonicalWorkflowAst>(encoded)

        assertEquals(workflow, decoded)
        assertTrue(encoded.contains("ANY_OF"))
        assertTrue(encoded.contains("BATCH"))
        assertTrue(encoded.contains("CONTINUE_ON_ERROR"))
    }

    @Test
    fun astValidationRejectsDuplicateIdsAcrossSelectionBoundaries() {
        val duplicateId = CanonicalNodeId("duplicate")
        expectIllegalArgument {
            CanonicalWorkflowAst(
                root = ActionSelectionNode(
                    id = CanonicalNodeId("selection"),
                    actions = listOf(
                        SetStateNode(
                            id = duplicateId,
                            target = TargetId("core.connectivity.wifi"),
                            state = BooleanValue(true)
                        ),
                        SetStateNode(
                            id = duplicateId,
                            target = TargetId("core.connectivity.bluetooth"),
                            state = BooleanValue(true)
                        )
                    ),
                    semantics = ActionSelectionSemantics.batch()
                )
            )
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
