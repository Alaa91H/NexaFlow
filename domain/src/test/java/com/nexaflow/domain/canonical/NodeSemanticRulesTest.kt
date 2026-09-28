package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSemanticRulesTest {

    private val wifi = TargetId("core.connectivity.wifi")
    private val bluetooth = TargetId("core.connectivity.bluetooth")
    private val brightness = TargetId("core.display.brightness")

    private fun observe(
        id: String,
        target: TargetId,
        predicate: String,
        vararg arguments: CanonicalArgument,
    ) = ObserveNode(
        id = CanonicalNodeId(id),
        target = target,
        predicate = PredicateId(predicate),
        arguments = CanonicalArguments(arguments.toList()),
    )

    private fun setState(id: String, target: TargetId, state: CanonicalValue) =
        SetStateNode(id = CanonicalNodeId(id), target = target, state = state)

    private fun setValue(id: String, target: TargetId, value: CanonicalValue) =
        SetValueNode(id = CanonicalNodeId(id), target = target, value = value)

    @Test
    fun contradictoryStateAssertionsAreDetected() {
        val observations = listOf(
            observe("o1", wifi, "core.predicate.match_state", CanonicalArgument(CanonicalFieldId("connected"), BooleanValue(true))),
            observe("o2", wifi, "core.predicate.match_state", CanonicalArgument(CanonicalFieldId("connected"), BooleanValue(false))),
        )

        val violations = evaluateStateConditions(observations)

        assertEquals(1, violations.size)
        val violation = violations.first() as ContradictoryStateConditions
        assertEquals(wifi, violation.target)
        assertEquals(CanonicalFieldId("connected"), violation.field)
        assertEquals("contradictory_state_conditions", violation.rule)
    }

    @Test
    fun sameStateAssertionTwiceIsNotAContradiction() {
        val observations = listOf(
            observe("o1", wifi, "core.predicate.match_state", CanonicalArgument(CanonicalFieldId("connected"), BooleanValue(true))),
            observe("o2", wifi, "core.predicate.match_state", CanonicalArgument(CanonicalFieldId("connected"), BooleanValue(true))),
        )

        assertTrue(evaluateStateConditions(observations).isEmpty())
    }

    @Test
    fun nonBooleanArgumentsAreNotStateAssertions() {
        val observations = listOf(
            observe(
                "o1",
                brightness,
                "core.predicate.match_state",
                CanonicalArgument(CanonicalFieldId("level"), PercentageValue("30")),
                CanonicalArgument(CanonicalFieldId("limit"), PercentageValue("80")),
            ),
        )

        assertTrue(evaluateStateConditions(observations).isEmpty())
    }

    @Test
    fun eventAllWithProvenMutuallyExclusivePredicatesFails() {
        val connected = PredicateId("core.predicate.match_state")
        val disconnected = PredicateId("core.predicate.match_transition")
        val rules = NodeSemanticRules.default().withOverrides(
            eventPredicateRules = mapOf(
                connected to EventPredicateRule(connected, mutuallyExclusiveWith = setOf(disconnected)),
            ),
        )
        val events = listOf(
            observe("e1", wifi, connected.value),
            observe("e2", wifi, disconnected.value),
        )

        val violations = evaluateEventGroup(events, ConditionLogic.ALL, rules)

        assertTrue(violations.any { it is EventAllOnMutuallyExclusivePredicates })
    }

    @Test
    fun eventAllWithoutProvenExclusivityFailsClosed() {
        val first = PredicateId("core.predicate.match_event")
        val second = PredicateId("core.predicate.match_state")
        val events = listOf(
            observe("e1", wifi, first.value),
            observe("e2", bluetooth, second.value),
        )

        val violations = evaluateEventGroup(events, ConditionLogic.ALL)

        assertTrue(
            "unproven ALL must fail closed",
            violations.any { it is EventAllRequiresProof && it.first == first && it.second == second },
        )
    }

    @Test
    fun eventAllOverTheSamePredicateIsAllowed() {
        val events = listOf(
            observe("e1", wifi, "core.predicate.match_event"),
            observe("e2", bluetooth, "core.predicate.match_event"),
        )

        assertTrue(evaluateEventGroup(events, ConditionLogic.ALL).isEmpty())
    }

    @Test
    fun eventAnyOfIsAlwaysAllowed() {
        val events = listOf(
            observe("e1", wifi, "core.predicate.match_state"),
            observe("e2", wifi, "core.predicate.match_transition"),
        )

        // Events combine with ANY_OF by design; a condition group over the
        // same observations with ANY is likewise always satisfiable.
        assertTrue(evaluateEventGroup(events, ConditionLogic.ANY).isEmpty())
    }

    @Test
    fun unregisteredEventPredicateFailsClosed() {
        val events = listOf(
            observe("e1", wifi, "core.predicate.match_state"),
            observe("e2", wifi, "core.predicate.unknown"),
        )

        val violations = evaluateEventGroup(events, ConditionLogic.ALL)

        assertTrue(violations.any { it is UnregisteredEventPredicate && it.predicate.value == "core.predicate.unknown" })
    }

    @Test
    fun duplicateConflictingWritesInOneBatchAreDetected() {
        val writes = listOf(
            setState("w1", wifi, BooleanValue(true)),
            setState("w2", wifi, BooleanValue(false)),
        )

        val violations = evaluateWriteConflicts(writes)

        assertEquals(1, violations.size)
        val violation = violations.first() as DuplicateConflictingWrites
        assertEquals(wifi, violation.target)
        assertEquals(2, violation.occurrences)
    }

    @Test
    fun identicalWritesWithinToleranceAreAllowed() {
        val writes = listOf(
            setState("w1", wifi, BooleanValue(true)),
            setState("w2", wifi, BooleanValue(true)),
        )

        assertTrue(evaluateWriteConflicts(writes).isEmpty())
    }

    @Test
    fun identicalWritesBeyondToleranceFail() {
        val rules = NodeSemanticRules.default()
        val writes = listOf(
            setState("w1", wifi, BooleanValue(true)),
            setState("w2", wifi, BooleanValue(true)),
            setState("w3", wifi, BooleanValue(true)),
        )

        val violations = evaluateWriteConflicts(writes, rules)

        assertTrue(violations.any { it is DuplicateConflictingWrites && it.occurrences == 3 })
    }

    @Test
    fun mixedSetStateAndSetValueOnSameTargetIsUnprovable() {
        val writes = listOf(
            setState("w1", brightness, IntegerValue(35)),
            setValue("w2", brightness, IntegerValue(80)),
        )

        // Distinct literal payloads are provably contradictory even when
        // produced by different write primitives.
        val violations = evaluateWriteConflicts(writes)

        assertTrue(violations.any { it is DuplicateConflictingWrites && it.target == brightness })
    }

    @Test
    fun identicalPayloadAcrossDifferentWritePrimitivesIsAllowed() {
        val writes = listOf(
            setState("w1", brightness, IntegerValue(35)),
            setValue("w2", brightness, IntegerValue(35)),
        )

        assertTrue(evaluateWriteConflicts(writes).isEmpty())
    }

    @Test
    fun expressionWriteInsideConflictingScopeFailsClosed() {
        val writes = listOf(
            setState("w1", brightness, ExpressionValue("battery.level", CanonicalValueKind.INTEGER)),
            setState("w2", brightness, IntegerValue(80)),
        )

        val violations = evaluateWriteConflicts(writes)

        assertTrue(violations.any { it is UnprovableWriteConflict && it.target == brightness })
    }

    @Test
    fun writesAcrossAWaitNeverConflict() {
        val root = SequenceNode(
            id = CanonicalNodeId("root"),
            children = listOf(
                setState("w1", wifi, BooleanValue(true)),
                WaitNode(CanonicalNodeId("wait"), DurationValue(1000)),
                setState("w2", wifi, BooleanValue(false)),
            ),
        )

        assertTrue(evaluateSemanticRules(root).isEmpty())
    }

    @Test
    fun writesOnOppositeBranchSidesNeverConflict() {
        val condition = ObservedConditionNode(
            id = CanonicalNodeId("cond"),
            observation = observe("cond.obs", wifi, "core.predicate.match_state", CanonicalArgument(CanonicalFieldId("connected"), BooleanValue(true))),
        )
        val root = BranchNode(
            id = CanonicalNodeId("branch"),
            condition = condition,
            ifTrue = setState("w1", wifi, BooleanValue(true)),
            ifFalse = setState("w2", wifi, BooleanValue(false)),
        )

        assertTrue(evaluateSemanticRules(root).isEmpty())
    }

    @Test
    fun duplicateConflictingWritesInsideOneSequenceScopeAreDetected() {
        val root = SequenceNode(
            id = CanonicalNodeId("root"),
            children = listOf(
                setState("w1", wifi, BooleanValue(true)),
                setState("w2", wifi, BooleanValue(false)),
                setState("w3", bluetooth, BooleanValue(true)),
            ),
        )

        val violations = evaluateSemanticRules(root)

        assertTrue(violations.any { it is DuplicateConflictingWrites && it.target == wifi })
    }

    @Test
    fun valueWriteRequirementsAreCheckedForDeclaredOperations() {
        val operation = OperationId("core.operation.invoke")
        val rules = NodeSemanticRules.default().withOverrides(
            writeOperationRules = mapOf(
                operation to WriteOperationRule(operation, writesValue = true),
            ),
        )
        val writes = listOf<CanonicalActionNode>(
            InvokeNode(
                id = CanonicalNodeId("i1"),
                target = TargetId("core.media.playback"),
                operation = operation,
                arguments = CanonicalArguments(
                    listOf(CanonicalArgument(CanonicalFieldId("value"), TextValue("next"))),
                ),
            ),
            InvokeNode(
                id = CanonicalNodeId("i2"),
                target = TargetId("core.media.playback"),
                operation = operation,
            ),
        )

        val violations = validateWriteValueRequirements(writes, rules)

        assertEquals(1, violations.size)
        val violation = violations.first() as WriteValueRequired
        assertEquals(operation, violation.operation)
    }

    @Test
    fun undeclaredWritePrimitiveFailsClosed() {
        // A custom registry that knows set_value but not set_state: a
        // SetStateNode inside a batch then has no declared semantics, so
        // conflict analysis must fail closed instead of skipping it.
        val rules = NodeSemanticRules.of(
            writeOperationRules = listOf(
                WriteOperationRule(CanonicalWriteOperations.SET_VALUE, writesValue = true),
            ),
        )
        val writes = listOf<CanonicalActionNode>(
            setState("w1", wifi, BooleanValue(true)),
            setValue("w2", wifi, BooleanValue(true)),
        )

        val violations = evaluateWriteConflicts(writes, rules)

        assertTrue(
            violations.any {
                it is UnregisteredWriteOperation && it.operation == CanonicalWriteOperations.SET_STATE
            },
        )
        // The unregistered write is excluded from conflict analysis.
        assertTrue(violations.none { it is DuplicateConflictingWrites })
    }

    @Test
    fun overridesMustMatchTheirStableIds() {
        val predicate = PredicateId("core.predicate.match_state")
        try {
            NodeSemanticRules.default().withOverrides(
                eventPredicateRules = mapOf(
                    PredicateId("core.predicate.other") to EventPredicateRule(predicate),
                ),
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun ruleEvaluationIsDeterministic() {
        val root = SequenceNode(
            id = CanonicalNodeId("root"),
            children = listOf(
                setState("w1", wifi, BooleanValue(true)),
                setState("w2", wifi, BooleanValue(false)),
                setState("w3", bluetooth, BooleanValue(true)),
                setState("w4", bluetooth, BooleanValue(false)),
            ),
        )

        val first = evaluateSemanticRules(root)
        val second = evaluateSemanticRules(root)

        assertEquals(first, second)
        assertEquals(2, first.size)
    }
}
