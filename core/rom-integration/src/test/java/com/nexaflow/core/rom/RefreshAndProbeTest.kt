package com.nexaflow.core.rom

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the grant-visibility defect: `refreshAndProbe()` used
 * to be `refreshRootAvailability(); isRootAvailable()` — and the storm-spacing
 * guard inside [SystemAppStatusDetector.isRootAvailable] silently swallowed
 * the invalidation, so a root grant that landed right after a previous probe
 * stayed hidden through every "refresh" until the 5s spacing window expired.
 *
 * Every test seeds its deterministic baseline with a forced [refreshAndProbe]
 * whose stub answers `false` (the "not granted yet" reality), so the class
 * never depends on probe state left behind by another test or on wall-clock
 * distance between two statements.
 */
class RefreshAndProbeTest {

    private var probeRuns = 0
    private var granted = false

    @Before
    fun setUp() {
        probeRuns = 0
        granted = false
        SystemAppStatusDetector.pathResolution = { true }
        SystemAppStatusDetector.rootProbe = {
            probeRuns++
            granted
        }
        // Seed: one forced probe records reality=false and anchors the
        // storm-spacing window to NOW. Clear the 2s TTL cache afterwards so
        // the test body starts from "cache invalid, spacing window open".
        SystemAppStatusDetector.refreshAndProbe()
        SystemAppStatusDetector.refreshRootAvailability()
        SystemAppStatusDetector.probeSpacingMs = 5_000L
    }

    @After
    fun tearDown() {
        SystemAppStatusDetector.probeSpacingMs = 5_000L
        SystemAppStatusDetector.pathResolution = null
        SystemAppStatusDetector.rootProbe = null
        SystemAppStatusDetector.refreshRootAvailability()
    }

    @Test
    fun `refreshAndProbe bypasses the storm-spacing guard and observes the fresh grant`() {
        granted = true // the user just tapped Allow in the root manager

        // Invalidate-only cannot observe the grant: the spacing window the
        // seed opened is still active, so the old (false) answer is reused.
        assertFalse(
            "invalidate-only must reuse the seeded answer while the spacing window is open",
            SystemAppStatusDetector.isRootAvailable()
        )

        // The forced refresh observes the new reality immediately — this is
        // the exact production contract that was silently broken before.
        assertTrue(
            "refreshAndProbe must bypass the spacing guard and re-probe",
            SystemAppStatusDetector.refreshAndProbe()
        )
        assertTrue(
            "refreshAndProbe must actually run a fresh probe, not reuse the cache",
            probeRuns >= 2
        )
    }

    @Test
    fun `refreshAndProbe result is then cached for ordinary isRootAvailable callers`() {
        granted = true
        assertTrue(SystemAppStatusDetector.refreshAndProbe())
        val runsAfterForcedProbe = probeRuns

        repeat(10) {
            assertTrue(SystemAppStatusDetector.isRootAvailable())
        }

        assertEquals(
            "after the forced probe, ordinary callers must reuse the cached answer",
            runsAfterForcedProbe,
            probeRuns
        )
    }

    @Test
    fun `invalidate-only can never observe a grant no matter how much wall time passes`() {
        // An effectively infinite spacing window means every ordinary
        // invalidate+query pair reuses the cached answer — proving that only
        // the forced refresh can ever see a grant transition. This is the
        // reason refreshAndProbe exists as a separate, deliberate path.
        SystemAppStatusDetector.probeSpacingMs = Long.MAX_VALUE / 2
        granted = true

        SystemAppStatusDetector.refreshRootAvailability()
        assertFalse(
            "invalidate-only must reuse the cached answer while the spacing window is open",
            SystemAppStatusDetector.isRootAvailable()
        )
        assertEquals("no fresh probe may run for invalidate-only", 1, probeRuns)

        assertTrue(
            "the forced refresh must still observe the grant regardless of the window",
            SystemAppStatusDetector.refreshAndProbe()
        )
        assertEquals("the forced refresh runs exactly one more probe", 2, probeRuns)
    }
}
