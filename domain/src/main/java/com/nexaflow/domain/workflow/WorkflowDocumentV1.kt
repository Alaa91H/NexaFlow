@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.nexaflow.domain.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * The versioned persisted workflow model — the single new source of truth for
 * authoring, import/export and diagnostics (roadmap P0.1). The legacy
 * [com.nexaflow.domain.models.Automation] remains fully supported: it is the
 * storage format of every existing task and is mapped losslessly at the
 * boundary; nothing here mutates it.
 *
 * Design rules pinned by the roadmap and enforced by tests:
 *  - every persisted condition is data ([ConditionExpr]), never a lambda —
 *    the runtime `WorkflowCondition` fun-interface stays execution-only;
 *  - [schemaVersion] gates forward compatibility: unknown future versions are
 *    rejected with a typed reason instead of being loaded half-parsed;
 *  - unknown node/condition kinds inside a known schema fail safely too;
 *  - [revision] changes on every user-visible edit, [id] never changes —
 *    recovery and rollback reference the revision, not just the id;
 *  - deterministic [hash] for diagnostics equality of two definitions.
 */
@Serializable
data class WorkflowDocumentV1(
    /** Persisted schema version. Must be [SCHEMA_VERSION] for this reader. */
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Stable identity across revisions. */
    val id: String,
    /** Bumped on every edit; immutable once persisted for a given revision. */
    val revision: Long = 1L,
    val metadata: WorkflowMetadataV1,
    /**
     * Automation-level behavior/presentation that is not represented by the
     * graph itself. Keeping it in the document makes legacy round-trips truly
     * lossless while the legacy Automation row remains the storage format.
     */
    val automationSettings: AutomationSettingsV1 = AutomationSettingsV1(),
    val triggers: List<TriggerDefinitionV1> = emptyList(),
    val constraints: List<ConstraintDefinitionV1> = emptyList(),
    val root: PersistedWorkflowNodeV1,
    val exitPolicy: ExitPolicyV1? = null,
    val variableDeclarations: List<VariableDeclarationV1> = emptyList(),
    val dependencies: WorkflowDependenciesV1 = WorkflowDependenciesV1(),
    val riskDescriptor: RiskDescriptorV1 = RiskDescriptorV1(),
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "Unsupported workflow schema version $schemaVersion (reader supports $SCHEMA_VERSION)"
        }
        require(id.isNotBlank()) { "WorkflowDocumentV1.id must not be blank" }
        require(revision >= 1L) { "WorkflowDocumentV1.revision must be >= 1" }
    }

    /**
     * Deterministic content hash over the definition (excludes volatile
     * fields: timestamps and the revision counter). Two documents with the
     * same semantic content share a hash — diagnostics compare content, not
     * edit counters. Built over the raw JSON so the constructor's validation
     * range (revision >= 1) does not interfere with canonicalization.
     */
    val hash: String by lazy {
        val element = com.nexaflow.domain.workflow.WorkflowDocumentMappers.json
            .encodeToJsonElement(WorkflowDocumentV1.serializer(), this)
            .jsonObject
            .toMutableMap()
            .apply {
                remove("revision")
                val meta = (this["metadata"] as? kotlinx.serialization.json.JsonObject)
                    ?.toMutableMap()
                if (meta != null) {
                    meta.remove("createdAt")
                    meta.remove("updatedAt")
                    put("metadata", kotlinx.serialization.json.JsonObject(meta))
                }
            }
        // Json object/map iteration order is not semantic. Canonicalize every
        // object recursively so equal workflow definitions hash identically
        // even when config maps were constructed in a different key order.
        val canonical = canonicalizeJson(JsonObject(element)).toString()
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(0, 16)
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class WorkflowMetadataV1(
    val name: String,
    val description: String = "",
    /** Presentation only; never interpreted. */
    val icon: String = "",
    val category: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Fields carried by the legacy Automation model that are not encoded by the
 * workflow graph. Defaults match Automation's historical defaults so documents
 * written by v3.86.0 (before this block existed) remain readable.
 *
 * [deepLinkToken] is intentionally absent: it is an authorization secret and
 * Automation marks it transient, so workflow export must never copy it.
 */
@Serializable
data class AutomationSettingsV1(
    val iconColor: Long = 0xFF448AFF,
    val backgroundColor: Long = 0xFF101010,
    val priority: Int = 5,
    val enabled: Boolean = false,
    val showToastOnToggle: Boolean = true,
    /** Legacy TriggerMatchMode name: ANY | ALL. */
    val triggerMatch: String = "ANY",
    val cooldownSeconds: Int = 10,
    val workflowVersion: Int = 1,
    val maintenanceProfile: MaintenanceProfileV1? = null,
)

/**
 * Version-pinned copy of legacy recurring-maintenance metadata. V1 documents
 * must not embed the mutable legacy model directly: adding a field to that
 * model must not silently change schemaVersion=1 on disk.
 */
@Serializable
data class MaintenanceProfileV1(
    val kind: String,
    val window: MaintenanceWindowV1? = null,
    val retryPolicy: MaintenanceRetryPolicyV1 = MaintenanceRetryPolicyV1(),
    val notificationPolicy: String = "IMPORTANT_EVENTS",
    val dependencyAutomationIds: List<String> = emptyList(),
    val recoveryPolicy: String = "DEFAULT",
)

@Serializable
data class MaintenanceWindowV1(
    val startTime: String? = null,
    val endTime: String? = null,
    val allowedDays: Set<Int> = emptySet(),
    val minimumBatteryPercent: Int? = null,
    val chargingRequired: Boolean = false,
    val unmeteredWifiRequired: Boolean = false,
    val screenOffRequired: Boolean = false,
    val deviceIdleRequired: Boolean = false,
    val maximumThermalStatus: Int? = null,
    val minimumFreeStorageBytes: Long? = null,
)

@Serializable
data class MaintenanceRetryPolicyV1(
    val maxAttempts: Int = 1,
    val initialDelayMs: Long = 15 * 60 * 1000L,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 6 * 60 * 60 * 1000L,
)

/** Recursively sorts object keys while preserving array order. */
private fun canonicalizeJson(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.entries
            .sortedBy { it.key }
            .associate { (key, value) -> key to canonicalizeJson(value) }
    )
    is JsonArray -> JsonArray(element.map(::canonicalizeJson))
    else -> element
}

/**
 * Persisted trigger definition. The legacy `Map<String,String>` config is kept
 * verbatim (compatibility with every existing serialized task) but accessors
 * here are typed and validated at the boundary, not inside handlers.
 */
@Serializable
data class TriggerDefinitionV1(
    /** Legacy serialized TriggerType name — the compatibility surface. */
    val type: String,
    val config: Map<String, String> = emptyMap(),
)

@Serializable
data class ConstraintDefinitionV1(
    /** Legacy serialized ConstraintType name — the compatibility surface. */
    val type: String,
    val config: Map<String, String> = emptyMap(),
)

/** How the task behaves when its condition stops being true. */
@Serializable
data class ExitPolicyV1(
    /** Legacy serialized ActionType names + typed config, mapped losslessly. */
    val actions: List<PersistedActionV1> = emptyList(),
    /** When true, exit restores the captured pre-run device state. */
    val revertOnExit: Boolean = false,
)

@Serializable
data class WorkflowDependenciesV1(
    /** Package names of Locale/Tasker-compatible plugins this workflow needs. */
    val plugins: List<String> = emptyList(),
    /** Declared marketplace sub-workflow references, resolved at import. */
    val subworkflows: List<String> = emptyList(),
)

/** Coarse risk class carried on the document for import/marketplace gates. */
@Serializable
data class RiskDescriptorV1(
    /** Legacy RiskLevel name: LOW | MEDIUM | HIGH | CRITICAL. */
    val level: String = "LOW",
    /** Why this level was assigned — shown in import review. */
    val rationale: String = "",
)

/**
 * Typed variable declaration (P0.3 contract surface on the document).
 * Secrets never carry values here — only a keystore-backed reference handle.
 */
@Serializable
data class VariableDeclarationV1(
    val name: String,
    /** Null means inferred at first assignment. */
    val runtimeType: String? = null,
    /** One of the legacy VariableScope names. */
    val scope: String = "WORKFLOW",
    /** Serialized [RuntimeValue] default, null when the variable starts unset. */
    val defaultValue: RuntimeValueV1? = null,
    /** True when [defaultValue] holds a SecretReference-style handle. */
    val isSecret: Boolean = false,
    val description: String = "",
) {
    init {
        require(runtimeType != "SECRET" || isSecret) {
            "SECRET runtimeType requires isSecret=true"
        }
        if (isSecret && defaultValue != null) {
            val handle = when (defaultValue) {
                is RuntimeValueV1.SecretReference -> defaultValue.handle
                // Read legacy-v1 documents that represented the handle as a
                // string, but still reject plaintext secret material.
                is RuntimeValueV1.StringValue -> defaultValue.value
                else -> null
            }
            require(
                handle != null &&
                    com.nexaflow.domain.variables.SecretReferenceRules
                        .validateHandle(handle)
                        .isEmpty()
            ) {
                "Secret variable defaults must be vault handles"
            }
        }
        require(defaultValue !is RuntimeValueV1.SecretReference || isSecret) {
            "SecretReference defaults require isSecret=true"
        }
    }
}

/**
 * Closed, serializable value algebra for declared defaults. Mirrors the domain
 * `RuntimeValue` kinds but lives in the persisted contract so documents never
 * embed runtime classes. Secrets are handles, never values.
 */
@Serializable
sealed interface RuntimeValueV1 {
    @Serializable
    @SerialName("null")
    data object NullValue : RuntimeValueV1

    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : RuntimeValueV1

    @Serializable
    @SerialName("boolean")
    data class BooleanValue(val value: Boolean) : RuntimeValueV1

    @Serializable
    @SerialName("int")
    data class IntValue(val value: Int) : RuntimeValueV1

    @Serializable
    @SerialName("long")
    data class LongValue(val value: Long) : RuntimeValueV1

    @Serializable
    @SerialName("double")
    data class DoubleValue(val value: Double) : RuntimeValueV1 {
        init { require(value.isFinite()) { "RuntimeValueV1.DoubleValue must be finite" } }
    }

    @Serializable
    @SerialName("list")
    data class ListValue(val values: List<RuntimeValueV1>) : RuntimeValueV1

    @Serializable
    @SerialName("object")
    data class ObjectValue(val values: Map<String, RuntimeValueV1>) : RuntimeValueV1

    /**
     * Keystore-backed secret handle. Only the reference is ever persisted —
     * never the secret material itself.
     */
    @Serializable
    @SerialName("secret_ref")
    data class SecretReference(val handle: String) : RuntimeValueV1 {
        init {
            require(
                com.nexaflow.domain.variables.SecretReferenceRules
                    .validateHandle(handle)
                    .isEmpty()
            ) {
                "SecretReference.handle must be a valid vault handle"
            }
        }
    }
}

/**
 * A persisted action: the legacy `Map<String,String>` config is preserved
 * verbatim for lossless round-tripping of every existing task.
 */
@Serializable
data class PersistedActionV1(
    /** Legacy serialized ActionType name — the compatibility surface. */
    val type: String,
    val config: Map<String, String> = emptyMap(),
    /** Per-action exit behavior; null leaves it unset. */
    val endBehavior: PersistedEndBehaviorV1? = null,
    /** Deterministic, position-independent node identity. */
    val nodeId: String,
)

/** Persisted per-action exit behavior (legacy EndBehavior as data). */
@Serializable
data class PersistedEndBehaviorV1(
    /** Legacy serialized EndMode name: LEAVE | REVERT | SET_VALUE | RERUN. */
    val mode: String,
    val config: Map<String, String> = emptyMap(),
)

/**
 * Persisted workflow nodes. Pure data: the runtime maps these onto its
 * [com.nexaflow.core.execution.workflow.WorkflowNode] tree. Everything that
 * was a lambda in the runtime becomes a [ConditionExpr] here.
 */
@Serializable
sealed interface PersistedWorkflowNodeV1 {
    /** Deterministic node identity used by trace, journal and recovery. */
    val nodeId: String

    @Serializable
    @SerialName("action")
    data class Action(
        override val nodeId: String,
        val action: PersistedActionV1,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("sequence")
    data class Sequence(
        override val nodeId: String,
        val children: List<PersistedWorkflowNodeV1>,
        val rollbackOnFailure: Boolean = false,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("parallel")
    data class Parallel(
        override val nodeId: String,
        val children: List<PersistedWorkflowNodeV1>,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("branch")
    data class Branch(
        override val nodeId: String,
        val condition: ConditionExpr,
        val whenTrue: PersistedWorkflowNodeV1,
        val whenFalse: PersistedWorkflowNodeV1? = null,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("loop")
    data class Loop(
        override val nodeId: String,
        val iterations: Int,
        val body: PersistedWorkflowNodeV1,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("delay")
    data class Delay(
        override val nodeId: String,
        val delayMs: Long,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("retry")
    data class Retry(
        override val nodeId: String,
        val body: PersistedWorkflowNodeV1,
        val maxAttempts: Int,
        val backoffMs: Long = 0L,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("timeout")
    data class Timeout(
        override val nodeId: String,
        val body: PersistedWorkflowNodeV1,
        val timeoutMs: Long,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("race")
    data class Race(
        override val nodeId: String,
        val children: List<PersistedWorkflowNodeV1>,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("while")
    data class While(
        override val nodeId: String,
        val condition: ConditionExpr,
        val body: PersistedWorkflowNodeV1,
        val maxIterations: Int = 1_000,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("try")
    data class Try(
        override val nodeId: String,
        val body: PersistedWorkflowNodeV1,
        val catchNode: PersistedWorkflowNodeV1? = null,
        val finallyNode: PersistedWorkflowNodeV1? = null,
    ) : PersistedWorkflowNodeV1

    @Serializable
    @SerialName("wait_until")
    data class WaitUntil(
        override val nodeId: String,
        val condition: ConditionExpr,
        val timeoutMs: Long,
        val pollIntervalMs: Long = 100L,
    ) : PersistedWorkflowNodeV1
}

/**
 * Persisted conditions as data. The runtime lambda (`WorkflowCondition`) is
 * produced only at the execution boundary from a typed factory; the persisted
 * model never carries executable code.
 */
@Serializable
sealed interface ConditionExpr {
    @Serializable
    @SerialName("equals")
    data class Equals(val left: ValueExpr, val right: ValueExpr) : ConditionExpr

    @Serializable
    @SerialName("not_equals")
    data class NotEquals(val left: ValueExpr, val right: ValueExpr) : ConditionExpr

    @Serializable
    @SerialName("compare")
    data class Compare(
        val left: ValueExpr,
        val op: CompareOp,
        val right: ValueExpr,
    ) : ConditionExpr

    @Serializable
    @SerialName("and")
    data class And(val terms: List<ConditionExpr>) : ConditionExpr {
        init { require(terms.size >= 2) { "And requires at least 2 terms" } }
    }

    @Serializable
    @SerialName("or")
    data class Or(val terms: List<ConditionExpr>) : ConditionExpr {
        init { require(terms.size >= 2) { "Or requires at least 2 terms" } }
    }

    @Serializable
    @SerialName("not")
    data class Not(val term: ConditionExpr) : ConditionExpr

    /**
     * Named predicate over declared variables/device state, resolved by a
     * typed runtime factory. `function` is a stable identifier from a fixed
     * registry — never free-form code.
     */
    @Serializable
    @SerialName("function")
    data class Function(
        val name: String,
        val arguments: Map<String, RuntimeValueV1> = emptyMap(),
    ) : ConditionExpr
}

enum class CompareOp { LESS_THAN, LESS_OR_EQUAL, GREATER_THAN, GREATER_OR_EQUAL }

/**
 * Values inside conditions: literal constants or `$`-path references into the
 * run context (the established JSONPath subset), never arbitrary expressions.
 */
@Serializable
sealed interface ValueExpr {
    @Serializable
    @SerialName("literal")
    data class Literal(val value: RuntimeValueV1) : ValueExpr

    /** Context reference, e.g. `$.variables.volume` or `$.pluginOutputs.x`. */
    @Serializable
    @SerialName("context_ref")
    data class ContextRef(val path: String) : ValueExpr {
        init { require(path.startsWith("$.")) { "ContextRef.path must start with '$.'" } }
    }
}
