package com.nexaflow.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsDeliveryStoreTest {

    private lateinit var store: SmsDeliveryStore

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SmsDeliveryStore(context)
        listOf("sms-a", "sms-b").forEach { store.clearForAutomation(it) }
    }

    @Test
    fun duplicateFingerprintIsRejectedAcrossClaims() = runBlocking {
        val fingerprint = "a".repeat(64)

        assertTrue(store.claim("sms-a", fingerprint, cooldownMillis = 0L, occurredAt = 1_000L))
        assertFalse(store.claim("sms-a", fingerprint, cooldownMillis = 0L, occurredAt = 2_000L))
    }

    @Test
    fun cooldownIsDurableAcrossDifferentMessages() = runBlocking {
        assertTrue(
            store.claim(
                automationId = "sms-a",
                fingerprint = "a".repeat(64),
                cooldownMillis = 10_000L,
                occurredAt = 10_000L
            )
        )
        assertFalse(
            store.claim(
                automationId = "sms-a",
                fingerprint = "b".repeat(64),
                cooldownMillis = 10_000L,
                occurredAt = 15_000L
            )
        )
        assertTrue(
            store.claim(
                automationId = "sms-a",
                fingerprint = "c".repeat(64),
                cooldownMillis = 10_000L,
                occurredAt = 20_001L
            )
        )
    }

    @Test
    fun samePhysicalMessageCanBeClaimedByDifferentAutomations() = runBlocking {
        val fingerprint = "d".repeat(64)

        assertTrue(store.claim("sms-a", fingerprint, cooldownMillis = 0L, occurredAt = 1_000L))
        assertTrue(store.claim("sms-b", fingerprint, cooldownMillis = 0L, occurredAt = 1_000L))
    }
}
