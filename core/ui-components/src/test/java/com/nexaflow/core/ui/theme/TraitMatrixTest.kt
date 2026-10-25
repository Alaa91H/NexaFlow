package com.nexaflow.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The appearance traits must compose, not replace each other.
 *
 * The old appearance model was a flat list of independent switches, so a
 * user could pick a combination that meant nothing — an accent that was
 * overridden, a background that a monochrome theme flattened anyway. The
 * traits model makes every combination meaningful, and this suite pins that
 * the whole matrix stays reachable and that the stored ids round-trip.
 */
class TraitMatrixTest {

    @Test
    fun everyTraitCombinationIsReachable() {
        val combinations = buildSet {
            for (monochrome in listOf(true, false)) {
                for (colorAccents in listOf(true, false)) {
                    for (outlines in listOf(true, false)) {
                        for (blur in listOf(true, false)) {
                            for (ambient in listOf(true, false)) {
                                for (density in NexaFlowDensity.entries) {
                                    for (background in NexaFlowBackground.entries) {
                                        add(
                                            NexaFlowTraits(
                                                monochrome = monochrome,
                                                colorAccents = colorAccents,
                                                outlines = outlines,
                                                blur = blur,
                                                ambientBackground = ambient,
                                                density = density,
                                                background = background
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Six booleans and two two-valued enums: 2^6 * 2 * 4.
        assertEquals(256, combinations.size)
    }

    @Test
    fun theDefaultTraitsAreTheFullColourExperience() {
        val traits = NexaFlowTraits()
        assertTrue("the default is not monochrome", !traits.monochrome)
        assertTrue("the default does not keep item colors", traits.colorAccents)
        assertTrue("the default draws no outlines", traits.outlines)
        assertTrue("the default has no translucent bars", traits.blur)
        assertTrue("the default draws no ambient shapes", traits.ambientBackground)
        assertEquals(NexaFlowDensity.COMFORTABLE, traits.density)
        assertEquals(NexaFlowBackground.AMBIENT, traits.background)
    }

    @Test
    fun backgroundIdsRoundTripThroughStorage() {
        NexaFlowBackground.entries.forEach { background ->
            assertEquals(
                background,
                NexaFlowBackground.fromStored(background.storedId)
            )
        }
    }

    @Test
    fun anUnknownBackgroundFallsBackToTheDefault() {
        assertEquals(
            NexaFlowBackground.AMBIENT,
            NexaFlowBackground.fromStored("not-a-background")
        )
        assertEquals(
            NexaFlowBackground.AMBIENT,
            NexaFlowBackground.fromStored(null)
        )
    }

    @Test
    fun changingOneTraitProducesADifferentTraitSet() {
        val base = NexaFlowTraits()
        assertNotEquals(base, base.copy(monochrome = true))
        assertNotEquals(base, base.copy(outlines = false))
        assertNotEquals(base, base.copy(density = NexaFlowDensity.COMPACT))
        assertNotEquals(base, base.copy(background = NexaFlowBackground.EMBER))
    }

    @Test
    fun everyAccentSeedIsReachableByItsStoredId() {
        listOf("indigo", "teal", "violet", "emerald", "amber", "rose").forEach { id ->
            assertNotEquals(
                "accent $id is missing from the palette",
                NexaFlowAccentSeeds.getValue(id),
                NexaFlowAccentSeeds.getValue(DefaultAccentId)
            )
        }
    }

    @Test
    fun anUnknownAccentFallsBackToTheDefault() {
        assertEquals(
            NexaFlowAccentSeeds.getValue(DefaultAccentId),
            accentSeedFor("not-an-accent")
        )
        assertEquals(
            NexaFlowAccentSeeds.getValue(DefaultAccentId),
            accentSeedFor(null)
        )
    }
}
