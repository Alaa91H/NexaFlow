package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyPhase25AdvancedExternalTest {

    private val familyAdapter = FamilyPhase25AdvancedExternal.adapterWithFamily()

    private fun overrides() = FamilyPhase25AdvancedExternal.ruleOverrides()

    @Test
    fun familyOverridesCoverAdvancedExternalMembers() {
        // 8 data transforms + 9 ROM + 2 privileged + 4 power + clipboard/
        // setting/http/wait/plugin = 28 actions + 1 plugin trigger.
        assertEquals(28, overrides().count { it.kind == LegacyNodeKind.ACTION })
        assertEquals(1, overrides().count { it.kind == LegacyNodeKind.TRIGGER })
    }

    @Test
    fun familyAdapterKeepsTheFullTable() {
        assertEquals(233, familyAdapter.declaredRules)
    }

    @Test
    fun dataTransformUpgradesToTypedExpression() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "DATA_HASH",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("expression", "sha256(input)")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        val expression = node.arguments[CanonicalFieldId("expression")] as ExpressionValue
        assertEquals("sha256(input)", expression.source)
    }

    @Test
    fun privilegedCommandNeverCarriesRawCommandText() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "ADVANCED_ROOT",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("command", "rm -rf /sdcard/junk")),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        val commandRef = node.arguments[CanonicalFieldId("commandRef")]
        // The raw command never enters the AST: only a stable secret ref does.
        assertEquals(
            SecretReferenceValue("legacy.privileged_command"),
            commandRef,
        )
        assertTrue(node.arguments.entries.none { it.value is TextValue })
    }

    @Test
    fun httpUpgradesUrlAndSecretAuthToken() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_HTTP_REQUEST",
                LegacyNodeKind.ACTION,
                listOf(
                    LegacyConfigEntry("url", "https://api.example.com/v1"),
                    LegacyConfigEntry("method", "POST"),
                    LegacyConfigEntry("auth_token", "super-secret-token"),
                ),
            ),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            UriValue("https://api.example.com/v1"),
            node.arguments[CanonicalFieldId("url")],
        )
        assertEquals(
            SecretReferenceValue("legacy.http_auth_token"),
            node.arguments[CanonicalFieldId("authToken")],
        )
    }

    @Test
    fun rebootActionsStaySkeletal() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_REBOOT", LegacyNodeKind.ACTION, emptyList()),
        )
        val node = (outcome as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(TargetId("core.device.power"), node.target)
        assertEquals(OperationId("core.operation.reboot"), node.operation)
    }

    @Test
    fun waitDurationRefinesWhenPresentAndDefersWhenAbsent() {
        val ok = familyAdapter.canonicalize(
            LegacyNodeInput(
                "SYSTEM_WAIT",
                LegacyNodeKind.ACTION,
                listOf(LegacyConfigEntry("duration", "5000")),
            ),
        )
        val node = (ok as LegacyAdapterOutcome.Canonicalized).node as InvokeNode
        assertEquals(
            DurationValue(5000),
            node.arguments[CanonicalFieldId("duration")],
        )

        val missing = familyAdapter.canonicalize(
            LegacyNodeInput("SYSTEM_WAIT", LegacyNodeKind.ACTION, emptyList()),
        ) as LegacyAdapterOutcome.Canonicalized
        assertTrue(missing.node is InvokeNode)
    }

    @Test
    fun pluginTriggerCarriesTypedPluginId() {
        val outcome = familyAdapter.canonicalize(
            LegacyNodeInput(
                "PLUGIN_EVENT",
                LegacyNodeKind.TRIGGER,
                listOf(LegacyConfigEntry("plugin_id", "com.example.plugin")),
            ),
        )
        val observation = (outcome as LegacyAdapterOutcome.Canonicalized).node as ObserveNode
        assertEquals(
            TextValue("com.example.plugin"),
            observation.arguments[CanonicalFieldId("pluginId")],
        )
    }

    @Test
    fun privilegedOperationsAreNeverBlindlyRetryable() {
        val planner = CanonicalExecutionPlanner.of(
            FamilyPhase25AdvancedExternal.commandSemantics() + listOf(
                CommandSemantics(
                    OperationId("core.operation.set_state"),
                    CommandIdempotency.IDEMPOTENT,
                    reversible = true,
                ),
            ),
        )
        val plan = planner.plan(
            root = InvokeNode(
                id = CanonicalNodeId("cmd"),
                target = TargetId("core.advanced.command"),
                operation = OperationId("core.operation.execute"),
            ),
            executionPolicy = PlanExecutionPolicy.SEQUENTIAL,
            failurePolicy = FailurePolicy.FAIL_FAST,
        )
        assertEquals(
            CommandIdempotency.CONDITIONALLY_IDEMPOTENT,
            plan.allCommands.single().idempotency,
        )
    }

    @Test
    fun httpSchemaIsSensitiveWithSecretTokenField() {
        val schema = FamilyPhase25AdvancedExternal.httpSchema()
        assertEquals(NodeSecurityClass.SENSITIVE, schema.securityClass)
        assertTrue(schema.capabilities.isNotEmpty())

        val valid = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(CanonicalFieldId("url"), UriValue("https://api.example.com")),
                NodeFieldValue(
                    CanonicalFieldId("method"),
                    EnumTokenValue("core.external.http.method", "GET"),
                ),
            ),
        )
        assertTrue(valid.isEmpty())

        val badMethod = validateNodeValues(
            schema,
            listOf(
                NodeFieldValue(CanonicalFieldId("url"), UriValue("https://api.example.com")),
                NodeFieldValue(
                    CanonicalFieldId("method"),
                    EnumTokenValue("core.external.http.method", "TRACE"),
                ),
            ),
        )
        assertTrue(badMethod.any { it is EnumTokenNotAllowed })
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
            val config = when {
                rule.legacyType == "SYSTEM_HTTP_REQUEST" -> listOf(
                    LegacyConfigEntry("url", "https://api.example.com"),
                    LegacyConfigEntry("method", "GET"),
                    LegacyConfigEntry("auth_token", "t"),
                )
                rule.legacyType == "SYSTEM_WAIT" -> listOf(LegacyConfigEntry("duration", "100"))
                rule.kind == LegacyNodeKind.TRIGGER -> emptyList()
                else -> emptyList()
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
            .filter { it.legacyType != "ADVANCED_ROOT" }
        try {
            FamilyPhase25AdvancedExternal.ruleOverrides(drifted)
            throw AssertionError("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
