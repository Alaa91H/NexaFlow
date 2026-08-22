package com.nexaflow.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun disabledChecksNeverReserveAnUpdateNotification() = runBlocking {
        val preferences = UpdatePreferences(context)
        preferences.setAutomaticChecksEnabled(false)

        assertFalse(preferences.claimNotification("3.38.8-test-disabled"))
        assertFalse(preferences.settings.first().automaticChecksEnabled)
    }

    @Test
    fun notificationClaimIsAtomicPerVersionAndNewVersionsMayNotify() = runBlocking {
        val preferences = UpdatePreferences(context)
        preferences.setAutomaticChecksEnabled(true)
        val firstVersion = "3.38.8-test-${System.nanoTime()}"
        val secondVersion = "3.38.9-test-${System.nanoTime()}"
        try {
            assertTrue(preferences.claimNotification(firstVersion))
            assertFalse(preferences.claimNotification(firstVersion))
            assertTrue(preferences.claimNotification(secondVersion))
        } finally {
            preferences.setAutomaticChecksEnabled(false)
        }
    }

    @Test
    fun installedMatchingReleaseClearsOnlyItsOwnNotificationReservation() = runBlocking {
        val preferences = UpdatePreferences(context)
        val installed = "3.39.1-test-${System.nanoTime()}"
        val newer = "3.39.2-test-${System.nanoTime()}"
        try {
            preferences.setAutomaticChecksEnabled(true)
            assertTrue(preferences.claimNotification(installed))
            assertTrue(preferences.clearNotificationReservationForInstalledVersion(installed))
            assertTrue(preferences.claimNotification(installed))
            assertTrue(preferences.claimNotification(newer))
            assertFalse(preferences.clearNotificationReservationForInstalledVersion(installed))
        } finally {
            preferences.setAutomaticChecksEnabled(false)
        }
    }

    @Test
    fun frequencyPersistsOnlySupportedCadences() = runBlocking {
        val preferences = UpdatePreferences(context)
        try {
            UpdateCheckFrequency.entries.forEach { frequency ->
                preferences.setFrequency(frequency)
                assertEquals(frequency, preferences.settings.first().frequency)
            }
        } finally {
            preferences.setFrequency(UpdateCheckFrequency.MONTHLY)
            preferences.setAutomaticChecksEnabled(false)
        }
    }
}
