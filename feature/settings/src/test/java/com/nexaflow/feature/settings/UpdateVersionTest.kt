package com.nexaflow.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun sameReleaseWithOrWithoutVPrefixIsNeverAnUpdate() {
        assertFalse(UpdateVersion.shouldOfferUpdate("v3.38.8", "3.38.8"))
        assertFalse(UpdateVersion.shouldOfferUpdate("3.38.8", "v3.38.8"))
    }

    @Test
    fun exactInstalledReleaseIsNeverOfferedAsAnUpdate() {
        assertFalse(UpdateVersion.shouldOfferUpdate("v3.39.1", "v3.39.1"))
        assertFalse(UpdateVersion.shouldOfferUpdate(" v3.39.1 ", "3.39.1"))
        assertTrue(UpdateVersion.isSameRelease("v3.39.1", "3.39.1"))
    }

    @Test
    fun taggedReleaseIsNotNewerThanDevelopmentBuildAlreadyAfterThatTag() {
        assertFalse(
            UpdateVersion.isStrictlyNewer(
                remote = "v3.38.8",
                installed = "v3.38.8-5-g46742c5"
            )
        )
    }

    @Test
    fun onlyHigherSemanticVersionIsAnUpdate() {
        assertTrue(UpdateVersion.isStrictlyNewer("v3.38.9", "v3.38.8"))
        assertTrue(UpdateVersion.isStrictlyNewer("v3.39.0", "v3.38.9"))
        assertFalse(UpdateVersion.isStrictlyNewer("v3.38.7", "v3.38.8"))
    }

    @Test
    fun stableReleaseSupersedesPrereleaseWithTheSameNumbers() {
        assertTrue(UpdateVersion.isStrictlyNewer("v3.38.8", "v3.38.8-rc.1"))
        assertFalse(UpdateVersion.isStrictlyNewer("v3.38.8-rc.1", "v3.38.8"))
    }

    @Test
    fun malformedVersionsFailClosedAndNeverPromptAnUpdate() {
        assertFalse(UpdateVersion.isStrictlyNewer("latest", "v3.38.8"))
        assertFalse(UpdateVersion.isStrictlyNewer("v3.38.9", "development-build"))
    }
}
