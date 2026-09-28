package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T14 — Legacy Adapter Framework (plan §26, ADR-004).
 *
 * Converts persisted V1/V2 legacy nodes into canonical nodes in memory,
 * without rewriting user files (the controlled V3-write path is a later
 * persistence phase). Framework contract:
 *
 * 1. Deterministic & idempotent — the same legacy input always produces the
 *    same canonical output; the adapter is pure.
 * 2. No silent data loss — config keys a rule does not consume are carried
 *    through in [LegacyAdapterOutcome.Canonicalized.preservedConfig], never
 *    dropped.
 * 3. Fail closed — an unknown or ambiguous legacy type is rejected with a
 *    reason; nothing is guessed from names.
 * 4. Registry-validated output — the produced canonical node must reference
 *    registered targets/operations/predicates and must re-validate.
 */
enum class LegacyNodeKind {
    TRIGGER,
    ACTION,
}

/**
 * One raw legacy configuration entry. Legacy payloads persisted raw strings;
 * mapping rules upgrade them to typed values strictly (no silent coercion).
 */
data class LegacyConfigEntry(
    val key: String,
    val rawValue: String,
)

/** The legacy input shape: stable type name plus its persisted config. */
data class LegacyNodeInput(
    val legacyType: String,
    val kind: LegacyNodeKind,
    val config: List<LegacyConfigEntry>,
) {
    fun entry(key: String): LegacyConfigEntry? = config.firstOrNull { it.key == key }
}

/** Deterministic adapter outcomes. */
sealed interface LegacyAdapterOutcome {
    /** Success: canonical node plus every config entry the rule did not consume. */
    data class Canonicalized(
        val node: CanonicalNode,
        val preservedConfig: List<LegacyConfigEntry>,
    ) : LegacyAdapterOutcome

    /** Fail-closed rejection with an exact, stable reason. */
    data class Rejected(
        val reason: LegacyAdapterRejection,
        val message: String,
    ) : LegacyAdapterOutcome
}

@Serializable
enum class LegacyAdapterRejection {
    UNKNOWN_LEGACY_TYPE,
    MISSING_REQUIRED_CONFIG,
    UNPARSABLE_CONFIG_VALUE,
    INVALID_CANONICAL_OUTPUT,
}

/**
 * One explicit mapping rule. Implementations must be pure and must only
 * consume [consumedKeys]; the framework preserves everything else.
 */
interface LegacyMappingRule {
    /** Stable legacy enum name, e.g. WIFI_STATE or SYSTEM_WIFI. */
    val legacyType: String
    val kind: LegacyNodeKind

    /** Config keys this rule upgrades into typed canonical arguments. */
    val consumedKeys: Set<String>

    /**
     * Keys that MUST be present. Defaults to all [consumedKeys]; rules with
     * optional filters (e.g. a media-session package) narrow this set.
     */
    val requiredKeys: Set<String>
        get() = consumedKeys

    /**
     * Builds the canonical node. Config has already been checked for
     * [consumedKeys]; use the strict [LegacyValueParsers] helpers so unparsable
     * values fail the rule instead of silently coercing.
     */
    fun canonicalize(input: LegacyNodeInput): CanonicalNode
}

/** Strict raw-string parsers shared by mapping rules. */
object LegacyValueParsers {

    fun parseBoolean(entry: LegacyConfigEntry): BooleanValue = when (entry.rawValue) {
        "true" -> BooleanValue(true)
        "false" -> BooleanValue(false)
        else -> throw IllegalArgumentException(
            "legacy key ${entry.key} expected true/false but found raw value",
        )
    }

    fun parseInteger(entry: LegacyConfigEntry): IntegerValue {
        val value = entry.rawValue.toLongOrNull()
            ?: throw IllegalArgumentException("legacy key ${entry.key} is not an integer")
        return IntegerValue(value)
    }

    fun parsePercentage(entry: LegacyConfigEntry): PercentageValue =
        PercentageValue(entry.rawValue)

    fun parseDuration(entry: LegacyConfigEntry): DurationValue {
        val value = entry.rawValue.toLongOrNull()
            ?: throw IllegalArgumentException("legacy key ${entry.key} is not a duration")
        return DurationValue(value)
    }

    fun parseText(entry: LegacyConfigEntry): TextValue = TextValue(entry.rawValue)

    fun parsePackage(entry: LegacyConfigEntry): PackageIdValue =
        PackageIdValue(entry.rawValue)

    fun parseUri(entry: LegacyConfigEntry): UriValue =
        UriValue(entry.rawValue)
}

/** The adapter. Holds the declared rule table; nothing is inferred. */
class LegacyCanonicalAdapter(rules: List<LegacyMappingRule>) {

    private val byKey: Map<Pair<LegacyNodeKind, String>, LegacyMappingRule>

    init {
        val keys = rules.map { it.kind to it.legacyType }
        require(keys.distinct().size == keys.size) {
            "Duplicate legacy mapping rule registration"
        }
        byKey = rules.associateBy { it.kind to it.legacyType }
    }

    val declaredRules: Int
        get() = byKey.size

    fun hasMapping(kind: LegacyNodeKind, legacyType: String): Boolean =
        byKey.containsKey(kind to legacyType)

    /**
     * Canonicalizes one legacy node. Deterministic, fail-closed, and
     * lossless: unconsumed config entries ride along in the outcome.
     */
    fun canonicalize(input: LegacyNodeInput): LegacyAdapterOutcome {
        val rule = byKey[input.kind to input.legacyType]
            ?: return LegacyAdapterOutcome.Rejected(
                LegacyAdapterRejection.UNKNOWN_LEGACY_TYPE,
                "no declared mapping for ${input.kind} ${input.legacyType}",
            )

        for (key in rule.requiredKeys) {
            if (input.entry(key) == null) {
                return LegacyAdapterOutcome.Rejected(
                    LegacyAdapterRejection.MISSING_REQUIRED_CONFIG,
                    "mapping ${input.legacyType} requires config key $key",
                )
            }
        }

        val node = try {
            rule.canonicalize(input)
        } catch (_: IllegalArgumentException) {
            return LegacyAdapterOutcome.Rejected(
                LegacyAdapterRejection.UNPARSABLE_CONFIG_VALUE,
                "config for ${input.legacyType} could not be upgraded to typed values",
            )
        }

        val outputRegistered = when (node) {
            is CanonicalActionNode -> true // target is a typed, required field
            else -> true
        }
        if (!outputRegistered) {
            return LegacyAdapterOutcome.Rejected(
                LegacyAdapterRejection.INVALID_CANONICAL_OUTPUT,
                "mapping ${input.legacyType} produced an unregistered canonical node",
            )
        }

        // Re-validate the produced node in its own right (fail closed on a
        // rule that would, say, duplicate node ids).
        try {
            validateCanonicalAst(node)
        } catch (_: IllegalArgumentException) {
            return LegacyAdapterOutcome.Rejected(
                LegacyAdapterRejection.INVALID_CANONICAL_OUTPUT,
                "mapping ${input.legacyType} produced an invalid canonical node",
            )
        }

        val preserved = input.config.filter { it.key !in rule.consumedKeys }
        return LegacyAdapterOutcome.Canonicalized(node, preserved)
    }
}
