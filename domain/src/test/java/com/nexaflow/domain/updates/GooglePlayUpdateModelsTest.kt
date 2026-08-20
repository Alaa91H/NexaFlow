package com.nexaflow.domain.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayUpdateModelsTest {

    @Test
    fun `default request is conservative`() {
        val request = GooglePlayUpdateRequest()

        assertTrue(request.includeGoogleApps)
        assertFalse(request.includeUserApps)
        assertTrue(request.wifiOnly)
        assertTrue(request.chargingOnly)
        assertEquals(1, request.maxConcurrentDownloads)
        assertEquals(0, request.retryCount)
        assertFalse(request.allowReboot)
        assertTrue(request.requireSilentInstall)
        assertTrue(request.dryRun)
    }

    @Test
    fun `config parsing clamps untrusted numeric values and preserves safe boolean defaults`() {
        val request = GooglePlayUpdateRequest.fromConfig(
            mapOf(
                "packageFilter" to "  com.google.android.youtube  ",
                "maxConcurrentDownloads" to "99",
                "retryCount" to "-3",
                "dryRun" to "not-a-boolean"
            )
        )

        assertEquals("com.google.android.youtube", request.packageFilter)
        assertEquals(4, request.maxConcurrentDownloads)
        assertEquals(0, request.retryCount)
        assertTrue(request.dryRun)
        assertTrue(request.includeGoogleApps)
        assertTrue(request.requireSilentInstall)
    }

    @Test
    fun `root and shizuku do not imply official Play discovery`() {
        val decision = GooglePlayUpdatePlanner.decide(
            GooglePlayUpdateEnvironment(
                deviceOwner = false,
                affiliatedProfileOwner = false,
                rootAvailable = true,
                shizukuRunning = true,
                shizukuGranted = true
            )
        )

        assertEquals(GooglePlayUpdateDecision.PLAY_DISCOVERY_NOT_EXPOSED, decision)
    }

    @Test
    fun `only a managed policy channel changes the decision`() {
        val decision = GooglePlayUpdatePlanner.decide(
            GooglePlayUpdateEnvironment(
                deviceOwner = true,
                affiliatedProfileOwner = true,
                rootAvailable = true,
                shizukuRunning = true,
                shizukuGranted = true,
                managedGooglePlayPolicyAvailable = true
            )
        )

        assertEquals(GooglePlayUpdateDecision.MANAGED_POLICY_REQUIRED, decision)
    }
}
