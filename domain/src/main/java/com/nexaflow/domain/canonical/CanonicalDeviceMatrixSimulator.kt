package com.nexaflow.domain.canonical

import kotlinx.serialization.Serializable

/**
 * T36 — Device matrix simulation (plan §T36).
 *
 * A deterministic, pure-domain device matrix: every [DeviceMatrixProfile]
 * bundles the OEM/ROM family, integration level and SDK band the canonical
 * runtime must support, plus the *expected outcome per canonical primitive*
 * (supported / degraded / unsupported with a typed reason). Tests and CI
 * replay the whole matrix without a device farm; the simulator reports
 * coverage and blind spots (primitives with no fully-supported profile).
 *
 * Contracts pinned by tests:
 *  - Deterministic: the same matrix always yields the same outcomes;
 *  - Fail closed: an outcome must exist for every (profile, primitive)
 *    combination the matrix declares — missing expectations abort the run;
 *  - Auditable: coverage is computed, never asserted.
 */
object CanonicalDeviceMatrixSimulator {

    /** Canonical primitives the runtime supports (T10/T15 surface). */
    enum class Primitive { SET_STATE, SET_VALUE, INVOKE, OPEN, SEND, TRANSFORM, WAIT, OBSERVE }

    /** OEM/ROM families in the support matrix (mirrors core/compat families). */
    enum class DeviceFamily { AOSP, STOCK_GOOGLE, SAMSUNG, XIAOMI_HYPEROS, HUAWEI, CUSTOM_ROM, WEAR_OS }

    /** Integration tier of the profile. */
    enum class IntegrationTier { NORMAL, SHIZUKU, ROOT, SYSTEM_PRIVILEGED }

    /** Android SDK bands (bounded, reviewed yearly). */
    enum class SdkBand { SDK_26_29, SDK_31_34, SDK_35_PLUS }

    /** Expected outcome for one primitive on one profile. */
    enum class Outcome { SUPPORTED, DEGRADED, UNSUPPORTED }

    /** One typed expectation cell (primitive → outcome). */
    @Serializable
    data class Expectation(
        val primitive: Primitive,
        val outcome: Outcome,
    )

    /** One matrix cell: a profile with expectations per primitive. */
    @Serializable
    data class DeviceMatrixProfile(
        val id: String,
        val family: DeviceFamily,
        val integration: IntegrationTier,
        val sdkBand: SdkBand,
        /** Expected outcome per primitive — must cover every Primitive. */
        val expectations: List<Expectation>,
        /** Capabilities the profile grants (informational for diagnostics). */
        val capabilities: List<String> = emptyList(),
    ) {
        init {
            require(id.matches(Regex("[a-z0-9-]{3,64}"))) { "Invalid profile id" }
            val required = Primitive.entries.toSet()
            val declared = expectations.map { it.primitive }.toSet()
            require(declared.size == expectations.size) { "duplicate primitive expectations" }
            require(declared.containsAll(required)) {
                "expectations missing: ${required - declared}"
            }
        }

        fun outcomeFor(primitive: Primitive): Outcome =
            expectations.first { it.primitive == primitive }.outcome
    }

    /** One replay result row. */
    data class MatrixResultRow(
        val profileId: String,
        val primitive: Primitive,
        val outcome: Outcome,
    )

    /** Coverage report over the replayed matrix. */
    data class CoverageReport(
        val profileCount: Int,
        val cellCount: Int,
        val supportedCount: Int,
        val degradedCount: Int,
        val unsupportedCount: Int,
        /** Primitive names with NO fully-supported profile anywhere. */
        val blindSpots: List<String>,
        /** Families with no profile at all (gap in the matrix itself). */
        val missingFamilies: List<DeviceFamily>,
    ) {
        val fullySupportedFraction: Double
            get() = if (cellCount == 0) 0.0 else supportedCount.toDouble() / cellCount
    }

    /** The default support matrix (reviewed, deterministic, serializable). */
    fun defaultMatrix(): List<DeviceMatrixProfile> = listOf(
        DeviceMatrixProfile(
            id = "aosp-normal-31",
            family = DeviceFamily.AOSP,
            integration = IntegrationTier.NORMAL,
            sdkBand = SdkBand.SDK_31_34,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.SUPPORTED), Expectation(Primitive.SET_VALUE, Outcome.SUPPORTED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.SUPPORTED), Expectation(Primitive.SEND, Outcome.SUPPORTED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.SUPPORTED),
            ),
            capabilities = listOf("WRITE_SETTINGS"),
        ),
        DeviceMatrixProfile(
            id = "stock-normal-35",
            family = DeviceFamily.STOCK_GOOGLE,
            integration = IntegrationTier.NORMAL,
            sdkBand = SdkBand.SDK_35_PLUS,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.SUPPORTED), Expectation(Primitive.SET_VALUE, Outcome.SUPPORTED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.SUPPORTED), Expectation(Primitive.SEND, Outcome.SUPPORTED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.SUPPORTED),
            ),
            capabilities = listOf("WRITE_SETTINGS", "WRITE_SECURE_SETTINGS"),
        ),
        DeviceMatrixProfile(
            id = "samsung-normal-33",
            family = DeviceFamily.SAMSUNG,
            integration = IntegrationTier.NORMAL,
            sdkBand = SdkBand.SDK_31_34,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.DEGRADED), Expectation(Primitive.SET_VALUE, Outcome.SUPPORTED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.SUPPORTED), Expectation(Primitive.SEND, Outcome.SUPPORTED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.DEGRADED),
            ),
            capabilities = listOf("WRITE_SETTINGS"),
        ),
        DeviceMatrixProfile(
            id = "xiaomi-root-34",
            family = DeviceFamily.XIAOMI_HYPEROS,
            integration = IntegrationTier.ROOT,
            sdkBand = SdkBand.SDK_31_34,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.SUPPORTED), Expectation(Primitive.SET_VALUE, Outcome.SUPPORTED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.SUPPORTED), Expectation(Primitive.SEND, Outcome.SUPPORTED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.SUPPORTED),
            ),
            capabilities = listOf("WRITE_SECURE_SETTINGS", "SHELL_COMMANDS"),
        ),
        DeviceMatrixProfile(
            id = "huawei-normal-29",
            family = DeviceFamily.HUAWEI,
            integration = IntegrationTier.NORMAL,
            sdkBand = SdkBand.SDK_26_29,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.DEGRADED), Expectation(Primitive.SET_VALUE, Outcome.DEGRADED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.SUPPORTED), Expectation(Primitive.SEND, Outcome.SUPPORTED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.DEGRADED),
            ),
        ),
        DeviceMatrixProfile(
            id = "wear-normal-34",
            family = DeviceFamily.WEAR_OS,
            integration = IntegrationTier.NORMAL,
            sdkBand = SdkBand.SDK_31_34,
            expectations = listOf(
                Expectation(Primitive.SET_STATE, Outcome.UNSUPPORTED), Expectation(Primitive.SET_VALUE, Outcome.UNSUPPORTED), Expectation(Primitive.INVOKE, Outcome.SUPPORTED), Expectation(Primitive.OPEN, Outcome.UNSUPPORTED), Expectation(Primitive.SEND, Outcome.DEGRADED), Expectation(Primitive.TRANSFORM, Outcome.SUPPORTED), Expectation(Primitive.WAIT, Outcome.SUPPORTED), Expectation(Primitive.OBSERVE, Outcome.DEGRADED),
            ),
        ),
    )

    /** All families the matrix must eventually cover. */
    val ALL_FAMILIES: Set<DeviceFamily> = DeviceFamily.entries.toSet()

    // ------------------------------------------------------------------
    // Replay + coverage
    // ------------------------------------------------------------------

    fun replay(matrix: List<DeviceMatrixProfile>): List<MatrixResultRow> =
        matrix.flatMap { profile ->
            Primitive.entries.map { primitive ->
                MatrixResultRow(
                    profileId = profile.id,
                    primitive = primitive,
                    outcome = profile.outcomeFor(primitive),
                )
            }
        }

    fun coverage(matrix: List<DeviceMatrixProfile>): CoverageReport {
        val rows = replay(matrix)
        val byPrimitive = rows.groupBy { it.primitive }
        val blindSpots = byPrimitive
            .filterValues { cells -> cells.none { it.outcome == Outcome.SUPPORTED } }
            .keys
            .map { it.name }
            .sorted()
        val coveredFamilies = matrix.map { it.family }.toSet()
        return CoverageReport(
            profileCount = matrix.size,
            cellCount = rows.size,
            supportedCount = rows.count { it.outcome == Outcome.SUPPORTED },
            degradedCount = rows.count { it.outcome == Outcome.DEGRADED },
            unsupportedCount = rows.count { it.outcome == Outcome.UNSUPPORTED },
            blindSpots = blindSpots,
            missingFamilies = (ALL_FAMILIES - coveredFamilies).sortedBy { it.name },
        )
    }
}
