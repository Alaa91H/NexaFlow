package com.nexaflow.core.pluginsdk

/**
 * The public protocol roles that an external automation plug-in can expose.
 * A plug-in may advertise more than one role, but each discovered instance is
 * resolved and invoked through exactly one role-specific adapter.
 */
enum class PluginType {
    SETTING,
    CONDITION,
    EVENT
}

/**
 * Protocol families recognised by NexaFlow. Recognition only declares how the
 * host may communicate; it never grants access to internal services.
 */
enum class PluginProtocol {
    LOCALE_BASE,
    TASKER_EXTENSION
}

/**
 * Trust is deliberately conservative. Discovery never upgrades an arbitrary
 * installed package beyond [UNTRUSTED]; a later, explicit user-policy decision
 * is required before a high-risk declaration can be invoked.
 */
enum class PluginTrustLevel {
    /** Package presence alone; no capability may be invoked. */
    UNTRUSTED,
    /** Discovery verified the documented Locale component pair only. */
    LOCALE_COMPATIBLE,
    /** Locale compatibility plus explicitly negotiated Tasker extension metadata. */
    TASKER_EXTENDED,
    /**
     * Legacy value retained for imported manifests. User consent is evaluated by
     * [PluginInvocationPolicy.requireApproval], never inferred from this value.
     */
    @Deprecated("Use protocol compatibility plus requireApproval")
    USER_APPROVED
}

/** Requirements verified by discovery before an instance can be enabled. */
enum class PluginPermissionRequirement {
    APPLICATION_ENABLED,
    EDIT_ACTIVITY_EXPORTED,
    RECEIVER_EXPORTED,
    EDIT_ACTIVITY_ENABLED,
    RECEIVER_ENABLED,
    HOST_CAN_SEND_INTENT,
    INTERNAL_STORAGE_INSTALL
}

/** Compatibility outcomes are visible to UI/import instead of silently failing. */
enum class PluginCompatibilityStatus {
    COMPATIBLE,
    PARTIALLY_COMPATIBLE,
    MISSING_EDIT_ACTIVITY,
    MISSING_RECEIVER,
    AMBIGUOUS_RECEIVER,
    PERMISSION_DENIED,
    DISABLED,
    UNSUPPORTED_PROTOCOL,
    INVALID_DECLARATION
}

/** Only values which can cross a Bundle boundary may enter the plug-in bridge. */
enum class PluginValueType {
    STRING,
    BOOLEAN,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    LIST,
    MAP
}

/** Bounded schema field used for optional plug-in inputs and advertised outputs. */
data class PluginValueSpec(
    val name: String,
    val type: PluginValueType,
    val required: Boolean = false,
    val maximumLength: Int = 512
) {
    init {
        require(name.matches(NAME_PATTERN)) { "Invalid plugin value name" }
        require(maximumLength in 1..4_096) { "maximumLength must be in 1..4096" }
    }

    private companion object {
        val NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    }
}

/**
 * Metadata declared or derived for one externally visible feature. This is
 * descriptive data, not executable code and not an entitlement to a backend.
 */
data class PluginCapabilityDeclaration(
    val id: String,
    val type: PluginType,
    val displayName: String,
    val inputSchema: List<PluginValueSpec> = emptyList(),
    val outputSchema: List<PluginValueSpec> = emptyList(),
    val requiresUserApproval: Boolean = true
) {
    init {
        require(id.matches(CAPABILITY_ID_PATTERN)) { "Invalid plugin capability id" }
        require(displayName.isNotBlank() && displayName.length <= 120) {
            "Plugin capability displayName must contain at most 120 characters"
        }
        require(inputSchema.map { it.name }.distinct().size == inputSchema.size) {
            "Plugin input schema names must be unique"
        }
        require(outputSchema.map { it.name }.distinct().size == outputSchema.size) {
            "Plugin output schema names must be unique"
        }
    }

    private companion object {
        val CAPABILITY_ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,95}")
    }
}

/** Stable identity of an explicit Android component without exposing Android types. */
data class PluginComponentRef(
    val packageName: String,
    val className: String
) {
    init {
        require(packageName.matches(PACKAGE_PATTERN)) { "Invalid plugin package name" }
        require(className.matches(CLASS_PATTERN)) { "Invalid plugin component class name" }
    }

    val flattened: String get() = "$packageName/$className"

    private companion object {
        val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        val CLASS_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
    }
}

/**
 * Normalized discovery entry. The host can construct it only after validating
 * the component pair for the requested [type]. It contains no live Context,
 * binder, class loader, secret, or arbitrary Bundle object.
 */
data class PluginDescriptor(
    val id: String,
    val packageName: String,
    val versionName: String? = null,
    val type: PluginType,
    val protocol: PluginProtocol,
    val editActivity: PluginComponentRef? = null,
    val receiver: PluginComponentRef? = null,
    val displayName: String,
    val capabilities: List<PluginCapabilityDeclaration> = emptyList(),
    val requiredChecks: Set<PluginPermissionRequirement> = emptySet(),
    val supportsConfiguration: Boolean = false,
    val supportsOutputVariables: Boolean = false,
    val supportsEventPayload: Boolean = false,
    val trustLevel: PluginTrustLevel = PluginTrustLevel.UNTRUSTED,
    val compatibility: PluginCompatibilityStatus = PluginCompatibilityStatus.INVALID_DECLARATION
) {
    init {
        require(id.matches(ID_PATTERN)) { "Invalid plugin descriptor id" }
        require(listOfNotNull(editActivity, receiver).all { it.packageName == packageName }) {
            "Plugin components must belong to the declared package"
        }
        require(displayName.isNotBlank() && displayName.length <= 120) {
            "Plugin displayName must contain at most 120 characters"
        }
        require(capabilities.map { it.id }.distinct().size == capabilities.size) {
            "Plugin capability ids must be unique"
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.:/-]{0,191}")
    }
}

/** Host-side policy. It bounds the adapter and never grants platform privileges. */
data class PluginInvocationPolicy(
    /** Wall-clock ceiling for a single adapter invocation. */
    val maxTimeoutMs: Long = 5_000L,
    /** Enables primitive-only outputs after an explicit schema and approval check. */
    val allowOutput: Boolean = true,
    /** Requires saved per-instance user approval before external execution. */
    val requireApproval: Boolean = true,
    val allowVariableBridge: Boolean = true,
    val maximumPayloadBytes: Int = LocaleContract.MAX_BUNDLE_BYTES,
    val deduplicationWindowMs: Long = 30_000L
) {
    init {
        require(maxTimeoutMs in 1_000L..300_000L) { "maxTimeoutMs must be in 1,000..300,000ms" }
        require(maximumPayloadBytes in 1..LocaleContract.MAX_BUNDLE_BYTES) {
            "maximumPayloadBytes must not exceed the Locale bundle limit"
        }
        require(deduplicationWindowMs in 0L..300_000L) {
            "deduplicationWindowMs must be in 0..300,000ms"
        }
    }

    /** Read-only aliases retained for integrations compiled against the first SDK draft. */
    @Deprecated("Use maxTimeoutMs")
    val timeoutMs: Long get() = maxTimeoutMs

    @Deprecated("Use allowOutput")
    val allowOutputVariables: Boolean get() = allowOutput

    @Deprecated("Use requireApproval")
    val requireUserApproval: Boolean get() = requireApproval
}

/** Explicit state triad; UNKNOWN is not implicitly coerced to false. */
enum class PluginConditionState {
    SATISFIED,
    UNSATISFIED,
    UNKNOWN
}

/** Structured terminal state for the external boundary. */
enum class PluginInvocationStatus {
    SUCCESS,
    FAILED,
    UNAVAILABLE,
    TIMED_OUT,
    CANCELLED,
    UNKNOWN,
    DUPLICATE
}

/**
 * Primitive-only data transferred by the value bridge. All maps/lists must
 * recursively contain [PluginValue]; arbitrary Parcelable/Serializable objects
 * are rejected by the Android adapter before this representation is created.
 */
sealed interface PluginValue {
    data class StringValue(val value: String) : PluginValue
    data class BooleanValue(val value: Boolean) : PluginValue
    data class IntegerValue(val value: Int) : PluginValue
    data class LongValue(val value: Long) : PluginValue
    data class FloatValue(val value: Float) : PluginValue
    data class DoubleValue(val value: Double) : PluginValue
    data class ListValue(val value: List<PluginValue>) : PluginValue
    data class MapValue(val value: Map<String, PluginValue>) : PluginValue
}

/** Safe result leaving an adapter. `message` is a redacted human summary. */
data class PluginInvocationResult(
    val status: PluginInvocationStatus,
    val correlationId: String,
    val message: String,
    val outputs: Map<String, PluginValue> = emptyMap(),
    val durationMs: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(correlationId.matches(CORRELATION_PATTERN)) { "Invalid correlation id" }
        require(message.length <= 1_024) { "Plugin result message is too long" }
        require(outputs.keys.all { it.matches(OUTPUT_NAME_PATTERN) }) { "Invalid plugin output name" }
        require(metadata.keys.all { it.matches(METADATA_NAME_PATTERN) }) { "Invalid plugin metadata name" }
        require(metadata.values.all { it.length <= 512 }) { "Plugin metadata value is too long" }
    }

    private companion object {
        val CORRELATION_PATTERN = Regex("[A-Za-z0-9_.:-]{1,128}")
        val OUTPUT_NAME_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
        val METADATA_NAME_PATTERN = Regex("[a-z][A-Za-z0-9_]{0,63}")
    }
}
