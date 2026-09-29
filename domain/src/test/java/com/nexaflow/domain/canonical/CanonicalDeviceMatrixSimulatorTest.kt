package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T36 — Device matrix simulator tests: every profile must declare a complete
 * expectation set, replay must be deterministic, and coverage must expose
 * blind spots instead of hiding them.
 */
class CanonicalDeviceMatrixSimulatorTest {

    private val fullExpectations = CanonicalDeviceMatrixSimulator.Primitive.entries.map {
        CanonicalDeviceMatrixSimulator.Expectation(
            it,
            CanonicalDeviceMatrixSimulator.Outcome.SUPPORTED,
        )
    }

    private fun profile(
        id: String = "test-profile",
        family: CanonicalDeviceMatrixSimulator.DeviceFamily =
            CanonicalDeviceMatrixSimulator.DeviceFamily.AOSP,
        expectations: List<CanonicalDeviceMatrixSimulator.Expectation> = fullExpectations,
    ) = CanonicalDeviceMatrixSimulator.DeviceMatrixProfile(
        id = id,
        family = family,
        integration = CanonicalDeviceMatrixSimulator.IntegrationTier.NORMAL,
        sdkBand = CanonicalDeviceMatrixSimulator.SdkBand.SDK_31_34,
        expectations = expectations,
    )

    // ------------------------------------------------------------------
    // Constructor validation
    // ------------------------------------------------------------------

    @Test
    fun profileWithCompleteExpectationsIsAccepted() {
        val built = profile()
        assertEquals(8, built.expectations.size)
    }

    @Test
    fun missingPrimitiveExpectationFailsClosed() {
        val incomplete = fullExpectations.filter {
            it.primitive != CanonicalDeviceMatrixSimulator.Primitive.SEND
        }
        try {
            profile(expectations = incomplete)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("missing"))
        }
    }

    @Test
    fun duplicatePrimitiveExpectationFailsClosed() {
        val duplicated = fullExpectations + fullExpectations.first()
        try {
            profile(expectations = duplicated)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("duplicate"))
        }
    }

    @Test
    fun invalidProfileIdFailsClosed() {
        try {
            profile(id = "Bad_Id With Spaces")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("profile id"))
        }
    }

    // ------------------------------------------------------------------
    // Replay
    // ------------------------------------------------------------------

    @Test
    fun replayProducesOneCellPerProfileTimesPrimitive() {
        val matrix = listOf(
            profile(id = "profile-a"),
            profile(id = "profile-b"),
        )

        val rows = CanonicalDeviceMatrixSimulator.replay(matrix)

        assertEquals(2 * CanonicalDeviceMatrixSimulator.Primitive.entries.size, rows.size)
        assertEquals(
            setOf("profile-a", "profile-b"),
            rows.map { it.profileId }.toSet(),
        )
    }

    @Test
    fun replayIsDeterministic() {
        val matrix = CanonicalDeviceMatrixSimulator.defaultMatrix()

        assertEquals(
            CanonicalDeviceMatrixSimulator.replay(matrix),
            CanonicalDeviceMatrixSimulator.replay(matrix),
        )
    }

    @Test
    fun defaultMatrixReplaysWithoutErrors() {
        val matrix = CanonicalDeviceMatrixSimulator.defaultMatrix()

        assertTrue(matrix.size >= 6)
        val rows = CanonicalDeviceMatrixSimulator.replay(matrix)
        assertEquals(matrix.size * CanonicalDeviceMatrixSimulator.Primitive.entries.size, rows.size)
    }

    // ------------------------------------------------------------------
    // Coverage
    // ------------------------------------------------------------------

    @Test
    fun coverageCountsOutcomesExactly() {
        val expectations = fullExpectations.map {
            when (it.primitive) {
                CanonicalDeviceMatrixSimulator.Primitive.SEND ->
                    CanonicalDeviceMatrixSimulator.Expectation(
                        it.primitive,
                        CanonicalDeviceMatrixSimulator.Outcome.DEGRADED,
                    )
                CanonicalDeviceMatrixSimulator.Primitive.OPEN ->
                    CanonicalDeviceMatrixSimulator.Expectation(
                        it.primitive,
                        CanonicalDeviceMatrixSimulator.Outcome.UNSUPPORTED,
                    )
                else -> it
            }
        }

        val report = CanonicalDeviceMatrixSimulator.coverage(
            listOf(profile(id = "only-one", expectations = expectations)),
        )

        assertEquals(8, report.cellCount)
        assertEquals(6, report.supportedCount)
        assertEquals(1, report.degradedCount)
        assertEquals(1, report.unsupportedCount)
    }

    @Test
    fun blindSpotsListPrimitivesWithNoSupportedProfile() {
        val degradedEverything = CanonicalDeviceMatrixSimulator.Primitive.entries.map {
            CanonicalDeviceMatrixSimulator.Expectation(
                it,
                CanonicalDeviceMatrixSimulator.Outcome.DEGRADED,
            )
        }
        val report = CanonicalDeviceMatrixSimulator.coverage(
            listOf(profile(id = "weak-device", expectations = degradedEverything)),
        )

        assertEquals(
            CanonicalDeviceMatrixSimulator.Primitive.entries.size,
            report.blindSpots.size,
        )
        assertTrue("SET_STATE" in report.blindSpots)
    }

    @Test
    fun missingFamiliesExposeMatrixGaps() {
        val report = CanonicalDeviceMatrixSimulator.coverage(
            listOf(
                profile(
                    id = "aosp-only",
                    family = CanonicalDeviceMatrixSimulator.DeviceFamily.AOSP,
                ),
            ),
        )

        assertTrue(
            CanonicalDeviceMatrixSimulator.DeviceFamily.SAMSUNG in report.missingFamilies,
        )
        assertTrue(
            CanonicalDeviceMatrixSimulator.DeviceFamily.WEAR_OS in report.missingFamilies,
        )
        assertEquals(
            CanonicalDeviceMatrixSimulator.DeviceFamily.entries.size - 1,
            report.missingFamilies.size,
        )
    }

    @Test
    fun fullySupportedFractionIsExact() {
        val report = CanonicalDeviceMatrixSimulator.coverage(
            listOf(profile(id = "full-support")),
        )
        assertEquals(1.0, report.fullySupportedFraction, 0.0001)
    }
}
