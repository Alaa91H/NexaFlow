package com.nexaflow.domain.workflow

import com.nexaflow.domain.models.Action
import com.nexaflow.domain.models.ActionType
import com.nexaflow.domain.models.Automation
import com.nexaflow.domain.models.Constraint
import com.nexaflow.domain.models.ConstraintType
import com.nexaflow.domain.models.EndBehavior
import com.nexaflow.domain.models.EndMode
import com.nexaflow.domain.models.Trigger
import com.nexaflow.domain.models.TriggerMatchMode
import com.nexaflow.domain.models.TriggerType
import com.nexaflow.domain.workflow.WorkflowDocumentMappers.toAutomation
import com.nexaflow.domain.workflow.WorkflowDocumentMappers.toDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests pinning the WorkflowDocumentV1 acceptance criteria from the
 * roadmap: legacy tasks read unchanged, round-trips are lossless, unknown
 * schema versions fail safely, and structural validation rejects broken
 * graphs before they can be persisted or executed.
 */
class WorkflowDocumentV1Test {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun automation(
        id: String = "task-1",
        actions: List<Action> = listOf(
            Action(type = ActionType.SYSTEM_WIFI, config = mapOf("enabled" to "true")),
            Action(
                type = ActionType.SYSTEM_BRIGHTNESS,
                config = mapOf("value" to "120"),
                endBehavior = EndBehavior(mode = EndMode.SET_VALUE, config = mapOf("mode" to "SET_VALUE", "value" to "80")),
            ),
        ),
        exitActions: List<Action> = listOf(
            Action(
                type = ActionType.SYSTEM_WIFI,
                config = mapOf("enabled" to "false"),
                endBehavior = EndBehavior(mode = EndMode.LEAVE),
            ),
        ),
        constraints: List<Constraint> = listOf(
            Constraint(type = ConstraintType.BATTERY, config = mapOf("direction" to "ABOVE", "level" to "50")),
        ),
    ): Automation = Automation(
        id = id,
        name = "Night Wi-Fi",
        description = "Turn Wi-Fi on at night",
        icon = "wifi",
        iconColor = 0xFF123456,
        backgroundColor = 0xFF654321,
        category = "connectivity",
        priority = 9,
        enabled = true,
        showToastOnToggle = false,
        triggers = listOf(
            Trigger(type = TriggerType.TIME, config = mapOf("start" to "22:00", "end" to "07:00")),
        ),
        actions = actions,
        triggerMatch = TriggerMatchMode.ALL,
        constraints = constraints,
        exitActions = exitActions,
        revertOnExit = false,
        cooldownSeconds = 30,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    // ------------------------------------------------------------------
    // NF-P0-006: legacy → document mapping is lossless
    // ------------------------------------------------------------------

    @Test
    fun legacyToDocumentPreservesEveryModeledField() {
        val legacy = automation()
        val doc = legacy.toDocument()

        assertEquals(WorkflowDocumentV1.SCHEMA_VERSION, doc.schemaVersion)
        assertEquals(legacy.id, doc.id)
        assertEquals(1L, doc.revision)
        assertEquals(legacy.name, doc.metadata.name)
        assertEquals(legacy.description, doc.metadata.description)
        assertEquals(legacy.category, doc.metadata.category)
        assertEquals(legacy.createdAt, doc.metadata.createdAt)
        assertEquals(legacy.updatedAt, doc.metadata.updatedAt)
        assertEquals(legacy.iconColor, doc.automationSettings.iconColor)
        assertEquals(legacy.backgroundColor, doc.automationSettings.backgroundColor)
        assertEquals(legacy.priority, doc.automationSettings.priority)
        assertEquals(legacy.enabled, doc.automationSettings.enabled)
        assertEquals(legacy.showToastOnToggle, doc.automationSettings.showToastOnToggle)
        assertEquals(legacy.triggerMatch.name, doc.automationSettings.triggerMatch)
        assertEquals(legacy.cooldownSeconds, doc.automationSettings.cooldownSeconds)
        assertEquals(legacy.workflowVersion, doc.automationSettings.workflowVersion)
        assertEquals(legacy.maintenanceProfile, doc.automationSettings.maintenanceProfile)

        assertEquals(1, doc.triggers.size)
        assertEquals(TriggerType.TIME.name, doc.triggers[0].type)
        assertEquals(mapOf("start" to "22:00", "end" to "07:00"), doc.triggers[0].config)

        assertEquals(1, doc.constraints.size)
        assertEquals(ConstraintType.BATTERY.name, doc.constraints[0].type)

        val runActions = (doc.root as PersistedWorkflowNodeV1.Sequence).children
            .map { (it as PersistedWorkflowNodeV1.Action).action }
        assertEquals(2, runActions.size)
        assertEquals(ActionType.SYSTEM_WIFI.name, runActions[0].type)
        assertEquals(mapOf("enabled" to "true"), runActions[0].config)
        assertEquals("SET_VALUE", runActions[1].endBehavior?.mode)
        assertEquals(mapOf("mode" to "SET_VALUE", "value" to "80"), runActions[1].endBehavior?.config)

        val exitPolicy = requireNotNull(doc.exitPolicy)
        assertEquals(1, exitPolicy.actions.size)
        assertEquals(ActionType.SYSTEM_WIFI.name, exitPolicy.actions[0].type)
        assertEquals(false, exitPolicy.revertOnExit)
    }

    // ------------------------------------------------------------------
    // NF-P0-008: round-trip corpus
    // ------------------------------------------------------------------

    @Test
    fun documentToAutomationRoundTripIsLossless() {
        val legacy = automation()
        val roundTripped = with(WorkflowDocumentMappers) { legacy.toDocument().toAutomation() }

        assertEquals(legacy, roundTripped)
    }

    @Test
    fun blankLegacyCategoryRemainsBlankAfterRoundTrip() {
        val legacy = automation().copy(category = "")
        val roundTripped = with(WorkflowDocumentMappers) { legacy.toDocument().toAutomation() }
        assertEquals("", roundTripped.category)
    }

    @Test
    fun serializationRoundTripPreservesDocument() {
        val doc = automation().toDocument(revision = 7L)
        val encoded = WorkflowDocumentMappers.encode(doc)
        val decoded = WorkflowDocumentMappers.decode(encoded)

        assertEquals(doc, decoded)
        assertEquals(doc.hash, decoded.hash)
        assertEquals(7L, decoded.revision)
    }

    @Test
    fun contentHashChangesWhenDefinitionChanges() {
        val base = automation().toDocument()
        val edited = base.copy(
            metadata = base.metadata.copy(name = "Renamed"),
            revision = base.revision + 1,
        )
        assertNotEquals(base.hash, edited.hash)
    }

    @Test
    fun contentHashIsRevisionIndependent() {
        val base = automation().toDocument()
        // Same content, different revision → same hash (diagnostics equality).
        val bumped = base.copy(revision = base.revision + 1)
        assertEquals(base.hash, bumped.hash)
    }

    @Test
    fun contentHashIgnoresMapInsertionOrder() {
        val forward = automation(
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_BRIGHTNESS,
                    config = linkedMapOf("value" to "120", "mode" to "manual"),
                ),
            ),
            exitActions = emptyList(),
            constraints = emptyList(),
        ).toDocument()
        val reversed = automation(
            actions = listOf(
                Action(
                    type = ActionType.SYSTEM_BRIGHTNESS,
                    config = linkedMapOf("mode" to "manual", "value" to "120"),
                ),
            ),
            exitActions = emptyList(),
            constraints = emptyList(),
        ).toDocument()

        assertEquals(forward, reversed)
        assertEquals(forward.hash, reversed.hash)
    }

    @Test
    fun persistedSecretDeclarationAcceptsVaultReference() {
        val declaration = VariableDeclarationV1(
            name = "service.token",
            runtimeType = "SECRET",
            scope = "WORKFLOW",
            defaultValue = RuntimeValueV1.SecretReference("vault:service-token"),
            isSecret = true,
        )
        assertEquals("vault:service-token", (declaration.defaultValue as RuntimeValueV1.SecretReference).handle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun persistedSecretDeclarationRejectsPlaintextDefault() {
        VariableDeclarationV1(
            name = "service.token",
            runtimeType = "SECRET",
            scope = "WORKFLOW",
            defaultValue = RuntimeValueV1.StringValue("plain-secret"),
            isSecret = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun secretReferenceRejectsNonVaultHandle() {
        RuntimeValueV1.SecretReference("plain-secret")
    }

    // ------------------------------------------------------------------
    // NF-P0-009: forward-compatible rejection
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun unknownSchemaVersionFailsSafely() {
        val doc = automation().toDocument()
        val encoded = WorkflowDocumentMappers.encode(doc)
        val future = encoded.replace(
            "\"schemaVersion\":1",
            "\"schemaVersion\":99",
        )
        WorkflowDocumentMappers.decode(future)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownNodeKindFailsSafely() {
        val doc = automation().toDocument()
        val encoded = WorkflowDocumentMappers.encode(doc)
        // Inject an unknown node discriminator inside the root's children.
        val poisoned = encoded.replace("\"kind\":\"sequence\"", "\"kind\":\"quantum_node\"")
        WorkflowDocumentMappers.decode(poisoned)
    }

    // ------------------------------------------------------------------
    // NF-P0-015: structural validation
    // ------------------------------------------------------------------

    @Test
    fun validDocumentPassesStructuralValidation() {
        val doc = automation().toDocument()
        assertTrue(WorkflowDocumentMappers.validateStructure(doc).isEmpty())
    }

    @Test
    fun duplicateNodeIdsAreRejected() {
        val doc = automation().toDocument()
        val child = (doc.root as PersistedWorkflowNodeV1.Sequence).children[0]
        val poisoned = doc.copy(
            root = PersistedWorkflowNodeV1.Sequence(
                nodeId = doc.root.nodeId,
                children = listOf(child, child),
            ),
        )
        val issues = WorkflowDocumentMappers.validateStructure(poisoned)
        assertTrue(issues.any { it is WorkflowDocumentMappers.ValidationIssue.DuplicateNodeId })
    }

    @Test
    fun duplicateExitNodeIdsAreRejected() {
        val doc = automation().toDocument()
        val poisoned = doc.copy(
            exitPolicy = ExitPolicyV1(
                actions = listOf(
                    PersistedActionV1(
                        nodeId = doc.root.nodeId,
                        type = ActionType.SYSTEM_WIFI.name,
                        config = mapOf("enabled" to "false"),
                    ),
                ),
            ),
        )
        val issues = WorkflowDocumentMappers.validateStructure(poisoned)
        assertTrue(issues.any { it is WorkflowDocumentMappers.ValidationIssue.DuplicateNodeId })
    }

    @Test
    fun runawayLoopBoundsAreRejected() {
        val doc = automation().toDocument()
        val poisoned = doc.copy(
            root = PersistedWorkflowNodeV1.While(
                nodeId = "while:runaway",
                condition = ConditionExpr.Equals(
                    left = ValueExpr.Literal(RuntimeValueV1.BooleanValue(true)),
                    right = ValueExpr.Literal(RuntimeValueV1.BooleanValue(true)),
                ),
                body = PersistedWorkflowNodeV1.Sequence(nodeId = "while:runaway:body", children = emptyList()),
                maxIterations = 100_000,
            ),
        )
        val issues = WorkflowDocumentMappers.validateStructure(poisoned)
        assertTrue(issues.any { it is WorkflowDocumentMappers.ValidationIssue.UnboundedLoop })
    }

    @Test
    fun retryBackoffOutsideRuntimeBoundsIsRejected() {
        val doc = automation().toDocument()
        val poisoned = doc.copy(
            root = PersistedWorkflowNodeV1.Retry(
                nodeId = "retry:bad-backoff",
                body = PersistedWorkflowNodeV1.Action(
                    nodeId = "retry:body",
                    action = PersistedActionV1(
                        nodeId = "retry:body-action",
                        type = ActionType.SYSTEM_WIFI.name,
                        config = mapOf("enabled" to "true"),
                    ),
                ),
                maxAttempts = 2,
                backoffMs = WorkflowDocumentMappers.Bounds.MAX_TIMEOUT_MS + 1,
            ),
        )
        val issues = WorkflowDocumentMappers.validateStructure(poisoned)
        assertTrue(issues.any { it is WorkflowDocumentMappers.ValidationIssue.InvalidBound })
    }

    @Test
    fun emptyGraphIsRejected() {
        val doc = automation().toDocument()
        val empty = doc.copy(
            root = PersistedWorkflowNodeV1.Sequence(nodeId = "run:empty", children = emptyList()),
        )
        val issues = WorkflowDocumentMappers.validateStructure(empty)
        assertTrue(issues.any { it is WorkflowDocumentMappers.ValidationIssue.EmptyGraph })
    }

    // ------------------------------------------------------------------
    // Migration idempotency (acceptance: migration must be idempotent)
    // ------------------------------------------------------------------

    @Test
    fun legacyToDocumentToLegacyToDocumentIsStable() {
        val legacy = automation()
        val firstDoc = legacy.toDocument()
        val secondDoc = with(WorkflowDocumentMappers) { firstDoc.toAutomation().toDocument() }
        assertEquals(firstDoc, secondDoc)
        assertEquals(firstDoc.hash, secondDoc.hash)
    }

    // ------------------------------------------------------------------
    // Revision contract (recovery references revision, not just id)
    // ------------------------------------------------------------------

    @Test
    fun revisionBumpsOnEditWhileIdStaysStable() {
        val doc = automation().toDocument(revision = 3L)
        val edited = doc.copy(revision = 4L)
        assertEquals(doc.id, edited.id)
        assertEquals(3L, doc.revision)
        assertEquals(4L, edited.revision)
    }

    @Test
    fun conditionExpressionsCarryDataNotLambdas() {
        // The persisted condition tree is composed of value expressions only.
        val condition = ConditionExpr.And(
            terms = listOf(
                ConditionExpr.Compare(
                    left = ValueExpr.ContextRef(path = "$.battery.level"),
                    op = CompareOp.GREATER_THAN,
                    right = ValueExpr.Literal(RuntimeValueV1.IntValue(50)),
                ),
                ConditionExpr.Not(
                    ConditionExpr.Equals(
                        left = ValueExpr.ContextRef(path = "$.call.state"),
                        right = ValueExpr.Literal(RuntimeValueV1.StringValue("active")),
                    ),
                ),
            ),
        )
        val doc = automation().toDocument().copy(
            root = PersistedWorkflowNodeV1.Branch(
                nodeId = "branch:1",
                condition = condition,
                whenTrue = PersistedWorkflowNodeV1.Action(
                    nodeId = "action:1",
                    action = PersistedActionV1(
                        nodeId = "action:1",
                        type = ActionType.SYSTEM_WIFI.name,
                        config = mapOf("enabled" to "true"),
                    ),
                ),
            ),
        )
        val encoded = WorkflowDocumentMappers.encode(doc)
        assertTrue(encoded.contains("\"kind\":\"and\""))
        assertTrue(encoded.contains("\"kind\":\"context_ref\""))
        assertTrue(encoded.contains("$.battery.level"))
    }
}
