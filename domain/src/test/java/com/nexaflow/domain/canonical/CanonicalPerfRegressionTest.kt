package com.nexaflow.domain.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T43 — Measured regression ledger tests: the probes are deterministic,
 * the counters are exact, and the checked-in baseline actually binds.
 */
class CanonicalPerfRegressionTest {

    // ------------------------------------------------------------------
    // Determinism
    // ------------------------------------------------------------------

    @Test
    fun measurementIsIdenticalAcrossRepeatedRuns() {
        for (probe in CanonicalPerfRegression.PROBES) {
            val first = CanonicalPerfRegression.measure(probe)
            val second = CanonicalPerfRegression.measure(probe)
            assertEquals(first, second)
        }
    }

    @Test
    fun probeTreeFormulaIsStable() {
        val tree = CanonicalPerfRegression.buildProbeTree("maxDepthSpine")
        val metrics = CanonicalPerformanceBudget.measureAst(tree)
        // The spine sits exactly at the depth limit by contract: 63 nested
        // sequences plus the leaf = 64 nodes at depth 64.
        assertEquals(CanonicalPerfRegression.MAX_DEPTH, metrics.maxDepth)
        assertEquals(CanonicalPerfRegression.MAX_DEPTH, metrics.nodeCount)
    }

    @Test
    fun unknownProbesFailClosed() {
        try {
            CanonicalPerfRegression.buildProbeTree("mystery")
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("unknown probe"))
        }
        try {
            // A real probe but a baseline table missing its entry: the
            // lookup itself must fail closed (measure() succeeds first).
            CanonicalPerfRegression.runProbe(
                "wideFanOut",
                listOf(CanonicalPerfRegression.Baseline("maxDepthSpine", 999)),
            )
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("no baseline"))
        }
    }

    // ------------------------------------------------------------------
    // The baseline binds
    // ------------------------------------------------------------------

    @Test
    fun everyShippedProbeIsWithinItsBaseline() {
        val results = CanonicalPerfRegression.runAll()
        assertEquals(CanonicalPerfRegression.PROBES.size, results.size)
        for (result in results) {
            assertTrue(
                "probe ${result.probe} regressed: ${result.measurement}",
                result.comparison is CanonicalPerfRegression.Comparison.WithinBaseline,
            )
        }
    }

    @Test
    fun anActualRegressionIsTypedWithBaselineAndObservation() {
        // Squeeze a probe's baseline below its true cost to prove the
        // comparison reports a typed regression, never a silent pass.
        val squeezed = listOf(
            CanonicalPerfRegression.Baseline("maxDepthSpine", 1),
            CanonicalPerfRegression.Baseline("wideFanOut", 10_000),
            CanonicalPerfRegression.Baseline("optimizerChurn", 10_000),
        )
        val result = CanonicalPerfRegression.runProbe("maxDepthSpine", squeezed)
        val regressed = result.comparison as CanonicalPerfRegression.Comparison.Regressed
        assertEquals(1, regressed.baseline)
        assertEquals(result.measurement.totalWork, regressed.observed)
        assertTrue(regressed.observed > regressed.baseline)
    }

    @Test
    fun optimizerChurnConvergesInBoundedPasses() {
        val result = CanonicalPerfRegression.runProbe("optimizerChurn")
        // Three adjacent duplicate writes collapse keep-last (2 removed) and
        // three adjacent waits merge (2 removed): exactly 4 rewrites.
        assertEquals(4, result.measurement.optimizerRewrites)
        assertTrue(result.measurement.optimizerRewrites <= CanonicalConsolidationOptimizer.MAX_PASSES)
    }

    @Test
    fun everyProbeHasABaselineAndEveryBaselineHasAProbe() {
        val probes = CanonicalPerfRegression.PROBES.toSet()
        val baselined = CanonicalPerfRegression.BASELINES.map { it.probe }.toSet()
        assertEquals(probes, baselined)
    }
}
