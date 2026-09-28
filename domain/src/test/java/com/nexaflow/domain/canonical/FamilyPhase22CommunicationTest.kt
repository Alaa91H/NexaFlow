package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase22CommunicationTest {

    private val familyAdapter = FamilyPhase22Communication.adapterWithFamily()

    private fun overrides() = FamilyPhase22Communication.ruleOverrides()

    @Test
    fun familyOverridesCoverCommunicationMembers() {
        assertEquals(14, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(4, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(233, familyAdapter.declaredRules)
    }

    @Test
    fun smsActionUpgradesTypedNumberAndText() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_SEND_SMS",
                LegacyNodeKind.ACTION,
                listOf(
                    LegacyConfigEntry("number", "+123456789"),
                    LegacyConfigEntry("text", "on my way"),
                ),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(TextValue("+123456789"), node.arguments[CanonicalFieldId("number")])
        assertEquals(TextValue("on my way"), node.arguments[CanonicalFieldId("text")])
    }

    @Test
    fun notificationBlockUpgradesTypedAppFilter() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_BLOCK_NOTIFICATION",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("package", "com.example.spam")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            PackageIdValue("com.example.spam"),
            node.arguments[CanonicalFieldId("appPackage")],
        )
    }

    @Test
    fun smsTriggerFilterUpgradesTypedListAndStaysOptional() {
        val filtered = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SMS",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("packages", "com.a.app|com.b.app")),
            ),
        ) as LegacyAdapterOutcome.Canonicalized
        val packages = (filtered.node as ObserveNode)
            .arguments[CanonicalFieldId("packages")] as CollectionValue
        assertEquals(2, packages.values.size)

        val anySender = familyAdapter.canonicalize(
            LegacyNodeInput("SMS", LegacyNodeKind.TRIGGER, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertEquals(0, (anySender.node as ObserveNode).arguments.entries.size)
    }

    @Test
    fun sendsAreNeverBlindlyRetryable() {
        val planner = CanonicalExecutionPlanner.of(
            FamilyPhase22Communication.commandSemantics() + listOf(
                CommandSemantics(
                    OperationId("core.operation.wait"),
                    CommandIdempotency.IDEMPOTENT,
                    reversible = false,
                ),
            ),
        )
        val plan = planner.plan(
            root = InvokeNode(
                id = CanonicalNodeId("sms1"),
                target = TargetId("core.communication.sms"),
                operation = OperationId("core.operation.send"),
            ),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )
        assertEquals(
            CommandIdempotency.NON_IDEMPOTENT,
            plan.allCommands.single().idempotency,
        )
    }

    @Test
    fun policyClearsAreIdempotentAndReversibleFree() {
        val semantics = FamilyPhase22Communication.commandSemantics()
            .first { it.operation == OperationId("core.operation.clear") }
        assertEquals(CommandIdempotency.IDEMPOTENT, semantics.idempotency)
    }

    @Test
    fun smsSchemaIsSensitiveAndComplete() {
        val schema = FamilyPhase22Communication.smsSchema()
        assertEquals(NodeSecurityClass.SENSITIVE, schema.securityClass)
        assertTrue(schema.capabilities.isNotEmpty())

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(CanonicalFieldId("number"), TextValue("+123456789")),
                NodeFieldValue(CanonicalFieldId("text"), TextValue("hello")),
            ),
        )
        assertTrue(valid.isEmpty())

        val missingText = validateNodeValues(
            schema,
            listOf(NodeFieldValue(CanonicalFieldId("number"), TextValue("+123456789"))),
        )
        assertTrue(missingText.any { it is MissingRequiredField })
    }

    @Test
    fun sendSemanticsAreSingleTarget() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase22Communication.sendSemantics,
            selectedTargetCount = 1,
            cardinality = OperationCardinality.SINGLE_TARGET,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun policySemanticsAllowBoundedMulti() {
        val errors = validateSelectionSemantics(
            semantics = FamilyPhase22Communication.policySemantics,
            selectedTargetCount = 5,
        )
        assertTrue("expected no violations, got $errors", errors.isEmpty())
    }

    @Test
    fun parityWithSkeletonTargetsIsPreserved() {
        val baseAdapter = LegacyCanonicalAdapter(LegacyMappingTable.all())
        for (rule in overrides()) {
            val input = LegacyNodeInput(rule.legacyType, rule.kind, emptyList())
            val skeleton = baseAdapter.canonicalize(input)
            val family = familyAdapter.canonicalize(input)
            val skeletonNode = (skeleton as? LegacyAdapterOutcome.Canonicalized)?.node
                as? InvokeNode ?: continue
            val familyNode = (family as? LegacyAdapterOutcome.Canonicalized)?.node
                as? InvokeNode ?: continue
            assertEquals(rule.legacyType, skeletonNode.target, familyNode.target)
            assertEquals(rule.legacyType, skeletonNode.operation, familyNode.operation)
        }
    }

    @Test
    fun canonicalizationRemainsIdempotent() {
        for (rule in overrides()) {
            val config = if (rule.legacyType == "SYSTEM_SEND_SMS") {
                listOf(
                    LegacyConfigEntry("number", "+123456789"),
                    LegacyConfigEntry("text", "hello"),
                )
            } else {
                emptyList()
            }
            val input = LegacyNodeInput(rule.legacyType, rule.kind, config)
            assertEquals(
                "non-idempotent ${rule.legacyType}",
                familyAdapter.canonicalize(input),
                familyAdapter.canonicalize(input),
            )
        }
    }

    @Test
    fun driftedTableFailsClosed() {
        val drifted = LegacyMappingTable.all()
            .filter { it.legacyType != "SYSTEM_SEND_SMS" }
        try {
            FamilyPhase22Communication.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
