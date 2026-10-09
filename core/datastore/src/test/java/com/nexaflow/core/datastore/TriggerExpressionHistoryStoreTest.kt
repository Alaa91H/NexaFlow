package com.nexaflow.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.nexaflow.core.security.OccurrenceIdentityHmac
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerExpressionHistoryStoreTest {
    private var now = 100L
    private val hmac = OccurrenceIdentityHmac { source, id ->
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("$source:$id".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private val backing = MemoryPreferencesDataStore()
    private fun store() = TriggerExpressionHistoryStore(backing, hmac, { now })


    @Test
    fun persistsMinimalHistoryAndRejectsDuplicateAfterReload() = runTest {
        val first = store().recordAndRead("auto", 1, listOf(0), sourceId = "sms", stableEventId = "secret-message-id")
        assertEquals(TriggerHistoryStatus.RECORDED, first.status)
        val encoded = store().encodedHistoryForTest().orEmpty()
        assertFalse(encoded.contains("secret-message-id"))
        assertFalse(encoded.contains("sms"))
        assertTrue(encoded.contains("digest"))

        val second = store().recordAndRead("auto", 1, listOf(0), sourceId = "sms", stableEventId = "secret-message-id")
        assertEquals(TriggerHistoryStatus.DUPLICATE, second.status)
        assertEquals(1, second.events.size)
    }

    @Test
    fun revisionIsolationTtlAndClockRegressionAreFailClosed() = runTest {
        store().recordAndRead("auto", 1, listOf(0), sourceId = "s", stableEventId = "one")
        now += 20
        val revised = store().recordAndRead("auto", 2, listOf(1), sourceId = "s", stableEventId = "two")
        assertEquals(listOf(1), revised.events.map { it.triggerIndex })
        now += TriggerExpressionHistoryStore.RETENTION_MS + 1
        val expired = store().recordAndRead("auto", 2, listOf(2), sourceId = "s", stableEventId = "three")
        assertEquals(listOf(2), expired.events.map { it.triggerIndex })
        now = 2
        val reset = store().recordAndRead("auto", 2, listOf(3), sourceId = "s", stableEventId = "four")
        assertEquals(TriggerHistoryStatus.CLOCK_RESET, reset.status)
        assertEquals(listOf(3), reset.events.map { it.triggerIndex })
    }

    @Test
    fun missingIdentityIsNotAdmittedAndPermissionScopeCanBeCleared() = runTest {
        assertEquals(TriggerHistoryStatus.UNAVAILABLE,
            store().recordAndRead("auto", 1, listOf(0), sourceId = "", stableEventId = "id").status)
        store().recordAndRead("auto", 1, listOf(0, 1), sourceId = "s", stableEventId = "one")
        store().clearSourcePermissionScope("auto", setOf(0))
        val remaining = store().recordAndRead("auto", 1, listOf(1), sourceId = "s", stableEventId = "one")
        assertEquals(TriggerHistoryStatus.DUPLICATE, remaining.status)
        assertEquals(1, remaining.events.size)
    }

    @Test
    fun oversizedPersistedEnvelopeIsClearedBeforeJsonDecode() = runTest {
        backing.updateData {
            mutablePreferencesOf(
                stringPreferencesKey("records") to
                    "x".repeat(TriggerExpressionHistoryStore.MAX_SERIALIZED_BYTES + 1)
            )
        }
        val result = store().read("auto", 1, now)
        assertEquals(TriggerHistoryStatus.CORRUPT_RESET, result.status)
        assertEquals(null, store().encodedHistoryForTest())
    }

    private class MemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())
        override val data: StateFlow<Preferences> = state.asStateFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
